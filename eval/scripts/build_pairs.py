# -*- coding: utf-8 -*-
"""政策原文 ↔ 官方解读/新闻 配对锚定 —— expand-rag-eval-coverage

输入：eval/tmp/anchor-map.json（由 export_anchor_map.py 产出）
输出：eval/pairs.json + eval/audit/pairs-audit.md

匹配逻辑（按证据强度，命中即返回）：
  1. **标题书名号**（原有）：从解读类文档标题的《》提取政策名，与政策文件标题匹配
  2. **正文书名号**（新增，主力）：从正文提取《》，与政策书名号 / 政策标题匹配
  3. **正文文号**（新增）：从正文提取 〔YYYY〕NN号，与政策 fileNum 匹配

实测依据（2026-09-14，216 篇语料）：
  - 媒体视角正文含《》者 123/128，含文号者仅 3/128 → **正文书名号是主力，文号是补充**
  - 仅标题匹配时配对 31 组；启用正文匹配后 147 组（媒体视角 4 → 119）

原有 matched 语义与 unmatched 清单保持不变；新增字段：
  - matchType:     exact | contains | docnum   （匹配精度）
  - matchSource:   title | body               （证据位置）
  - matchEvidence: 命中的原文片段（≤40 字，可审计）

用法：
  python eval/scripts/build_pairs.py                # 默认启用正文匹配
  python eval/scripts/build_pairs.py --title-only   # 复现旧行为（仅标题书名号）
"""
import argparse
import json
import os
import re
import sys
from datetime import datetime

sys.stdout.reconfigure(encoding="utf-8")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib_eval import load_corpus_doc  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ANCHOR = os.path.join(os.path.dirname(HERE), "tmp", "anchor-map.json")
OUT = os.path.join(os.path.dirname(HERE), "pairs.json")

POLICY_SECTION = "政策文件"
INTERPRET_SECTIONS = ["部门解读", "新闻发布会", "媒体视角"]

BOOK_RE = re.compile(r"《([^》]+)》")
NUM_RE = re.compile(r"[\u4e00-\u9fa5A-Za-z]{0,12}[〔\[［]\s*(20\d{2})\s*[〕\]］]\s*(\d{1,4})\s*号")

# 书名号里的泛称，不能作为政策名（否则"《方案》"会乱配）
NOISE_BOOK = {"预案", "方案", "办法", "通知", "条例", "规定", "意见", "决定", "公告",
              "规划", "要点", "细则", "标准", "目录", "清单", "计划", "报告", "批复"}
MIN_BOOK_LEN = 4      # 书名号内容规范化后至少 4 字
MIN_CONTAIN_LEN = 6   # contains 匹配双方至少 6 字，防止短串误配


def norm(s: str) -> str:
    return re.sub(r"[\s、《》〈〉\"'“”‘’、，,。：:；;（）()\-—－]", "", s or "")


def build_index(policies: list[dict]) -> dict:
    """构建政策侧索引：书名号 / 完整标题 / 文号 / (年,序号)。"""
    idx = {"by_book": {}, "by_title": {}, "by_file_num": {}, "by_num_key": {}, "title_list": []}
    for p in policies:
        idx["by_title"][norm(p["title"])] = p
        for b in BOOK_RE.findall(p["title"]):
            idx["by_book"].setdefault(norm(b), p)
        fn = norm(p.get("fileNum"))
        if fn:
            idx["by_file_num"].setdefault(fn, p)
            m = NUM_RE.search(p["fileNum"] or "")
            if m:
                idx["by_num_key"].setdefault((m.group(1), m.group(2)), []).append(p)
        idx["title_list"].append((norm(p["title"]), p))
    return idx


def _try_names(names, source, idx, fallback):
    """在一组候选名上做 exact -> contains 匹配。返回 (命中, fallback)。"""
    for n in names:
        k = norm(n)
        if not k:
            continue
        p = idx["by_book"].get(k) or idx["by_title"].get(k)
        if p:
            return (p, "exact", source, n), fallback
        for kk, pp in idx["title_list"]:
            if kk and (k in kk or kk in k) and min(len(k), len(kk)) >= MIN_CONTAIN_LEN:
                if fallback is None:
                    fallback = (pp, "contains", source, n)
                break
    return None, fallback


def find_policy(doc_title: str, body: str, idx: dict, title_only: bool = False):
    """返回 (policy, matchType, matchSource, evidence) 或 None。

    标题源沿用原实现：无书名号时退化为「去掉 文字解读/政策解读/解读/问答/新闻发布会/答记者问
    后缀的标题主干」，否则 --title-only 无法复现旧结果。
    """
    fallback = None
    names = BOOK_RE.findall(doc_title or "")
    if not names:
        stem = re.sub(r"(文字解读|政策解读|解读|问答|新闻发布会|答记者问)$",
                      "", (doc_title or "").strip()).strip()
        if stem:
            names = [stem]
    hit, fallback = _try_names(names, "title", idx, fallback)
    if hit:
        return hit
    if title_only:
        return fallback

    # 正文书名号（主力；此处才应用泛称与长度过滤）
    body = body or ""
    body_names = [n for n in BOOK_RE.findall(body)
                  if len(norm(n)) >= MIN_BOOK_LEN and norm(n) not in NOISE_BOOK]
    hit, fallback = _try_names(body_names, "body", idx, fallback)
    if hit:
        return hit

    # 正文文号（补充；媒体视角正文极少出现文号）
    for m in NUM_RE.finditer(body):
        frag = m.group(0)
        p = idx["by_file_num"].get(norm(frag))
        if p:
            return p, "docnum", "body", frag
        cands = idx["by_num_key"].get((m.group(1), m.group(2)))
        if cands and len(cands) == 1:
            return cands[0], "docnum", "body", frag
    return fallback


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--title-only", action="store_true", help="只做标题书名号匹配（复现旧行为）")
    args = ap.parse_args()

    if not os.path.exists(ANCHOR):
        print("ERROR: 缺少 eval/tmp/anchor-map.json，请先运行 export_anchor_map.py")
        sys.exit(1)
    m = json.load(open(ANCHOR, encoding="utf-8"))
    docs = m["docs"]

    policies = [d for d in docs if d["section"] == POLICY_SECTION]
    idx = build_index(policies)

    pairs, unmatched = [], []
    n = 0
    for d in docs:
        if d["section"] not in INTERPRET_SECTIONS:
            continue
        body = load_corpus_doc(d["folder"]) or ""
        hit = find_policy(d["title"], body, idx, title_only=args.title_only)
        if hit:
            p, match_type, match_source, evidence = hit
            n += 1
            pairs.append({
                "pairId": f"p-{n:03d}",
                "policyDocId": p["docId"],
                "policyTitle": p["title"],
                "policyFileNum": p.get("fileNum") or "",
                "policyChunkCount": p["chunkCount"],
                "interpretDocId": d["docId"],
                "interpretTitle": d["title"],
                "interpretSection": d["section"],
                "interpretChunkCount": d["chunkCount"],
                "bookName": BOOK_RE.findall(p["title"])[0]
                            if BOOK_RE.findall(p["title"]) else p["title"],
                "matchType": match_type,
                "matchSource": match_source,
                "matchEvidence": (evidence or "")[:40],
                "reviewState": "pending",
            })
        else:
            unmatched.append({
                "docId": d["docId"],
                "title": d["title"],
                "section": d["section"],
                "reason": "标题与正文均无可匹配的政策名/文号"
                          if not args.title_only else "标题无可匹配书名号",
            })

    payload = {
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "source": "eval/tmp/anchor-map.json",
        "titleOnly": args.title_only,
        "policyCount": len(policies),
        "pairCount": len(pairs),
        "unmatchedCount": len(unmatched),
        "pairs": pairs,
        "unmatched": unmatched,
    }
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)

    by_sec, by_src, by_type = {}, {}, {}
    for p in pairs:
        by_sec[p["interpretSection"]] = by_sec.get(p["interpretSection"], 0) + 1
        by_src[p["matchSource"]] = by_src.get(p["matchSource"], 0) + 1
        by_type[p["matchType"]] = by_type.get(p["matchType"], 0) + 1

    print(f"OK -> {OUT}")
    print(f"配对 {len(pairs)} 组 | 政策文件 {len(policies)} 篇 | 未匹配 {len(unmatched)} 篇"
          f"{'（--title-only）' if args.title_only else ''}")
    for k, v in sorted(by_sec.items()):
        total = len([d for d in docs if d["section"] == k])
        print(f"  {k}: {v}/{total}")
    print(f"  证据位置 {by_src} | 匹配精度 {by_type}")

    # ---- 人工核对清单 eval/audit/pairs-audit.md ----
    import collections
    AUDIT = os.path.join(os.path.dirname(HERE), "audit", "pairs-audit.md")
    pol_cnt = collections.Counter(p["policyDocId"] for p in pairs)
    un_sec = collections.Counter(u["section"] for u in unmatched)
    L = []
    a = L.append
    a("# 政策原文 ↔ 解读/新闻 配对审计\n")
    a(f"- 生成时间：{payload['generatedAt']}")
    a(f"- 来源：`eval/tmp/anchor-map.json`（{len(docs)} 篇全量锚点映射）")
    a(f"- 模式：**{'仅标题书名号（--title-only）' if args.title_only else '标题 + 正文书名号 + 正文文号'}**")
    a(f"- 配对 **{len(pairs)}** 组，覆盖唯一政策 **{len(pol_cnt)}** 篇 / 政策文件共 {len(policies)} 篇\n")
    a("## 1. 覆盖情况\n")
    a("| 栏目 | 篇数 | 已配对 | 未配对 | 配对率 |")
    a("|---|---|---|---|---|")
    for sec in INTERPRET_SECTIONS:
        tot = len([d for d in docs if d["section"] == sec])
        got = len([p for p in pairs if p["interpretSection"] == sec])
        a(f"| {sec} | {tot} | {got} | {tot - got} | {(got / tot * 100 if tot else 0):.0f}% |")
    a(f"\n## 2. 匹配证据分布\n")
    a("| 证据位置 | 组数 |")
    a("|---|---|")
    for k, v in sorted(by_src.items()):
        a(f"| {k} | {v} |")
    a("\n| 匹配精度 | 组数 |")
    a("|---|---|")
    for k, v in sorted(by_type.items()):
        a(f"| {k} | {v} |")
    a(f"\n## 3. 未匹配 {len(unmatched)} 篇（{'、'.join(f'{k} {v}' for k, v in un_sec.items())}）")
    a("按 design D3 退化为单文档出题入口，不参与跨文档出题。\n")
    a("## 4. 待人工核对清单（把 reviewState 改为 confirmed / rejected）\n")
    a("| pairId | 政策标题 | 解读/新闻标题 | 栏目 | 证据位置 | 匹配精度 | 命中片段 |")
    a("|---|---|---|---|---|---|---|")
    for p in pairs:
        a(f"| {p['pairId']} | {p['policyTitle'][:40]} | {p['interpretTitle'][:40]} | "
          f"{p['interpretSection']} | {p['matchSource']} | {p['matchType']} | {p['matchEvidence'][:30]} |")
    os.makedirs(os.path.dirname(AUDIT), exist_ok=True)
    open(AUDIT, "w", encoding="utf-8").write("\n".join(L) + "\n")
    print(f"OK -> {AUDIT}")
    print("下一步：人工逐条核对，把 reviewState 从 pending 改为 confirmed / rejected")


if __name__ == "__main__":
    main()

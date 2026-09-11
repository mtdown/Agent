# -*- coding: utf-8 -*-
"""
政策原文 ↔ 官方解读 配对锚定 —— add-rag-eval-dataset / Task 2.1

输入：eval/tmp/anchor-map.json（由 export_anchor_map.py 产出）
输出：eval/pairs.json（人工核对后把 reviewState 置为 confirmed / rejected）

匹配逻辑：
  1. 从解读类文档标题的书名号《》中提取政策名
  2. 规范化后与政策文件标题做「精确相等 → 包含」两级匹配
  3. 未匹配者标记为 unmatched，不参与 A 类出题（仍留在检索池作干扰项）
"""
import json
import os
import re
import sys
from datetime import datetime

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
ANCHOR = os.path.join(os.path.dirname(HERE), "tmp", "anchor-map.json")
OUT = os.path.join(os.path.dirname(HERE), "pairs.json")

POLICY_SECTION = "政策文件"
INTERPRET_SECTIONS = ["部门解读", "新闻发布会", "媒体视角"]

BOOK_RE = re.compile(r"《([^》]+)》")


def norm(s: str) -> str:
    return re.sub(r"[\s、《》〈〉\"'“”‘’、，,。：:；;（）()\-—－]", "", s or "")


def main():
    if not os.path.exists(ANCHOR):
        print("ERROR: 缺少 eval/tmp/anchor-map.json，请先运行 export_anchor_map.py")
        sys.exit(1)
    m = json.load(open(ANCHOR, encoding="utf-8"))
    docs = m["docs"]

    policies = [d for d in docs if d["section"] == POLICY_SECTION]
    policy_by_norm = {norm(p["title"]): p for p in policies}
    policy_list = [(norm(p["title"]), p) for p in policies]

    pairs, unmatched = [], []
    idx = 0
    for d in docs:
        if d["section"] not in INTERPRET_SECTIONS:
            continue
        names = BOOK_RE.findall(d["title"])
        # 书名号为空时，退化用整标题（去掉常见后缀）尝试
        cands = names if names else [re.sub(r"(文字解读|政策解读|解读|问答|新闻发布会|答记者问)$", "", d["title"]).strip()]
        hit = None
        match_type = None
        book = None
        for n in cands:
            key = norm(n)
            if not key:
                continue
            if key in policy_by_norm:
                hit, match_type, book = policy_by_norm[key], "exact", n
                break
            for pk, p in policy_list:
                if pk and (key in pk or pk in key) and min(len(key), len(pk)) >= 6:
                    hit, match_type, book = p, "contains", n
                    break
            if hit:
                break
        if hit:
            idx += 1
            pairs.append({
                "pairId": f"p-{idx:02d}",
                "policyDocId": hit["docId"],
                "policyTitle": hit["title"],
                "policyFileNum": hit["fileNum"],
                "policyChunkCount": hit["chunkCount"],
                "interpretDocId": d["docId"],
                "interpretTitle": d["title"],
                "interpretSection": d["section"],
                "interpretChunkCount": d["chunkCount"],
                "bookName": book,
                "matchType": match_type,
                "reviewState": "pending",
            })
        else:
            unmatched.append({
                "docId": d["docId"],
                "title": d["title"],
                "section": d["section"],
                "reason": "标题无可匹配书名号或匹配不到政策文件",
            })

    payload = {
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "source": "eval/tmp/anchor-map.json",
        "policyCount": len(policies),
        "pairCount": len(pairs),
        "unmatchedCount": len(unmatched),
        "pairs": pairs,
        "unmatched": unmatched,
    }
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)

    by_sec = {}
    for p in pairs:
        by_sec[p["interpretSection"]] = by_sec.get(p["interpretSection"], 0) + 1
    print(f"OK -> {OUT}")
    print(f"配对 {len(pairs)} 组 | 政策文件 {len(policies)} 篇 | 未匹配 {len(unmatched)} 篇")
    for k, v in sorted(by_sec.items()):
        total = len([d for d in docs if d["section"] == k])
        print(f"  {k}: {v}/{total}")
    print("下一步：人工逐条核对，把 reviewState 从 pending 改为 confirmed / rejected")

    # ---- 产出人工核对清单 eval/audit/pairs-audit.md ----
    import collections
    AUDIT = os.path.join(os.path.dirname(HERE), "audit", "pairs-audit.md")
    pol_cnt = collections.Counter(p["policyDocId"] for p in pairs)
    un_sec = collections.Counter(u["section"] for u in unmatched)
    L = []
    a = L.append
    a("# 政策原文 ↔ 官方解读 配对审计\n")
    a(f"- 生成时间：{payload['generatedAt']}")
    a(f"- 来源：`eval/tmp/anchor-map.json`（216 篇全量锚点映射）")
    a(f"- 配对 **{len(pairs)}** 组，覆盖唯一政策 **{len(pol_cnt)}** 篇 / 政策文件共 {len(policies)} 篇")
    a("- 匹配方式说明：政策标题普遍形如「重庆市人民政府办公厅关于印发《X》的通知」，")
    a("  而解读标题书名号内只有「X」，因此**全部命中 contains（包含）匹配**，无一为 exact。")
    a("  这属于预期内的正常情况，但每对仍需人工确认。\n")
    a("## 1. 覆盖情况\n")
    a("| 栏目 | 篇数 | 已配对 | 未配对 |")
    a("|---|---|---|---|")
    for sec in INTERPRET_SECTIONS:
        tot = len([d for d in docs if d["section"] == sec])
        got = len([p for p in pairs if p["interpretSection"] == sec])
        a(f"| {sec} | {tot} | {got} | {tot - got} |")
    a(f"\n未匹配共 {len(unmatched)} 篇（{'、'.join(f'{k} {v}' for k, v in un_sec.items())}），")
    a("按 design D3 保留在检索池作干扰项，不参与 A 类出题。\n")
    multi = {k: v for k, v in pol_cnt.items() if v > 1}
    if multi:
        a(f"## 2. 一对多政策（{len(multi)} 篇政策被多篇解读引用）\n")
        a("同一政策被多次解读时，出题需注意问题去重：\n")
        a("| 政策标题 | 解读数 |")
        a("|---|---|")
        for k, v in multi.items():
            t = [p for p in pairs if p["policyDocId"] == k][0]["policyTitle"]
            a(f"| {t[:60]} | {v} |")
        a("")
    a("## 3. 待人工核对清单（把 reviewState 改为 confirmed / rejected）\n")
    a("| pairId | 政策标题 | 解读标题 | 栏目 | 匹配方式 | 确认 |")
    a("|---|---|---|---|---|---|")
    for p in pairs:
        a(f"| {p['pairId']} | {p['policyTitle'][:52]} | {p['interpretTitle'][:52]} | "
          f"{p['interpretSection']} | {p['matchType']} | {p['reviewState']} |")
    os.makedirs(os.path.dirname(AUDIT), exist_ok=True)
    open(AUDIT, "w", encoding="utf-8").write("\n".join(L) + "\n")
    print(f"OK -> {AUDIT}")


if __name__ == "__main__":
    main()

# -*- coding: utf-8 -*-
"""全库内容覆盖出题 —— expand-rag-eval-coverage

目标：让 216 篇语料每篇都产出候选题，把内容覆盖率从 15.3% 推向全量。

与 v1 `generate_pair_questions.py` 的差异：
  - v1 只能对「有配对的解读篇」出题（31 对封顶），本脚本对**任意一篇**文档出题
  - 分两种题型：
      crossdoc  问题来自解读/新闻篇，gold 锚**政策原文**（跨文档定位，难）
      single    问题与 gold 同在**本文档**（文档内定位，易）
  - 降级机制：crossdoc 定位失败**或答案可定位性校验未过**时，用本文档 chunk 重定位为 single
  - 五道质量闸门（G1~G5），全自动，无人工复核

配额依据 design D2：政策文件 2 / 部门解读 2 / 新闻发布会 2 / 媒体视角 1（媒体视角占语料 59%，
若不压配额会主导 overall 指标）。

闸门阈值校准（2026-09-14 小样本实测）：
  - detail（引用型）答案 3-gram 覆盖率 0.85~1.00；overview（归纳型）0.42~0.62
  - 故 G2 阈值取 0.35（低于归纳型下沿，仍能拦住"答案与 gold 无关"的题）
  - G5 指代词表须包含"这个预案/该办法"等，否则会漏放"这个预案讲了什么"这类题

全程只读业务库。中间结果缓存在 eval/tmp/ 便于断点续跑。

用法：
  python eval/scripts/generate_coverage_questions.py --dry-run            # 只打印计划
  python eval/scripts/generate_coverage_questions.py --sections 媒体视角 --limit 5
  python eval/scripts/generate_coverage_questions.py                      # 全量
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib_eval import (  # noqa: E402
    EVAL_DIR, TMP_DIR, char_sim, chat, extract_json, fetch_chunks,
    keyword_bonus, load_corpus_doc, load_env, norm_flat, quote_hit,
)

CACHE1 = os.path.join(TMP_DIR, "cov_stage1.json")
CACHE3 = os.path.join(TMP_DIR, "cov_stage3.json")
DROPPED = os.path.join(TMP_DIR, "dropped-coverage.json")

QUOTA = {"政策文件": 2, "部门解读": 2, "新闻发布会": 2, "媒体视角": 1}
TOP_K = 12
CHUNK_SNIPPET = 900
MAX_GOLD = 4
MAX_ANSWER_LEN = 200          # G3
G2_THRESHOLD = 0.35           # G2 答案可定位性（3-gram 覆盖率）
G4_THRESHOLD = 0.85           # G4 问题重复（bigram Jaccard）

SYS = "你是政务知识库评测数据集构建助手，输出必须是严格合法的 JSON，不要输出任何解释性文字。"

# G5 指代表述：问题不得依赖"被出题文档"这一语境，必须自带主题词
REF_WORDS = ("本文", "上述", "该解读", "该新闻", "该通知", "该文件", "本通知",
             "该方案", "此通知", "该报道", "上述文件", "该规划",
             "这个预案", "该预案", "这份方案", "该项政策", "该政策", "该办法",
             "该计划", "该纲要", "该条例", "该规定", "该意见", "该决定",
             "该规划纲要", "这份文件", "这项政策")

PROMPT1 = """下面是一篇**{doc_kind}**文章的全文（标题：{title}）。

请基于这篇文章，生成 {n} 个**普通市民或企业办事人员会真实提出的问题**，且这些问题必须能用文章的**原文**回答。

要求：
1. 问题用自然口语，像真实用户提问；不要出现"本文""上述文件""该解读""该通知"这类指代表述，要带上主题词（例如"重庆市地震应急预案""重庆市推进空中丝绸之路建设实施方案"）。
{n_rule}
2. 每题给出参考答案，答案**完全来自下面这篇文章的内容**，不得编造、不得引入文章之外的信息；答案控制在 150 字以内。
3. 每题额外给出 answerQuote：从下面这篇文章中**原样摘抄**的、支撑该答案的那一句（10-60 字，必须与原文完全一致，不要改写）。
4. 每题额外给出 evidenceKey：答案中最关键的 4-10 字术语（用于在原文中定位）。

输出格式（严格 JSON，不要 markdown 代码块）：
{{"questions":[{{"q":"问题","a":"参考答案","type":"overview|detail","answerQuote":"原文摘抄","evidenceKey":"关键术语"}}]}}

文章全文如下：
----------------
{body}
----------------"""

PROMPT3 = """下面是政策《{book}》原文的若干段落（编号 0 开始）。

问题：{q}
参考答案：{a}
关键术语：{key}

任务：找出**包含回答该问题所必需信息**的段落，并为每个选中段落**原样摘抄**其中能支撑答案的那一句原文（10-80 字，必须与段落文字逐字一致，不许改写或概括）。

硬性要求：
- 摘抄句必须真实出现在该段落中；如果某段落里找不到这样的句子，就不要选它。
- 不要为凑数选择只是"主题相关"但不含答案的段落。
- 若没有任何段落真正包含答案，输出 {{"support":[]}}。

输出格式（严格 JSON）：
{{"support":[{{"i":段落编号,"quote":"该段落中的原样摘抄"}}]}}

段落：
{chunks}"""


# ---------------- 质量闸门 ----------------

def ngrams(s: str, n: int = 3) -> set:
    t = norm_flat(s)
    return {t[i:i + n] for i in range(len(t) - n + 1)}


def gate_g2_answer_grounded(answer: str, gold_texts: list[str]) -> bool:
    """G2：答案的 3-gram 必须大部分能在 gold 段并集中找到。"""
    a = ngrams(answer, 3)
    if not a:
        return False
    g = ngrams("".join(gold_texts), 3)
    return len(a & g) / len(a) >= G2_THRESHOLD


def g4_is_dup(q: str, seen: list[set]) -> bool:
    """G4：与已收录问题字符 bigram Jaccard > 阈值即判重。"""
    a = ngrams(q, 2)
    if not a:
        return True
    for b in seen:
        if b and len(a & b) / len(a | b) > G4_THRESHOLD:
            return True
    return False


def g5_has_reference(q: str) -> bool:
    """G5：问题含指代表述。"""
    return any(w in q for w in REF_WORDS)


# ---------------- 出题与定位 ----------------

def gen_questions(cfg, body: str, title: str, n: int, doc_kind: str) -> list[dict]:
    """阶段 1：从一篇文章生成 n 个问题。"""
    n_rule = ("2. 第 1 题：要点/概览型（例如\"XX政策主要包括哪些方面的支持措施？\"）。\n"
              "3. 第 2 题：细节/指标型（含具体数字、对象、条件、时间节点或责任单位）。"
              if n >= 2 else
              "2. 只出 1 题：细节/指标型（含具体数字、对象、条件、时间节点或责任单位），"
              "优先选择能给出确切数字或明确条件的内容。")
    prompt = PROMPT1.format(doc_kind=doc_kind, title=title, n=n, n_rule=n_rule, body=body[:9000])
    st, content = chat(cfg, prompt, system=SYS, temperature=0.0, max_tokens=4000)
    if st != 200:
        return []
    obj = extract_json(content)
    qs = (obj or {}).get("questions") if isinstance(obj, dict) else obj
    if not isinstance(qs, list):
        return []
    return [q for q in qs if isinstance(q, dict) and q.get("q") and q.get("a")][:n]


def locate_gold(cfg, q: dict, chunks: list[dict], book: str) -> tuple[list[dict], int]:
    """阶段 2/3：在给定 chunk 池里定位 gold，返回 (support, 幻觉剔除数)。"""
    if not chunks:
        return [], 0
    query = " ".join(str(x) for x in (q["q"], q.get("a", ""), q.get("answerQuote", ""),
                                     q.get("evidenceKey", "")))
    scored = sorted(chunks,
                    key=lambda c: -(char_sim(query, c["text"]) + 0.6 * keyword_bonus(query, c["text"]))
                    )[:TOP_K]
    key = (q.get("evidenceKey") or "").strip()
    if len(key) >= 3:
        have = {c["chunkIndex"] for c in scored}
        for c in [c for c in chunks if key in c["text"]][:3]:
            if c["chunkIndex"] not in have:
                scored.append(c)
                have.add(c["chunkIndex"])
    snippet = "\n\n".join(f"[{n}] {c['text'][:CHUNK_SNIPPET]}" for n, c in enumerate(scored))
    st, content = chat(cfg, PROMPT3.format(book=book, q=q["q"], a=q["a"],
                                           key=q.get("evidenceKey", ""), chunks=snippet),
                       system=SYS, temperature=0.0, max_tokens=1200)
    if st != 200:
        return [], 0
    obj = extract_json(content)
    raw = obj.get("support") if isinstance(obj, dict) else None
    if not isinstance(raw, list):
        return [], 0
    sup, bad = [], 0
    for x in raw:
        if not isinstance(x, dict):
            continue
        try:
            i = int(x.get("i"))
        except Exception:  # noqa: BLE001
            continue
        if not (0 <= i < len(scored)):
            continue
        quote = str(x.get("quote", "")).strip()
        if not quote_hit(quote, scored[i]["text"]):   # G1：摘抄必须逐字命中原文
            bad += 1
            continue
        sup.append({"chunkIndex": scored[i]["chunkIndex"], "quote": quote[:120], "rank": i})
    return sorted(sup, key=lambda x: x["rank"])[:MAX_GOLD], bad


def locate_checked(cfg, q: dict, chunks: list[dict], book: str,
                   cache_key: str, cache3: dict, fresh: bool) -> list[dict] | None:
    """定位 gold 并跑 G2；任一不通过返回 None（由调用方决定是否降级）。"""
    if cache_key in cache3 and not fresh:
        sup = cache3[cache_key]
    else:
        sup, _bad = locate_gold(cfg, q, chunks, book)
        cache3[cache_key] = sup
    if not sup:
        return None
    want = {s["chunkIndex"] for s in sup}
    texts = [c["text"] for c in chunks if c["chunkIndex"] in want]
    return sup if gate_g2_answer_grounded(q["a"], texts) else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0, help="只处理前 N 篇（调试用）")
    ap.add_argument("--sections", default="", help="只处理指定栏目，逗号分隔")
    ap.add_argument("--out", default=os.path.join(EVAL_DIR, "candidates-coverage.jsonl"))
    ap.add_argument("--fresh", action="store_true", help="忽略缓存")
    ap.add_argument("--dry-run", action="store_true", help="只打印计划，不调用 LLM")
    ap.add_argument("--fill-missing", action="store_true",
                    help="补漏模式：读 --out 已有结果，只给「本文档未被 gold 覆盖」的文档"
                         "补 1 道强制锚本文档的题（禁用 crossdoc），其余文档全部跳过")
    args = ap.parse_args()

    cfg = load_env()
    anchor = json.load(open(os.path.join(TMP_DIR, "anchor-map.json"), encoding="utf-8"))
    pairs_doc = json.load(open(os.path.join(EVAL_DIR, "pairs.json"), encoding="utf-8"))
    docs = anchor["docs"]
    pair_of = {int(p["interpretDocId"]): p for p in pairs_doc["pairs"]}

    want = set(s for s in args.sections.split(",") if s)
    todo = [d for d in docs if (not want or d["section"] in want)]
    if args.limit:
        todo = todo[: args.limit]

    # ---- 补漏模式：只处理「本文档未被任何 gold 覆盖」的文档 ----
    base_rows, base_dropped, covered_self = [], [], set()
    if args.fill_missing:
        if not os.path.exists(args.out):
            raise SystemExit(f"--fill-missing 需要已存在的 {args.out}")
        base_rows = [json.loads(l) for l in open(args.out, encoding="utf-8") if l.strip()]
        for r in base_rows:
            self_id = (r.get("source") or {}).get("interpretDocId")
            if self_id and any(str(g.get("docId")) == str(self_id) for g in (r.get("gold") or [])):
                covered_self.add(int(self_id))
        base_dropped = (json.load(open(DROPPED, encoding="utf-8"))
                        if os.path.exists(DROPPED) else [])
        before = len(todo)
        todo = [d for d in todo if int(d["docId"]) not in covered_self]
        print(f"[补漏] 已有 {len(base_rows)} 题，本文档已覆盖 {len(covered_self)} 篇；"
              f"待补 {len(todo)}/{before} 篇")

    print(f"待处理 {len(todo)} 篇 | 配额 {QUOTA}")
    for k, v in Counter(d["section"] for d in todo).items():
        print(f"  {k}: {v} 篇 × {QUOTA.get(k, 1)} 题 = {v * QUOTA.get(k, 1)} 题")
    if args.dry_run:
        return

    all_ids = [int(d["docId"]) for d in docs]
    chunks_map = fetch_chunks(cfg, all_ids)
    print(f"已拉取 chunk：{len(chunks_map)} 篇 / {sum(len(v) for v in chunks_map.values())} 块")

    cache1 = {} if args.fresh else (json.load(open(CACHE1, encoding="utf-8")) if os.path.exists(CACHE1) else {})
    cache3 = {} if args.fresh else (json.load(open(CACHE3, encoding="utf-8")) if os.path.exists(CACHE3) else {})

    rows, dropped = list(base_rows), list(base_dropped)
    n_single = sum(1 for r in rows if r["category"] == "single")
    n_cross = sum(1 for r in rows if r["category"] == "crossdoc")
    n_fallback = 0
    seen_q: dict[str, list[set]] = {}
    for r in rows:                      # 预置已有题的去重指纹（补漏模式必需）
        seen_q.setdefault(r["category"], []).append(ngrams(r["question"], 2))
    stats = Counter()

    for i, d in enumerate(todo, 1):
        doc_id = int(d["docId"])
        sec, title = d["section"], d["title"]
        n_quota = QUOTA.get(sec, 1)
        body = load_corpus_doc(d["folder"]) or ""
        if len(body.strip()) < 200:
            dropped.append({"docId": doc_id, "title": title, "gate": "pre",
                            "reason": f"正文过短 ({len(body)} 字)"})
            stats["pre_short"] += 1
            continue

        pr = pair_of.get(doc_id)
        self_chunks = chunks_map.get(doc_id, [])
        pol_chunks = chunks_map.get(int(pr["policyDocId"]), []) if pr else []
        if args.fill_missing:
            pol_chunks = []   # 强制走本文档锚点，确保该篇进入 gold 覆盖
        pol_title = pr["policyTitle"] if pr else ""
        book = (pr or {}).get("bookName") or pol_title
        doc_kind = "政策文件" if sec == "政策文件" else "政策解读或新闻报道"

        # ---- 阶段 1：出题 ----
        # 补漏模式必须重新出题：若复用 cache1，会得到与已有 crossdoc 题
        # 完全相同的一句问话（只是 gold 换了文档），等于往基准里注入重复题。
        if doc_id in cache1 and not args.fresh and not args.fill_missing:
            qs = cache1[doc_id]
        else:
            qs = gen_questions(cfg, body, title, n_quota, doc_kind)
            cache1[doc_id] = qs
        if not qs:
            dropped.append({"docId": doc_id, "title": title, "gate": "stage1",
                            "reason": "阶段1 调用失败或 JSON 解析失败"})
            stats["stage1_fail"] += 1
            continue

        # ---- 阶段 2/3 + 降级 ----
        for j, q in enumerate(qs, 1):
            sup, cat, gold_doc_id, gold_title, match = None, "single", doc_id, title, "self"
            if pol_chunks:
                sup = locate_checked(cfg, q, pol_chunks, book, f"{doc_id}::{j}::pol", cache3, args.fresh)
                if sup:
                    cat, gold_doc_id, gold_title = "crossdoc", int(pr["policyDocId"]), pol_title
                    match = pr.get("matchSource", "") or "crossdoc"
                    stats["crossdoc_ok"] += 1
                else:
                    stats["crossdoc_fail"] += 1
            if not sup:
                self_key = f"{doc_id}::{j}::self"
                if self_key in cache3 and not args.fresh:
                    sup = cache3[self_key]
                else:
                    sup, _bad = locate_gold(cfg, q, self_chunks, title)
                    cache3[self_key] = sup
                if sup:
                    want_idx = {s["chunkIndex"] for s in sup}
                    texts = [c["text"] for c in self_chunks if c["chunkIndex"] in want_idx]
                    if not gate_g2_answer_grounded(q["a"], texts):
                        sup = None
                if sup and pol_chunks:
                    match = "fallback-self"
                    n_fallback += 1
            if not sup:
                dropped.append({"docId": doc_id, "j": j, "q": q["q"], "gate": "G1/G2",
                                "reason": "跨文档与本文档均无法定位到可用 gold 段"})
                stats["no_gold"] += 1
                continue

            # ---- G3 答案长度 ----
            if len(q["a"]) > MAX_ANSWER_LEN:
                dropped.append({"docId": doc_id, "j": j, "q": q["q"], "gate": "G3",
                                "reason": f"答案 {len(q['a'])} 字 > {MAX_ANSWER_LEN}"})
                stats["G3_too_long"] += 1
                continue
            # ---- G5 指代表述 ----
            if g5_has_reference(q["q"]):
                dropped.append({"docId": doc_id, "j": j, "q": q["q"], "gate": "G5",
                                "reason": "问题含指代表述"})
                stats["G5_reference"] += 1
                continue
            # ---- G4 问题去重 ----
            # 补漏模式下还要与已有 crossdoc 题比对，避免「同一问话、两个 gold」
            dup_pool = seen_q.setdefault(cat, [])
            if args.fill_missing and cat == "single":
                dup_pool = dup_pool + seen_q.get("crossdoc", [])
            if g4_is_dup(q["q"], dup_pool):
                dropped.append({"docId": doc_id, "j": j, "q": q["q"], "gate": "G4",
                                "reason": "与同类别已有问题重复"})
                stats["G4_dup"] += 1
                continue
            seen_q[cat].append(ngrams(q["q"], 2))

            qid = f"{'X' if cat == 'crossdoc' else 'S'}-{len(rows) + 1:03d}"
            rows.append({
                "id": qid,
                "category": cat,
                "question": q["q"].strip(),
                "answer": q["a"].strip(),
                "expectRefusal": False,
                "gold": [{"docId": gold_doc_id, "chunkIndex": s["chunkIndex"],
                          "why": (book or gold_title)[:40], "quote": s["quote"]} for s in sup],
                "source": {
                    "pairId": (pr or {}).get("pairId", ""),
                    "policyDocId": gold_doc_id,
                    "policyTitle": gold_title,
                    "interpretDocId": doc_id,
                    "interpretTitle": title,
                    "interpretSection": sec,
                    "docNumber": (pr or {}).get("policyFileNum", ""),
                    "kind": cat,
                    "docSection": sec,
                },
                "permission": None,
                "meta": {
                    "qType": q.get("type", ""),
                    "evidenceKey": q.get("evidenceKey", ""),
                    "answerQuote": q.get("answerQuote", ""),
                    "matchType": (pr or {}).get("matchType", "") if cat == "crossdoc" else "",
                    "matchEvidence": (pr or {}).get("matchEvidence", "") if cat == "crossdoc" else "",
                    "goldVerified": True,
                    "generatedBy": cfg["LLM_MODEL"],
                    "temperature": 0,
                    "reviewState": "auto",
                    "reviewedBy": "auto",
                    "reviewNote": match,
                },
            })
            n_cross += cat == "crossdoc"
            n_single += cat == "single"

        print(f"[{i}/{len(todo)}] {title[:30]} 完成，累计 {len(rows)} 题 "
              f"(crossdoc {n_cross} / single {n_single})")

    json.dump(cache1, open(CACHE1, "w", encoding="utf-8"), ensure_ascii=False)
    json.dump(cache3, open(CACHE3, "w", encoding="utf-8"), ensure_ascii=False)
    with open(args.out, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    json.dump(dropped, open(DROPPED, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    gold_docs = {g["docId"] for r in rows for g in r["gold"]}
    gold_chunks = {(g["docId"], g["chunkIndex"]) for r in rows for g in r["gold"]}
    print(f"\n产出 {len(rows)} 题 -> {args.out}")
    print(f"  crossdoc {n_cross} / single {n_single}（其中降级 {n_fallback} 题）")
    print(f"  覆盖文档 {len(gold_docs)}/{len(docs)} | 覆盖 chunk {len(gold_chunks)}/2061")
    print(f"  丢弃 {len(dropped)} 项 -> {DROPPED}")
    print(f"  闸门统计 {dict(stats)}")


if __name__ == "__main__":
    main()

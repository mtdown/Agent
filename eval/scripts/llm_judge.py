#!/usr/bin/env python3
"""用 LLM 对候选题做质量评审（LLM-as-Judge），输出 keep / edit / drop 建议。

设计要点：
- 分三套判据：有证据题（A/B/E）审「可答性 / 忠实性 / 证据充分性 / 问题自然度」；
  无答案题（C）审「是否真属库外、会不会误导系统硬答」；权限题（D）审「题面是否明确指向受限文档」。
- Judge 只做**预筛**，不替代人：人工只需复核 AI 标记 edit/drop 的题 + 抽样复核 keep 的题。
- 支持 --repeat 跑第二遍（temperature 0.3）计算 Judge 自身一致率，写进报告。
用法：
    python eval/scripts/llm_judge.py --limit 5        # 试跑
    python eval/scripts/llm_judge.py --fresh          # 全量
    python eval/scripts/llm_judge.py --repeat         # 一致性复跑
"""
import argparse
import collections
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib_eval import EVAL_DIR, TMP_DIR, chat, extract_json, fetch_chunks, load_env  # noqa: E402

CAND = os.path.join(EVAL_DIR, "candidates.jsonl")
OUT = os.path.join(EVAL_DIR, "ai-judge.jsonl")
CACHE = os.path.join(TMP_DIR, "judge-cache.json")
REPORT = os.path.join(EVAL_DIR, "audit", "ai-judge-report.md")

EVID_CHARS = 1400     # 单个 gold chunk 送入 judge 的最大字数
MAX_EVID = 5          # 最多送几个 gold chunk 全文
OTHER_SNIPPET = 160   # 非 gold chunk 的摘要长度（用于让 judge 判断 gold 是否漏标）

SYS = "你是中文政务领域 RAG 评测集的质量审核员。你只做严格审核，不迎合、不客气，发现问题就直说。"

PROMPT_EVID = """请审核下面这道**政策问答评测题**是否合格。

【问题】
{q}

【标准答案】
{a}

【该政策原文的全部段落】（编号连续。其中标 ★ 的是**已标注为证据**的段落，其余是同文档的其他段落）
{evid}

请严格打分并给出处置建议。

评分标准（1-5 分，5 分最好）：
1. answerability 可答性：综合**上面所有段落**（不限标 ★ 的），能否得出标准答案？
   5=完全可以；4=基本可以，缺极少量细节；3=能答一半；2=只能答很少一部分；1=完全答不出
2. faithfulness 忠实性：答案里每个事实是否都能在上面段落中找到依据？有无编造、外推或张冠李戴？
   5=完全忠实；4=一处措辞轻微外推但无事实错误；3=少量无依据表述；2=有实质错误；1=大段编造
3. sufficiency 标注完整性：**标 ★ 的段落集合**是否恰好覆盖回答该题所需的全部内容？
   5=恰到好处；4=多一个略相关段落或少一个次要段落；3=缺失或冗余较明显；2=严重缺失/冗余；1=完全不对
4. naturalness 问题自然度：是否像真实用户会问的话？表述清晰、无语病、不依赖上下文、不把答案直接写进题面？
   5=很自然；4=略生硬但不影响；3=有语病或不够像人话；2=依赖上下文才能理解；1=题面已含答案或根本不是问题

处置建议 verdict：
- keep：四项均 >= 4 分
- edit：存在 3 分项，或 sufficiency=3（**只需补/删 gold 段落即可修复**）
- drop：answerability <= 2 或 faithfulness <= 2（答案本身有问题，补证据也救不回来）

**关键**：若 sufficiency < 5，必须在 missing 中列出**应该补充标注为证据、但当前没标 ★ 的段落编号**（若当前标 ★ 的段落里有明显无关的，列入 redundant）。这样题目可以通过补 gold 修复，而不是丢弃。

输出严格 JSON（不要 markdown 代码块）：
{{"answerability":<1-5>,"faithfulness":<1-5>,"sufficiency":<1-5>,"naturalness":<1-5>,
"verdict":"keep|edit|drop","missing":[段落编号,...],"redundant":[段落编号,...],
"issues":["问题标签",...],"reason":"一句话说明主要问题或为什么合格",
"suggestion":"若 verdict!=keep，写出具体修改方案；否则空字符串"}}

问题标签只能从这些里选，可多选，没有就给空数组：
答案编造 / 答案不完整 / 证据缺失 / 证据无关 / 问题过泛 / 问题依赖上下文 / 题面含答案 / 语病 / 数字或文号错误 / 其他
"""

PROMPT_UNANS = """请审核下面这道**无答案题**（用于测试 RAG 系统在知识库没有相关内容时是否会如实拒答，而不是编造）。

【问题】
{q}

【背景】该知识库只收录重庆市政府及市级部门的政策文件、解读、新闻发布会与媒体报道，共 216 篇。
【该题设定的"库外"理由】{reason}

请判断：
1. plausibility 合理性（1-5）：按上述背景，这个主题**确实不可能**出现在该知识库吗？
   5=确实是库外内容；3=存疑，可能沾边；1=其实库里很可能就有
2. naturalness 问题自然度（1-5）：是否像真实用户会问的话？5=很自然；3=生硬；1=不像人会问
3. trap_risk 诱导硬答风险（1-5，分数越高越安全）：这个问题会不会逼着系统"无论如何也要答"？
   5=不会诱导；3=有一定诱导性；1=强烈诱导（例如题面已经把答案说出来了）

verdict：keep（三项均 >= 4）/ edit / drop（plausibility <= 2）

输出严格 JSON：
{{"plausibility":<1-5>,"naturalness":<1-5>,"trap_risk":<1-5>,"verdict":"keep|edit|drop",
"issues":["标签"],"reason":"一句话","suggestion":"..."}}
标签可选：疑似库内其实有 / 不像真实提问 / 诱导硬答 / 主题与政务无关 / 其他
"""

PROMPT_PERM = """请审核下面这道**权限隔离测试题**。

【问题】
{q}

【背景】该知识库按"空间"隔离，用户只能检索到自己有权限的空间内的文档。
本题要求：有权限用户应能检索到相关文档；无权限用户必须检索不到任何内容（0 命中）。

请判断：
1. specificity 明确性（1-5）：这个问题是否明确指向某个具体文档/政策，使得"能检索到"与"检索不到"有清晰判据？
   5=非常明确；3=较宽泛；1=过于宽泛，无法判断
2. naturalness 问题自然度（1-5）
3. sensitivity 敏感性（1-5）：该题能否真正检验越权泄漏——若系统泄漏了内容，会不会被判为"命中"？
   5=能清晰暴露泄漏；3=一般；1=即使泄漏也难判定

verdict：keep（三项均 >= 4）/ edit / drop（specificity <= 2）

输出严格 JSON：
{{"specificity":<1-5>,"naturalness":<1-5>,"sensitivity":<1-5>,"verdict":"keep|edit|drop",
"issues":["标签"],"reason":"一句话","suggestion":"..."}}
标签可选：指向不明确 / 不像真实提问 / 无法判定泄漏 / 其他
"""

DIM_MAP = {
    "evidence": ["answerability", "faithfulness", "sufficiency", "naturalness"],
    "unanswerable": ["plausibility", "naturalness", "trap_risk"],
    "permission": ["specificity", "naturalness", "sensitivity"],
}


def build_input(row: dict, chunks: dict) -> tuple[str, str, list]:
    """返回 (judge_type, prompt, idx_map)；idx_map[n] 为提示词中 [n] 对应的 chunkIndex。"""
    cat = row["category"]
    if cat == "unanswerable":
        return "unanswerable", PROMPT_UNANS.format(
            q=row["question"],
            reason=row.get("meta", {}).get("outOfScopeReason", "未注明"),
        ), []
    if cat == "permission":
        return "permission", PROMPT_PERM.format(q=row["question"]), []

    # A/B/E：送该政策文档的**全部** chunk——gold 给全文，其余给摘要。
    # 目的：让 judge 能区分「答案本身编造」与「gold 标注不全」，后者可通过补 gold 修复。
    gold_keys = {(int(g["docId"]), g["chunkIndex"]) for g in row.get("gold", [])}
    doc_id = next((int(g["docId"]) for g in row.get("gold", [])), None)
    evids = []
    idx_map: list = []          # 提示词里的编号 -> chunkIndex
    if doc_id is not None:
        for c in chunks.get(doc_id, []):
            n = len(evids)
            key = (doc_id, c["chunkIndex"])
            if key in gold_keys:
                t = c["text"]
                if len(t) > EVID_CHARS:
                    t = t[:EVID_CHARS] + " ……(截断)"
                evids.append(f"[★{n}] {t}")
            else:
                t = c["text"][:OTHER_SNIPPET].replace("\n", " ")
                evids.append(f"[{n}] {t}…")
            idx_map.append(c["chunkIndex"])
    evid = "\n".join(evids) if evids else "（本题未标注证据片段）"
    return "evidence", PROMPT_EVID.format(
        q=row["question"], a=row["answer"], evid=evid
    ), idx_map


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0, help="只审前 N 题")
    ap.add_argument("--only", default="", help="只审指定类别，逗号分隔，如 pair,docnum")
    ap.add_argument("--fresh", action="store_true", help="忽略缓存重跑")
    ap.add_argument("--repeat", action="store_true", help="跑第二遍算一致率")
    args = ap.parse_args()

    cfg = load_env()
    rows = [json.loads(l) for l in open(CAND, encoding="utf-8")]
    if args.only:
        want = {x.strip() for x in args.only.split(",") if x.strip()}
        rows = [r for r in rows if r["category"] in want]
    if args.limit:
        rows = rows[: args.limit]

    # 一次性拉取所需 chunk
    doc_ids = sorted({int(g["docId"]) for r in rows for g in r.get("gold", [])})
    chunks = fetch_chunks(cfg, doc_ids) if doc_ids else {}
    print(f"待审 {len(rows)} 题，涉及 {len(doc_ids)} 篇文档，已载入 {sum(len(v) for v in chunks.values())} 个 chunk")

    cache = {}
    if os.path.exists(CACHE) and not args.fresh:
        cache = json.load(open(CACHE, encoding="utf-8"))
    cache2 = {}
    if args.repeat:
        p2 = os.path.join(TMP_DIR, "judge-cache-run2.json")
        if os.path.exists(p2) and not args.fresh:
            cache2 = json.load(open(p2, encoding="utf-8"))

    results = []
    t0 = time.time()
    n_call = 0
    for i, r in enumerate(rows, 1):
        jtype, prompt, idx_map = build_input(r, chunks)
        dims = DIM_MAP[jtype]
        rec = {"id": r["id"], "category": r["category"], "judgeType": jtype,
               "question": r["question"], "scores": {}, "verdict": "error",
               "issues": [], "reason": "", "suggestion": "",
               "missing": [], "redundant": [],
               "missingChunks": [], "redundantChunks": []}

        def run(cache_obj, tag, temperature):
            nonlocal n_call
            if r["id"] in cache_obj and not args.fresh:
                return cache_obj[r["id"]]
            st, content = chat(cfg, prompt, system=SYS, temperature=temperature,
                               max_tokens=1200)
            n_call += 1
            if st != 200:
                print(f"[{i}/{len(rows)}] {r['id']} {tag} HTTP {st}")
                return None
            obj = extract_json(content)
            if not isinstance(obj, dict) or "verdict" not in obj:
                print(f"[{i}/{len(rows)}] {r['id']} {tag} JSON 解析失败: {str(content)[:120]}")
                return None
            item = {
                "judgeType": jtype,
                "scores": {d: obj.get(d) for d in dims},
                "verdict": obj.get("verdict", "error"),
                "issues": obj.get("issues", []) or [],
                "reason": str(obj.get("reason", ""))[:300],
                "suggestion": str(obj.get("suggestion", ""))[:300],
                "missing": obj.get("missing", []) or [],
                "redundant": obj.get("redundant", []) or [],
                # 把提示词里的段落编号映射回真实 chunkIndex，供后续自动补 gold
                "missingChunks": [idx_map[n] for n in (obj.get("missing") or [])
                                  if isinstance(n, int) and 0 <= n < len(idx_map)],
                "redundantChunks": [idx_map[n] for n in (obj.get("redundant") or [])
                                    if isinstance(n, int) and 0 <= n < len(idx_map)],
            }
            cache_obj[r["id"]] = item
            json.dump(cache_obj, open(
                os.path.join(TMP_DIR, f"judge-cache{tag}.json"), "w", encoding="utf-8"),
                ensure_ascii=False, indent=1)
            return item

        item = run(cache, "", 0.0)
        if item:
            rec.update(item)
        results.append(rec)

        if args.repeat:
            item2 = run(cache2, "-run2", 0.3)
            if item2:
                rec["verdict2"] = item2["verdict"]
        if i % 10 == 0:
            print(f"  进度 {i}/{len(rows)}，用时 {time.time()-t0:.0f}s")

    # 一致率
    agree = None
    if args.repeat:
        both = [x for x in results if x.get("verdict2")]
        same = sum(1 for x in both if x["verdict"] == x["verdict2"])
        agree = (same, len(both))

    # --only 评审时，保留其他类别的既有结论（避免覆盖整份 ai-judge.jsonl）
    merged = {x["id"]: x for x in results}
    if args.only and os.path.exists(OUT) and not args.fresh:
        for line in open(OUT, encoding="utf-8"):
            x = json.loads(line)
            merged.setdefault(x["id"], x)
    order = [json.loads(l)["id"] for l in open(CAND, encoding="utf-8")]
    results = [merged[i] for i in order if i in merged]

    with open(OUT, "w", encoding="utf-8") as f:
        for x in results:
            f.write(json.dumps(x, ensure_ascii=False) + "\n")

    # ---------- 报告 ----------
    os.makedirs(os.path.dirname(REPORT), exist_ok=True)
    cnt = collections.Counter(x["verdict"] for x in results)
    by_cat = collections.defaultdict(collections.Counter)
    for x in results:
        by_cat[x["category"]][x["verdict"]] += 1
    issues = collections.Counter()
    for x in results:
        for t in x.get("issues", []):
            issues[t] += 1

    w = REPORT
    with open(w, "w", encoding="utf-8") as f:
        f.write("# AI 评审报告（LLM-as-Judge）\n\n")
        f.write(f"- 评审模型：`{cfg['LLM_MODEL']}`，`temperature=0`，已关闭思考链\n")
        f.write(f"- 候选题：**{len(results)}** 道，LLM 调用 {n_call} 次，用时 {time.time()-t0:.0f} 秒\n")
        if agree:
            f.write(f"- **Judge 自身一致率：{agree[0]}/{agree[1]} = {agree[0]/max(1,agree[1])*100:.0f}%**"
                    f"（第二遍 temperature=0.3）\n")
        f.write("\n> Judge 只做预筛。**keep 的题仍建议抽样人工复核**，edit/drop 的题必须人工过一遍。\n\n")

        f.write("## 1. 总体结论\n\n| 处置 | 题数 | 占比 |\n|---|---|---|\n")
        for v in ["keep", "edit", "drop", "error"]:
            if cnt.get(v):
                f.write(f"| {v} | {cnt[v]} | {cnt[v]/len(results)*100:.0f}% |\n")

        f.write("\n## 2. 分类别结果\n\n| 类别 | keep | edit | drop | error | 小计 |\n|---|---|---|---|---|---|\n")
        for c in ["pair", "docnum", "synthetic", "unanswerable", "permission"]:
            if c not in by_cat:
                continue
            cc = by_cat[c]
            f.write(f"| {c} | {cc.get('keep',0)} | {cc.get('edit',0)} | {cc.get('drop',0)} "
                    f"| {cc.get('error',0)} | {sum(cc.values())} |\n")

        f.write("\n## 3. 问题标签分布\n\n| 标签 | 出现次数 |\n|---|---|\n")
        for t, n in issues.most_common():
            f.write(f"| {t} | {n} |\n")
        if not issues:
            f.write("| （无） | 0 |\n")

        f.write("\n## 4. 需要人工重点复核（edit + drop）\n\n")
        f.write("| 题号 | 类别 | 处置 | 主要问题 | 修改建议 |\n|---|---|---|---|---|\n")
        for x in results:
            if x["verdict"] in ("edit", "drop", "error"):
                f.write(f"| {x['id']} | {x['category']} | {x['verdict']} | "
                        f"{x['reason'][:60]} | {x['suggestion'][:60]} |\n")

        # 可修复题：sufficiency 低但答案本身没问题，补/删 gold 即可
        fixable = [x for x in results
                   if x["verdict"] in ("edit", "drop")
                   and (x.get("missingChunks") or x.get("redundantChunks"))
                   and isinstance(x["scores"].get("answerability"), int)
                   and x["scores"]["answerability"] >= 4
                   and x["scores"].get("faithfulness", 0) >= 4]
        f.write(f"\n## 5. 可通过补/删 gold 修复（{len(fixable)} 题）\n\n")
        f.write("这些题的**答案本身没问题**（可答性≥4 且忠实性≥4），只是证据标注不全或有冗余，"
                "补上 Judge 指出的 chunk 即可转为可用题，无需丢弃。\n\n")
        if fixable:
            f.write("| 题号 | 类别 | 当前处置 | 建议补充 chunkIndex | 建议移除 chunkIndex |\n|---|---|---|---|---|\n")
            for x in fixable:
                f.write(f"| {x['id']} | {x['category']} | {x['verdict']} | "
                        f"{x.get('missingChunks') or '—'} | {x.get('redundantChunks') or '—'} |\n")
        else:
            f.write("（无）\n")

        # 各维度均分
        f.write("\n## 6. 各维度平均分\n\n| 判据类型 | 维度 | 均分 |\n|---|---|---|\n")
        for jt, dims in DIM_MAP.items():
            sub = [x for x in results if x["judgeType"] == jt]
            if not sub:
                continue
            for d in dims:
                vals = [x["scores"].get(d) for x in sub if isinstance(x["scores"].get(d), (int, float))]
                if vals:
                    f.write(f"| {jt} | {d} | {sum(vals)/len(vals):.2f} |\n")

    print(f"\n完成：{OUT}")
    print("处置分布:", dict(cnt))
    if agree:
        print(f"Judge 一致率: {agree[0]}/{agree[1]}")
    print("报告:", REPORT)


if __name__ == "__main__":
    main()

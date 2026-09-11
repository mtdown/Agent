# -*- coding: utf-8 -*-
"""A 类配对题生成：从「官方解读」生成问题，答案取解读原文，gold 只绑政策原文 chunk。

三段式：
  1) LLM 读整篇解读 -> 2 题（1 要点 + 1 细节）+ 参考答案
  2) 字符 bigram 粗排在政策原文 chunk 上取 top-K 候选
  3) LLM 从候选 chunk 中精筛真正支持答案的 chunkIndex（可为空 -> 丢弃）

全程只读业务库。中间结果缓存在 eval/tmp/ 便于断点续跑。
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib_eval import (  # noqa: E402
    EVAL_DIR, TMP_DIR, chat, db_conn, extract_json, fetch_chunks,
    keyword_bonus, char_sim, load_corpus_doc, load_corpus_meta, load_env, quote_hit,
)

CACHE1 = os.path.join(TMP_DIR, "qa_stage1.json")
CACHE3 = os.path.join(TMP_DIR, "qa_stage3.json")
TOP_K = 12
CHUNK_SNIPPET = 900
MAX_GOLD = 4  # overview 题最多绑 4 个 chunk，避免 gold 过宽拖垮指标可解释性

SYS = "你是政务知识库评测数据集构建助手，输出必须是严格合法的 JSON，不要输出任何解释性文字。"

PROMPT1 = """下面是一篇**官方政策解读**文章的全文（政策名：《{book}》）。

请基于这篇解读，生成 {n} 个**普通市民或企业办事人员会真实提出的问题**，且这些问题必须能用该政策**原文**回答。

要求：
1. 问题用自然口语，像真实用户提问；不要出现"本文""上述文件""该解读"这类指代表述，要带上政策主题词。
2. 第 1 题：要点/概览型（例如"XX政策主要包括哪些方面的支持措施？"）。
3. 第 2 题：细节/指标型（含具体数字、对象、条件、时间节点或责任单位，且能在政策原文中找到确切依据）。
4. 每题给出参考答案，答案**完全来自下面这篇解读的内容**，不得编造、不得引入解读之外的信息；答案控制在 120 字以内。
5. 每题额外给出 answerQuote：从下面这篇解读中**原样摘抄**的、支撑该答案的那一句（10-60 字，必须与解读文字完全一致，不要改写）。
6. 每题额外给出 evidenceKey：答案中最关键的 4-10 字术语（用于在政策原文中定位）。

输出格式（严格 JSON，不要 markdown 代码块）：
{{"questions":[{{"q":"问题","a":"参考答案","type":"overview|detail","answerQuote":"解读原文摘抄","evidenceKey":"关键术语"}}]}}

解读全文如下：
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


def norm_title(s: str) -> str:
    import re
    return re.sub(r"[\s、《》〈〉\"'“”‘’]", "", s or "")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0, help="只处理前 N 组配对（调试用）")
    ap.add_argument("--assume-confirmed", action="store_true", help="把 pending 视为 confirmed")
    ap.add_argument("--only", default="", help="只处理指定 pairId，逗号分隔")
    ap.add_argument("--out", default=os.path.join(EVAL_DIR, "candidates-A.jsonl"))
    ap.add_argument("--fresh", action="store_true", help="忽略阶段1/3缓存，重新调用 LLM")
    args = ap.parse_args()

    cfg = load_env()
    pairs_doc = json.load(open(os.path.join(EVAL_DIR, "pairs.json"), encoding="utf-8"))
    anchor = json.load(open(os.path.join(TMP_DIR, "anchor-map.json"), encoding="utf-8"))

    # folder 索引：metadataId -> folder/section
    folder_of = {d["metadataId"]: (d["folder"], d["section"]) for d in anchor["docs"]}

    pairs = pairs_doc["pairs"]
    if args.assume_confirmed:
        pairs = [p for p in pairs if p.get("reviewState") in ("confirmed", "pending")]
    else:
        pairs = [p for p in pairs if p.get("reviewState") == "confirmed"]
    if args.only:
        want = set(args.only.split(","))
        pairs = [p for p in pairs if p["pairId"] in want]
    if args.limit:
        pairs = pairs[: args.limit]
    if not pairs:
        raise SystemExit("没有可用的 confirmed 配对（可加 --assume-confirmed）")

    print(f"待处理配对 {len(pairs)} 组")

    cache1 = {} if args.fresh else (json.load(open(CACHE1, encoding="utf-8")) if os.path.exists(CACHE1) else {})
    cache3 = {} if args.fresh else (json.load(open(CACHE3, encoding="utf-8")) if os.path.exists(CACHE3) else {})

    # 预取所有政策 chunk
    doc_ids = [int(p["policyDocId"]) for p in pairs]
    chunks_map = fetch_chunks(cfg, doc_ids)
    print(f"已拉取政策 chunk：{len(chunks_map)} 篇 / {sum(len(v) for v in chunks_map.values())} 块")

    out_rows = []
    dropped = []
    n_call = 0

    for i, pr in enumerate(pairs, 1):
        pid = pr["pairId"]
        pol_id = int(pr["policyDocId"])
        folder, section = folder_of.get(str(pr.get("interpretDocId")), (None, None))
        # 解读篇在 anchor 里通过 docId 找 metadataId
        meta_id = None
        for d in anchor["docs"]:
            if int(d["docId"]) == int(pr["interpretDocId"]):
                meta_id, folder = d["metadataId"], d["folder"]
                break
        if not meta_id:
            dropped.append({"pairId": pid, "reason": "解读篇未在 anchor-map 中找到"})
            continue
        body = load_corpus_doc(folder)
        if not body or len(body.strip()) < 200:
            dropped.append({"pairId": pid, "reason": f"解读正文过短或缺失 ({len(body or '')} 字)"})
            continue
        body = body[:9000]

        # ---------- 阶段 1：生成问题 ----------
        if pid in cache1 and not args.fresh:
            qs = cache1[pid]
        else:
            prompt = PROMPT1.format(book=pr.get("bookName") or pr["policyTitle"], n=2, body=body)
            st, content = chat(cfg, prompt, system=SYS, temperature=0.0, max_tokens=4000)
            n_call += 1
            if st != 200:
                print(f"[{i}/{len(pairs)}] {pid} 阶段1 失败 HTTP {st}: {str(content)[:150]}")
                dropped.append({"pairId": pid, "reason": f"阶段1调用失败 {st}"})
                continue
            obj = extract_json(content)
            qs = (obj or {}).get("questions") if isinstance(obj, dict) else obj
            if not isinstance(qs, list) or not qs:
                print(f"[{i}/{len(pairs)}] {pid} 阶段1 JSON 解析失败，原文前200: {str(content)[:200]}")
                dropped.append({"pairId": pid, "reason": "阶段1 JSON 解析失败"})
                continue
            qs = [q for q in qs if isinstance(q, dict) and q.get("q") and q.get("a")][:2]
            cache1[pid] = qs
            json.dump(cache1, open(CACHE1, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

        chunks = chunks_map.get(pol_id, [])
        if not chunks:
            dropped.append({"pairId": pid, "reason": "政策篇无 ACTIVE chunk"})
            continue

        # ---------- 阶段 2/3：定位证据 ----------
        for j, q in enumerate(qs):
            qid = f"A-{pid}-{j+1}"
            # 查询串 = 问题 + 答案 + 解读原文摘抄 + 关键术语（原文摘抄与政策措辞最接近，检索最准）
            query = " ".join(str(x) for x in
                             [q["q"], q.get("a", ""), q.get("answerQuote", ""), q.get("evidenceKey", "")])
            scored = sorted(
                chunks,
                key=lambda c: -(char_sim(query, c["text"]) + 0.6 * keyword_bonus(query, c["text"])),
            )[:TOP_K]
            # 强制并入含关键术语的 chunk（最多 3 个），防止粗排漏掉真证据
            key = (q.get("evidenceKey") or "").strip()
            if len(key) >= 3:
                forced = [c for c in chunks if key in c["text"]][:3]
                have = {c["chunkIndex"] for c in scored}
                for c in forced:
                    if c["chunkIndex"] not in have:
                        scored.append(c)
                        have.add(c["chunkIndex"])
            cands = [c["chunkIndex"] for c in scored]

            ck = f"{pid}::{j}"
            if ck in cache3 and not args.fresh:
                sup = cache3[ck]
            else:
                snippet = "\n\n".join(
                    f"[{n}] {c['text'][:CHUNK_SNIPPET]}" for n, c in enumerate(scored)
                )
                prompt = PROMPT3.format(
                    book=pr.get("bookName") or pr["policyTitle"],
                    q=q["q"], a=q["a"], key=q.get("evidenceKey", ""), chunks=snippet,
                )
                st, content = chat(cfg, prompt, system=SYS, temperature=0.0, max_tokens=1200)
                n_call += 1
                if st != 200:
                    print(f"[{i}/{len(pairs)}] {qid} 阶段3 失败 HTTP {st}")
                    continue
                obj = extract_json(content)
                raw = obj.get("support") if isinstance(obj, dict) else None
                if not isinstance(raw, list):
                    print(f"[{i}/{len(pairs)}] {qid} 阶段3 JSON 解析失败: {str(content)[:150]}")
                    continue
                # 校验：摘抄句必须真实出现在对应段落中，否则判为幻觉并剔除
                sup, bad = [], 0
                for x in raw:
                    if not isinstance(x, dict):
                        continue
                    try:
                        n = int(x.get("i"))
                    except Exception:  # noqa: BLE001
                        continue
                    if not (0 <= n < len(scored)):
                        continue
                    quote = str(x.get("quote", "")).strip()
                    if not quote_hit(quote, scored[n]["text"]):
                        bad += 1
                        continue
                    sup.append({
                        "chunkIndex": scored[n]["chunkIndex"],
                        "quote": quote[:120],
                        "rank": n,
                    })
                if bad:
                    print(f"[{i}/{len(pairs)}] {qid} 剔除 {bad} 个摘抄不匹配的段落（疑似幻觉）")
                cache3[ck] = sup
                json.dump(cache3, open(CACHE3, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

            if not sup:
                dropped.append({"pairId": pid, "qid": qid, "q": q["q"],
                                "reason": "政策原文中无段落能通过摘抄校验（无支撑或全是幻觉）"})
                continue
            # 按粗排相似度顺序保留（rank 越小越相关），限制 gold 宽度
            sup = sorted(sup, key=lambda x: x["rank"])[:MAX_GOLD]

            out_rows.append({
                "id": qid,
                "category": "pair",
                "question": q["q"].strip(),
                "answer": q["a"].strip(),
                "expectRefusal": False,
                "gold": [
                    {"docId": pol_id, "chunkIndex": s["chunkIndex"],
                     "why": (pr.get("bookName") or pr["policyTitle"])[:40], "quote": s["quote"]}
                    for s in sup
                ],
                "source": {
                    "pairId": pid,
                    "policyDocId": pol_id,
                    "policyTitle": pr["policyTitle"],
                    "interpretDocId": int(pr["interpretDocId"]),
                    "interpretTitle": pr["interpretTitle"],
                    "interpretSection": pr.get("interpretSection", ""),
                    "docNumber": pr.get("policyFileNum") or "",
                },
                "permission": None,
                "meta": {
                    "qType": q.get("type", ""),
                    "evidenceKey": q.get("evidenceKey", ""),
                    "answerQuote": q.get("answerQuote", ""),
                    "candidateChunks": cands,
                    "goldVerified": True,
                    "generatedBy": f"{cfg['LLM_MODEL']}",
                    "temperature": 0,
                    "reviewState": "pending",
                    "reviewNote": "",
                },
            })
        print(f"[{i}/{len(pairs)}] {pid} 完成，累计 {len(out_rows)} 题 / LLM 调用 {n_call}")

    with open(args.out, "w", encoding="utf-8") as f:
        for r in out_rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    json.dump(dropped, open(os.path.join(TMP_DIR, "dropped-A.json"), "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    print(f"\n产出 {len(out_rows)} 题 -> {args.out}")
    print(f"丢弃 {len(dropped)} 项 -> eval/tmp/dropped-A.json")
    print(f"LLM 调用 {n_call} 次")


if __name__ == "__main__":
    main()

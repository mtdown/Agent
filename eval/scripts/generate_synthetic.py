# -*- coding: utf-8 -*-
"""E 类合成补充题：从政策原文 chunk 直接生成，补齐 A 类未覆盖的长尾政策。

与 A 类区别：答案直接来自政策原文，因此 gold 确定（即源 chunk），
但仍要求模型摘抄原句并通过校验，防止改写导致的假 gold。
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib_eval import (  # noqa: E402
    EVAL_DIR, TMP_DIR, chat, extract_json, fetch_chunks, load_env, quote_hit,
)

CACHE = os.path.join(TMP_DIR, "qa_synth.json")
SYS = "你是政务知识库评测数据集构建助手，输出必须是严格合法的 JSON，不要输出任何解释性文字。"

PROMPT = """下面是重庆市某政策文件正文中的一个段落。

请基于**且仅基于**这个段落，生成 1 个普通市民或企业办事人员会真实提出的问题，并给出参考答案。

要求：
1. 问题用自然口语，带上政策主题词，不要出现"本段""上述内容"这类指代表述。
2. 答案必须完全来自该段落，不得引入段落之外的信息；控制在 120 字以内。
3. 额外给出 quote：从该段落中**原样摘抄**的、支撑答案的那一句（10-80 字，逐字一致，不许改写）。
4. 额外给出 evidenceKey：答案中最关键的 4-10 字术语。
5. 若该段落信息量不足（例如只有目录、只有落款、只有表格碎片），直接输出 {{"skip":true}}。

输出格式（严格 JSON）：
{{"q":"问题","a":"参考答案","quote":"段落原样摘抄","evidenceKey":"关键术语"}}

段落：
{chunk}

政策标题：{title}"""


def info_density(text: str) -> float:
    """启发式：偏好含数字、条款号、责任单位的中等长度段落。"""
    t = text or ""
    score = 0.0
    score += min(len(re.findall(r"\d", t)), 40) * 1.0
    score += min(len(re.findall(r"[（(]\s*[一二三四五六七八九十]+\s*[)）]", t)), 8) * 6
    score += min(len(re.findall(r"责任单位|牵头单位|市发展改革委|市财政局|各区县", t)), 5) * 5
    score += min(len(re.findall(r"到20\d\d年|自.*?起|有效期", t)), 4) * 4
    if len(t) < 300:
        score -= 30
    if len(t) > 2000:
        score -= 20
    # 目录型段落（大量连续的 "1.1 xxx" 短行）降权
    if len(re.findall(r"\d+\.\d+\s", t)) > 8:
        score -= 40
    return score


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--count", type=int, default=12)
    ap.add_argument("--out", default=os.path.join(EVAL_DIR, "candidates-E.jsonl"))
    ap.add_argument("--fresh", action="store_true")
    args = ap.parse_args()

    cfg = load_env()
    anchor = json.load(open(os.path.join(TMP_DIR, "anchor-map.json"), encoding="utf-8"))

    covered = set()
    a_path = os.path.join(EVAL_DIR, "candidates-A.jsonl")
    if os.path.exists(a_path):
        for line in open(a_path, encoding="utf-8"):
            covered.add(json.loads(line)["source"]["policyDocId"])
    b_path = os.path.join(EVAL_DIR, "candidates-B.jsonl")
    if os.path.exists(b_path):
        for line in open(b_path, encoding="utf-8"):
            covered.add(json.loads(line)["source"]["policyDocId"])

    pol = [d for d in anchor["docs"] if d["section"] == "政策文件"]
    # 优先未覆盖、chunk 多的政策
    pol.sort(key=lambda d: (int(d["docId"]) in covered, -d["chunkCount"]))
    picked = pol[: max(6, args.count)]

    chunks_map = fetch_chunks(cfg, [int(d["docId"]) for d in picked])
    cache = {} if args.fresh else (json.load(open(CACHE, encoding="utf-8")) if os.path.exists(CACHE) else {})

    rows, stat = [], {"skip": 0, "fail": 0, "badquote": 0}
    for d in picked:
        if len(rows) >= args.count:
            break
        cs = [c for c in chunks_map.get(int(d["docId"]), []) if c["chunkIndex"] > 0]
        if not cs:
            continue
        cs_sorted = sorted(cs, key=lambda c: -info_density(c["text"]))
        for c in cs_sorted[:2]:
            if len(rows) >= args.count:
                break
            ck = f"{d['docId']}::{c['chunkIndex']}"
            if ck in cache and not args.fresh:
                res = cache[ck]
            else:
                prompt = PROMPT.format(chunk=c["text"][:1800], title=d["title"])
                st, content = chat(cfg, prompt, system=SYS, temperature=0.0, max_tokens=1500)
                if st != 200:
                    stat["fail"] += 1
                    continue
                obj = extract_json(content)
                if not isinstance(obj, dict) or obj.get("skip"):
                    stat["skip"] += 1
                    cache[ck] = {"skip": True}
                    json.dump(cache, open(CACHE, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
                    continue
                res = {
                    "q": str(obj.get("q", "")).strip(),
                    "a": str(obj.get("a", "")).strip(),
                    "quote": str(obj.get("quote", "")).strip(),
                    "evidenceKey": str(obj.get("evidenceKey", "")).strip(),
                }
                cache[ck] = res
                json.dump(cache, open(CACHE, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

            if not res.get("q") or not res.get("a"):
                stat["fail"] += 1
                continue
            quote = res.get("quote", "")
            if quote and not quote_hit(quote, c["text"]):
                stat["badquote"] += 1
                continue
            rows.append({
                "id": f"E-{len(rows)+1:02d}",
                "category": "synthetic",
                "question": res["q"],
                "answer": res["a"],
                "expectRefusal": False,
                "gold": [{
                    "docId": int(d["docId"]), "chunkIndex": c["chunkIndex"],
                    "why": d["title"][:40], "quote": quote[:120],
                }],
                "source": {
                    "pairId": "", "policyDocId": int(d["docId"]),
                    "policyTitle": d["title"], "interpretDocId": 0,
                    "interpretTitle": "", "interpretSection": "政策文件",
                    "docNumber": d.get("fileNum") or "",
                },
                "permission": None,
                "meta": {
                    "qType": "synthetic",
                    "evidenceKey": res.get("evidenceKey", ""),
                    "answerQuote": quote,
                    "candidateChunks": [c["chunkIndex"]],
                    "goldVerified": bool(quote),
                    "generatedBy": cfg["LLM_MODEL"],
                    "temperature": 0,
                    "reviewState": "pending",
                    "reviewNote": "",
                },
            })

    with open(args.out, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"产出 {len(rows)} 题 -> {args.out}")
    print(f"跳过(信息量不足) {stat['skip']} | 调用失败 {stat['fail']} | 摘抄校验失败 {stat['badquote']}")


if __name__ == "__main__":
    main()

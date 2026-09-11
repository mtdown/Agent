# -*- coding: utf-8 -*-
"""B 类文号题生成：问题含完整文号，专测文号精确匹配层。

gold 策略：跳过 chunk 0（front-matter，含文号原文，会让题目退化为字符串匹配），
绑定政策正文开头的 2 个 chunk。

只读业务库。
"""
from __future__ import annotations

import argparse
import json
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib_eval import EVAL_DIR, TMP_DIR, fetch_chunks, load_env  # noqa: E402

CLEAN_TITLE_RE = re.compile(r"《([^》]+)》")


def clean_subject(title: str) -> str:
    m = CLEAN_TITLE_RE.search(title or "")
    if m:
        return m.group(1)
    t = re.sub(r"^.*?关于", "", title or "")
    return t or title


def first_sentence(text: str, limit: int = 100) -> str:
    t = re.sub(r"\s+", " ", (text or "").replace("---", " ")).strip()
    # 去掉 front-matter 残留
    t = re.sub(r"^(title|fileNum|pubDate|sourceUrl|column|channel|metadataId|bodyShort)\s*[:：].*?(?=\s|$)", "", t).strip()
    for sep in ("。", "；", "！", "?"):
        i = t.find(sep)
        if 0 < i < limit:
            return t[: i + 1]
    return t[:limit]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--count", type=int, default=10)
    ap.add_argument("--seed", type=int, default=20260911)
    ap.add_argument("--out", default=os.path.join(EVAL_DIR, "candidates-B.jsonl"))
    args = ap.parse_args()

    cfg = load_env()
    anchor = json.load(open(os.path.join(TMP_DIR, "anchor-map.json"), encoding="utf-8"))
    # 注：不要从 corpus-audit.md 反解 folder 编号做过滤（表格里的数字不代表空壳，会误删）
    # 空壳过滤交给下面的「正文总长 + chunk 数」硬约束
    pol = [d for d in anchor["docs"] if d.get("fileNum") and d["section"] == "政策文件"]
    if not pol:
        raise SystemExit("没有可用的带文号政策")

    chunks_map = fetch_chunks(cfg, [int(d["docId"]) for d in pol])
    # 过滤掉正文过短的
    usable = []
    for d in pol:
        cs = chunks_map.get(int(d["docId"]), [])
        body = [c for c in cs if c["chunkIndex"] > 0]
        total = sum(len(c["text"]) for c in body)
        if total >= 800 and len(body) >= 2:
            usable.append((d, body))
    if not usable:
        raise SystemExit("没有正文足够长的政策")

    random.seed(args.seed)
    random.shuffle(usable)
    picked = usable[: args.count]

    rows = []
    for i, (d, body) in enumerate(picked, 1):
        subj = clean_subject(d["title"])
        gold_chunks = [c["chunkIndex"] for c in body[:2]]
        head = first_sentence(body[0]["text"])
        tmpl = i % 3
        if tmpl == 1:
            q = f"{d['fileNum']} 这份文件是关于什么内容的？"
        elif tmpl == 2:
            q = f"请介绍一下 {d['fileNum']} 文件的主要内容。"
        else:
            q = f"文号 {d['fileNum']} 对应的政策文件，主要规定了哪些方面？"
        a = f"即《{subj}》。{head}"

        rows.append({
            "id": f"B-{i:02d}",
            "category": "docnum",
            "question": q,
            "answer": a,
            "expectRefusal": False,
            "gold": [
                {"docId": int(d["docId"]), "chunkIndex": ci,
                 "why": f"{subj} 正文开头", "quote": ""}
                for ci in gold_chunks
            ],
            "source": {
                "pairId": "",
                "policyDocId": int(d["docId"]),
                "policyTitle": d["title"],
                "interpretDocId": 0,
                "interpretTitle": "",
                "interpretSection": "政策文件",
                "docNumber": d["fileNum"],
            },
            "permission": None,
            "meta": {
                "qType": "docnum",
                "evidenceKey": d["fileNum"],
                "answerQuote": "",
                "candidateChunks": [c["chunkIndex"] for c in body[:6]],
                "goldVerified": False,
                "generatedBy": "rule",
                "temperature": 0,
                "reviewState": "pending",
                "reviewNote": "",
            },
        })

    with open(args.out, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"带文号政策 {len(pol)} 篇，可用(正文≥800字) {len(usable)} 篇")
    print(f"产出 {len(rows)} 题 -> {args.out}")
    for r in rows[:3]:
        print(f"  {r['id']}: {r['question']}")


if __name__ == "__main__":
    main()

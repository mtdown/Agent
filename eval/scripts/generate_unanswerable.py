# -*- coding: utf-8 -*-
"""C 类无答案题：问题指向知识库中确实不存在的内容，期望系统拒答（expectRefusal=true）。

硬约束：每题的「核心实体词」必须在本库 ACTIVE chunk 中 0 命中（虚构文号则须在
docNumber 中不存在），否则该不成立，直接剔除并说明理由。

注意：四川/成都等词在本库大量出现（川渝通办联合发文），**不能**当库外实体使用。
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib_eval import EVAL_DIR, db_conn, load_env  # noqa: E402

# (问题, 核心实体词列表, 库外理由)
CANDIDATES = [
    ("重庆市养犬管理条例规定，个人养犬需要办理哪些手续？",
     ["养犬", "犬只"], "本库无养犬管理相关文件"),
    ("在重庆办理婚姻登记需要携带哪些材料？",
     ["婚姻登记"], "本库无婚姻登记办事文件"),
    ("重庆市的住房补贴怎么申请，需要满足什么条件？",
     ["住房补贴"], "本库无住房补贴政策"),
    ("重庆医保报销的比例是多少，起付线怎么算？",
     ["医保报销"], "本库无医保报销待遇文件"),
    ("重庆小客车指标摇号怎么申请？",
     ["摇号"], "本库无小客车指标调控文件"),
    ("在重庆办理护照需要多长时间，去哪里办？",
     ["护照"], "本库无出入境证件办理文件"),
    ("外地户口迁入重庆需要满足哪些条件？",
     ["户籍迁入"], "本库无户籍迁入办事文件"),
    ("重庆办理食品经营许可证需要提交什么材料？",
     ["食品经营许可"], "本库无食品经营许可办事文件"),
    ("重庆建设工程消防验收的流程是怎样的？",
     ["消防验收"], "本库无消防验收办事文件"),
    ("重庆的学区是怎么划分的？",
     ["学区"], "本库无义务教育学区划分文件"),
    ("渝府发〔2026〕888号 文件的主要内容是什么？",
     ["渝府发〔2026〕888号"], "虚构文号，库中不存在"),
    ("渝府办发〔2026〕777号 规定了对哪些企业的扶持措施？",
     ["渝府办发〔2026〕777号"], "虚构文号，库中不存在"),
    ("渝府发〔2025〕999号 是哪一年印发的？",
     ["渝府发〔2025〕999号"], "虚构文号，库中不存在"),
    ("重庆市关于民营经济发展促进条例的解读材料有哪些？",
     ["民营经济发展促进条例"], "库中无该文件（需校验）"),
    ("重庆市电动自行车管理办法规定的上牌流程是什么？",
     ["电动自行车管理"], "库中无该文件（需校验）"),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(EVAL_DIR, "candidates-C.jsonl"))
    args = ap.parse_args()

    cfg = load_env()
    conn = db_conn(cfg)
    rows, rejected = [], []
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT DISTINCT docNumber FROM wiki_chunk WHERE docNumber IS NOT NULL AND docNumber<>''")
            doc_numbers = {r[0] for r in cur.fetchall()}

            for i, (q, keys, reason) in enumerate(CANDIDATES, 1):
                bad = []
                for k in keys:
                    if re.search(r"〔\d{4}〕\d+号", k):
                        if k in doc_numbers:
                            bad.append(f"文号 {k} 在库中存在")
                        continue
                    cur.execute(
                        "SELECT COUNT(*) FROM wiki_chunk WHERE status='ACTIVE' AND chunkText LIKE %s",
                        (f"%{k}%",),
                    )
                    n = cur.fetchone()[0]
                    if n > 0:
                        bad.append(f"「{k}」命中 {n} 个 chunk")
                if bad:
                    rejected.append({"q": q, "reason": "; ".join(bad)})
                    continue
                rows.append({
                    "id": f"C-{i:02d}",
                    "category": "unanswerable",
                    "question": q,
                    "answer": "知识库中没有相关内容，应当明确告知无法回答。",
                    "expectRefusal": True,
                    "gold": [],
                    "source": {
                        "pairId": "", "policyDocId": 0, "policyTitle": "",
                        "interpretDocId": 0, "interpretTitle": "",
                        "interpretSection": "", "docNumber": "",
                    },
                    "permission": None,
                    "meta": {
                        "qType": "unanswerable",
                        "evidenceKey": " / ".join(keys),
                        "answerQuote": "",
                        "candidateChunks": [],
                        "goldVerified": True,
                        "outOfScopeReason": reason,
                        "generatedBy": "rule+db-verified",
                        "temperature": 0,
                        "reviewState": "pending",
                        "reviewNote": "",
                    },
                })
    finally:
        conn.close()

    with open(args.out, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"候选 {len(CANDIDATES)} 题 -> 通过校验 {len(rows)} 题，剔除 {len(rejected)} 题")
    for r in rejected:
        print(f"  剔除：{r['q'][:40]} | {r['reason']}")
    print(f"产出 -> {args.out}")


if __name__ == "__main__":
    main()

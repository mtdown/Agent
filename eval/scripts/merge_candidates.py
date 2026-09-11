# -*- coding: utf-8 -*-
"""汇总 A/B/C/D/E 五类候选题为 eval/candidates.jsonl，并做规范化校验与去重。

校验项：
  1. id 唯一
  2. 必填字段齐全
  3. A/B/E 类 gold 非空；C 类 expectRefusal=true 且 gold 为空；D 类 permission 非空
  4. **锚点有效性**：每个 gold 的 (docId, chunkIndex) 必须能在当前 ACTIVE chunk 中查到
  5. 问题去重（字符 bigram Jaccard >= 0.85 视为重复，后者丢弃）
"""
from __future__ import annotations

import json
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib_eval import (  # noqa: E402
    EVAL_DIR, TMP_DIR, bigrams, db_conn, load_env,
)

FILES = ["candidates-A.jsonl", "candidates-B.jsonl", "candidates-C.jsonl",
         "candidates-D.jsonl", "candidates-E.jsonl"]
REQUIRED = ["id", "category", "question", "answer", "expectRefusal", "gold", "source", "permission", "meta"]
DUP_THRESHOLD = 0.85


def main():
    cfg = load_env()
    rows, errors, dup_info = [], [], []
    for fn in FILES:
        p = os.path.join(EVAL_DIR, fn)
        if not os.path.exists(p):
            errors.append(f"缺少文件 {fn}")
            continue
        for line in open(p, encoding="utf-8"):
            line = line.strip()
            if line:
                rows.append(json.loads(line))

    # 锚点有效性：一次性取回所有 (docId, chunkIndex)
    pairs = {(g["docId"], g["chunkIndex"]) for r in rows for g in r["gold"]}
    conn = db_conn(cfg)
    valid = set()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT docId, chunkIndex FROM wiki_chunk WHERE status='ACTIVE'")
            valid = {(int(a), int(b)) for a, b in cur.fetchall()}
    finally:
        conn.close()
    missing = sorted(pairs - valid)
    if missing:
        errors.append(f"{len(missing)} 个 gold 锚点在当前 ACTIVE chunk 中不存在：{missing[:10]}")

    # 逐条校验
    kept, seen_ids = [], set()
    seen_q = []  # (bigrams, id)
    for r in rows:
        rid = r.get("id")
        if rid in seen_ids:
            errors.append(f"重复 id {rid}")
            continue
        miss = [k for k in REQUIRED if k not in r]
        if miss:
            errors.append(f"{rid} 缺字段 {miss}")
            continue
        cat = r["category"]
        if cat in ("pair", "docnum", "synthetic") and not r["gold"]:
            errors.append(f"{rid} ({cat}) gold 为空")
            continue
        if cat == "unanswerable" and (not r["expectRefusal"] or r["gold"]):
            errors.append(f"{rid} (unanswerable) 应为 expectRefusal=true 且 gold 为空")
            continue
        if cat == "permission" and not r["permission"]:
            errors.append(f"{rid} (permission) permission 为空")
            continue
        bad_anchor = [g for g in r["gold"] if (g["docId"], g["chunkIndex"]) not in valid]
        if bad_anchor:
            errors.append(f"{rid} gold 锚点失效 {[(g['docId'], g['chunkIndex']) for g in bad_anchor]}")
            continue
        # 去重（D 类是 A/B 的镜像，跳过）
        bg = bigrams(r["question"])
        is_dup = False
        if cat != "permission":
            for b2, oid in seen_q:
                if bg and b2 and len(bg & b2) / len(bg | b2) >= DUP_THRESHOLD:
                    dup_info.append({"dropped": rid, "similarTo": oid, "q": r["question"][:50]})
                    is_dup = True
                    break
        if is_dup:
            continue
        seen_q.append((bg, rid))
        seen_ids.add(rid)
        kept.append(r)

    out = os.path.join(EVAL_DIR, "candidates.jsonl")
    with open(out, "w", encoding="utf-8") as f:
        for r in kept:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    # 报告
    rep = ["# 候选题汇总报告\n"]
    rep.append(f"- 输入文件：{', '.join(FILES)}，共读取 {len(rows)} 题")
    rep.append(f"- 校验后保留 **{len(kept)}** 题，剔除 {len(rows)-len(kept)} 题")
    rep.append(f"- gold 锚点校验：{len(pairs)} 个唯一锚点，失效 {len(missing)} 个\n")
    rep.append("## 分类统计\n")
    rep.append("| 类别 | 题数 |")
    rep.append("|---|---|")
    for k, v in sorted(Counter(r["category"] for r in kept).items()):
        rep.append(f"| {k} | {v} |")
    if dup_info:
        rep.append("\n## 重复题（已丢弃后者）\n")
        rep.append("| 丢弃 | 相似于 | 问题 |")
        rep.append("|---|---|---|")
        for d in dup_info:
            rep.append(f"| {d['dropped']} | {d['similarTo']} | {d['q']} |")
    if errors:
        rep.append("\n## 校验错误\n")
        for e in errors:
            rep.append(f"- {e}")
    open(os.path.join(TMP_DIR, "merge-report.md"), "w", encoding="utf-8").write("\n".join(rep) + "\n")

    print(f"读取 {len(rows)} -> 保留 {len(kept)}（去重 {len(dup_info)}，错误 {len(errors)}）")
    print("分类：", dict(Counter(r["category"] for r in kept)))
    print(f"产出 -> {out}")
    if errors:
        print("错误：")
        for e in errors[:15]:
            print("  -", e)


if __name__ == "__main__":
    main()

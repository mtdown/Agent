# -*- coding: utf-8 -*-
"""诊断：gold 在检索排名里埋多深（rerank 理论增益的上界）。

**这是"要不要上重排/混合检索"的第一个诊断工具**：输出写入 `eval/tmp/diag-rankings.json`，
供 `diag_hybrid_pool.py` 消费（后者算混合候选池的 oracle）。

回答三个问题：
  1. 用深候选（topK=DEPTH）算前缀 10 的标准指标，是否复现 archive 基线 0.5623？
     → 复现即证明本分支与基线可比（同一仪器、同一口径）。
  2. 若有一个完美 reranker，只重排前 N 条候选，recall@6 能到多少？
     → oracle recall@6 vs N，给出 rerank 的天花板。
  3. gold 段在排名中的位次分布（需要挖到多深才能凑齐 6 段）。

只读：不写业务库、不改后端。产物写 eval/tmp/（已 gitignore）。
"""
from __future__ import annotations

import json
import os
import sys
from datetime import datetime

HERE = os.path.dirname(os.path.abspath(__file__))
EVAL_DIR = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(EVAL_DIR, "scripts"))

from lib_eval import load_env  # noqa: E402
from lib_rag_eval import (  # noqa: E402
    FETCH_K,
    KS,
    METRIC_KEYS,
    HttpRetriever,
    aggregate,
    compute_metrics,
    gold_pairs,
    load_golden,
    probe,
)

POLICY_SPACE_ID = 2095544464810774531
DEPTH = 200          # 一次取多深
SCORED_CATS = {"pair", "docnum", "synthetic", "crossdoc", "single"}


def main():
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    out_path = os.path.join(HERE, os.path.basename(
        sys.argv[2] if len(sys.argv) > 2 else "diag-rankings.json"))

    cfg = load_env()
    key = cfg.get("EVAL_MEMBER_API_KEY", "")
    base_url = cfg.get("BACKEND_BASE_URL", "http://localhost:8123/api")
    r = HttpRetriever(base_url, key, [POLICY_SPACE_ID])

    if not probe(r, key):
        sys.exit(1)

    rows = [x for x in load_golden(os.path.join(EVAL_DIR, "golden.v2.jsonl"))
            if x["category"] in SCORED_CATS]
    if limit:
        rows = rows[:limit]
    print(f"[diag] {len(rows)} 题，DEPTH={DEPTH}")

    per_q = []
    for i, row in enumerate(rows, 1):
        hits = r.search(row["question"], DEPTH, api_key=key)
        gold = gold_pairs(row)
        gset = set(gold)
        ranks = []
        for h in hits:
            if h.coord in gset:
                ranks.append(hits.index(h) + 1)
        per_q.append({
            "id": row["id"],
            "category": row["category"],
            "goldCount": len(gset),
            "gold": sorted(gset),
            "returnCount": len(hits),
            "goldRanks": sorted(ranks),
            "hits": [h.to_dict() for h in hits],
            "metrics10": compute_metrics(gold, hits[:FETCH_K]) if gold else None,
        })
        if i % 25 == 0 or i == len(rows):
            print(f"  进度 {i}/{len(rows)}")

    json.dump({"depth": DEPTH, "fetchK": FETCH_K,
               "ts": datetime.now().isoformat(timespec="seconds"),
               "perQuestion": per_q},
              open(out_path, "w", encoding="utf-8"), ensure_ascii=False)

    # ---- 1. 复现基线 ----
    scored = [{"metrics": q["metrics10"]} | q for q in per_q if q["metrics10"]]
    overall = aggregate(scored, METRIC_KEYS)
    print("\n=== 1. 前缀 10 的标准指标（应与 archive 基线一致）===")
    print(f"  题数 {overall['count']}")
    for k in ("recall", "docRecall", "hitRate"):
        print(f"  {k:10s} " + " ".join(f"@{j}={overall[f'{k}@{j}']:.4f}" for j in KS))
    print(f"  mrr        {overall['mrr']:.4f}")
    print("  archive 基线: recall@6=0.5623 docRecall@6=0.8697 hitRate@6=0.6842 mrr=0.4486")

    # ---- 2. oracle 上界 ----
    print("\n=== 2. 完美 rerank 的 recall@6 上界（重排前 N 条候选）===")
    for n in (6, 10, 20, 30, 50, 100, 200):
        vals = []
        for q in scored:
            gold = {tuple(x) for x in q["gold"]}
            cand = {(h["docId"], h["chunkIndex"]) for h in q["hits"][:n]}
            vals.append(min(6, len(gold & cand)) / q["goldCount"])
        print(f"  N={n:4d}  oracle recall@6 = {sum(vals)/len(vals):.4f}")

    # ---- 3. 深度分布 ----
    print("\n=== 3. gold 段位次分布（gold 全部落在前 k 条内的题数）===")
    for k in (1, 3, 6, 10, 20, 50, 200):
        allin = sum(1 for q in scored if q["goldRanks"] and max(q["goldRanks"]) <= k)
        anyin = sum(1 for q in scored if q["goldRanks"] and min(q["goldRanks"]) <= k)
        print(f"  前 {k:3d} 条：gold 全进的题 {allin:3d}，至少进一段的题 {anyin:3d}")
    miss = sum(1 for q in scored if not q["goldRanks"])
    print(f"  前 {DEPTH} 条内一段 gold 都没有的题: {miss} / {len(scored)}")


if __name__ == "__main__":
    main()

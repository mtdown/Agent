# -*- coding: utf-8 -*-
"""单次 mhr-rag run 的完整报告（纯离线，不碰后端、不花额度）。

回答的是"这一次跑成了什么样"，而不是"相比上次涨了多少"（那是 compare_runs.py）。
多查询 / 证据整理这类方案要先过**检索层门槛**才允许烧生成额度，门槛需要的
数字这里必须一次算齐：

  1. recall@6 / recall@10（= gold chunk 覆盖）
  2. docRecall@6 / docRecall@10（= gold 文档覆盖）
  3. oracle@fetchK —— 池子里的理论上限。**必须和实测一起看**：
     oracle 高而实测低说明瓶颈在排序，不在召回；oracle 本身低才是召回不足。
  4. 耗时 mean / p50 / p95（多查询每问多一次快速 LLM 调用，耗时是必要成本证据）
  5. 按题型、按 gold 文档数的拆解

oracle 只能离线算：run 结果里只存 hits，gold 坐标要回固定题集（或 golden）按 id 取。
因此 --questions 必须与 run 的 goldenFile 一致，否则 oracle 就是错的。

用法：
    python report_run.py --run results/multi-query-only/mhr-20260917-XXXX.json
    python report_run.py --run <run.json> --reference results/mhr-20260916-233042.json
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import mhr_lib as L  # noqa: E402
from lib_rag_eval import (  # noqa: E402
    Hit,
    aggregate,
    compute_metrics,
    gold_pairs,
    load_golden,
    metric_keys_for,
    quantile,
)

FIXED_RETRIEVAL = os.path.join(L.ANCHOR_DIR, "mhr-eval-600-fixed.jsonl")


def load(path: str) -> dict:
    d = json.load(open(path, encoding="utf-8"))
    if "overall" not in d:
        sys.exit(f"[FATAL] 不是 run 结果文件（缺 overall）：{path}")
    return d


def table(rows, headers):
    widths = [max(len(str(r[i])) for r in rows + [headers]) for i in range(len(headers))]
    out = ["  ".join(str(headers[i]).ljust(widths[i]) for i in range(len(headers))),
           "  ".join("-" * w for w in widths)]
    for r in rows:
        out.append("  ".join(str(r[i]).ljust(widths[i]) for i in range(len(headers))))
    return "\n".join(out)


def fmt(v, nd=4):
    return "-" if v is None else f"{v:.{nd}f}"


def latency_stats(rows):
    vals = sorted(r["latencyMs"] for r in rows if r.get("latencyMs") is not None)
    if not vals:
        return None
    return {"count": len(vals), "mean": round(sum(vals) / len(vals), 1),
            "p50": quantile(vals, 0.5), "p95": quantile(vals, 0.95), "max": vals[-1]}


def enrich(run: dict, gold_by_id: dict) -> list:
    """给每条计分记录补上「池内上限」指标：oracle@fetchK 及文档级上限。

    用 compute_metrics 在 K=fetchK 上重算一次，口径与其余 K 完全一致（走 set 去重）。
    """
    fetch_k = run["config"].get("fetchK") or 50
    out = []
    for r in run["perQuestion"]:
        if r.get("refusal") or not r.get("metrics"):
            continue
        gold = gold_pairs(gold_by_id.get(r["id"], {}))
        if not gold:
            continue
        hits = [Hit(h["docId"], h["chunkIndex"], h.get("score", 0.0), h.get("docTitle", ""),
                    original_indexes=h.get("originalChunkIndexes"),
                    evidence_group_id=h.get("evidenceGroupId"))
                for h in (r.get("hits") or [])]
        pool = compute_metrics(gold, hits, [fetch_k])
        row = dict(r)
        row["_oracle"] = pool[f"recall@{fetch_k}"]
        row["_oracleDoc"] = pool[f"docRecall@{fetch_k}"]
        row["_goldCount"] = len(set(gold))
        out.append(row)
    return out


def mean_of(rows, key):
    vals = [r[key] for r in rows if r.get(key) is not None]
    return round(sum(vals) / len(vals), 4) if vals else None


def section_single(rows, run, gold_by_id, focus=6):
    fetch_k = run["config"].get("fetchK")
    metric_keys = metric_keys_for(run["config"].get("ks"))
    overall = aggregate(rows, metric_keys)
    print(f"\n=== 1. overall（n={overall['count']}，固定池深 {fetch_k}） ===")
    print(table([
        [f"recall@{focus}", fmt(overall.get(f"recall@{focus}")), "gold chunk 覆盖"],
        [f"recall@10", fmt(overall.get(f"recall@10")), "gold chunk 覆盖"],
        [f"docRecall@{focus}", fmt(overall.get(f"docRecall@{focus}")), "gold 文档覆盖"],
        [f"docRecall@10", fmt(overall.get("docRecall@10")), "gold 文档覆盖"],
        [f"hitRate@{focus}", fmt(overall.get(f"hitRate@{focus}")), "至少命中一块"],
        ["mrr", fmt(overall.get("mrr")), "首个命中的排名倒数"],
        [f"oracle@{fetch_k}", fmt(mean_of(rows, "_oracle")), "池内上限（理论最好排序）"],
        [f"oracleDoc@{fetch_k}", fmt(mean_of(rows, "_oracleDoc")), "池内文档上限"],
    ], ["metric", "value", "含义"]))

    gap = (mean_of(rows, "_oracle") or 0) - (overall.get(f"recall@{focus}") or 0)
    print(f"  未兑现缺口 oracle@{fetch_k} - recall@{focus} = {gap:.4f}"
          f"（{gap / (mean_of(rows, '_oracle') or 1) * 100:.1f}% 的池内上限没排进 top-{focus}）")

    print(f"\n=== 2. 按题型 ===")
    cats = sorted({r["category"] for r in rows})
    table_rows = []
    for c in cats:
        sub = [r for r in rows if r["category"] == c]
        a = aggregate(sub, metric_keys)
        table_rows.append([
            c, str(a["count"]),
            fmt(a.get(f"recall@{focus}")), fmt(a.get("recall@10")),
            fmt(a.get(f"docRecall@{focus}")), fmt(a.get("mrr")),
            fmt(mean_of(sub, "_oracle")),
            fmt((latency_stats(sub) or {}).get("mean"), 1),
        ])
    a = aggregate(rows, metric_keys)
    table_rows.append(["ALL", str(a["count"]), fmt(a.get(f"recall@{focus}")), fmt(a.get("recall@10")),
                       fmt(a.get(f"docRecall@{focus}")), fmt(a.get("mrr")),
                       fmt(mean_of(rows, "_oracle")),
                       fmt((latency_stats(rows) or {}).get("mean"), 1)])
    print(table(table_rows, ["type", "n", f"recall@{focus}", "recall@10",
                             f"docRecall@{focus}", "mrr", f"oracle@{fetch_k}", "mean_ms"]))

    print(f"\n=== 3. 按 gold 文档数（多跳难度刻度） ===")
    table_rows = []
    for nd in sorted({r.get("goldDocs", 0) for r in rows}):
        sub = [r for r in rows if r.get("goldDocs") == nd]
        a = aggregate(sub, metric_keys)
        table_rows.append([f"{nd} 篇", str(a["count"]), fmt(a.get(f"recall@{focus}")),
                           fmt(a.get(f"docRecall@{focus}")), fmt(mean_of(sub, "_oracle"))])
    print(table(table_rows, ["goldDocs", "n", f"recall@{focus}", f"docRecall@{focus}",
                             f"oracle@{fetch_k}"]))

    lat = latency_stats(rows)
    print(f"\n=== 4. 耗时（客户端端到端，单并发） ===")
    if lat:
        print(table([[k, str(v)] for k, v in lat.items()],
                    ["stat", "ms"]))
    else:
        print("  该 run 未记录 latencyMs（改动前的结果文件没有这一列）")

    zeros = [r for r in rows if r["metrics"].get(f"recall@{focus}", 0) == 0]
    print(f"\n=== 5. 失败面 ===")
    print(f"  recall@{focus}=0（全部漏掉）：{len(zeros)}/{len(rows)} "
          f"（{len(zeros) / len(rows) * 100:.1f}%）")
    if zeros:
        from collections import Counter
        print("  按题型：", dict(sorted(Counter(r["category"] for r in zeros).items())))
        print("  按 gold 文档数：", dict(sorted(Counter(r.get("goldDocs", 0) for r in zeros).items())))
        doc_in = [r for r in zeros if r["metrics"].get(f"docRecall@{focus}", 0) > 0]
        print(f"  其中文档已进但切片没凑齐：{len(doc_in)}/{len(zeros)}")

    # chunk 整理必须能自证：开关打开但一对可合并的相邻 chunk 都没有时，输出与输入
    # 逐条相同、指标照出数。这一节就是"没效果"与"没生效"的分界线。
    top_merged = 0
    pool_merged = 0
    covered_units = 0
    for r in rows:
        hs = r.get("hits") or []
        if any(len(h.get("originalChunkIndexes") or []) > 1 for h in hs[:focus]):
            top_merged += 1
        for h in hs:
            units = len(h.get("originalChunkIndexes") or [])
            if units > 1:
                pool_merged += 1
                covered_units += units
    print(f"\n=== 6. chunk 整理生效面 ===")
    print(f"  top-{focus} 内含合并块的题数：{top_merged}/{len(rows)}"
          f"（{top_merged / len(rows) * 100:.1f}%）")
    print(f"  池内合并块数：{pool_merged}，这些块覆盖的原始 chunk 数：{covered_units}")
    if pool_merged == 0:
        print("  → 本 run 没有产生任何合并块：要么整理没开，要么池里没有可合并的相邻 chunk。"
              " 此时代理指标的任何变化都**不能归因于 chunk 整理**。")
    return rows


def section_reference(ref_run, gold_by_id, ids, focus=6):
    """参照 run 取同题交集重算 —— 只在 config 一致时才允许横比。"""
    print(f"\n=== 参照：{ref_run['runId']}（取同题交集） ===")
    ref_rows = [r for r in enrich(ref_run, gold_by_id) if r["id"] in ids]
    if not ref_rows:
        print("  同题交集为空，无法对照")
        return
    metric_keys = metric_keys_for(ref_run["config"].get("ks"))
    a = aggregate(ref_rows, metric_keys)
    print(f"  计分题数={a['count']}  fetchK={ref_run['config'].get('fetchK')}  "
          f"ks={ref_run['config'].get('ks')}")
    print(table([
        [f"recall@{focus}", fmt(a.get(f"recall@{focus}"))],
        ["recall@10", fmt(a.get("recall@10"))],
        [f"docRecall@{focus}", fmt(a.get(f"docRecall@{focus}"))],
        ["mrr", fmt(a.get("mrr"))],
        [f"oracle@{ref_run['config'].get('fetchK')}", fmt(mean_of(ref_rows, "_oracle"))],
    ], ["metric", "value"]))


def main():
    ap = argparse.ArgumentParser(description="单次 mhr-rag run 报告（含 oracle 与耗时）")
    ap.add_argument("--run", required=True, help="要报告的 run json")
    ap.add_argument("--questions", default=FIXED_RETRIEVAL,
                    help="与 run 同一批题的题集文件（默认固定 600 题），用于回取 gold 坐标")
    ap.add_argument("--reference", default=None, help="可选参照 run（只统计同题交集）")
    ap.add_argument("--focus", type=int, default=6)
    args = ap.parse_args()

    run = load(args.run)
    if not os.path.exists(args.questions):
        sys.exit(f"[FATAL] 题集不存在：{args.questions}")
    gold_by_id = {r["id"]: r for r in load_golden(args.questions)}

    print(f"runId   : {run['runId']}")
    print(f"golden  : {run['config'].get('goldenFile')}")
    print(f"questions: {os.path.relpath(args.questions, L.REPO_ROOT)}（{len(gold_by_id)} 题）")
    print(f"counts  : {run['counts']}")

    rows = enrich(run, gold_by_id)
    missing = [r["id"] for r in run["perQuestion"]
               if not r.get("refusal") and r.get("metrics") and r["id"] not in gold_by_id]
    if missing:
        print(f"  ! {len(missing)} 条计分记录在题集里找不到 gold，已排除（题集与 run 不一致？）")

    section_single(rows, run, gold_by_id, args.focus)

    if args.reference:
        ref = load(args.reference)
        section_reference(ref, gold_by_id, {r["id"] for r in rows}, args.focus)
        same = all(ref["config"].get(k) == run["config"].get(k) for k in ("fetchK", "ks", "goldenFile"))
        print(f"\n[可比性] 参照 run 与本次 run 的 fetchK/ks/goldenFile "
              f"{'一致，差值可解释' if same else '不一致 —— 差值含口径差异，不可当作方案收益'}")


if __name__ == "__main__":
    main()

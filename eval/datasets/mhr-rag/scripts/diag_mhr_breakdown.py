# -*- coding: utf-8 -*-
"""拆解 mhr-rag 英文评测结果：题型 / 多跳难度 / 失败模式（纯离线，不看后端）。

为什么要单独拆：
测得 aggregate 数字只能回答"涨了多少"，回答不了"涨在哪、还差在哪"。
英文每题跨 2–4 篇文档，与中文"100% 单文档"是两种难度模型，必须按
**证据广度**而不是只看题型来拆 —— 中文侧已经证明题型不是真变量（详见
`MEMORY-detail-召回优化.md`），这里要验证英文侧是不是同一个故事。

用法：
    python diag_mhr_breakdown.py --a results/mhr-20260916-184519.json --b results/mhr-20260916-233042.json
    python diag_mhr_breakdown.py --a <before> --b <after> --subset-first 600   # 同题子集
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import mhr_lib as L  # noqa: E402
from lib_rag_eval import KS, aggregate, metric_keys_for  # noqa: E402

# 上游 4 类题型的中文解释（MHR 论文口径 + 本数据集实测）
TYPE_DESC = {
    "comparison_query": "比较类：跨多篇比较同一指标（谁更高/更好）",
    "inference_query": "推理类：需归纳多篇线索才能推出答案",
    "temporal_query": "时序类：带时间限定或要按时间排序",
    "null_query": "拒答类：语料里没有答案（无证据），不进召回分母",
}


def load(path: str) -> dict:
    return json.load(open(path, encoding="utf-8"))


def scored(d: dict, ids=None) -> list:
    rows = d["perQuestion"]
    if ids is not None:
        rows = [r for r in rows if r.get("id") in ids]
    return [r for r in rows if not r.get("refusal") and r.get("metrics")]


def set_ids(d: dict, golden_rows: list) -> set:
    return {r["id"] for r in golden_rows}


def agg(rows: list, ks=None) -> dict:
    return aggregate(rows, metric_keys_for(ks))


def table(rows, headers):
    widths = [max(len(str(r[i])) for r in rows + [headers]) for i in range(len(headers))]
    out = ["  ".join(str(headers[i]).ljust(widths[i]) for i in range(len(headers))),
           "  ".join("-" * w for w in widths)]
    for r in rows:
        out.append("  ".join(str(r[i]).ljust(widths[i]) for i in range(len(headers))))
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser(description="拆解 mhr-rag 结果")
    ap.add_argument("--a", required=True, help="改造前 run")
    ap.add_argument("--b", required=True, help="改造后 run")
    ap.add_argument("--subset-first", type=int, default=0, help="只统计 golden 前 N 题")
    ap.add_argument("--focus", type=int, default=6)
    args = ap.parse_args()

    A, B = load(args.a), load(args.b)
    ids = None
    if args.subset_first:
        from lib_rag_eval import load_golden
        ids = {r["id"] for r in load_golden(L.GOLDEN_PATH)[: args.subset_first]}
        print(f"[子集] golden 前 {args.subset_first} 题")
    sa, sb = scored(A, ids), scored(B, ids)
    ks = B.get("config", {}).get("ks") or KS
    print(f"计分题数：A(前)={len(sa)}  B(后)={len(sb)}")

    k = args.focus
    print(f"\n=== 1. 题型结构（来源：MultiHop-RAG 上游划分） ===")
    from collections import Counter
    ca, cb = Counter(r["category"] for r in sa), Counter(r["category"] for r in sb)
    rows = []
    for t in sorted(set(ca) | set(cb)):
        rows.append([t, str(cb.get(t, 0)), TYPE_DESC.get(t, "")])
    print(table(rows, ["question_type", "n", "含义"]))

    print(f"\n=== 2. 分题型 recall@{k} / docRecall@{k} / mrr ===")
    rows = []
    for t in sorted(set(ca) | set(cb)):
        ra = agg([r for r in sa if r["category"] == t], ks)
        rb = agg([r for r in sb if r["category"] == t], ks)
        va, vb = ra.get(f"recall@{k}"), rb.get(f"recall@{k}")
        rows.append([t, f"{cb.get(t, 0)}",
                     f"{va:.4f} -> {vb:.4f}", f"{vb - va:+.4f}",
                     f"{ra.get('docRecall@' + str(k)):.4f} -> {rb.get('docRecall@' + str(k)):.4f}",
                     f"{ra.get('mrr'):.4f} -> {rb.get('mrr'):.4f}"])
    print(table(rows, ["type", "n", f"recall@{k}", "delta", f"docRecall@{k}", "mrr"]))

    print(f"\n=== 3. 按「证据文档数」拆（多跳难度的真正刻度，2–4 篇） ===")
    rows = []
    for nd in sorted({r.get("goldDocs", 0) for r in sb}):
        ra = agg([r for r in sa if r.get("goldDocs") == nd], ks)
        rb = agg([r for r in sb if r.get("goldDocs") == nd], ks)
        if not rb:
            continue
        va, vb = ra.get(f"recall@{k}"), rb.get(f"recall@{k}")
        rows.append([f"{nd} 篇", f"{rb['count']}",
                     f"{va:.4f} -> {vb:.4f}", f"{vb - va:+.4f}",
                     f"{ra.get('docRecall@' + str(k)):.4f} -> {rb.get('docRecall@' + str(k)):.4f}"])
    print(table(rows, ["goldDocs", "n", f"recall@{k}", "delta", f"docRecall@{k}"]))

    print(f"\n=== 4. 按「gold 切片数」拆（要凑齐几个 chunk 才满分） ===")
    def bucket(n):
        if n <= 2:
            return "1-2 块"
        if n == 3:
            return "3 块"
        if n == 4:
            return "4 块"
        return ">=5 块"
    rows = []
    for bk in ["1-2 块", "3 块", "4 块", ">=5 块"]:
        ra = agg([r for r in sa if bucket(r.get("goldCount", 0)) == bk], ks)
        rb = agg([r for r in sb if bucket(r.get("goldCount", 0)) == bk], ks)
        if not rb or not ra:
            continue
        va, vb = ra.get(f"recall@{k}"), rb.get(f"recall@{k}")
        rows.append([bk, f"{rb['count']}", f"{va:.4f} -> {vb:.4f}", f"{vb - va:+.4f}"])
    print(table(rows, ["goldChunks", "n", f"recall@{k}", "delta"]))

    print(f"\n=== 5. 失败模式：完全没召回（recall@{k}=0）的题 ===")
    rows = []
    for label, S in (("前", sa), ("后", sb)):
        zero = [r for r in S if r["metrics"].get(f"recall@{k}", 0) == 0]
        part = [r for r in S if 0 < r["metrics"].get(f"recall@{k}", 0) < 1]
        full = [r for r in S if r["metrics"].get(f"recall@{k}", 0) >= 1]
        rows.append([label, f"{len(zero)} ({len(zero)/len(S)*100:.1f}%)",
                     f"{len(part)} ({len(part)/len(S)*100:.1f}%)",
                     f"{len(full)} ({len(full)/len(S)*100:.1f}%)"])
    print(table(rows, ["", "recall=0（全丢）", "0<recall<1（部分）", "recall=1（满分）"]))
    zb = [r for r in sb if r["metrics"].get(f"recall@{k}", 0) == 0]
    if zb:
        from collections import Counter as C
        print("\n改造后仍未召回的题，按证据文档数分布：",
              dict(sorted(C(r.get("goldDocs", 0) for r in zb).items())))
        print("按题型分布：", dict(sorted(C(r["category"] for r in zb).items())))
        doc_hit = [r for r in zb if r["metrics"].get(f"docRecall@{k}", 0) > 0]
        print(f"其中「文档进了但切片没凑齐」：{len(doc_hit)}/{len(zb)} "
              f"（{len(doc_hit)/len(zb)*100:.1f}%）")

    print(f"\n=== 6. 抬 K 在英文上还值不值（recall@6 -> recall@10） ===")
    oa, ob = agg(sa, ks), agg(sb, ks)
    rows = []
    for kk in ks:
        if kk not in (6, 10):
            continue
        rows.append([f"recall@{kk}", f"{oa.get('recall@' + str(kk)):.4f}",
                     f"{ob.get('recall@' + str(kk)):.4f}"])
    gain_b = ob.get("recall@10", 0) - ob.get(f"recall@{k}", 0)
    rows.append([f"6->10 边际增益", f"{oa.get('recall@10') - oa.get('recall@6'):+.4f}",
                 f"{gain_b:+.4f}"])
    print(table(rows, ["metric", "A 前", "B 后"]))


if __name__ == "__main__":
    main()

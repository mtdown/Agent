# -*- coding: utf-8 -*-<arg_value:6124c78e>
"""对比 mhr-rag 两次 run 的结果（纯离线，只读 results/*.json，不碰后端）。

用途：把「改造前」与「改造后」的英文基线放在一张表里，判断中文侧 +23.1pt 的收益
是否**泛化**到英文跨领域语料，而不是只在自有 216 篇政务语料上成立。

为什么必须单独一个脚本而不是塞进 run_eval.py：
  run_eval.py 的职责是"跑出一次结果"，对比是"解读两次结果"——两者生命周期不同，
  结果文件会越攒越多，对比需求会反复出现。

用法：
    python compare_runs.py                      # 自动取 results/ 下最新两个
    python compare_runs.py --a <before.json> --b <after.json>
    python compare_runs.py --a <before.json> --b <after.json> --focus 6
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import mhr_lib as L  # noqa: E402
from lib_rag_eval import (  # noqa: E402
    aggregate,
    load_golden,
    metric_keys_for,
)

DEFAULT_FOCUS = 6
# 只报这些指标，避免 16 列把差异摊薄；mrr 单独一行
FOCUS_METRICS = ("recall", "docRecall", "hitRate")


def load(path: str) -> dict:
    d = json.load(open(path, encoding="utf-8"))
    if "overall" not in d:
        sys.exit(f"[FATAL] 不是 run 结果文件（缺 overall）：{path}")
    return d


def pick_latest_two(out_dir: str) -> tuple[str, str]:
    files = sorted(f for f in os.listdir(out_dir) if f.startswith("mhr-") and f.endswith(".json"))
    if len(files) < 2:
        sys.exit(f"[FATAL] results/ 下少于 2 个 run，无法对比：{files}")
    # 文件名含时间戳，字典序 = 时间序；b = 最新
    return os.path.join(out_dir, files[-2]), os.path.join(out_dir, files[-1])


def delta(a, b):
    if a is None or b is None:
        return ""
    return f"{b - a:+.4f}"


def table(rows, headers, aligns=None):
    """极简等宽表格（不引第三方依赖）。"""
    widths = [max(len(str(r[i])) for r in rows + [headers]) for i in range(len(headers))]
    line = "  ".join("-" * w for w in widths)
    out = ["  ".join(str(headers[i]).ljust(widths[i]) for i in range(len(headers))), line]
    for r in rows:
        out.append("  ".join(str(r[i]).ljust(widths[i]) for i in range(len(headers))))
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser(description="对比 mhr-rag 两次评测结果")
    ap.add_argument("--a", default=None, help="改造前 run json")
    ap.add_argument("--b", default=None, help="改造后 run json")
    ap.add_argument("--out-dir", default=os.path.join(L.DATASET_DIR, "results"))
    ap.add_argument("--focus", type=int, default=DEFAULT_FOCUS, help="重点看哪个 K（默认 6）")
    ap.add_argument("--subset-first", type=int, default=0,
                    help="只比 golden 里的前 N 题（A/B 取交集重算）。用于「新链路只跑了一部分」"
                         "时与旧全量结果做同题对比 —— 省额度但不失可比性")
    args = ap.parse_args()

    pa, pb = (args.a, args.b) if args.a and args.b else pick_latest_two(args.out_dir)
    A, B = load(pa), load(pb)

    # 同题子集对比：A 可能是全量，B 只跑了前 N 题。两边都按同一批 id 重算，
    # 这样"跑一半"也能和"跑全量"横比 —— 前提是题序打散（实测游程 142/200，
    # 前 800 题的题型占比与全量最大偏差 1.6pt）。
    if args.subset_first:
        ids = {r["id"] for r in load_golden(L.GOLDEN_PATH)[: args.subset_first]}
        for tag, D in (("A", A), ("B", B)):
            metric_keys = metric_keys_for(D["config"].get("ks"))
            sub = [r for r in D["perQuestion"]
                   if r.get("id") in ids and not r.get("refusal") and r.get("metrics")]
            D["_sub"] = sub
            D["_subOverall"] = aggregate(sub, metric_keys)
        na, nb = len(A["_sub"]), len(B["_sub"])
        print(f"[子集] 只比 golden 前 {args.subset_first} 题的交集：A 命中 {na} / B 命中 {nb}")
        if min(na, nb) == 0:
            sys.exit("[FATAL] 子集交集为空，无法对比")
        # 后续所有 overall 口径都换成子集口径
        for D in (A, B):
            D["overall"] = D["_subOverall"]
            D["counts"] = dict(D["counts"], scored=len(D["_sub"]))
            by_cat = {}
            metric_keys = metric_keys_for(D["config"].get("ks"))
            for c in sorted({r["category"] for r in D["_sub"]}):
                by_cat[c] = aggregate([r for r in D["_sub"] if r["category"] == c], metric_keys)
            D["byQuestionType"] = by_cat
            D["overallMultiDoc"] = None

    print(f"A(前) {A['runId']}  scored={A['counts']['scored']}  spaceId={A['config'].get('spaceId')}")
    print(f"B(后) {B['runId']}  scored={B['counts']['scored']}  spaceId={B['config'].get('spaceId')}")

    # 可比性体检：golden / 语料 / K 定义变了就不许比
    warn = []
    for k in ("goldenFile", "corpusSha256", "qaSha256", "fetchK", "ks"):
        va, vb = A["config"].get(k), B["config"].get(k)
        if va != vb:
            warn.append(f"  ! config.{k} 不一致：{va} vs {vb}")
    if A["counts"]["scored"] != B["counts"]["scored"]:
        warn.append(f"  ! 计分题数不一致：{A['counts']['scored']} vs {B['counts']['scored']}")
    if warn:
        print("\n[可比性体检] 发现差异，结论需谨慎：")
        print("\n".join(warn))
    else:
        print("\n[可比性体检] golden / 语料指纹 / K 定义一致，可直接横比")

    k = args.focus
    print(f"\n=== overall（K={k} 为主） ===")
    rows = []
    for m in FOCUS_METRICS + ("mrr",):
        key = m if m == "mrr" else f"{m}@{k}"
        va, vb = A["overall"].get(key), B["overall"].get(key)
        rows.append([key, f"{va:.4f}", f"{vb:.4f}", delta(va, vb),
                     f"{'%+7.1fpt' % ((vb - va) * 100)}"])
    print(table(rows, ["metric", "A 前", "B 后", "delta", "pt"]))

    print(f"\n=== recall@K 全 K 曲线 ===")
    rows = []
    for kk in A["config"]["ks"]:
        va, vb = A["overall"].get(f"recall@{kk}"), B["overall"].get(f"recall@{kk}")
        rows.append([f"recall@{kk}", f"{va:.4f}", f"{vb:.4f}", delta(va, vb)])
        va2, vb2 = A["overall"].get(f"docRecall@{kk}"), B["overall"].get(f"docRecall@{kk}")
        rows.append([f"docRecall@{kk}", f"{va2:.4f}", f"{vb2:.4f}", delta(va2, vb2)])
    print(table(rows, ["metric", "A 前", "B 后", "delta"]))

    print(f"\n=== 按 question_type（recall@{k} / docRecall@{k}） ===")
    rows = []
    for c in sorted(set(A["byQuestionType"]) | set(B["byQuestionType"])):
        ca, cb = A["byQuestionType"].get(c), B["byQuestionType"].get(c)
        if not ca or not cb:
            rows.append([c, "-", "-", "-", "单侧缺失"])
            continue
        va, vb = ca.get(f"recall@{k}"), cb.get(f"recall@{k}")
        da, db = ca.get(f"docRecall@{k}"), cb.get(f"docRecall@{k}")
        rows.append([c, f"{ca['count']}/{cb['count']}",
                     f"{va:.4f} -> {vb:.4f}", delta(va, vb),
                     f"{da:.4f} -> {db:.4f}"])
    print(table(rows, ["type", "n(A/B)", f"recall@{k}", "delta", f"docRecall@{k}"]))

    for label in ("overallMultiDoc",):
        ma, mb = A.get(label), B.get(label)
        if ma and mb:
            print(f"\n=== {label}（gold 跨 >=2 篇文档，n={ma['count']}/{mb['count']}） ===")
            rows = []
            for m in FOCUS_METRICS + ("mrr",):
                key = m if m == "mrr" else f"{m}@{k}"
                va, vb = ma.get(key), mb.get(key)
                rows.append([key, f"{va:.4f}", f"{vb:.4f}", delta(va, vb)])
            print(table(rows, ["metric", "A 前", "B 后", "delta"]))

    print("\n提醒：本数据集是纯向量链路（文号层对英文恒不生效），"
          "与自有 216 篇的「向量+文号」混合链路不可横比绝对值；"
          "只有同一数据集内的 A/B 差值可解释。")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""分类别的 `recall@6` 天花板分解（纯离线，只读 golden + 结果文件，不调任何模型 API）。

回答四个问题：
  ① 每个类别的 `recall@6` 完美排序天花板是多少（`mean(min(6,|gold|)/|gold|)`）——
     凡有题目 `|gold| > 6`，该题就**不可能**满分（6 个槽位装不下），天花板因此 < 1.0；
  ② 全体天花板 0.9783 由哪些类别"拉低"；
  ③ 分离「评测口径造成的天花板」与「系统真实可攻空间」——
     对每个类别算 `实测 / 该类别天花板`，避免把口径问题误读成检索能力问题；
  ④ 同一批 hits，把评测单元从「切块坐标」换成「连续证据区」(evidence span) 后，
     `recall@6` 变成多少 —— 用于量化"切块粒度对指标本身的污染有多大"。

背景（2026-09-16）：曾据 `recall@6` 绝对值判定"pair 与 docnum 最需提升"。
本脚本证明该判断对 docnum 不成立：其天花板仅 0.7506，实测 0.6848 已兑现 91.2%，
剩余空间被 B-06(gold 33 段)/B-08(gold 25 段) 两道题的 gold 定义吃掉 ——
而这两题的 gold 在原文里是**同一片连续证据区**，只是被 600 字符切块边界切碎。
证据区口径下 docnum = 1.0000（满分）。

用法：
    python eval/scripts/diag_gold_ceiling.py
    python eval/scripts/diag_gold_ceiling.py --result eval/results/<file>.json
"""

import argparse
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
EVAL_DIR = os.path.dirname(HERE)
DEFAULT_GOLDEN = os.path.join(EVAL_DIR, "golden.v2.jsonl")
DEFAULT_RESULT = os.path.join(EVAL_DIR, "results", "baseline-20260916-201335-http.json")

# 参与打分的类别（unanswerable / permission 两类不计入 recall，见 run_eval.py）
SCORED_CATS = ("pair", "docnum", "synthetic", "crossdoc", "single")
SLOTS = 6  # topK=6，即 recall@6 的槽位数


def load_golden(path):
    rows = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            row = json.loads(line)
            rows[row["id"]] = row
    return rows


def gold_coords(row):
    """gold 锚点 -> {(docId, chunkIndex)}；按坐标去重以对齐 compute_metrics 口径。"""
    out = set()
    for item in row.get("gold") or []:
        if isinstance(item, dict):
            out.add((item.get("docId"), item.get("chunkIndex")))
        elif isinstance(item, (list, tuple)) and len(item) == 2:
            out.add((item[0], item[1]))
    return out


def ceiling(n_gold):
    """该题在 recall@6 下的完美排序上限 —— 槽位不够时分子封顶。"""
    if n_gold <= 0:
        return 0.0
    return min(SLOTS, n_gold) / n_gold


def coalesce_spans(coords, max_gap=0):
    """把 gold 坐标合并为「证据区」（evidence span）。

    同一文档内 chunkIndex 间隔 <= max_gap + 1 的 gold 视为同一片连续证据区。
    max_gap=0（默认，最保守）表示只有原文里真正相邻的文字才合并 ——
    即「一整片连续证据被 600 字符切块边界切开」的情形。
    """
    by_doc = {}
    for doc_id, idx in coords:
        by_doc.setdefault(doc_id, []).append(idx)
    spans = []
    for doc_id, idxs in by_doc.items():
        idxs.sort()
        cur = [idxs[0]]
        for x in idxs[1:]:
            if x - cur[-1] <= max_gap + 1:
                cur.append(x)
            else:
                spans.append({(doc_id, i) for i in cur})
                cur = [x]
        spans.append({(doc_id, i) for i in cur})
    return spans


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--golden", default=DEFAULT_GOLDEN)
    ap.add_argument("--result", default=DEFAULT_RESULT)
    args = ap.parse_args()

    golden = load_golden(args.golden)
    with open(args.result, encoding="utf-8") as fh:
        result = json.load(fh)

    # 对齐：结果文件里的题必须都在 golden 里，且属于计分类别
    pairs = [
        (q, golden[q["id"]])
        for q in result.get("perQuestion", [])
        if q.get("id") in golden and q.get("category") in SCORED_CATS
    ]
    if not pairs:
        raise SystemExit("没有可对齐的题目：检查 --golden / --result 是否匹配")

    print(f"结果文件: {os.path.basename(args.result)}")
    print(f"对齐题数: {len(pairs)}")
    print()

    # ---- ① 分类别天花板 vs 实测 ----
    print("=== ① 分类别：槽位天花板 vs 实测 ===")
    print(f"  {'类别':<10} {'n':>4} {'均值|gold|':>9} {'天花板':>8} {'实测rec@6':>10} {'兑现率':>8} {'|gold|>6题数':>12}")
    ctrl = {}  # 类别 -> (天花板, 权重占比, 拉低全体的量)
    for cat in SCORED_CATS:
        sub = [(q, g) for q, g in pairs if q["category"] == cat]
        if not sub:
            continue
        caps = [ceiling(len(gold_coords(g))) for _, g in sub]
        recs = [q["metrics"]["recall@6"] for q, _ in sub]
        lens = [len(gold_coords(g)) for _, g in sub]
        cap = sum(caps) / len(caps)
        rec = sum(recs) / len(recs)
        share = len(sub) / len(pairs)
        ctrl[cat] = (cap, share, (1.0 - cap) * share)
        print(
            f"  {cat:<10} {len(sub):>4} {sum(lens) / len(lens):>9.2f} {cap:>8.4f} "
            f"{rec:>10.4f} {rec / cap:>7.1%} {sum(1 for x in caps if x < 1.0):>12}"
        )
    all_caps = [ceiling(len(gold_coords(g))) for _, g in pairs]
    all_recs = [q["metrics"]["recall@6"] for q, _ in pairs]
    overall_cap = sum(all_caps) / len(all_caps)
    overall_rec = sum(all_recs) / len(all_recs)
    print(
        f"  {'全体':<10} {len(pairs):>4} {'':>9} {overall_cap:>8.4f} "
        f"{overall_rec:>10.4f} {overall_rec / overall_cap:>7.1%}"
    )
    print()

    # ---- ② 谁把全体天花板从 1.0 拉下来 ----
    print("=== ② 全体天花板 1.0 -> %.4f 的来源 ===" % overall_cap)
    for cat, (cap, share, drag) in sorted(ctrl.items(), key=lambda kv: -kv[1][2]):
        if drag <= 1e-9:
            print(f"  {cat:<10} 贡献 0（该类别无 |gold|>6 的题）")
        else:
            print(f"  {cat:<10} -{drag:.4f}  (天花板 {cap:.4f} x 题量占比 {share:.1%})")
    print(f"  合计拉低 -{sum(v[2] for v in ctrl.values()):.4f} -> {overall_cap:.4f}")
    print()

    # ---- ③ 分离口径问题与真实空间 ----
    print("=== ③ 分离「口径空间」与「系统空间」 ===")
    print(f"  {'类别':<10} {'口径吃掉':>9} {'系统仍欠':>9} {'总差距':>8}")
    for cat in SCORED_CATS:
        sub = [(q, g) for q, g in pairs if q["category"] == cat]
        if not sub:
            continue
        cap = sum(ceiling(len(gold_coords(g))) for _, g in sub) / len(sub)
        rec = sum(q["metrics"]["recall@6"] for q, _ in sub) / len(sub)
        print(f"  {cat:<10} {1.0 - cap:>9.4f} {cap - rec:>9.4f} {1.0 - rec:>8.4f}")
    print()
    print("  注：「口径吃掉」= 6 槽位装不下 |gold|>6 的题；这部分无论排序多好都拿不到。")
    print()

    # ---- ④ pair / docnum 逐题明细（长尾来源） ----
    for cat in ("pair", "docnum"):
        sub = [(q, g) for q, g in pairs if q["category"] == cat]
        lens = sorted((len(gold_coords(g)) for _, g in sub), reverse=True)
        print(f"=== ④ {cat} gold 段数长尾（共 {len(sub)} 题）===")
        print(f"  段数分布(降序): {lens}")
        gt = [x for x in lens if x > SLOTS]
        print(f"  段数 > {SLOTS} 的题: {len(gt)} 题 -> 这些题天花板 = {[round(ceiling(x), 4) for x in gt]}")
        # 多 gold 题的坐标形态
        multi = [(q, g) for q, g in sub if len(gold_coords(g)) >= 3]
        if multi:
            docs_multi = sum(1 for _, g in multi if len({d for d, _ in gold_coords(g)}) > 1)
            contig = 0
            for _, g in multi:
                c = sorted(gold_coords(g), key=lambda x: x[1])
                idx = [i for _, i in c]
                if len({d for d, _ in c}) == 1 and idx[-1] - idx[0] + 1 == len(c):
                    contig += 1
            print(
                f"  多 gold 题 {len(multi)} 题: 跨文档 {docs_multi} / "
                f"单文档且 chunkIndex 连续 {contig} / 单文档不连续 {len(multi) - docs_multi - contig}"
            )
        print()

    # ---- ⑤ 最小可引用口径 ----
    print("=== ⑤ 最小可引用口径（可用于对外表述的子集）===")
    combos = [
        ("全体 5 类", SCORED_CATS),
        ("剔除 synthetic(E)", ("pair", "docnum", "crossdoc", "single")),
        ("剔除 synthetic+docnum", ("pair", "crossdoc", "single")),
        ("仅 crossdoc+single", ("crossdoc", "single")),
    ]
    for label, cats in combos:
        sub = [q for q, _ in pairs if q["category"] in cats]
        if not sub:
            continue
        v = sum(q["metrics"]["recall@6"] for q in sub) / len(sub)
        print(f"  {label:<22} n={len(sub):>3}  recall@6 = {v:.4f}")

    # ---- ⑥ 切块坐标级 vs 证据区级 ----
    print()
    print("=== ⑥ 同样是 recall@6，把评测单元从「切块坐标」换成「连续证据区」 ===")
    print("  （max_gap=0：只有原文中真正相邻的 gold 块才合并，不引入主观判断）")
    print(f"  {'类别':<10} {'n':>4} {'坐标级':>8} {'证据区级':>9} {'差':>8} {'平均gold块':>10} {'平均证据区':>10}")
    for cat in SCORED_CATS:
        sub = [(q, g) for q, g in pairs if q["category"] == cat]
        if not sub:
            continue
        coord_rec, span_rec, n_blocks, n_spans = [], [], [], []
        for q, g in sub:
            cs = gold_coords(g)
            sp = coalesce_spans(cs)
            n_blocks.append(len(cs))
            n_spans.append(len(sp))
            coord_rec.append(q["metrics"]["recall@6"])
            hits = {(h["docId"], h["chunkIndex"]) for h in (q.get("hits") or [])[:SLOTS]}
            span_rec.append(sum(1 for s in sp if s & hits) / len(sp))
        a, b = sum(coord_rec) / len(coord_rec), sum(span_rec) / len(span_rec)
        print(
            f"  {cat:<10} {len(sub):>4} {a:>8.4f} {b:>9.4f} {b - a:>+8.4f} "
            f"{sum(n_blocks) / len(n_blocks):>10.2f} {sum(n_spans) / len(n_spans):>10.2f}"
        )
    coord_all = [q["metrics"]["recall@6"] for q, _ in pairs]
    span_all = []
    for q, g in pairs:
        sp = coalesce_spans(gold_coords(g))
        hits = {(h["docId"], h["chunkIndex"]) for h in (q.get("hits") or [])[:SLOTS]}
        span_all.append(sum(1 for s in sp if s & hits) / len(sp))
    a, b = sum(coord_all) / len(coord_all), sum(span_all) / len(span_all)
    print(f"  {'全体':<10} {len(pairs):>4} {a:>8.4f} {b:>9.4f} {b - a:>+8.4f}")
    print()
    print("  读法：若某类别两列几乎相同（如 single/crossdoc），说明它的 gold 本就是 1~2 块，")
    print("        该口径变化对它无影响 —— 这同时反证了证据区口径并非「放水」。")
    print("        两列差距大的类别（docnum/pair），其 gold 是「一整片连续证据被切块边界切开」。")

    o = result.get("overall", {})
    if o:
        print()
        print("=== overall 各指标 ===")
        for k in ("recall@1", "recall@3", "recall@6", "recall@10", "docRecall@6", "hitRate@6", "mrr"):
            if k in o:
                print(f"  {k:<12} = {o[k]:.4f}")


if __name__ == "__main__":
    main()

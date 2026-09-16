#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""抬高答案集 K 的收益 / 代价分解（回答「recall@10=0.8515 是不是就够了」）。

纯离线，只读一份评测结果 + golden 题集，不调模型 API。

回答三件事：
  ① recall@k 到底是什么粒度（段落级 vs 文档级）
  ② K 从 6 抬到 10，多买到的真证据有多少、是哪些题买到的
  ③ K 从 6 抬到 10，多喂给生成层的噪声有多少、上下文涨多少

用法：
  python eval/scripts/diag_topk_tradeoff.py \
      [eval/results/baseline-20260916-201335-http.json] [eval/golden.v2.jsonl]
"""

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
EVAL_DIR = os.path.dirname(HERE)

# 取自 diag_hybrid_pool 的语料统计：2061 块 / 平均块长 386 token
AVG_CHUNK_TOKENS = 386
EPS = 1e-9


def find_data(name):
    for base in (EVAL_DIR, os.path.join(EVAL_DIR, "tmp"), HERE):
        p = os.path.join(base, name)
        if os.path.exists(p):
            return p
    return None


def load_gold(path):
    """id -> 去重后的坐标集合（与 lib_rag_eval.compute_metrics 口径一致）"""
    out = {}
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if not line:
            continue
        r = json.loads(line)
        coords = {(g["docId"], g["chunkIndex"]) for g in (r.get("gold") or [])}
        out[r["id"]] = coords
    return out


def main():
    rp = sys.argv[1] if len(sys.argv) > 1 else find_data(
        "baseline-20260916-201335-http.json")
    if not rp or not os.path.exists(rp):
        rp = os.path.join(EVAL_DIR, "results", "baseline-20260916-201335-http.json")
    gp = sys.argv[2] if len(sys.argv) > 2 else os.path.join(EVAL_DIR, "golden.v2.jsonl")

    res = json.load(open(rp, encoding="utf-8"))
    golds = load_gold(gp)

    rows = []
    for q in res["perQuestion"]:
        gset = golds.get(q["id"])
        hits = q.get("hits") or []
        if not gset or not hits or q.get("error"):
            continue
        coords = [(h["docId"], h["chunkIndex"]) for h in hits]
        gdocs = {d for d, _ in gset}
        rows.append({
            "id": q["id"],
            "cat": q.get("category") or "?",
            "ng": len(gset),
            "n6": len(coords[:6]),
            "n10": len(coords[:10]),
            "rec6": len(gset & set(coords[:6])) / len(gset),
            "rec10": len(gset & set(coords[:10])) / len(gset),
            "docrec6": len(gdocs & {d for d, _ in coords[:6]}) / len(gdocs),
            "docrec10": len(gdocs & {d for d, _ in coords[:10]}) / len(gdocs),
            "hit6": len(gset & set(coords[:6])),
            "hit10": len(gset & set(coords[:10])),
        })

    n = len(rows)
    mean = lambda xs: sum(xs) / len(xs) if xs else 0.0

    print(f"结果文件：{os.path.basename(rp)}")
    print(f"可比题数：{n}（已剔除无 gold / 无 hits / 出错的题）")

    print("\n=== ① 粒度确认（口径来自 lib_rag_eval.compute_metrics） ===")
    print("  recall@k    = |gold坐标 ∩ 前k条坐标| / |gold坐标|   ← 段落(切片)级")
    print("  docRecall@k = |gold文档 ∩ 前k条文档| / |gold文档|   ← 文档级")
    print("  分母按坐标去重：同一题多个 quote 落在同一 chunk 只计一次")
    print(f"  本题集 gold 段数分布：均值 {mean([r['ng'] for r in rows]):.2f} 段/题，"
          f"最大 {max(r['ng'] for r in rows)} 段，>6 段的题 {sum(1 for r in rows if r['ng'] > 6)} 道")

    print("\n=== ② K=6 → K=10 买到了什么 ===")
    r6, r10 = mean([r["rec6"] for r in rows]), mean([r["rec10"] for r in rows])
    d6, d10 = mean([r["docrec6"] for r in rows]), mean([r["docrec10"] for r in rows])
    print(f"  recall@6   {r6:.4f} → recall@10   {r10:.4f}   （+{r10 - r6:.4f}）")
    print(f"  docRecall@6 {d6:.4f} → docRecall@10 {d10:.4f}   （+{d10 - d6:.4f}）")
    gain = [r for r in rows if r["rec10"] > r["rec6"] + EPS]
    print(f"  受益题（recall@10 > recall@6）：{len(gain)} 道 / {n} 道 = {len(gain) / n:.1%}")
    if gain:
        print(f"    这些题平均补回 {mean([r['rec10'] - r['rec6'] for r in gain]):.4f}，"
              f"补回后平均 {mean([r['rec10'] for r in gain]):.4f}")
        print(f"    其中「K=10 才从 0 变有」的题：{sum(1 for r in gain if r['rec6'] < EPS)} 道")
        print(f"    其中「K=10 后达到满分」的题：{sum(1 for r in gain if r['rec10'] > 1 - EPS)} 道")
    print(f"  未受益题：{n - len(gain)} 道 = {1 - len(gain) / n:.1%}（K=10 对它们毫无增益）")

    print("\n=== ③ K=6 → K=10 付出了什么（噪声与上下文） ===")
    perfect = [r for r in rows if r["rec6"] > 1 - EPS]
    print(f"  K=6 已满分的题：{len(perfect)} 道 / {n} 道 = {len(perfect) / n:.1%}")
    print(f"    这些题 K=10 多喂的段落全部是噪声，平均多喂 "
          f"{mean([r['n10'] - r['n6'] for r in perfect]):.2f} 段/题")
    h6, h10 = mean([r["hit6"] for r in rows]), mean([r["hit10"] for r in rows])
    print(f"  全体平均喂入：{mean([r['n6'] for r in rows]):.2f} 段 → {mean([r['n10'] for r in rows]):.2f} 段 "
          f"（+{(mean([r['n10'] for r in rows]) / mean([r['n6'] for r in rows]) - 1):.1%}）")
    print(f"  其中命中 gold：{h6:.2f} 段 → {h10:.2f} 段")
    print(f"  证据密度（gold / 喂入）：{h6 / mean([r['n6'] for r in rows]):.1%} → "
          f"{h10 / mean([r['n10'] for r in rows]):.1%}")
    print(f"  估算上下文：{mean([r['n6'] for r in rows]) * AVG_CHUNK_TOKENS:.0f} token → "
          f"{mean([r['n10'] for r in rows]) * AVG_CHUNK_TOKENS:.0f} token"
          f"（按平均块长 {AVG_CHUNK_TOKENS} token 估）")

    print("\n=== ④ 分类别看边际 ===")
    print(f"  {'类别':>10} {'n':>4} {'recall@6':>9} {'recall@10':>10} {'增量':>8} {'受益题占比':>11}")
    cats = {}
    for r in rows:
        cats.setdefault(r["cat"], []).append(r)
    for c in sorted(cats, key=lambda x: -len(cats[x])):
        v = cats[c]
        a, b = mean([r["rec6"] for r in v]), mean([r["rec10"] for r in v])
        g = sum(1 for r in v if r["rec10"] > r["rec6"] + EPS)
        print(f"  {c:>10} {len(v):>4} {a:>9.4f} {b:>10.4f} {b - a:>+8.4f} {g / len(v):>10.1%}")

    print("\n=== ⑤ 单调性：这条路的边界在哪 ===")
    ov = res["overall"]
    print("  本系统 pool=50 同一份排名上的 recall@k 曲线（k 越大只增不减，是定义决定的）：")
    for k in (1, 3, 5, 6, 10):
        print(f"    recall@{k:<3d} = {ov[f'recall@{k}']:.4f}")
    print("  ⇒ 「recall@k ≥ 0.85」这句目标，本身就能靠调大 k 达成，与排序质量无关。")
    print(f"  ⚠ 本结果 fetchK=10，**无法**回答「K 抬到 20/50 够不够」——"
          f"需要以 fetchK=20/50 重跑一轮才能测。")


if __name__ == "__main__":
    main()

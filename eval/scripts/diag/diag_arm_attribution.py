# -*- coding: utf-8 -*-
"""两臂答案层差异的定性归因：判决迁移矩阵 + 显著性 + 误拒判据校准。

回答四个问题：
  1. 正确率提升是不是统计显著？（McNemar 精确检验）
  2. 变化来自哪种判决迁移？INCORRECT 有没有被真正修好？
  3. 误拒率上升里有多少是判据误伤（判官判 CORRECT 却带拒答措辞）？
  4. 引用覆盖率与耗时的配对差异是否显著？

用法（按仓库根相对路径读 run 产物，请在仓库根目录执行）：
  python eval/scripts/diag/diag_arm_attribution.py
      默认复现 results/ask-qwen38max-nonthinking/REPORT.md 的那次对比
  python eval/scripts/diag/diag_arm_attribution.py <基线run.json> <实验臂run.json>

口径说明：配对检验只在两文件共同题目的交集上做；两比例 z 检验用独立样本正态近似，
显著性阈值取 |t|>1.98（α=0.05 双侧）。样本量在百题量级时这些近似只能作参考。
"""
from __future__ import annotations

import argparse
import json
import math
from collections import Counter
from pathlib import Path

DEFAULT_BASE = ("eval/datasets/mhr-rag/results/ask-baseline-off/"
                "mhr-ask-20260917-155551.json")
DEFAULT_ARM = ("eval/datasets/mhr-rag/results/ask-qwen38max-nonthinking/"
               "mhr-ask-20260920-212222.json")
ORDER = ["CORRECT", "PARTIAL", "INCORRECT", None]


def load(p):
    d = json.load(open(p, encoding="utf-8"))
    return {q["id"]: q for q in d["perQuestion"]}


def verdict(q):
    return (q.get("judge") or {}).get("verdict")


def arm_label(p):
    """用 run 文件所在的臂目录名作标签（如 ask-baseline-off）。"""
    return Path(p).parent.name


def binom_two_sided(k: int, n: int) -> float:
    """McNemar 精确检验（B=变差数, C=变好数, n=B+C），返回双侧 p。"""
    if n == 0:
        return 1.0
    tail = sum(math.comb(n, i) for i in range(k, n + 1)) / (2 ** n)
    return min(1.0, 2 * tail)


def two_prop_z(p1: float, n1: int, p2: float, n2: int):
    se = math.sqrt(p1 * (1 - p1) / n1 + p2 * (1 - p2) / n2)
    if se == 0:
        return 0.0, 0.0
    z = (p2 - p1) / se
    # 双侧 p（正态近似）
    p = math.erfc(abs(z) / math.sqrt(2))
    return z, p


def main() -> None:
    ap = argparse.ArgumentParser(description="两臂答案层差异归因")
    ap.add_argument("baseline", nargs="?", default=DEFAULT_BASE,
                    help="基线臂 run.json（默认：ask-baseline-off 那次）")
    ap.add_argument("arm", nargs="?", default=DEFAULT_ARM,
                    help="实验臂 run.json（默认：qwen38max-nonthinking 那次）")
    args = ap.parse_args()

    tag_a, tag_b = arm_label(args.baseline), arm_label(args.arm)
    A, B = load(args.baseline), load(args.arm)
    ids = [i for i in A if i in B]
    print(f"基线={tag_a}  实验臂={tag_b}")
    print(f"共同题目: {len(ids)}")

    print("\n" + "=" * 78)
    print(f"① 判决迁移矩阵（行={tag_a}，列={tag_b}）")
    print("=" * 78)
    mat = Counter((verdict(A[i]), verdict(B[i])) for i in ids)
    hdr = f"{'':12s}" + "".join(f"{str(c):>12s}" for c in ORDER)
    print(hdr)
    for r in ORDER:
        row = f"{str(r):12s}" + "".join(f"{mat.get((r, c), 0):>12d}" for c in ORDER)
        print(row)

    print("\n" + "=" * 78)
    print("② CORRECT 显著性：McNemar 精确检验（全体题目）")
    print("=" * 78)
    better = sum(1 for i in ids if verdict(A[i]) != "CORRECT" and verdict(B[i]) == "CORRECT")
    worse = sum(1 for i in ids if verdict(A[i]) == "CORRECT" and verdict(B[i]) != "CORRECT")
    n = better + worse
    p = binom_two_sided(max(better, worse), n)
    print(f"  变好(B->CORRECT)={better}  变差(A->CORRECT)={worse}  不一致对 n={n}")
    print(f"  McNemar 精确双侧 p = {p:.4f}  -> {'显著' if p < 0.05 else '不显著'}")
    acc_a = sum(1 for q in A.values() if verdict(q) == "CORRECT") / len(A)
    acc_b = sum(1 for q in B.values() if verdict(q) == "CORRECT") / len(B)
    z0, p0 = two_prop_z(acc_a, len(A), acc_b, len(B))
    print(f"  两比例 z 检验（独立样本近似）: z={z0:.2f} p={p0:.4f}")

    print("\n" + "=" * 78)
    print("③ INCORRECT 是否被修好")
    print("=" * 78)
    a_inc = {i for i in ids if verdict(A[i]) == "INCORRECT"}
    b_inc = {i for i in ids if verdict(B[i]) == "INCORRECT"}
    print(f"  baseline INCORRECT={len(a_inc)}  arm INCORRECT={len(b_inc)}")
    print(f"  两臂都错（未修好）={len(a_inc & b_inc)}")
    print(f"  baseline 错、arm 修好={len(a_inc - b_inc)} -> {sorted(a_inc - b_inc)}")
    print(f"  baseline 对、arm 新错={len(b_inc - a_inc)} -> {sorted(b_inc - a_inc)}")

    print("\n" + "=" * 78)
    print("④ 误拒判据校准")
    print("=" * 78)
    for tag, D in ((tag_a, A), (tag_b, B)):
        ans = [q for q in D.values() if not q.get("expectRefusal")]
        fr = [q for q in ans if q.get("refusalDetected")]
        corr = [q for q in fr if verdict(q) == "CORRECT"]
        substantive = len(fr) - len(corr)          # 扣掉判据误伤
        print(f"  {tag}: 原始误拒 {len(fr)}/{len(ans)} = {len(fr)/len(ans):.4f}"
              f" | 判官判 CORRECT 的误伤 {len(corr)} 题"
              f" | 校正后实质未答 {substantive}/{len(ans)} = {substantive/len(ans):.4f}")

    ans_a = [q for q in A.values() if not q.get("expectRefusal")]
    ans_b = [q for q in B.values() if not q.get("expectRefusal")]
    fr_a = sum(1 for q in ans_a if q.get("refusalDetected"))
    fr_b = sum(1 for q in ans_b if q.get("refusalDetected"))
    corr_b = sum(1 for q in ans_b if q.get("refusalDetected") and verdict(q) == "CORRECT")
    z1, p1 = two_prop_z(fr_a / len(ans_a), len(ans_a), fr_b / len(ans_b), len(ans_b))
    z2, p2 = two_prop_z(fr_a / len(ans_a), len(ans_a),
                        (fr_b - corr_b) / len(ans_b), len(ans_b))
    print(f"\n  原始误拒率差 z={z1:.2f} p={p1:.4f} -> {'显著' if p1 < 0.05 else '不显著'}")
    print(f"  校正后未答率差 z={z2:.2f} p={p2:.4f} -> {'显著' if p2 < 0.05 else '不显著'}")

    print("\n" + "=" * 78)
    print("⑤ 误拒标记对判决的预测力（判据能否代表真拒答）")
    print("=" * 78)
    for tag, D in ((tag_a, A), (tag_b, B)):
        ans = [q for q in D.values() if not q.get("expectRefusal")]
        fl = [q for q in ans if q.get("refusalDetected")]
        un = [q for q in ans if not q.get("refusalDetected")]
        acc_fl = sum(1 for q in fl if verdict(q) == "CORRECT") / len(fl) if fl else 0
        acc_un = sum(1 for q in un if verdict(q) == "CORRECT") / len(un) if un else 0
        print(f"  {tag}: 带拒答措辞题的正确率={acc_fl:.4f} (n={len(fl)})"
              f" | 不带措辞题的正确率={acc_un:.4f} (n={len(un)})")

    print("\n" + "=" * 78)
    print("⑥ 引用覆盖率与耗时的配对检验")
    print("=" * 78)
    pairs = []
    for i in ids:
        va = A[i].get("citationCoverageStatements")
        vb = B[i].get("citationCoverageStatements")
        if va is not None and vb is not None:
            pairs.append((va, vb))
    diffs = [b - a for a, b in pairs]
    n = len(diffs)
    mean = sum(diffs) / n
    var = sum((x - mean) ** 2 for x in diffs) / (n - 1) if n > 1 else 0.0
    se = math.sqrt(var / n) if n else 0.0
    t = mean / se if se else 0.0
    print(f"  引用覆盖率配对 n={n} 均值差={mean:+.4f} SE={se:.4f} t={t:.2f}"
          f" -> {'显著' if abs(t) > 1.98 else '不显著'}")
    cov_a = sum(a for a, _ in pairs) / n
    cov_b = sum(b for _, b in pairs) / n
    print(f"    baseline 均值={cov_a:.4f}  arm 均值={cov_b:.4f}")

    # 与 runner 的 citationCoverageMacro 同口径：只取"有答案题"再做配对
    pairs88 = []
    for i in ids:
        if A[i].get("expectRefusal") or B[i].get("expectRefusal"):
            continue
        va = A[i].get("citationCoverageStatements")
        vb = B[i].get("citationCoverageStatements")
        if va is not None and vb is not None:
            pairs88.append((va, vb))
    d88 = [b - a for a, b in pairs88]
    n88 = len(d88)
    if n88 > 1:
        m88 = sum(d88) / n88
        v88 = sum((x - m88) ** 2 for x in d88) / (n88 - 1)
        se88 = math.sqrt(v88 / n88)
        t88 = m88 / se88 if se88 else 0.0
        print(f"  仅有答案题配对 n={n88} 均值差={m88:+.4f} SE={se88:.4f} t={t88:.2f}"
              f" -> {'显著' if abs(t88) > 1.98 else '不显著'}"
              f"（runner 口径 {sum(a for a, _ in pairs88)/n88:.4f} -> {sum(b for _, b in pairs88)/n88:.4f}）")

    def pct(vals, q):
        s = sorted(vals)
        k = min(len(s) - 1, int(round((len(s) - 1) * q)))
        return s[k]

    for tag, D in ((tag_a, A), (tag_b, B)):
        lat = [q["latencyMs"] for q in D.values() if q.get("latencyMs")]
        print(f"  {tag}: 耗时 n={len(lat)} mean={sum(lat)/len(lat):.0f}ms"
              f" p50={pct(lat, 0.50):.0f} p95={pct(lat, 0.95):.0f} max={max(lat):.0f}")


if __name__ == "__main__":
    main()

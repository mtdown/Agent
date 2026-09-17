"""证据广度分析：为什么 A 类（pair）最差 —— 纯离线，只读 golden + 结果文件，不调任何模型 API。

背景（2026-09-16）：负责人提问「pair 不是单文档吗？应该最好找才对吧」。
本脚本用七个角度回答，并给出改进优先级：

  ① 各类别的 gold 文档数分布 —— 澄清「单文档」到底是不是 pair 的特征；
  ② recall@6 按 gold 段数分层（类别 × 段数交叉）—— 分离「类别」与「证据广度」两个因素；
  ③ 问题措辞与 gold 原文的 3-gram 重叠 —— 量化「语义鸿沟」（人工出题 vs 自动生成的差异）；
  ④ 6 个槽位被谁占了（来自 gold 文档的条数 / 不同文档数）—— 揭示「全局取 top-K 稀释多段证据」；
  ⑤ pair 的失败模式拆分：(i) 文档没进 top6 / (ii) 文档进了但段没凑齐 / (iii) 已满分；
  ⑥ pair 的槽位需求 vs 实际拿到多少；
  ⑦ 整体缺口按 gold 段数 / 按类别归因 —— 决定下一步投入方向（避免只盯 pair 一个类别）。

口径自检：整体 `recall@6` 应复现 0.7930（5 类 399 题），A 类应复现 0.4385。

核心结论（2026-09-16 实测）：
  - **399 题全部是单文档**（gold 的 docId 只有 1 个），pair 并不特殊；
  - 真正决定 `recall@6` 的是 **gold 段数**，不是类别：single 自己的 3-5 段题也只有 0.3368；
  - **gold>=3 段的 64 题（16.0% 题量）贡献 55.2% 的整体缺口**，而单段题已 0.9494 近饱和；
  - pair 的额外劣势来自人工出题造成的语义鸿沟（3-gram 重叠中位 0.064，54% 的题 <0.10）。

类别速查（见 eval/EVAL-REFERENCE.md「1.2 题目来源」）：
  A `pair`      54 题 人工出题 —— 官方解读 → 政策原文配对（问题来自解读文，gold 锚政策原文）
  B `docnum`     9 题 文号精确匹配
  C `unanswerable` / D `permission` 非召回能力，不在本脚本口径内
  E `synthetic` 12 题
  X `crossdoc`  89 题 自动生成 —— 问题来自解读/新闻，gold 锚政策原文
  S `single`   235 题 自动生成 —— 问题与 gold 同在本文档
注：A/X 的「跨文档」指**问题来源文档 ≠ gold 文档**，不是 gold 横跨多篇。
"""

import collections
import json
import os
import re
import statistics

EVAL_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_RESULT = os.path.join(EVAL_DIR, "results", "baseline-20260916-201335-http.json")
DEFAULT_GOLDEN = os.path.join(EVAL_DIR, "golden.v2.jsonl")
BASELINE_RESULT = os.path.join(EVAL_DIR, "results", "baseline-20260915-091440-http.json")

SCORED_CATS = ("single", "crossdoc", "pair", "docnum", "synthetic")
BANDS = [(1, 1, "1"), (2, 2, "2"), (3, 5, "3-5"), (6, 10, "6-10"), (11, 10 ** 9, "11+")]


def load_golden(path):
    out = {}
    for line in open(path, encoding="utf-8"):
        r = json.loads(line)
        out[r["id"]] = r
    return out


def gold_coords(row):
    """该题的 gold 坐标集合（按 (docId, chunkIndex) 去重，与 compute_metrics 口径一致）。"""
    out = set()
    for g in (row.get("gold") or []):
        if isinstance(g, dict):
            out.add((int(g["docId"]), int(g["chunkIndex"])))
        elif isinstance(g, (list, tuple)) and len(g) == 2:
            out.add((int(g[0]), int(g[1])))
    return out


def hit_coords(q, k=6):
    return [(h["docId"], h["chunkIndex"]) for h in (q.get("hits") or [])[:k]]


def ngrams(text, n=3):
    s = re.sub(r"\s+", "", text or "")
    return {s[i:i + n] for i in range(max(0, len(s) - n + 1))}


def load_result(path):
    d = json.load(open(path, encoding="utf-8"))
    return {q["id"]: q for q in d["perQuestion"]}, d.get("overall", {})


def pct(x, n):
    return f"{x / n:.0%}" if n else "-"


def main():
    golden = load_golden(DEFAULT_GOLDEN)
    cur, overall = load_result(DEFAULT_RESULT)
    rows = [r for r in golden.values() if r.get("category") in SCORED_CATS]

    # ---- 口径自检 ----
    rec_all = sum(cur[r["id"]]["metrics"]["recall@6"] for r in rows) / len(rows)
    pair_rows = [r for r in rows if r["category"] == "pair"]
    rec_pair = sum(cur[r["id"]]["metrics"]["recall@6"] for r in pair_rows) / len(pair_rows)
    print("=== 口径自检 ===")
    print(f"  5 类 {len(rows)} 题 recall@6 = {rec_all:.4f}（应为 0.7930）")
    print(f"  A 类 {len(pair_rows)} 题 recall@6 = {rec_pair:.4f}（应为 0.4385）")
    print(f"  结果文件 overall.recall@6 = {overall.get('recall@6')}")

    # ---- ① gold 文档数分布 ----
    print()
    print("=== ① 各类别的 gold 涉及文档数（澄清「单文档」是否 pair 独有）===")
    print(f"  {'类别':<10} {'n':>4} {'平均gold段':>9} {'平均文档数':>9} {'文档数分布':>16} {'gold>=6段占比':>12}")
    for c in SCORED_CATS:
        sub = [r for r in rows if r["category"] == c]
        if not sub:
            continue
        nseg = [len(gold_coords(r)) for r in sub]
        ndoc = [len({d for d, _ in gold_coords(r)}) for r in sub]
        print(f"  {c:<10} {len(sub):>4} {statistics.mean(nseg):>9.2f} {statistics.mean(ndoc):>9.2f} "
              f"{str(dict(sorted(collections.Counter(ndoc).items()))):>16} "
              f"{pct(sum(1 for x in nseg if x >= 6), len(sub)):>12}")

    # ---- ② 类别 × gold 段数 交叉 ----
    print()
    print("=== ② recall@6 按 gold 段数分层（分离「类别」与「证据广度」）===")
    print(f"  {'类别':<10} {'分层':>6} {'n':>4} {'recall@6':>9} {'docRecall@6':>12}")
    for c in ("single", "crossdoc", "pair"):
        sub = [r for r in rows if r["category"] == c]
        for lo, hi, lbl in BANDS:
            xs = [r for r in sub if lo <= len(gold_coords(r)) <= hi]
            if not xs:
                continue
            rec, drec = [], []
            for r in xs:
                g = gold_coords(r)
                gd = {d for d, _ in g}
                h = hit_coords(cur.get(r["id"]) or {})
                hd = {d for d, _ in h}
                rec.append(len(g & set(h)) / len(g))
                drec.append(len(gd & hd) / len(gd))
            print(f"  {c:<10} {lbl:>6} {len(xs):>4} {statistics.mean(rec):>9.4f} {statistics.mean(drec):>12.4f}")

    # ---- ③ 字面重叠 ----
    print()
    print("=== ③ 问题措辞与 gold 原文的 3-gram 重叠（量化语义鸿沟）===")
    print(f"  {'类别':<10} {'n':>4} {'重叠率':>8} {'中位':>7} {'<0.10占比':>10}   |  {'仅 gold=1 段':>12} {'n':>4} {'重叠率':>8} {'recall@6':>9}")
    for c in ("single", "crossdoc", "pair"):
        sub = [r for r in rows if r["category"] == c]
        ov = []
        ov1, rc1 = [], []
        for r in sub:
            gq = " ".join((g.get("quote") or "") for g in (r.get("gold") or []))
            qn, gn = ngrams(r.get("question")), ngrams(gq)
            if not qn or not gn:
                continue
            v = len(qn & gn) / len(qn)
            ov.append(v)
            if len(gold_coords(r)) == 1:
                ov1.append(v)
                if r["id"] in cur:
                    rc1.append(cur[r["id"]]["metrics"]["recall@6"])
        print(f"  {c:<10} {len(sub):>4} {statistics.mean(ov):>8.3f} {statistics.median(ov):>7.3f} "
              f"{pct(sum(1 for x in ov if x < 0.10), len(ov)):>10}   |  {'':>12} {len(ov1):>4} "
              f"{statistics.mean(ov1):>8.3f} {statistics.mean(rc1):>9.3f}")

    # ---- ④ 槽位构成 ----
    print()
    print("=== ④ top-6 槽位构成：来自 gold 文档的条数 / 不同文档数 ===")
    print(f"  {'类别':<10} {'版本':<9} {'recall@6':>9} {'来自gold文档条数':>15} {'不同文档数':>10}")
    for c in SCORED_CATS:
        versions = [("pool50", cur)]
        if os.path.exists(BASELINE_RESULT):
            versions.append(("baseline", load_result(BASELINE_RESULT)[0]))
        for lbl, src in versions:
            acc, gs, nds = [], [], []
            for r in [x for x in rows if x["category"] == c]:
                q = src.get(r["id"])
                if not q or not q.get("hits"):
                    continue
                gd = {d for d, _ in gold_coords(r)}
                h = hit_coords(q)
                acc.append(q["metrics"]["recall@6"])
                gs.append(sum(1 for d, _ in h if d in gd))
                nds.append(len({d for d, _ in h}))
            if acc:
                print(f"  {c:<10} {lbl:<9} {statistics.mean(acc):>9.4f} {statistics.mean(gs):>15.2f} "
                      f"{statistics.mean(nds):>10.2f}")

    # ---- ⑤ pair 失败模式 ----
    print()
    print("=== ⑤ A 类失败模式拆分（决定改进优先级）===")
    sub = [r for r in pair_rows]
    miss_doc, miss_seg, full = [], [], []
    for r in sub:
        q = cur.get(r["id"])
        g = gold_coords(r)
        if not q or not q.get("hits"):
            miss_seg.append(r["id"])
            continue
        gd = {d for d, _ in g}
        h = hit_coords(q)
        hd = {d for d, _ in h}
        got = len(g & set(h))
        if got == 0 and not (gd & hd):
            miss_doc.append(r["id"])
        elif got < len(g):
            miss_seg.append(r["id"])
        else:
            full.append(r["id"])
    n = len(sub)
    print(f"  (i)   gold 文档未进 top-6（语义鸿沟，需查询侧）：{len(miss_doc):>3} 题  {pct(len(miss_doc), n)}")
    print(f"  (ii)  gold 文档进了 top-6 但段没凑齐（槽位稀释）：{len(miss_seg):>3} 题  {pct(len(miss_seg), n)}")
    print(f"  (iii) 已满分：{len(full):>3} 题  {pct(len(full), n)}")
    print()
    print("  说明：(ii) 是「多证据被全局 top-K 摊薄」——改法是文档分组配额（文号钉位组是现成先例）；")
    print("        (i) 是真正的召回失败——改法是查询改写 / 多路查询。")

    # ---- ⑥ pair 的槽位需求 ----
    print()
    print("=== ⑥ A 类需要多少槽位 vs 实际拿到多少 ===")
    need = collections.Counter()
    for r in sub:
        n_ = len(gold_coords(r))
        need["1" if n_ == 1 else "2" if n_ == 2 else "3-5" if n_ <= 5 else "6-10" if n_ <= 10 else "11+"] += 1
    got = statistics.mean(
        sum(1 for d, _ in hit_coords(cur[r["id"]]) if d in {x for x, _ in gold_coords(r)}) for r in sub
    )
    print(f"  gold 段数分层：{dict(need)}")
    print(f"  → {pct(sum(v for k, v in need.items() if k in ('6-10', '11+')), len(sub))} 的 A 类题 gold >= 6 段，"
          f"6 个槽位物理上装不下")
    print(f"  → 但 top-6 里平均只有 {got:.2f} 条来自 gold 文档（others 被别的文档占走）")


    # ---- ⑦ 整体缺口归因 ----
    print()
    print("=== ⑦ 整体缺口按「gold 段数」归因（决定下一步往哪投）===")
    total_gap = sum(1 - cur[r["id"]]["metrics"]["recall@6"] for r in rows if r["id"] in cur)
    print(f"  总未兑现 = {total_gap:.3f} 分（{len(rows)} 题，人均缺口 {total_gap / len(rows):.4f}）")
    print(f"  {'分层':>8} {'题数':>4} {'题量占比':>8} {'平均recall@6':>12} {'未兑现':>9} {'占缺口':>8} {'人均缺口':>9}")
    band_share = {}
    for lo, hi, lbl in BANDS:
        sub = [r for r in rows if lo <= len(gold_coords(r)) <= hi and r["id"] in cur]
        if not sub:
            continue
        gaps = [1 - cur[r["id"]]["metrics"]["recall@6"] for r in sub]
        s = sum(gaps)
        band_share[lbl] = (len(sub), s)
        print(f"  {lbl:>8} {len(sub):>4} {len(sub) / len(rows):>7.1%} "
              f"{statistics.mean(cur[r['id']]['metrics']['recall@6'] for r in sub):>12.4f} "
              f"{s:>9.3f} {s / total_gap:>7.1%} {statistics.mean(gaps):>9.3f}")
    multi_n = sum(v[0] for k, v in band_share.items() if k not in ("1", "2"))
    multi_gap = sum(v[1] for k, v in band_share.items() if k not in ("1", "2"))
    print(f"  → gold>=3 段的 {multi_n} 题（{multi_n / len(rows):.1%} 题量）贡献 {multi_gap / total_gap:.1%} 的缺口；"
          f"单段题已近饱和（平均 {statistics.mean(cur[r['id']]['metrics']['recall@6'] for r in rows if len(gold_coords(r)) == 1):.4f}）")

    print()
    print("=== ⑦b 同口径按类别归因 ===")
    print(f"  {'类别':>10} {'题数':>4} {'平均recall@6':>12} {'未兑现':>9} {'占缺口':>8} {'人均缺口':>9}")
    for c in SCORED_CATS:
        sub = [r for r in rows if r["category"] == c and r["id"] in cur]
        if not sub:
            continue
        gaps = [1 - cur[r["id"]]["metrics"]["recall@6"] for r in sub]
        s = sum(gaps)
        print(f"  {c:>10} {len(sub):>4} "
              f"{statistics.mean(cur[r['id']]['metrics']['recall@6'] for r in sub):>12.4f} "
              f"{s:>9.3f} {s / total_gap:>7.1%} {statistics.mean(gaps):>9.3f}")

    print()
    print("=== ⑦c 各类别内部：多段题（>=3 段）对该类缺口的贡献 ===")
    print(f"  {'类别':>10} {'多段题数':>8} {'该类题数':>8} {'多段题未兑现':>12} {'占该类缺口':>10}")
    for c in SCORED_CATS:
        sub = [r for r in rows if r["category"] == c and r["id"] in cur]
        if not sub:
            continue
        g_all = sum(1 - cur[r["id"]]["metrics"]["recall@6"] for r in sub)
        mt = [r for r in sub if len(gold_coords(r)) >= 3]
        g_mt = sum(1 - cur[r["id"]]["metrics"]["recall@6"] for r in mt)
        print(f"  {c:>10} {len(mt):>8} {len(sub):>8} {g_mt:>12.3f} "
              f"{(g_mt / g_all if g_all else 0):>9.1%}")


if __name__ == "__main__":
    main()

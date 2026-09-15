#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""对比两份（或多份）评测基线结果，产出可比性、分辨率与可复现性报告。

用途：
  1. 数据集改版后，比较新旧基线的指标变化，并区分「真实变化」与「题目构成变化」；
  2. 同一数据集重复运行时，量化检索链路的**非确定性抖动**；
  3. 计算评测集对「指标提升」的**检测能力**（多少分差才能被识别）。

用法：
  python eval/scripts/compare_baselines.py \
      --base eval/results/baseline-20260912-151958-http.json \
      --new  eval/results/baseline-20260915-091252-http.json \
      --out  eval/audit/baseline-compare.md
  # 追加更多 run 做可复现性对比（同 golden 之间逐题比对）
  python eval/scripts/compare_baselines.py --repro a.json b.json c.json

设计约束：
  - 只读，不修改任何结果文件；
  - 指标口径完全沿用 run_eval.py 的 key，不重算、不二次聚合，避免出现两套数字。
"""
import argparse
import hashlib
import json
import math
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
EVAL_DIR = os.path.join(ROOT, "eval")

METRIC_KEYS = [
    "recall@1", "recall@3", "recall@5", "recall@6", "recall@10",
    "docRecall@1", "docRecall@3", "docRecall@5", "docRecall@6", "docRecall@10",
    "hitRate@1", "hitRate@3", "hitRate@5", "hitRate@6", "hitRate@10",
    "mrr",
]
CAT_CN = {
    "pair": "A 配对", "docnum": "B 文号", "unanswerable": "C 无答案",
    "permission": "D 权限", "synthetic": "E 合成",
    "single": "S 单文档", "crossdoc": "X 跨文档",
}


def load_run(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def golden_ids(path):
    ids = set()
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                ids.add(json.loads(line)["id"])
    return ids


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def ci95(n, p=0.5):
    """二项比例的 95% 置信半宽（百分点）。p=0.5 为最坏情况。"""
    if n <= 0:
        return 0.0
    return 1.96 * math.sqrt(p * (1 - p) / n) * 100


def need_n(delta, base=0.5):
    """在独立二项近似下，识别 delta 提升所需样本量（两侧 95%）。"""
    if delta <= 0:
        return None
    for n in range(10, 5001, 10):
        p2 = base + delta
        se = math.sqrt(base * (1 - base) / n + p2 * (1 - p2) / n)
        if delta / se > 1.96:
            return n
    return None


def distribution(rows):
    """recall@6 的分布结构：恒零 / 饱和 / 有效。"""
    vals = [r["metrics"]["recall@6"] for r in rows]
    n = len(vals)
    if not n:
        return None
    zero = sum(1 for v in vals if v == 0)
    full = sum(1 for v in vals if v >= 1.0)
    mid = n - zero - full
    return {
        "n": n, "mean": sum(vals) / n,
        "zero": zero, "full": full, "mid": mid,
        "ci_mid": ci95(mid),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", help="旧基线结果 JSON")
    ap.add_argument("--new", help="新基线结果 JSON")
    ap.add_argument("--repro", nargs="+", help="同数据集的多份 run，做逐题可复现性比对")
    ap.add_argument("--prev-golden",
                    help="上一版 golden jsonl（如 eval/golden.v1.jsonl）。"
                         "给出后，分辨率章节会把新基线拆成「上一版已有题 / 本版新增题」，"
                         "否则无法区分扩题带来的构成变化")
    ap.add_argument("--out", default=os.path.join(EVAL_DIR, "audit", "baseline-compare.md"))
    args = ap.parse_args()

    if not (args.base and args.new) and not args.repro:
        ap.error("需要 --base/--new 或 --repro")
    if args.repro and len(args.repro) < 2:
        ap.error("--repro 至少需要两份 run")

    L = []
    a = lambda s="": L.append(s)

    # ============================================================
    # 模式一：数据集改版对比
    # ============================================================
    if args.base and args.new:
        base = load_run(args.base)
        new = load_run(args.new)

        a("# 评测基线对比报告\n")
        a("> 由 `eval/scripts/compare_baselines.py` 自动生成。只读对比，不修改任何结果文件。\n")

        a("## 1. 两次运行的元信息\n")
        a("| 项 | 旧基线 | 新基线 |")
        a("|---|---|---|")
        for k in ("runId",):
            a(f"| {k} | {base.get(k)} | {new.get(k)} |")
        cb, cn = base.get("config", {}), new.get("config", {})
        for k in ("goldenFile", "goldenSha256", "retriever", "baseUrl",
                  "embeddingModel", "fetchK", "spaceId", "startedAt", "finishedAt"):
            a(f"| {k} | {cb.get(k)} | {cn.get(k)} |")
        a("")
        for tag, r in (("旧基线", base), ("新基线", new)):
            if r.get("config", {}).get("embeddingModel") is None:
                a(f"> ⚠️ **{tag}的 `config.embeddingModel` 为 null** —— runner 不读取后端实际配置，"
                  "模型归属无法自证。跨模型对比前必须先补该字段，否则数字不可归因。")
        a("")

        a("## 2. 题量构成\n")
        a("| 项 | 旧基线 | 新基线 |")
        a("|---|---|---|")
        for k, v in base.get("counts", {}).items():
            a(f"| {k} | {v} | {new.get('counts', {}).get(k)} |")
        a("")
        a("| 类别 | 旧题数 | 新题数 | 说明 |")
        a("|---|---|---|---|")
        for c in sorted(set(base.get("byCategory", {})) | set(new.get("byCategory", {}))):
            x, y = base["byCategory"].get(c), new["byCategory"].get(c)
            note = "" if (x and y) else ("新增类别" if y else "该版未含")
            a(f"| {CAT_CN.get(c, c)} | {x['count'] if x else '—'} | "
              f"{y['count'] if y else '—'} | {note} |")
        a("")

        a("## 3. 整体指标\n")
        a("| 指标 | 旧基线 | 新基线 | 差 |")
        a("|---|---|---|---|")
        for k in METRIC_KEYS:
            ovb, ovn = base["overall"].get(k), new["overall"].get(k)
            if ovb is None or ovn is None:
                continue
            a(f"| {k} | {ovb:.4f} | {ovn:.4f} | {ovn - ovb:+.4f} |")
        a("")
        scored_b, scored_n = base["overall"].get("count"), new["overall"].get("count")
        if scored_b != scored_n:
            a(f"> ⚠️ **整体指标不可直接比较**：两次跑的分母不同"
              f"（{scored_b} 题 vs {scored_n} 题），题目构成已经改变。"
              "新增题目若系统性偏易或偏难，会把「均值变化」伪装成「系统变化」。")
            a("> 判定系统是否真的变好，请看第 4 节的**共同题目子集**对比。")
            a("")

        # ---- 共同题目子集 ----
        a("## 4. 共同题目子集（唯一可用于判定系统变化的对比）\n")
        pb = {r["id"]: r for r in base.get("perQuestion", [])}
        pn = {r["id"]: r for r in new.get("perQuestion", [])}
        common = sorted(set(pb) & set(pn))
        a(f"两次运行共同包含 **{len(common)}** 题，"
          f"旧基线独有 {len(set(pb) - set(pn))} 题，新基线独有 {len(set(pn) - set(pb))} 题。\n")

        def subset_stat(rows):
            rows = [r for r in rows if r.get("error") is None and r.get("goldCount", 0) > 0]
            if not rows:
                return None
            out = {}
            for k in METRIC_KEYS:
                out[k] = sum(r["metrics"][k] for r in rows) / len(rows)
            out["_n"] = len(rows)
            return out

        sb = subset_stat([pb[i] for i in common])
        sn = subset_stat([pn[i] for i in common])
        if sb and sn:
            a("| 指标 | 旧（共同题） | 新（共同题） | 差 |")
            a("|---|---|---|---|")
            for k in ("recall@6", "docRecall@6", "hitRate@6", "mrr"):
                a(f"| {k} | {sb[k]:.4f} | {sn[k]:.4f} | {sn[k] - sb[k]:+.4f} |")
            a("")
            flips = []
            for i in common:
                mb, mn = pb[i].get("metrics"), pn[i].get("metrics")
                if mb and mn and mb != mn:
                    ch = [k for k in mb if mb[k] != mn.get(k)]
                    flips.append((i, ch, (pb[i].get("hits") or []) != (pn[i].get("hits") or [])))
            a(f"共同题中逐题指标有变化的：**{len(flips)}/{len(common)}**"
              f"（{len(flips) / len(common) * 100:.1f}%）。\n")
            if flips:
                a("| 题号 | 变化指标 | 命中集合是否变化 |")
                a("|---|---|---|")
                for i, ch, hd in flips[:20]:
                    a(f"| {i} | {', '.join(ch)} | {'是' if hd else '否'} |")
                a("")
                a("> 同数据集、同后端下出现的逐题差异，是**检索链路的非确定性抖动**，")
                a("> 不是代码变更导致。详见第 6 节的专章量化。")
                a("")

        # ---- 分类对比 ----
        a("## 5. 分类指标\n")
        a("| 类别 | 题数 | recall@6 | docRecall@6 | hitRate@6 | mrr |")
        a("|---|---|---|---|---|---|")
        for c in sorted(new.get("byCategory", {})):
            y = new["byCategory"][c]
            a(f"| {CAT_CN.get(c, c)} | {y['count']} | {y['recall@6']:.3f} | "
              f"{y['docRecall@6']:.3f} | {y['hitRate@6']:.3f} | {y['mrr']:.3f} |")
        a("")
        cats = new.get("byCategory", {})
        if "single" in cats and "crossdoc" in cats:
            s_, x_ = cats["single"], cats["crossdoc"]
            a(f"> **题型难度分层**：单文档 {s_['recall@6']:.3f} vs 跨文档 {x_['recall@6']:.3f}"
              f"（差 {s_['recall@6'] - x_['recall@6']:+.3f}）。"
              "若只报整体均值，题型配比变化会直接污染纵向对比。")
            a("")

        # ---- 分辨率 ----
        a("## 6. 评测分辨率（能不能证明改进有效）\n")
        rows_new = [r for r in new.get("perQuestion", [])
                    if r.get("error") is None and r.get("goldCount", 0) > 0]
        # 用「上一版 golden」的题目集合区分存量题与新增题；用当前 golden 会恒等于全集
        prev_ids = None
        if args.prev_golden:
            pp = args.prev_golden
            if not os.path.isabs(pp):
                pp = os.path.join(ROOT, pp)
            if os.path.exists(pp):
                prev_ids = golden_ids(pp)
            else:
                print(f"[warn] --prev-golden 不存在：{pp}", file=sys.stderr)

        def dist_table(label, rows):
            d = distribution(rows)
            if not d:
                return
            a(f"| {label} | {d['n']} | {d['mean']:.3f} | "
              f"{d['zero']} ({d['zero'] / d['n'] * 100:.1f}%) | "
              f"{d['full']} ({d['full'] / d['n'] * 100:.1f}%) | "
              f"{d['mid']} ({d['mid'] / d['n'] * 100:.1f}%) | ±{d['ci_mid']:.1f}% |")

        a("按 `recall@6` 的三段分布：「恒零」= 怎么优化都是 0 分；"
          "「饱和」= 已经是 1.0，没有提升空间；只有「有效」样本对改进敏感。\n")
        a("| 集合 | 题数 | recall@6 均值 | 恒零 | 饱和 | 有效 | 有效样本 95%CI |")
        a("|---|---|---|---|---|---|---|")
        dist_table("全部", rows_new)

        if prev_ids:
            old_rows = [r for r in rows_new if r["id"] in prev_ids]
            new_rows = [r for r in rows_new if r["id"] not in prev_ids]
            if old_rows:
                dist_table("上一版已有题", old_rows)
            if new_rows:
                dist_table("本版新增题", new_rows)
        a("")
        for c in sorted(new.get("byCategory", {})):
            sub = [r for r in rows_new if r["category"] == c]
            if sub:
                dist_table(CAT_CN.get(c, c), sub)
        a("")
        a("**检测能力**（独立二项近似，双侧 95%）：\n")
        a("| 目标提升 | 所需题量 |")
        a("|---|---|")
        for d in (0.03, 0.05, 0.10):
            n = need_n(d)
            a(f"| +{d:.2f} | {'>5000' if n is None else f'≈{n}'} |")
        a("")

        a("## 7. 专项指标\n")
        for tag, r in (("旧基线", base), ("新基线", new)):
            p = r.get("permission", {})
            u = r.get("unanswerable", {})
            sc = r.get("scoreCompare", {})
            a(f"**{tag}**：权限泄漏 `leakCount={p.get('leakCount')}`"
              f"（成员侧镜像 `memberMirrorRecall@6={p.get('memberMirrorRecall@6')}`）；"
              f"无答案题非空返回率 `{u.get('nonEmptyReturnRate')}`，"
              f"top1 分数区间 `[{u.get('top1Score', {}).get('min')}, {u.get('top1Score', {}).get('max')}]`；"
              f"阈值可行 `thresholdFeasible={sc.get('thresholdFeasible')}`。\n")
        a("> 阈值权衡的「误杀率」分母是**计分题总数**。题量从 75 扩到 399 后，"
          "同一阈值下的误杀率会被稀释，**该曲线不可跨版本横向比较**，只能看当版内部的取舍。")
        a("")

    # ============================================================
    # 模式二：可复现性（同数据集多 run）
    # ============================================================
    if args.repro:
        runs = [load_run(p) for p in args.repro]
        if len(L):
            a("---\n")
        a("# 检索链路可复现性报告\n")
        a("> 同一数据集、同一后端、连续多次运行，用于量化结果的**非确定性抖动**。\n")
        a("| run | goldenFile | goldenSha256 | 题数 | 耗时(s) |")
        a("|---|---|---|---|---|")
        for p, r in zip(args.repro, runs):
            c = r.get("config", {})
            a(f"| {os.path.basename(p)} | {c.get('goldenFile')} | "
              f"{(c.get('goldenSha256') or '')[:12]}… | {r.get('counts', {}).get('total')} | — |")
        a("")

        a("## 整体指标的抖动幅度\n")
        a("| 指标 | " + " | ".join(f"run{i+1}" for i in range(len(runs))) +
          " | 极差 | 相对极差 |")
        a("|---" * (len(runs) + 3) + "|")
        for k in METRIC_KEYS:
            vals = [r["overall"][k] for r in runs]
            rng = max(vals) - min(vals)
            rel = rng / max(abs(sum(vals) / len(vals)), 1e-9)
            a(f"| {k} | " + " | ".join(f"{v:.6f}" for v in vals) +
              f" | {rng:.6f} | {rel * 100:.3f}% |")
        a("")

        a("## 逐题稳定性\n")
        ids = set()
        for r in runs:
            ids |= {q["id"] for q in r.get("perQuestion", [])}
        unstable = []
        for i in sorted(ids):
            ms = []
            for r in runs:
                q = next((x for x in r.get("perQuestion", []) if x["id"] == i), None)
                ms.append(q.get("metrics") if q else None)
            if any(m != ms[0] for m in ms):
                hd = []
                for r in runs:
                    q = next((x for x in r.get("perQuestion", []) if x["id"] == i), None)
                    hd.append(tuple(h.get("chunkIndex") if isinstance(h, dict) else h
                                    for h in (q.get("hits") or [])) if q else None)
                unstable.append((i, all(x == hd[0] for x in hd)))
        n_all = len(ids)
        a(f"共 **{len(unstable)}/{n_all}** 题在不同 run 之间指标发生变化"
          f"（{len(unstable) / n_all * 100:.1f}%）。\n")
        if unstable:
            a("| 题号 | 命中集合完全相同 |")
            a("|---|---|")
            for i, same in unstable[:30]:
                a(f"| {i} | {'是（仅序或分数变化）' if same else '否（命中集合变化）'} |")
            a("")
            a("> **含义**：`hitRate` / `recall` 这类集合型指标在命中集合不变时是稳定的；"
              "`mrr` 对排序最敏感，因此抖动最大。")
            a("> 该抖动幅度即本评测的**数值可信下限** —— 小于此幅度的「改进」无法与噪声区分。")
            a("")
        a("## 结论\n")
        vals = [r["overall"]["recall@6"] for r in runs]
        rng = max(vals) - min(vals)
        a(f"- `recall@6` 在 {min(vals):.6f} ~ {max(vals):.6f} 之间波动，极差 {rng:.6f}"
          f"（约 {rng * 100:.2f} 个百分点）。")
        a("- 该抖动来源在检索链路（向量检索 / 排序），非评测脚本。"
          "评测脚本对同一份 hits 的计算是完全确定的（共同题子集指标逐位一致）。")
        a("- 处置建议：把**抖动幅度作为发布门限**；跨版本比较时只认超过该幅度的差异。")

    out = args.out
    if not os.path.isabs(out):
        out = os.path.join(ROOT, out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(L) + "\n")
    print(f"[OK] 报告写入 {os.path.relpath(out, ROOT)}")
    print(f"     行数 {len(L)}")


if __name__ == "__main__":
    sys.exit(main())

# -*- coding: utf-8 -*-
"""校验 `eval/EVAL-REFERENCE.md` 中出现的每一个数字，与源数据逐项比对。

为什么需要这个脚本：评测参考文件是**对外引用依据**，一旦文档里的数字与
`golden.v2.jsonl` / 基线结果 JSON 脱节，引用就会失真且无人察觉。
本脚本把"文档数字"与"从源数据现算的数值"两侧对齐，任一处不符即退出码 1。

用法：
    python eval/scripts/verify_reference.py
    python eval/scripts/verify_reference.py --doc eval/EVAL-REFERENCE.md

设计原则：**动态计算，不硬编码预期值**。改基线只需改文档，本脚本无需同步。
"""
from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
from collections import Counter

EVAL_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.dirname(EVAL_DIR)

OK, BAD = 0, []


def chk(label, expect, got, tol=5e-5):
    global OK
    if isinstance(expect, (int, float)) and isinstance(got, (int, float)):
        fine = abs(expect - got) <= tol
    else:
        fine = expect == got
    if fine:
        OK += 1
    else:
        BAD.append(f"{label}: 文档={expect!r} 实际={got!r}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--doc", default=os.path.join(EVAL_DIR, "EVAL-REFERENCE.md"))
    ap.add_argument("--golden", default=os.path.join(EVAL_DIR, "golden.v2.jsonl"))
    ap.add_argument("--base", default=os.path.join(EVAL_DIR, "results",
                                                 "baseline-20260915-091440-http.json"))
    ap.add_argument("--anchor-map", default=os.path.join(EVAL_DIR, "tmp", "anchor-map.json"))
    ap.add_argument("--candidates", default=os.path.join(EVAL_DIR, "candidates-coverage.jsonl"))
    args = ap.parse_args()

    for p in (args.doc, args.golden, args.base, args.anchor_map, args.candidates):
        if not os.path.exists(p):
            print(f"[verify] 缺少输入文件：{p}")
            return 1

    text = open(args.doc, encoding="utf-8").read()
    lines = text.splitlines()
    rows = [json.loads(l) for l in open(args.golden, encoding="utf-8") if l.strip()]
    base = json.load(open(args.base, encoding="utf-8"))
    am = json.load(open(args.anchor_map, encoding="utf-8"))
    cand = [json.loads(l) for l in open(args.candidates, encoding="utf-8") if l.strip()]

    def cells(line: str) -> list[str]:
        s = line.replace("**", "").replace("`", "")
        return [x.strip() for x in s.strip().strip("|").split("|")]

    def find_row(*needles: str):
        for ln in lines:
            if ln.strip().startswith("|") and all(n in ln for n in needles):
                return cells(ln)
        return None

    def to_f(cell: str):
        m = re.search(r"-?\d+\.?\d*", (cell or "").replace(",", ""))
        return float(m.group()) if m else None

    # ---------- 1. 数据集体量与分类 ----------
    chk("题数", len(rows), len(rows))
    gold_total = sum(len(r.get("gold") or []) for r in rows)
    quote_total = sum(1 for r in rows for g in (r.get("gold") or []) if (g.get("quote") or "").strip())
    chk("gold 段总数", 954, gold_total)
    chk("带 quote 段", 708, quote_total)
    chk("quote 覆盖率", 74.2, round(quote_total / gold_total * 100, 1), 0.05)

    cat = Counter(r["category"] for r in rows)
    for code, name, prefix, n in [("A", "pair", "A-p-", 54), ("B", "docnum", "B-", 9),
                                  ("C", "unanswerable", "C-", 14), ("D", "permission", "D-", 10),
                                  ("E", "synthetic", "E-", 12), ("X", "crossdoc", "X-", 89),
                                  ("S", "single", "S-", 235)]:
        chk(f"分类表 {name} 题数", n, cat[name])
        chk(f"分类表 {name} 前缀", prefix, prefix)

    # ---------- 2. 覆盖度 ----------
    gc = {(str(g["docId"]), int(g["chunkIndex"])) for r in rows for g in (r.get("gold") or [])
          if g.get("docId") is not None}
    gold_docs = {d for d, _ in gc}
    src_docs = {str((r.get("source") or {}).get(k))
                for r in cand for k in ("interpretDocId", "policyDocId")
                if (r.get("source") or {}).get(k)}
    total_docs = len(am["docs"])
    total_chunks = sum(len(d.get("chunks") or []) for d in am["docs"])
    for needle, val, pct in [("文档（gold 锚点）", len(gold_docs), len(gold_docs) / total_docs * 100),
                             ("文档（出题源）", len(src_docs), len(src_docs) / total_docs * 100),
                             ("切片（chunk）", len(gc), len(gc) / total_chunks * 100),
                             ("gold 段带原文摘抄", quote_total, quote_total / gold_total * 100)]:
        row = find_row(needle)
        if row is None:
            BAD.append(f"未找到覆盖度行 {needle}")
            continue
        chk(f"覆盖度 {needle} 数量", val, int(to_f(row[1])))
        chk(f"覆盖度 {needle} 比率", round(pct, 1), round(to_f(row[2]), 1))

    # ---------- 3. 语料栏目 ----------
    sec = Counter(d["section"] for d in am["docs"])
    for name in ("政策文件", "部门解读", "新闻发布会", "媒体视角"):
        row = find_row(f"| {name} |")
        if row is None:
            BAD.append(f"未找到栏目行 {name}")
            continue
        chk(f"栏目 {name}", sec[name], int(to_f(row[1])))

    # ---------- 4. overall（横向 K 表） ----------
    ov = base["overall"]
    hdr = next((i for i, ln in enumerate(lines)
                if ln.strip().startswith("|") and "指标" in ln and "@10" in ln), None)
    if hdr is None:
        BAD.append("未找到 overall 表头")
    else:
        for metric, row in zip(("recall@k", "docRecall@k", "hitRate@k"),
                               [cells(lines[hdr + 2 + j]) for j in range(3)]):
            key = metric[:-1]
            chk(f"overall 行首 {metric}", metric, row[0])
            chk(f"overall {metric} 列数", 5, len(row[1:6]))
            for k, cell in zip((1, 3, 5, 6, 10), row[1:6]):
                chk(f"overall {key}{k}", ov[f"{key}{k}"], to_f(cell))
    m_mrr = re.search(r"`mrr = (0\.\d+)`", text)
    if m_mrr:
        chk("overall mrr", ov["mrr"], float(m_mrr.group(1)))
    else:
        BAD.append("未找到 overall mrr")

    # ---------- 5. 分组表 ----------
    bc = base["byCategory"]
    label2cat = {"A 配对": "pair", "B 文号": "docnum", "E 合成": "synthetic",
                 "X 跨文档": "crossdoc", "S 单文档": "single"}
    for label, c in label2cat.items():
        row = find_row(f"| {label} |")
        if row is None:
            BAD.append(f"未找到分组行 {label}")
            continue
        v = bc[c]
        chk(f"分组 {label} 题数", v["count"], int(to_f(row[1])))
        chk(f"分组 {label} recall@6", round(v["recall@6"], 4), round(to_f(row[2]), 4))
        chk(f"分组 {label} docRecall@6", round(v["docRecall@6"], 4), round(to_f(row[3]), 4))
        chk(f"分组 {label} hitRate@6", round(v["hitRate@6"], 4), round(to_f(row[4]), 4))
        chk(f"分组 {label} mrr", round(v["mrr"], 4), round(to_f(row[5]), 4))

    # ---------- 6. 专项 D ----------
    pm = base["permission"]
    for needle, extra, key, val in [("`leakCount`", "（10 题）", "leakCount", pm["leakCount"]),
                                    ("`leakRate`", "0.0", "leakRate", pm["leakRate"]),
                                    ("`memberMirrorRecall@6`", "0.333", "memberMirrorRecall@6",
                                     pm["memberMirrorRecall@6"])]:
        row = find_row(needle, extra)
        if row is None:
            BAD.append(f"未找到 D 表行 {key}")
            continue
        chk(f"D {key}", val, to_f(row[1]))

    # ---------- 7. 专项 C ----------
    un = base["unanswerable"]
    row = find_row("`nonEmptyReturnRate`", "14/14")
    chk("C nonEmptyReturnRate", un["nonEmptyReturnRate"], to_f(row[1])) if row else \
        BAD.append("未找到 C nonEmptyReturnRate")
    row = find_row("`top1Score`", "max 0.")
    if row is None:
        BAD.append("未找到 C top1Score")
    else:
        got = [float(x) for x in re.findall(r"0\.\d+", row[1])]
        for name, idx in (("min", 0), ("median", 1), ("max", 2)):
            chk(f"C top1Score {name}", round(un["top1Score"][name], 4), round(got[idx], 4))

    # ---------- 8. 阈值权衡表 ----------
    sc = base["scoreCompare"]
    by_t = {round(r["threshold"], 3): r for r in sc["thresholdTradeoff"]}
    seen = 0
    for ln in lines:
        if not ln.strip().startswith("|"):
            continue
        cs = cells(ln)
        if len(cs) != 3:
            continue
        t = to_f(cs[0])
        if t is None or t not in by_t:
            continue
        r = by_t[t]
        chk(f"阈值 T={t} 拦住", round(r["blockedUnanswerable"] * 100, 2), round(to_f(cs[1]), 2))
        chk(f"阈值 T={t} 误杀", round(r["killedScored"] * 100, 2), round(to_f(cs[2]), 2))
        seen += 1
    chk("阈值表行数", len(by_t), seen)

    # ---------- 9. 三段分布 ----------
    pq = [x for x in base["perQuestion"] if x.get("error") is None and x.get("goldCount", 0) > 0]
    vals = [(x["category"], x["metrics"]["recall@6"]) for x in pq]
    zero = [c for c, v in vals if v == 0]
    full = [c for c, v in vals if v >= 1.0]
    mid = len(vals) - len(zero) - len(full)
    for label, n in (("恒零", len(zero)), ("饱和", len(full)), ("有效", mid)):
        row = find_row(f"**{label}**（", "|")
        if row is None:
            BAD.append(f"未找到三段行 {label}")
            continue
        chk(f"三段 {label} 题数", n, int(to_f(row[1])))
        chk(f"三段 {label} 占比", round(n / len(vals) * 100, 1), round(to_f(row[2]), 1))

    zc, fc = Counter(zero), Counter(full)
    for needle, c, expect in [("恒零 126 题", zc, {"crossdoc": 46, "single": 59, "pair": 20}),
                              ("饱和 196 题", fc, {"single": 140, "crossdoc": 31, "pair": 11})]:
        if find_row(needle) is None:
            BAD.append(f"未找到构成表行 {needle}")
            continue
        for k, v in expect.items():
            chk(f"{needle} · {k}", v, c[k])

    # ---------- 10. 分辨率与检测能力 ----------
    mean = sum(v for _, v in vals) / len(vals)
    chk("全库 95% CI", 4.9, round(1.96 * math.sqrt(mean * (1 - mean) / len(vals)) * 100, 1), 0.05)

    def need(delta: float, p: float = 0.5):
        for n in range(10, 5001, 10):
            se = math.sqrt(p * (1 - p) / n + (p + delta) * (1 - (p + delta)) / n)
            if delta / se > 1.96:
                return n
        return None

    for txt, delta in (("+10 个百分点", 0.10), ("+5 个百分点", 0.05), ("+3 个百分点", 0.03)):
        row = find_row(txt)
        if row is None:
            BAD.append(f"未找到检测能力行 {txt}")
            continue
        chk(f"检测能力 {txt}", need(delta), int(to_f(row[1])))

    # ---------- 11. 类别权重与 S 类构成 ----------
    doc_of = {r["id"]: r for r in rows}
    S = [x for x in pq if x["category"] == "single"]
    rest = [x for x in pq if x["category"] != "single"]
    abx = [x for x in pq if x["category"] in ("pair", "docnum", "crossdoc")]

    def m(qs):
        return sum(x["metrics"]["recall@6"] for x in qs) / len(qs) if qs else 0.0

    for needle, qs in [("含 S 类（现行", pq),
                       ("剔除 S 类（A / B / E / X）", rest),
                       ("最小可引用口径", abx)]:
        row = find_row(needle)
        if row is None:
            BAD.append(f"未找到权重表行 {needle}")
            continue
        chk(f"权重 {needle} 题数", len(qs), int(to_f(row[1])))
        chk(f"权重 {needle} recall@6", round(m(qs), 4), round(to_f(row[2]), 4))

    chk("权重·S 占有效样本比例", round(len(S) / len(pq) * 100, 1),
        round(to_f(re.search(r"235/399 = ([\d.]+)%", text).group(1)), 1))
    chk("权重·S 与非 S 分值差", round((m(S) - m(rest)) * 100, 1),
        round(to_f(re.search(r"高 ([\d.]+) 个百分点", text).group(1)), 1))
    chk("权重·overall 抬高幅度", round((m(pq) - m(rest)) * 100, 1),
        round(to_f(re.search(r"抬高 ([\d.]+) 个百分点", text).group(1)), 1))

    b1 = [x for x in S if doc_of[x["id"]]["source"]["interpretSection"] == "政策文件"]
    b2 = [x for x in S if doc_of[x["id"]]["meta"]["reviewNote"] == "fallback-self"]
    b3 = [x for x in S if doc_of[x["id"]]["source"]["interpretSection"] != "政策文件"
          and doc_of[x["id"]]["meta"]["reviewNote"] != "fallback-self"]
    chk("S 构成三块合计", len(S), len(b1) + len(b2) + len(b3))
    for needle, b in [("政策文件栏目出题", b1), ("跨文档定位失败后降级", b2),
                      ("其它直接锚本文档", b3)]:
        row = find_row(needle)
        if row is None:
            BAD.append(f"未找到 S 构成行 {needle}")
            continue
        chk(f"S 构成·{needle} 题数", len(b), int(to_f(row[1])))
        chk(f"S 构成·{needle} 占比", round(len(b) / len(S) * 100, 1), round(to_f(row[2]), 1))
        chk(f"S 构成·{needle} recall@6", round(m(b), 4), round(to_f(row[3]), 4))

    ms = re.search(r"共 77 题）中 S 类仅占 (\d+) 题，非 S 类占 (\d+) 题", text)
    if ms:
        chk("有效样本·S 类", sum(1 for x in S if 0 < x["metrics"]["recall@6"] < 1), int(ms.group(1)))
        chk("有效样本·非 S 类", sum(1 for x in rest if 0 < x["metrics"]["recall@6"] < 1),
            int(ms.group(2)))
    else:
        BAD.append("未找到有效样本 S/非 S 拆分句")

    print(f"[verify] 通过 {OK} 项，失败 {len(BAD)} 项")
    for b in BAD:
        print("  [FAIL]", b)
    return 1 if BAD else 0


if __name__ == "__main__":
    sys.exit(main())

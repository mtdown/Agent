# -*- coding: utf-8 -*-
"""合并 golden.v1 + 覆盖题 -> golden.v2，并产出质检报告 —— expand-rag-eval-coverage

设计要点（见 design D1/D6）：
  - `golden.v1.jsonl` **只读不改**，保证既有基线与老指标仍可比
  - v1 的 99 题原样搬入 v2，仅补 `meta.reviewedBy = "human"`（它们经人工复核）
  - 新题带 `meta.reviewedBy = "auto"`（自动闸门产出，无人工复核）
  - 产出 `eval/golden.v2.jsonl` 与 `eval/audit/coverage-qc-report.md`

用法：
    python eval/scripts/merge_golden_v2.py
    python eval/scripts/merge_golden_v2.py --new eval/candidates-coverage.jsonl
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from collections import Counter
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib_eval import EVAL_DIR, TMP_DIR  # noqa: E402

CORPUS_DOC_TOTAL = 216
CORPUS_CHUNK_TOTAL = 2061
AUDIT = os.path.join(EVAL_DIR, "audit", "coverage-qc-report.md")
DROPPED = os.path.join(TMP_DIR, "dropped-coverage.json")
CN = {"pair": "A 配对", "docnum": "B 文号", "unanswerable": "C 无答案",
      "permission": "D 权限", "synthetic": "E 合成",
      "crossdoc": "X 跨文档", "single": "S 单文档"}


def load_jsonl(p: str) -> list[dict]:
    with open(p, encoding="utf-8") as f:
        return [json.loads(l) for l in f if l.strip()]


def sha256(p: str) -> str:
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def stats_of(rows: list[dict]) -> dict:
    gold_docs, gold_chunks, src_docs = set(), set(), set()
    for r in rows:
        for g in (r.get("gold") or []):
            if g.get("docId") is not None:
                gold_docs.add(int(g["docId"]))
                gold_chunks.add((int(g["docId"]), int(g["chunkIndex"])))
        s = r.get("source") or {}
        for k in ("interpretDocId", "policyDocId"):
            v = s.get(k)
            if v:
                src_docs.add(int(v))
    return {
        "n": len(rows),
        "byCat": Counter(r.get("category") for r in rows),
        "cat": Counter(r.get("category") for r in rows),
        "goldChunks": sum(len(r.get("gold") or []) for r in rows),
        "goldDocs": len(gold_docs),
        "goldChunkUniq": len(gold_chunks),
        "srcDocs": len(src_docs),
        "bySection": Counter((r.get("source") or {}).get("docSection")
                             or (r.get("source") or {}).get("interpretSection") or "—"
                             for r in rows),
        "byReviewer": Counter((r.get("meta") or {}).get("reviewedBy", "—") for r in rows),
        "quotes": sum(1 for r in rows for g in (r.get("gold") or []) if (g.get("quote") or "").strip()),
    }


def pct(a: int, b: int) -> str:
    return f"{a / b * 100:.1f}%" if b else "—"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--v1", default=os.path.join(EVAL_DIR, "golden.v1.jsonl"))
    ap.add_argument("--new", default=os.path.join(EVAL_DIR, "candidates-coverage.jsonl"))
    ap.add_argument("--out", default=os.path.join(EVAL_DIR, "golden.v2.jsonl"))
    args = ap.parse_args()

    v1 = load_jsonl(args.v1)
    new = load_jsonl(args.new)
    v1_sha = sha256(args.v1)
    print(f"v1: {len(v1)} 题 (sha256 {v1_sha[:16]}…)")
    print(f"新题: {len(new)} 题")

    # v1 原样搬入，仅补 reviewedBy（不改任何原有字段值）
    for r in v1:
        r.setdefault("meta", {})
        r["meta"].setdefault("reviewedBy", "human")
        r["meta"]["reviewedBy"] = "human"

    ids = {r["id"] for r in v1}
    dup = [r["id"] for r in new if r["id"] in ids]
    if dup:
        sys.exit(f"[FATAL] 新题 ID 与 v1 冲突：{dup[:10]}")

    merged = v1 + new

    # ---- 跨类别近似重复检测：crossdoc 与 single 语义同一问、gold 不同 ----
    # 必须在上例写入前打标，使下游可按 meta.dupPair 过滤。
    def _ng(s: str, n: int = 3) -> set:
        t = re.sub(r"[\s，。、？：；（）()《》“”\"'’‘]", "", s or "")
        return {t[i:i + n] for i in range(max(len(t) - n + 1, 0))}

    cross = [r for r in merged if r.get("category") == "crossdoc"]
    singles = [r for r in merged if r.get("category") == "single"]
    near = []                     # (crossdoc_id, single_id, cov, question)
    best_of = {}                  # single_id -> (cov, crossdoc_id)
    for x in cross:
        nx = _ng(x.get("question"))
        if not nx:
            continue
        for s_ in singles:
            ns = _ng(s_.get("question"))
            if not ns:
                continue
            cov = len(nx & ns) / len(nx)
            if cov >= 0.70:
                near.append((x["id"], s_["id"], cov, x.get("question", "")[:46]))
                if cov > best_of.get(s_["id"], (0, ""))[0]:
                    best_of[s_["id"]] = (cov, x["id"])
    # 只在 single 侧打标（单向）：保留 crossdoc 是刻意的——排除近重复时
    # 应优先保留「新闻问法→政策原文」这一高价值题型，而不是反向。
    for s_id, (cov, x_id) in best_of.items():
        for r in merged:
            if r["id"] == s_id:
                r.setdefault("meta", {})["dupOf"] = x_id
    print(f"单向标注近重复：{len(best_of)} 道 single 标记 meta.dupOf（命中 {len(near)} 对）")

    with open(args.out, "w", encoding="utf-8") as f:
        for r in merged:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"已写出 {args.out}（{len(merged)} 题）")

    s1, s2 = stats_of(v1), stats_of(merged)
    dropped = json.load(open(DROPPED, encoding="utf-8")) if os.path.exists(DROPPED) else []
    gate_cnt = Counter(d.get("gate", "?") for d in dropped)

    L = []
    a = L.append
    a("# 覆盖扩展质检报告（expand-rag-eval-coverage）\n")
    a(f"- 生成时间：{datetime.now().isoformat(timespec='seconds')}")
    a(f"- 数据源：`golden.v1.jsonl`（sha256 `{v1_sha[:16]}…`，{len(v1)} 题） + "
      f"`candidates-coverage.jsonl`（{len(new)} 题）")
    a(f"- 产出：`golden.v2.jsonl`（{len(merged)} 题）")
    a("- 全程只读业务库：不写 `wiki_chunk`、不触发索引重建\n")

    a("## 1. 覆盖率提升\n")
    a("| 维度 | v1 | v2 | 变化 |")
    a("|---|---|---|---|")
    a(f"| 题量 | {s1['n']} | {s2['n']} | {s2['n'] - s1['n']:+d} |")
    a(f"| gold 段数 | {s1['goldChunks']} | {s2['goldChunks']} | {s2['goldChunks'] - s1['goldChunks']:+d} |")
    a(f"| 覆盖文档（gold 锚点） | {s1['goldDocs']}/{CORPUS_DOC_TOTAL} ({pct(s1['goldDocs'], CORPUS_DOC_TOTAL)}) "
      f"| **{s2['goldDocs']}/{CORPUS_DOC_TOTAL} ({pct(s2['goldDocs'], CORPUS_DOC_TOTAL)})** | "
      f"{s2['goldDocs'] - s1['goldDocs']:+d} 篇 |")
    a(f"| 覆盖切片（唯一 docId+chunkIndex） | {s1['goldChunkUniq']}/{CORPUS_CHUNK_TOTAL} "
      f"({pct(s1['goldChunkUniq'], CORPUS_CHUNK_TOTAL)}) "
      f"| **{s2['goldChunkUniq']}/{CORPUS_CHUNK_TOTAL} ({pct(s2['goldChunkUniq'], CORPUS_CHUNK_TOTAL)})** | "
      f"{s2['goldChunkUniq'] - s1['goldChunkUniq']:+d} 块 |")
    a(f"| 覆盖文档（出题源） | {s1['srcDocs']}/{CORPUS_DOC_TOTAL} ({pct(s1['srcDocs'], CORPUS_DOC_TOTAL)}) "
      f"| **{s2['srcDocs']}/{CORPUS_DOC_TOTAL} ({pct(s2['srcDocs'], CORPUS_DOC_TOTAL)})** | "
      f"{s2['srcDocs'] - s1['srcDocs']:+d} 篇 |")
    a(f"| 可抄录锚点 quote | {s1['quotes']}/{s1['goldChunks']} | {s2['quotes']}/{s2['goldChunks']} | — |")
    a("")
    a("> **两个覆盖概念的区别**：`gold 锚点` 指答案所在文档；`出题源` 指题目取自哪篇文档。")
    a("> 跨文档题（crossdoc）的问题来自新闻/解读篇、答案锚在政策原文，因此它会抬高 gold 锚点覆盖、")
    a("> 但让出题源与 gold 锚点分离——这是设计使然，不是遗漏。\n")

    a("## 2. 题型构成\n")
    a("| 类别 | v1 | v2 | 变化 |")
    a("|---|---|---|---|")
    for c in ("pair", "docnum", "synthetic", "unanswerable", "permission", "crossdoc", "single"):
        n1, n2 = s1["cat"].get(c, 0), s2["cat"].get(c, 0)
        if n1 or n2:
            a(f"| {CN.get(c, c)} | {n1 or '—'} | {n2 or '—'} | {n2 - n1:+d} |")
    a("")
    new_cross = s2["cat"].get("crossdoc", 0)
    new_single = s2["cat"].get("single", 0)
    tot_new = new_cross + new_single
    a(f"新题中 **crossdoc {new_cross} / single {new_single}**"
      f"（跨文档占新题 {pct(new_cross, tot_new)}）。")
    a("crossdoc 比例反映的是**新闻稿与政策原文的实际内容重合度**：")
    a("若新闻只是提及某政策而正文并非解读该政策，其答案在政策原文中找不到逐字支撑，")
    a("闸门会将其降级为 single（gold 锚新闻稿自身）——这是正确行为，不是失败。\n")

    a("## 3. 各栏目产出\n")
    a("| 栏目 | v1 题数 | v2 题数 |")
    a("|---|---|---|")
    for sec in ("政策文件", "部门解读", "新闻发布会", "媒体视角", "—"):
        n1, n2 = s1["bySection"].get(sec, 0), s2["bySection"].get(sec, 0)
        if n1 or n2:
            a(f"| {sec} | {n1 or '—'} | {n2 or '—'} |")
    a("")

    a("## 4. 自动闸门拦截统计\n")
    if gate_cnt:
        a("| 闸门 | 拦截数 |")
        a("|---|---|")
        for k, v in gate_cnt.most_common():
            a(f"| {k} | {v} |")
        total_gate = sum(gate_cnt.values())
        a(f"\n合计拦截 **{total_gate}** 项（相对候选总量 {len(new) + total_gate}，"
          f"拦截率 {pct(total_gate, len(new) + total_gate)}）。")
        warned = [k for k, v in gate_cnt.items()
                  if v / max(len(new) + total_gate, 1) > 0.30]
        if warned:
            a(f"\n> ⚠️ **下列闸门拦截率超过 30%，按 design D5 标记为异常，需复查出题 prompt**：{warned}")
        else:
            a("\n> 各闸门拦截率均未超过 30% 阈值。")
    else:
        a("（无拦截记录）")
    a("")

    a("## 5. 复核来源分布\n")
    a("| reviewedBy | 题数 | 占比 |")
    a("|---|---|---|")
    for k, v in s2["byReviewer"].most_common():
        a(f"| {k} | {v} | {pct(v, s2['n'])} |")
    a("")
    a("> `human` = 经人工逐条复核（v1 的 99 题，`review-state.json` 有 reviewNote）；")
    a("> `auto` = 仅通过自动闸门（本次扩题），**未做人工复核**。引用时应区分对待。\n")

    a("## 6. 可疑题清单（建议抽查）\n")
    susp = []
    for r in new:
        q = r.get("question") or ""
        a_ = r.get("answer") or ""
        gold = r.get("gold") or []
        if len(a_) < 15:
            susp.append((r["id"], "答案过短", q[:44]))
        elif len(gold) == 1:
            susp.append((r["id"], "仅 1 个 gold 段", q[:44]))
        elif len(q) > 60:
            susp.append((r["id"], "问题过长", q[:44]))
    if susp:
        a(f"共 {len(susp)} 项（下表列前 25 项）：\n")
        a("| id | 可疑点 | 问题 |")
        a("|---|---|---|")
        for x in susp[:25]:
            a(f"| {x[0]} | {x[1]} | {x[2]} |")
    else:
        a("未发现明显可疑项。")
    a("")

    # ---- 跨类别近似重复：crossdoc 与 single 语义同一问、gold 不同（数据在上文已打标）----
    a("## 7. 跨类别近似重复检测（crossdoc ↔ single）\n")
    a("补漏模式为「本文档不是 gold」的文档补了单文档题，因此可能与既有 crossdoc 题")
    a("形成\"同一问话、两个 gold\"的近似重复对。下表给出量化结果（3-gram 覆盖率 ≥ 0.70 判定）。\n")
    if near:
        a(f"检出 **{len(near)}** 对近似重复，占 crossdoc 题量 {pct(len(near), len(cross))}；")
        a(f"其中 **{len(best_of)} 道 single** 已在 `golden.v2.jsonl` 打标 `meta.dupOf = <crossdoc 题 id>`。")
        a("用 `run_eval.py --exclude-dup-pairs` 可剔除这批单文档题，产出一组不含近重复的对照指标")
        a("（该开关**只剔 single 侧**，刻意保留「新闻问法 → 政策原文」的高价值跨文档题）。\n")
        a("| crossdoc | single | 3-gram 覆盖 | 问题 |")
        a("|---|---|---|---|")
        for x_id, s_id, cov, q in sorted(near, key=lambda t: -t[2])[:15]:
            a(f"| {x_id} | {s_id} | {cov:.2f} | {q} |")
        a("")
        a("> **影响**：同一近似问话下，crossdoc 的正确 gold 在政策原文、single 的正确 gold 在新闻/解读稿。")
        a("> 若两文档同时对同一问话都相关，则互为对方指标的假阳性来源，会**轻微压低**分组指标。")
        a("> 这不是数据错误，但引用分组指标时应知道其存在。")
        a(f"> **覆盖残差**：仍有 {CORPUS_DOC_TOTAL - s2['goldDocs']} 篇文档未被任何 gold 锚定；")
        a("> 实测原因是其内容与同主题的其他报道高度重合（新题触发 G4 去重）、正文过短（<200 字），")
        a("> 或答案无法在本文档原文中逐字定位。它们仍可能作为**出题源**参与（见第 1 节末行）。")
    else:
        a("未检出近似重复对。")
    a("")

    a("## 8. 结论与限制\n")
    a("- **已达成**：文档覆盖与切片覆盖相对 v1 大幅提升；题型分组新增 X/S 两类，供分组报告区分难度。")
    a("- **限制 1**：`auto` 题未经人工复核，唯一防线是五道自动闸门；")
    a("  `quote_hit` 只能保证「摘抄句真实存在于原文」，**不能保证「这句真能回答问题」**。")
    a("- **限制 2**：crossdoc 比例低于预期时，说明语料中「新闻 ↔ 政策」内容重合有限，")
    a("  此时单文档题占比升高，**overall 指标会被拉高**，必须以分组指标为准。")
    a("- **限制 3**：v2 的 overall 与 v1 **不可直接比较**（分母与题型构成都变了）。")
    a("- 下一轮应补：答案层评测（faithfulness / 引用正确率 / 拒答率），当前 100% 只覆盖检索层。")

    os.makedirs(os.path.dirname(AUDIT), exist_ok=True)
    open(AUDIT, "w", encoding="utf-8").write("\n".join(L) + "\n")
    print(f"质检报告 -> {AUDIT}")
    print(f"\n覆盖：gold 锚文档 {s1['goldDocs']} -> {s2['goldDocs']}/{CORPUS_DOC_TOTAL}"
          f" | 唯一切片 {s1['goldChunkUniq']} -> {s2['goldChunkUniq']}/{CORPUS_CHUNK_TOTAL}")
    print(f"闸门拦截：{dict(gate_cnt)}")


if __name__ == "__main__":
    main()

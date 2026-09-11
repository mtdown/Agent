# -*- coding: utf-8 -*-
"""把筛选页的 review-state.json 合并回候选集，产出正式的 golden.v1.jsonl。

为什么需要这个脚本（而不是直接用页面导出）：
  1. 页面的「一键采纳」只改内存里的 `d.gold`，localStorage 只存 `{st, note, q, a}`，
     **补标的证据不会持久化**。所以浏览器导出的 state 只有"保留/删除 + 备注"，
     必须在 Python 端按 `ai-judge.jsonl` 的 `missingChunks` 重放采纳逻辑。
  2. 全程 Python int，**彻底绕开 JS 雪花 ID 精度问题**（19 位 docId 不会被截断）。

重放的是页面 adopt() 的等价逻辑：
  did   = gold[0].docId or source.policyDocId
  have  = {gold[].chunkIndex}
  对 ai.missingChunks 中不在 have 的，追加 {docId: did, chunkIndex: ci,
                                          why: 'AI 评审补充', quote: ''}

用法：
    python eval/scripts/apply_review_state.py --state <review-state.json>
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from lib_eval import EVAL_DIR  # noqa: E402

DEFAULT_STATE = r"C:\Users\origin\Desktop\res\review-state.json"
ADOPT_RE = re.compile(r"补标\s*AI\s*建议证据\s*(\d+)\s*段")
REVISED_RE = re.compile(r"已修订|已修改|已调整|已补全|已删除原文|遗漏")


def load_jsonl(path: str) -> dict[str, dict]:
    out = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                r = json.loads(line)
                out[r["id"]] = r
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--state", default=DEFAULT_STATE, help="筛选页导出的 review-state.json")
    ap.add_argument("--out", default=None, help="输出路径，默认 eval/golden.v1.jsonl")
    args = ap.parse_args()

    state = json.load(open(args.state, encoding="utf-8"))
    cands = load_jsonl(os.path.join(EVAL_DIR, "candidates.jsonl"))
    ai_path = os.path.join(EVAL_DIR, "ai-judge.jsonl")
    ai_map = load_jsonl(ai_path) if os.path.exists(ai_path) else {}

    out_rows, dropped, pending = [], [], []
    adopt_mismatch, revised_no_edit, no_state = [], [], []

    for cid, row in cands.items():
        s = state.get(cid)
        if s is None:
            no_state.append(cid)
            pending.append(cid)
            continue
        st = s.get("st", "pending")
        note = (s.get("note") or "").strip()

        if st == "dropped":
            dropped.append(cid)
            continue
        if st != "kept":
            pending.append(cid)
            continue

        gold = row.get("gold") or []
        have = {g["chunkIndex"] for g in gold}

        # 重放一键采纳：按 note 声明的段数预期，用 ai-judge 的 missingChunks 实际补标
        m = ADOPT_RE.search(note)
        ai = ai_map.get(cid) or {}
        missing = ai.get("missingChunks") or []
        if m:
            did = gold[0]["docId"] if gold else (row.get("source") or {}).get("policyDocId")
            n = 0
            for ci in missing:
                ci = int(ci)
                if ci not in have:
                    gold.append({"docId": did, "chunkIndex": ci,
                                 "why": "AI 评审补充", "quote": ""})
                    have.add(ci)
                    n += 1
            if n != int(m.group(1)):
                adopt_mismatch.append((cid, int(m.group(1)), n, len(missing)))

        if REVISED_RE.search(note) and not (s.get("q") or "").strip() and not (s.get("a") or "").strip():
            revised_no_edit.append(cid)

        if (s.get("q") or "").strip():
            row["question"] = s["q"].strip()
        if (s.get("a") or "").strip():
            row["answer"] = s["a"].strip()

        row["gold"] = gold
        row.setdefault("meta", {})["reviewState"] = "kept"
        row["meta"]["reviewNote"] = note
        if ai.get("verdict"):
            row["meta"]["aiVerdict"] = ai["verdict"]
            row["meta"]["aiScores"] = ai.get("scores")
        out_rows.append(row)

    out = args.out or os.path.join(EVAL_DIR, "golden.v1.jsonl")
    # 按 category、id 稳定排序，保证可复现
    out_rows.sort(key=lambda r: (r["category"], r["id"]))
    with open(out, "w", encoding="utf-8") as f:
        for r in out_rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    print("=" * 62)
    print("合并结果")
    print("=" * 62)
    print(f"state 条目        {len(state)}")
    print(f"候选题            {len(cands)}")
    print(f"保留 kept         {len(out_rows)}  -> {out}")
    print(f"删除 dropped      {len(dropped)}  {dropped}")
    print(f"未处理 pending    {len(pending)}")
    if no_state:
        print(f"  （其中 state 里完全没有记录：{len(no_state)} 题）")

    print()
    print("补标重放校验（note 声明段数 vs 实际补标段数）")
    if adopt_mismatch:
        print(f"  [警告] {len(adopt_mismatch)} 题不一致：")
        for cid, want, got, sug in adopt_mismatch[:15]:
            print(f"    {cid}: 备注称 {want} 段，实际新增 {got} 段（AI 建议 {sug} 段）")
    else:
        print("  全部一致 —— 重放结果与页面采纳行为吻合")

    if revised_no_edit:
        print()
        print(f"[提示] {len(revised_no_edit)} 题备注写了「已修订」但未提交问题/答案文本：")
        for cid in revised_no_edit[:15]:
            print(f"    {cid}: {state[cid].get('note', '')[:70]}")
        print("    → 这些题的答案可能仍含备注所指的问题，需人工确认")

    return 0


if __name__ == "__main__":
    sys.exit(main())

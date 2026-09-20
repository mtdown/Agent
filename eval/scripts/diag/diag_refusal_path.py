# -*- coding: utf-8 -*-
"""拒答判据的决策路径取证：直接复用 runner 的函数，看 4 例误伤是怎么被判成拒答的。

结论导向：`is_refusal` 的第一分支「首个陈述句含强短语即判拒答」会在
「套话开头 + 后文照常作答」这一形态上误报——因为该分支不再要求后续无实质陈述。

用法: python tmp/diag_refusal_path.py
"""
from __future__ import annotations

import importlib.util
import json
import pathlib
import sys

RUNNER = pathlib.Path("eval/datasets/mhr-rag/scripts/run_ask_eval_mhr.py")
RESULT = pathlib.Path(
    "eval/datasets/mhr-rag/results/ask-qwen38max-nonthinking/mhr-ask-20260920-212222.json")
TARGETS = ["MHR-0232", "MHR-0258", "MHR-0312", "MHR-2216",
           "MHR-0017", "MHR-1288", "MHR-1840", "MHR-1889"]


def load_runner():
    spec = importlib.util.spec_from_file_location("mhr_runner", RUNNER)
    mod = importlib.util.module_from_spec(spec)
    sys.modules["mhr_runner"] = mod
    spec.loader.exec_module(mod)
    return mod


def main() -> None:
    m = load_runner()
    d = json.load(open(RESULT, encoding="utf-8"))
    pq = {q["id"]: q for q in d["perQuestion"]}
    print(f"{'题号':10s} {'判官':10s} {'首句有强短语':12s} {'全文带角标陈述':12s} "
          f"{'is_refusal':10s} 首句")
    print("-" * 118)
    for qid in TARGETS:
        q = pq.get(qid)
        if not q:
            print(f"{qid} 缺失")
            continue
        a = q.get("answer") or ""
        sig = m.refusal_signal(a)
        sts = m.split_statements(a)
        first_strong = bool(sts and m.refusal_signal(sts[0])["strong"])
        cited = [s for s in sts if m.CITATION_MARKER_RE.search(s)]
        verdict = (q.get("judge") or {}).get("verdict")
        first = (sts[0][:42] + "…") if sts else "(无)"
        print(f"{qid:10s} {str(verdict):10s} {str(first_strong):12s} "
              f"{f'{len(cited)} 条':12s} {str(m.is_refusal(a)):10s} {first}")
    print()
    print("注：判官判 CORRECT 却 is_refusal=True ⇒ 该题属判据误伤。"
          "误伤成立的条件是「首句有强短语」为 True 且「全文带角标陈述」> 0。")


if __name__ == "__main__":
    main()

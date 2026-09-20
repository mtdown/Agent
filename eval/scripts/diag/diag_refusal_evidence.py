# -*- coding: utf-8 -*-
"""误拒判据缺陷取证：打印"判官判 CORRECT 却触发拒答信号"的题面与答案原文。

这是本次实验最需要人眼校准的一点：拒答判据锚定 SYSTEM_PROMPT 的规范措辞
（"未能找到相关依据"），只要答案里出现该措辞就记 refusalDetected=true。
如果模型给了正确答案、只是额外补了一句"依据不足"的合规声明，就会被误记为拒答。

用法: python tmp/diag_refusal_evidence.py
"""
from __future__ import annotations

import json
import textwrap

PATH = ("eval/datasets/mhr-rag/results/ask-qwen38max-nonthinking/"
        "mhr-ask-20260920-212222.json")
TARGETS = ["MHR-0232", "MHR-0258", "MHR-0312", "MHR-2216"]
WIDTH = 100


def main() -> None:
    d = json.load(open(PATH, encoding="utf-8"))
    pq = {q["id"]: q for q in d["perQuestion"]}
    for qid in TARGETS:
        q = pq.get(qid)
        if not q:
            print(f"[缺失] {qid}")
            continue
        print("=" * WIDTH)
        print(f"{qid}  category={q.get('category')}  verdict={(q.get('judge') or {}).get('verdict')}")
        print(f"问题: {q.get('question')}")
        print(f"gold: {q.get('goldAnswer')}")
        print(f"判官理由: {(q.get('judge') or {}).get('reason')}")
        print("-" * WIDTH)
        print("模型答案:")
        for line in textwrap.wrap((q.get("answer") or "").replace("\n", " "), WIDTH):
            print("  " + line)
        print()


if __name__ == "__main__":
    main()

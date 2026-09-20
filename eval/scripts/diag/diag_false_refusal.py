# -*- coding: utf-8 -*-
"""误拒构成剖析：基线臂 vs qwen3.8-max 非思考臂。

目的：解释"误拒率 5.68% -> 25%"是什么构成的，特别是有多少题是"判官认为答案正确但
触发了拒答措辞"——那属于判据缺陷（判据锚定 SYSTEM_PROMPT 规范措辞，会误伤"依据不足"
的合规声明），不能当作生成模型退化。

用法: python tmp/diag_false_refusal.py
"""
from __future__ import annotations

import collections
import json

ARMS = [
    ("baseline-off (deepseek-chat)",
     "eval/datasets/mhr-rag/results/ask-baseline-off/mhr-ask-20260917-155551.json"),
    ("qwen38max-nonthinking",
     "eval/datasets/mhr-rag/results/ask-qwen38max-nonthinking/mhr-ask-20260920-212222.json"),
]


def main() -> None:
    for tag, path in ARMS:
        d = json.load(open(path, encoding="utf-8"))
        pq = d["perQuestion"]
        ans = [q for q in pq if not q.get("expectRefusal")]
        fr = [q for q in ans if q.get("refusalDetected")]
        verdict = collections.Counter((q.get("judge") or {}).get("verdict") for q in fr)
        print("=" * 78)
        print(f"{tag}: 误拒 {len(fr)}/{len(ans)} = {len(fr)/len(ans):.4f}")
        print(f"  构成 {dict(verdict)}")
        corr = [q for q in fr if (q.get("judge") or {}).get("verdict") == "CORRECT"]
        print(f"  判官判 CORRECT 却触发拒答: {len(corr)} 题 -> {[q['id'] for q in corr]}")
        bycat = collections.Counter(q.get("category") for q in fr)
        print(f"  误拒题按题型: {dict(bycat)}")
        print()
        print(f"  {'题号':10s} {'题型':18s} {'判官':10s} 信号")
        for q in fr:
            sig = q.get("refusalSignal")
            if isinstance(sig, dict):
                keys = ",".join(f"{k}={v}" for k, v in sig.items()) or "-"
            else:
                keys = str(sig)
            v = (q.get("judge") or {}).get("verdict")
            print(f"  {q['id']:10s} {str(q.get('category')):18s} {str(v):10s} {keys}")
        print()


if __name__ == "__main__":
    main()

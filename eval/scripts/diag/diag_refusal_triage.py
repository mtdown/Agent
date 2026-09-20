# -*- coding: utf-8 -*-
"""拒答判据校准：把两臂所有 refusalDetected=true 的答案开头打印出来，人眼分类。

分类标准（本次人工判定的三档）：
  R  = 真拒答：只有"未能找到相关依据"之类声明，没有任何答案内容
  H  = 带保留语但作答：先/中插一句套话，但正文给出了实质回答
  P  = 部分作答：给出部分证据后明确表示无法完整回答

用途：判断"误拒率 5.68% -> 25%"究竟是模型行为退化，还是判据把"套话前缀"误判成拒答。

用法: python tmp/diag_refusal_triage.py
"""
from __future__ import annotations

import json
import re
import textwrap

ARMS = [
    ("baseline-off (deepseek-chat)",
     "eval/datasets/mhr-rag/results/ask-baseline-off/mhr-ask-20260917-155551.json"),
    ("qwen38max-nonthinking",
     "eval/datasets/mhr-rag/results/ask-qwen38max-nonthinking/mhr-ask-20260920-212222.json"),
]

HEDGE = re.compile(
    r"未能找到相关依据|无法回答|提供的资料中并未提及|提供的资料中并未包含|"
    r"提供的资料中未提及|提供的资料中未包含|无法依据现有资料回答|无法完全回答"
)


def answered_body_len(answer: str) -> int:
    """扣掉套话句之后的正文长度，用于粗判"到底有没有作答"。"""
    body = answer or ""
    for m in HEDGE.finditer(body):
        body = body[:m.start()] + body[m.end():]
    body = re.sub(r"[\s，。；、,.;:\[\]0-9]+", "", body)
    return len(body)


def main() -> None:
    for tag, path in ARMS:
        d = json.load(open(path, encoding="utf-8"))
        pq = d["perQuestion"]
        ans = [q for q in pq if not q.get("expectRefusal")]
        fr = [q for q in ans if q.get("refusalDetected")]
        print("=" * 96)
        print(f"{tag}: 误拒 {len(fr)}/{len(ans)} = {len(fr)/len(ans):.4f}")
        print("=" * 96)
        for q in fr:
            a = (q.get("answer") or "").strip()
            v = (q.get("judge") or {}).get("verdict")
            blen = answered_body_len(a)
            print(f"\n[{q['id']}] {q.get('category')} verdict={v} 去套话后正文≈{blen}字")
            print(f"  开头: {textwrap.shorten(a, 220, placeholder=' …')}")
        print("\n")


if __name__ == "__main__":
    main()

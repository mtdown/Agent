# -*- coding: utf-8 -*-
"""列出两条赛道的全部 run 产物：题量、配置、指标、文件改写时间。

只读，不写任何仓库文件。用途：`eval/EXPERIMENT-LEDGER.md` 的数字复算入口 ——
台账里每一行都能用这个脚本在磁盘上原地核出来，不依赖记忆或 README。

用法:
    python eval/scripts/list_run_ledger.py            # 全部
    python eval/scripts/list_run_ledger.py --dataset mhr

注意：run 文件只记录 **runner 侧** 配置（baseUrl / goldenFile / fetchK / ks / spaceId，
答案层另有 judgeModel / answerSet）。**后端检索配置（切分 / embedding / 池深 / 重排 / 开关）
不在产物里**，必须回到 change 工件 + `application.yml` + git 提交核对。
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import time

KEYS = ["recall@1", "recall@3", "recall@5", "recall@6", "recall@10",
        "docRecall@6", "hitRate@6", "mrr"]

PATTERNS = {
    "zh": [("中文主赛道", "eval/results/*.json")],
    "mhr": [
        ("英文泛化赛道", "eval/datasets/mhr-rag/results/*.json"),
        ("英文·答案层", "eval/datasets/mhr-rag/results/*/*.json"),
    ],
}


def report(title: str, pat: str, skipped_basenames: tuple[str, ...] = ()) -> None:
    files = sorted(glob.glob(pat))
    print("=" * 110)
    print(f"### {title}  ({pat})  命中 {len(files)} 个文件")
    print("=" * 110)
    for p in files:
        name = os.path.basename(p)
        if name.startswith(".") or name in skipped_basenames:
            continue
        mt = time.strftime("%Y-%m-%d %H:%M", time.localtime(os.path.getmtime(p)))
        size = os.path.getsize(p)
        try:
            d = json.load(open(p, encoding="utf-8"))
        except Exception as exc:  # noqa: BLE001
            print(f"\n{name}  改写于 {mt}  [{size / 1024:.0f}KB]  解析失败: {exc}")
            continue
        cfg = d.get("config") or {}
        ov = d.get("overall") or {}
        cnt = d.get("counts") or {}
        summ = d.get("summary") or {}
        print(f"\n{name}")
        print(f"  文件改写于 {mt}  [{size / 1024:.0f}KB]")
        print(f"  runId={d.get('runId')}  golden={cfg.get('goldenFile')}  "
              f"retriever={cfg.get('retriever')}  fetchK={cfg.get('fetchK')}  "
              f"ks={cfg.get('ks')}  spaceId={cfg.get('spaceId')}")
        if cfg.get("startedAt"):
            print(f"  时段 {cfg.get('startedAt')} → {cfg.get('finishedAt')}")
        if cnt:
            print(f"  counts={json.dumps(cnt, ensure_ascii=False)}")
        if ov:
            line = "  ".join(f"{k}={ov[k]:.4f}" for k in KEYS if k in ov)
            print(f"  overall n={ov.get('count')}: {line}")
        if d.get("arm") is not None or d.get("layer") is not None:
            print(f"  layer={d.get('layer')}  arm={d.get('arm')}  dataset={d.get('dataset')}")
        if summ:
            a = summ.get("answerable") or {}
            rf = summ.get("refusable") or {}
            print(f"  【答案层】graded={summ.get('gradedCount')} err={summ.get('errorCount')} "
                  f"acc={summ.get('accuracy')} 上沿={summ.get('accuracyUpper')} "
                  f"partial={summ.get('partialCount')}")
            if a:
                print(f"    有答案题 n={a.get('count')} acc={a.get('accuracy')} "
                      f"误拒={a.get('falseRefusalRate')} 引用覆盖={a.get('citationCoverageMacro')}")
            if rf:
                print(f"    应拒答题 n={rf.get('count')} 拒答正确率={rf.get('refusalAccuracy')}")
            if summ.get("latencyMs"):
                print(f"    耗时 {json.dumps(summ['latencyMs'], ensure_ascii=False)}")


def main() -> None:
    ap = argparse.ArgumentParser(description="列出全部 RAG run 产物的配置与指标")
    ap.add_argument("--dataset", choices=["zh", "mhr", "all"], default="all")
    args = ap.parse_args()
    groups = PATTERNS["zh"] + PATTERNS["mhr"] if args.dataset == "all" else PATTERNS[args.dataset]
    for title, pat in groups:
        report(title, pat)


if __name__ == "__main__":
    main()

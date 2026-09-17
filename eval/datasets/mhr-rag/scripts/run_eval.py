# -*- coding: utf-8 -*-
"""mhr-rag 评测 runner：用 MultiHop-RAG 的 2556 题驱动本系统检索。

**与自有 216 篇评测完全隔离**：本 runner 只读 `eval/datasets/mhr-rag/anchor/golden.mhr.jsonl`，
不接受自有数据集的参数，也不会写进 `eval/results/`（结果落在本数据集目录下）。

共享的只有取数与打分内核 `eval/scripts/lib_rag_eval.py` —— 保证两个数据集
的 recall / docRecall / hitRate / mrr **定义完全一致**，可横向比较。

必须知道的口径差异（不声明就等于误读指标）：
1. **本数据集测的是纯向量检索链路**。后端的「文号精确命中层」靠
   `MarkdownChunker.DOC_NUMBER_PATTERN`（中文公文号），英文查询恒不命中，
   该层在这里是死代码。自有 216 篇测的是「向量 + 文号命中」混合链路。
2. **每题是跨文档多跳**：gold 覆盖 2–4 篇文档，recall@K 要召回全部证据才满分。
3. **gold 走 set 去重**：同一题多条 quote 落在同一 chunk 时只算一个坐标，
   分母相应缩小（与自有数据集同口径，换取可比性）。

## 为什么必须有 checkpoint / resume

一次全量 = 2556 题 ×（1 次 embedding + 1 次 50 篇候选的 rerank），**约 30 分钟、
数千次付费调用**。历史上出现过跑到 800 题时 DashScope 返回 `Arrearage`（账号欠费），
后端连续抛错，runner 按设计保护性中止 —— 已跑的 800 题因为没落盘**全部作废**。

所以：默认每 `--checkpoint-every`（100）题把进度写到 `tmp/`，中断后用
`--resume <checkpoint>` 接着跑，只补没跑的题。改一次 runner 省一轮全量额度。

用法：
    python eval/datasets/mhr-rag/scripts/run_eval.py --space-id <导入空间ID>
    python eval/datasets/mhr-rag/scripts/run_eval.py --space-id 123 --limit 100 --probe
    python eval/datasets/mhr-rag/scripts/run_eval.py --space-id 123 --resume tmp/mhr-ckpt-<runId>.json
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime

import mhr_lib as L  # noqa: E402
from lib_eval import load_env  # noqa: E402
from lib_rag_eval import (  # noqa: E402
    FETCH_K,
    KS,
    METRIC_KEYS,
    HttpRetriever,
    RetrieverError,
    aggregate,
    compute_metrics,
    gold_pairs,
    load_golden,
    probe,
)

MAX_CONSECUTIVE_ERRORS = 5
# 英文探针：不能用中文查询探测，否则测的是另一条（会走文号层判定）的输入分布。
PROBE_QUERY = "Which companies reported earnings growth in the same quarter?"
CHECKPOINT_DIR = os.path.join(L.REPO_ROOT, "tmp")


def checkpoint_path(run_id: str) -> str:
    return os.path.join(CHECKPOINT_DIR, f"mhr-ckpt-{run_id}.json")


def write_checkpoint(run_id: str, args, records: list, errors: list, total: int) -> str:
    """把已跑进度落盘。中断后靠它续跑，避免整轮付费调用作废。"""
    os.makedirs(CHECKPOINT_DIR, exist_ok=True)
    path = checkpoint_path(run_id)
    payload = {
        "runId": run_id,
        "dataset": L.DATASET,
        "config": {
            "goldenFile": os.path.relpath(args.golden, L.REPO_ROOT).replace("\\", "/"),
            "spaceId": args.space_id,
            "fetchK": FETCH_K,
            "ks": KS,
            "profile": L.EN_PROFILE,
            "corpusSha256": L.CORPUS_SHA256,
            "qaSha256": L.QA_SHA256,
        },
        "progress": {"done": len(records), "total": total, "errors": len(errors)},
        "records": records,
        "errors": errors,
    }
    tmp_path = path + ".part"
    json.dump(payload, open(tmp_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    os.replace(tmp_path, path)  # 原子替换：写一半被中断不会留下半截文件
    return path


def main():
    ap = argparse.ArgumentParser(description="mhr-rag 评测 runner")
    ap.add_argument("--golden", default=L.GOLDEN_PATH)
    ap.add_argument("--space-id", type=int, required=False, default=None,
                    help="mhr-rag 语料所在的 Wiki 空间 ID（建议用独立空间，避免污染空间级对照）")
    ap.add_argument("--base-url", default=None, help="后端地址，默认取 .env 的 BACKEND_BASE_URL")
    ap.add_argument("--member-key", default=None)
    ap.add_argument("--limit", type=int, default=0, help="只跑前 N 题（调试用；与 --resume 同用时按剩余题算）")
    ap.add_argument("--probe", action="store_true", help="仅探测后端与 key，不跑评测")
    ap.add_argument("--out-dir", default=os.path.join(L.DATASET_DIR, "results"))
    ap.add_argument("--checkpoint-every", type=int, default=100,
                    help="每 N 题写一次进度到 tmp/（0 = 关闭，不推荐）")
    ap.add_argument("--resume", default=None, help="从 checkpoint 续跑：只补没跑过的题")
    args = ap.parse_args()

    cfg = load_env()
    member_key = args.member_key or cfg.get("EVAL_MEMBER_API_KEY", "")
    base_url = args.base_url or cfg.get("BACKEND_BASE_URL", "http://localhost:8123/api")
    space_ids = [args.space_id] if args.space_id else None
    retriever = HttpRetriever(base_url, member_key, space_ids)

    if args.probe:
        sys.exit(0 if probe(retriever, member_key, PROBE_QUERY) else 1)
    if not member_key:
        sys.exit("[FATAL] 缺少成员 API Key：先跑 eval/scripts/prepare_keys.py，或传 --member-key")
    if not probe(retriever, member_key, PROBE_QUERY):
        sys.exit(1)
    if not os.path.exists(args.golden):
        sys.exit(f"[FATAL] 缺少 golden：{args.golden}\n        先跑 bind_anchor.py 完成 gold 绑定。")

    rows = load_golden(args.golden)
    if args.limit:
        rows = rows[: args.limit]

    # --- 续跑：载入 checkpoint，已完成的题直接跳过 -------------------------
    records, errors, run_id = [], [], None
    if args.resume:
        if not os.path.exists(args.resume):
            sys.exit(f"[FATAL] checkpoint 不存在：{args.resume}")
        ck = json.load(open(args.resume, encoding="utf-8"))
        # 关键可比性体检：golden / 语料 / 空间变了，续出来的结果不能和前半段混在一起
        for k, v in (("corpusSha256", L.CORPUS_SHA256), ("qaSha256", L.QA_SHA256)):
            if ck["config"].get(k) != v:
                sys.exit(f"[FATAL] checkpoint 的 {k} 与当前不一致，拒绝续跑："
                         f"{ck['config'].get(k)} vs {v}")
        if args.space_id and ck["config"].get("spaceId") not in (None, args.space_id):
            sys.exit(f"[FATAL] checkpoint 的 spaceId={ck['config'].get('spaceId')} "
                     f"与本次 --space-id={args.space_id} 不一致，拒绝续跑")
        records = ck["records"]
        errors = ck.get("errors", [])
        run_id = ck["runId"]
        args.space_id = ck["config"].get("spaceId", args.space_id)
        retriever = HttpRetriever(base_url, member_key, [args.space_id] if args.space_id else None)
        print(f"[resume] 载入 {len(records)}/{len(rows)} 题（runId={run_id}），"
              f"本次只跑剩余 {len(rows) - len(records)} 题")
    else:
        run_id = datetime.now().strftime("mhr-%Y%m%d-%H%M%S")

    done = {r.get("id") for r in records}
    print(f"[mhr-rag] 载入 {len(rows)} 题，一次取 top{FETCH_K} 本地截断算 K∈{KS}")

    consecutive = 0
    for i, row in enumerate(rows, 1):
        if row.get("id") in done:
            continue
        gold = gold_pairs(row)
        if not gold:
            records.append({"id": row.get("id"), "category": row.get("category"),
                            "refusal": True, "metrics": None, "hits": []})
            continue
        try:
            hits = retriever.search(row["question"], FETCH_K)
            consecutive = 0
        except RetrieverError as exc:
            errors.append({"id": row.get("id"), "error": str(exc)})
            consecutive += 1
            # 中止前**必须先落盘**：已经付费跑出来的题不能跟着一起丢
            if exc.fatal or consecutive >= MAX_CONSECUTIVE_ERRORS:
                if args.checkpoint_every:
                    p = write_checkpoint(run_id, args, records, errors, len(rows))
                    print(f"\n[checkpoint] 中止前已保存 {len(records)} 题 -> {p}")
                sys.exit(f"[FATAL] 连续 {consecutive} 次失败，中止"
                         f"（绝不把故障跑出的全 0 分当指标）：{exc}")
            continue
        records.append({
            "id": row.get("id"),
            "category": row.get("category"),
            "refusal": False,
            "question": row.get("question"),
            "goldCount": len(gold),
            "goldDocs": len({d for d, _ in gold}),
            "hits": [h.to_dict() for h in hits],
            "metrics": compute_metrics(gold, hits),
        })
        if args.checkpoint_every and len(records) % args.checkpoint_every == 0:
            write_checkpoint(run_id, args, records, errors, len(rows))

    scored = [r for r in records if not r["refusal"] and r["metrics"]]
    refused = [r for r in records if r["refusal"]]
    by_cat = {}
    for c in sorted({r["category"] for r in scored}):
        by_cat[c] = aggregate([r for r in scored if r["category"] == c], METRIC_KEYS)

    overall = aggregate(scored, METRIC_KEYS)
    multi = [r for r in scored if r["goldDocs"] > 1]
    overall_multi = aggregate(multi, METRIC_KEYS) if multi else None

    os.makedirs(args.out_dir, exist_ok=True)
    result = {
        "runId": run_id,
        "dataset": L.DATASET,
        "config": {
            "goldenFile": os.path.relpath(args.golden, L.REPO_ROOT).replace("\\", "/"),
            "baseUrl": base_url,
            "spaceId": args.space_id,
            "fetchK": FETCH_K,
            "ks": KS,
            "profile": L.EN_PROFILE,
            "corpusSha256": L.CORPUS_SHA256,
            "qaSha256": L.QA_SHA256,
        },
        "counts": {"total": len(records), "scored": len(scored), "refusal": len(refused),
                   "errors": len(errors)},
        "overall": overall,
        "overallMultiDoc": overall_multi,
        "byQuestionType": by_cat,
        "refusalByType": {c: sum(1 for r in refused if r["category"] == c)
                          for c in sorted({r["category"] for r in refused})},
        "errors": errors,
        "perQuestion": records,
    }
    out_path = os.path.join(args.out_dir, f"{run_id}.json")
    json.dump(result, open(out_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    print()
    print("=" * 72)
    print(f"[mhr-rag] 可答 {len(scored)} 题 / 拒答 {len(refused)} 题 / 错误 {len(errors)}")
    print(f"  overall      recall@6={overall.get('recall@6')}  docRecall@6={overall.get('docRecall@6')}"
          f"  mrr={overall.get('mrr')}")
    for c, agg in by_cat.items():
        print(f"  {c:18s} n={agg['count']:4d}  recall@6={agg.get('recall@6')}"
              f"  docRecall@6={agg.get('docRecall@6')}")
    print(f"结果 -> {os.path.relpath(out_path, L.REPO_ROOT)}")
    print("提醒：本数据集为纯向量链路（文号层对英文恒不生效），"
          "与自有 216 篇的「向量+文号」混合链路不可直接横比绝对值。")


if __name__ == "__main__":
    main()

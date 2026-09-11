# -*- coding: utf-8 -*-
"""任务 5.3：生成 eval/manifest.json —— 数据集可复现快照。

记录四件事，使他人拿到 manifest 就能判断「这份数据集是在什么语料、什么库状态、
什么模型下生成的」：

  1. corpus：语料目录 216 篇 content.md 的逐篇 sha256 与汇总 hash
  2. chunkSnapshot：wiki_chunk 的行数、文档数、状态分布（校验只读约束的基线）
  3. models：出题/评审用的 LLM 与 embedding 模型名（**不记录任何密钥**）
  4. mix：实际分类配比与对目标配比的偏差说明

只读约定：只查 wiki_chunk、只读语料，不写业务库。

用法：
    python eval/scripts/gen_manifest.py                       # 基于 candidates.jsonl
    python eval/scripts/gen_manifest.py eval/golden.v1.jsonl  # 基于正式数据集
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from datetime import datetime

sys.stdout.reconfigure(encoding="utf-8")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from lib_eval import CORPUS, EVAL_DIR, SECTIONS, db_conn, load_env  # noqa: E402

TARGET_MIX = {"pair": 54, "docnum": 9, "unanswerable": 15, "permission": 10, "synthetic": 12}
CAT_CN = {
    "pair": "A 配对题", "docnum": "B 文号题", "unanswerable": "C 无答案题",
    "permission": "D 权限题", "synthetic": "E 合成题",
}


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def scan_corpus() -> dict:
    """扫描语料目录，逐篇 content.md 记 hash，并算汇总 hash。"""
    files = []
    by_section: dict[str, int] = {}
    for sec, cn in SECTIONS:
        base = os.path.join(CORPUS, sec)
        if not os.path.isdir(base):
            continue
        n = 0
        for folder in sorted(os.listdir(base)):
            p = os.path.join(base, folder, "content.md")
            if os.path.isfile(p):
                files.append((f"{sec}/{folder}/content.md", sha256_file(p)))
                n += 1
        by_section[cn] = n
    files.sort()
    agg = hashlib.sha256("\n".join(f"{k}:{v}" for k, v in files).encode("utf-8")).hexdigest()
    return {
        "root": CORPUS,
        "fileCount": len(files),
        "bySection": by_section,
        "aggregateSha256": agg,
        "files": files,
    }


def scan_chunks(cfg: dict) -> dict:
    conn = db_conn(cfg)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM wiki_chunk")
            total = cur.fetchone()[0]
            cur.execute("SELECT COUNT(DISTINCT docId) FROM wiki_chunk")
            docs = cur.fetchone()[0]
            cur.execute("SELECT status, COUNT(*) FROM wiki_chunk GROUP BY status ORDER BY status")
            status = {str(s): int(c) for s, c in cur.fetchall()}
            cur.execute("SELECT spaceId, COUNT(*) FROM wiki_chunk GROUP BY spaceId ORDER BY spaceId")
            spaces = {str(s): int(c) for s, c in cur.fetchall()}
            cur.execute("SELECT MIN(id), MAX(id) FROM wiki_chunk")
            mn, mx = cur.fetchone()
        return {
            "rows": int(total),
            "docCount": int(docs),
            "statusDist": status,
            "spaceDist": spaces,
            "chunkIdRange": [int(mn), int(mx)] if mn is not None else None,
        }
    finally:
        conn.close()


def calc_mix(path: str) -> dict:
    counts: dict[str, int] = {}
    total = 0
    review: dict[str, int] = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            r = json.loads(line)
            c = r.get("category", "?")
            counts[c] = counts.get(c, 0) + 1
            total += 1
            st = (r.get("meta") or {}).get("reviewState", "?")
            review[st] = review.get(st, 0) + 1
    deviations = []
    for c, tgt in TARGET_MIX.items():
        act = counts.get(c, 0)
        if act != tgt:
            deviations.append(f"{CAT_CN[c]} 实际 {act} / 目标 {tgt}（差 {act - tgt:+d}）")
    return {
        "source": os.path.basename(path),
        "total": total,
        "byCategory": {CAT_CN.get(c, c): n for c, n in sorted(counts.items())},
        "reviewStateDist": review,
        "deviations": deviations or ["与目标配比完全一致"],
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("path", nargs="?", default=None, help="统计来源，默认 candidates.jsonl")
    args = ap.parse_args()

    path = args.path or os.path.join(EVAL_DIR, "candidates.jsonl")
    if not os.path.exists(path):
        raise SystemExit(f"找不到数据集文件：{path}")

    cfg = load_env()
    print("扫描语料目录 …")
    try:
        corpus = scan_corpus()
        print(f"  语料 {corpus['fileCount']} 篇，汇总 hash {corpus['aggregateSha256'][:16]}…")
    except Exception as e:  # noqa: BLE001
        corpus = {"root": CORPUS, "error": f"{type(e).__name__}: {e}"}
        print(f"  [警告] 语料不可访问：{e}")

    print("扫描 wiki_chunk …")
    chunks = scan_chunks(cfg)
    print(f"  {chunks['rows']} 行 / {chunks['docCount']} 篇 / 状态 {chunks['statusDist']}")

    mix = calc_mix(path)
    print(f"统计 {path}：{mix['total']} 题")

    manifest = {
        "schemaVersion": 1,
        "generatedAt": datetime.now().astimezone().isoformat(timespec="seconds"),
        "changeId": "add-rag-eval-dataset",
        "branch": "feature/rag-eval开发",
        "dataset": mix,
        "models": {
            # 只记模型名，绝不记录密钥
            "llm": cfg.get("LLM_MODEL", ""),
            "llmBaseUrl": cfg.get("LLM_BASE_URL", ""),
            "embedding": cfg.get("EMBEDDING_MODEL", ""),
            "temperature": 0,
            "enableThinking": False,
        },
        "corpus": corpus,
        "chunkSnapshot": chunks,
        "readOnly": {
            "constraint": "构建过程不写业务库、不触发索引重建",
            "expectedRows": 2061,
            "expectedStatus": "全 ACTIVE",
            "actualRows": chunks["rows"],
            "constraintHeld": chunks["rows"] == 2061
            and list(chunks["statusDist"].keys()) == ["ACTIVE"],
        },
    }

    out = os.path.join(EVAL_DIR, "manifest.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    print(f"\n已写入 {out}")
    print(f"只读约束是否保持：{manifest['readOnly']['constraintHeld']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

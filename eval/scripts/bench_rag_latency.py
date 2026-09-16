"""Latency benchmark for the RAG retrieval endpoint.

Why this exists: recall alone is not a shippable result. The candidate-pool
depth and the cross-encoder re-rank are both latency knobs — a deeper pool buys
recall and pays for it in wall time, and the re-ranker is a serial external call
on the critical path. Any recall number has to be reported together with the
latency it costs.

What it measures, per question:
  - wall ms around POST /open/rag/search (what the caller actually waits for)
  - the backend's own per-phase timings (embed / vector / lexical / fusion /
    rerank), read from data.timings of the same response

Usage:
    python eval/scripts/bench_rag_latency.py --limit 200
    python eval/scripts/bench_rag_latency.py --concurrency 4 --limit 100
"""

import argparse
import json
import os
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib_eval import load_env  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
POLICY_SPACE_ID = 2095544464810774531
PHASES = ("permissionMs", "docCountMs", "docNumberMs", "embedMs", "vectorMs",
          "lexicalMs", "fusionMs", "rerankMs", "totalMs")


def percentile(values, q):
    """Nearest-rank percentile — stable and easy to reason about on small samples."""
    if not values:
        return None
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int(round(q * (len(ordered) - 1)))))
    return ordered[index]


def summary(values):
    if not values:
        return {}
    return {
        "n": len(values),
        "mean": round(statistics.fmean(values), 1),
        "p50": round(percentile(values, 0.50), 1),
        "p90": round(percentile(values, 0.90), 1),
        "p95": round(percentile(values, 0.95), 1),
        "p99": round(percentile(values, 0.99), 1),
        "max": round(max(values), 1),
    }


def as_int(value):
    """The backend serialises Long timings as JSON strings, and unexecuted phases as null."""
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def ask(base_url, api_key, query, top_k, timeout):
    payload = json.dumps({"query": query, "topK": top_k, "spaceIds": [POLICY_SPACE_ID]}).encode("utf-8")
    request = urllib.request.Request(base_url.rstrip("/") + "/open/rag/search", data=payload, method="POST")
    request.add_header("Content-Type", "application/json")
    request.add_header("X-API-Key", api_key)
    started = time.perf_counter()
    with urllib.request.urlopen(request, timeout=timeout) as response:
        body = json.loads(response.read().decode("utf-8"))
    wall = (time.perf_counter() - started) * 1000.0
    if body.get("code") != 0:
        raise RuntimeError(f"业务错误 code={body.get('code')} {body.get('message')}")
    timings = (body.get("data") or {}).get("timings") or {}
    return wall, {phase: as_int(timings.get(phase)) for phase in PHASES}


def main():
    ap = argparse.ArgumentParser(description="RAG 检索延迟基准")
    ap.add_argument("--golden", default=os.path.join(ROOT, "golden.v2.jsonl"))
    ap.add_argument("--base-url", default=None)
    ap.add_argument("--api-key", default=None)
    ap.add_argument("--top-k", type=int, default=6)
    ap.add_argument("--limit", type=int, default=200, help="取样题数（0 = 全部）")
    ap.add_argument("--concurrency", type=int, default=1)
    ap.add_argument("--timeout", type=int, default=60)
    ap.add_argument("--warmup", type=int, default=3, help="预热题数，不计入统计")
    ap.add_argument("--label", default="", help="写进输出的配置标签，便于对比不同池深")
    args = ap.parse_args()

    cfg = load_env()
    base_url = args.base_url or cfg.get("BACKEND_BASE_URL", "http://localhost:8123/api")
    api_key = args.api_key or cfg.get("EVAL_MEMBER_API_KEY", "")
    if not api_key:
        sys.exit("[FATAL] 缺少成员 API Key：先跑 eval/scripts/prepare_keys.py 或传 --api-key")

    questions = []
    with open(args.golden, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                questions.append(json.loads(line)["question"])
    if args.limit:
        questions = questions[: args.limit]

    for query in questions[: args.warmup]:
        ask(base_url, api_key, query, args.top_k, args.timeout)

    walls = []
    phases = {phase: [] for phase in PHASES}
    errors = []
    lock = threading.Lock()
    started = time.perf_counter()

    def run(query):
        try:
            wall, timing = ask(base_url, api_key, query, args.top_k, args.timeout)
        except Exception as exc:  # noqa: BLE001 — latency bench must not die on one bad call
            with lock:
                errors.append(f"{type(exc).__name__}: {exc}")
            return
        with lock:
            walls.append(wall)
            for phase, value in timing.items():
                if value is not None:
                    phases[phase].append(value)

    if args.concurrency > 1:
        with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
            list(pool.map(run, questions))
    else:
        for query in questions:
            run(query)
    wall_clock = time.perf_counter() - started

    report = {
        "label": args.label,
        "baseUrl": base_url,
        "topK": args.top_k,
        "concurrency": args.concurrency,
        "questions": len(questions),
        "errors": len(errors),
        "wallMs": summary(walls),
        "serverPhaseMs": {phase: summary(phases[phase]) for phase in PHASES if phases[phase]},
        "throughputQps": round(len(walls) / wall_clock, 2) if wall_clock > 0 else None,
        "wallClockSeconds": round(wall_clock, 1),
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if errors:
        print("\n[WARN] 前 3 条错误：")
        for message in errors[:3]:
            print("  -", message)


if __name__ == "__main__":
    main()

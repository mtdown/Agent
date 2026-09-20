# -*- coding: utf-8 -*-
"""MHR（英文多跳）**答案层**评测 runner：消费 `POST /open/rag/ask` 的 SSE。

与检索层 `run_eval.py` 的分工（刻意不合并为总分）：
- 检索层回答「材料找没找到」：recall / docRecall / hitRate / mrr。
- 本脚本回答「答得对不对、该拒的拒没拒」：正确率 / 拒答正确率 / 误拒率 / 引用角标覆盖率。

为什么判据必须是 LLM 判官（不是字符串匹配）
----------------------------------------------
2026-09-17 实测：本数据集上**字面/包含判据不可用**，前 100 题里 47/100 与人工复核分歧，
两个方向都错：
  ① gold 是英文，而后端 SYSTEM_PROMPT 强制「回答使用简体中文」⇒ `Everton Football Club`
     被答成「埃弗顿（Everton）」，包含判据误判为错；
  ② 拒答词表不全（漏了「未能找到」）⇒ 真拒答被判成答对。
教训：**不要在需要跨语言/跨措辞等价的场合用字面判据。**

因此本脚本的**主口径 = LLM 判官**（默认 `eval/.env` 的 LLM_MODEL，temperature 0），
并对判官输出做三分类以量化"严格下沿"：
  CORRECT   —— 结论与 gold 一致；
  PARTIAL   —— 列出了相符证据但没给出明确结论（历史判官把这类一律判错，导致 0.55 偏低）；
  INCORRECT —— 结论错、或该答却拒答。
报告主口径取 **CORRECT 率**（与历史 0.55 同口径可比），同时给出 CORRECT+PARTIAL 上沿。

零 LLM 成本的辅助信号（可复算、不花钱）：
- `refusalDetected`：两级拒答判定，锚定 SYSTEM_PROMPT 规定的规范表述
  （「根据现有资料未能找到相关依据」），**不穷举自然语言变体**；
- 拒答正确率 / 误拒率（对 `expectRefusal` 字段）；
- 引用角标覆盖率（实质陈述中带 `[n]` 的比例）。

用法：
    python run_ask_eval_mhr.py --probe
    python run_ask_eval_mhr.py --space-id 2095544464810774534 --arm evidence-assembly-only \
        --out-dir ../results/ask-evidence-assembly-only
    python run_ask_eval_mhr.py --recompute <result.json>
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.dirname(os.path.abspath(__file__))))), "scripts"))

import mhr_lib as L  # noqa: E402
from lib_eval import chat, extract_json, load_env  # noqa: E402
from lib_rag_eval import quantile  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")

ANSWER_SET = os.path.join(L.ANCHOR_DIR, "mhr-answer-100-fixed.jsonl")
MAX_CONSECUTIVE_ERRORS = 5
CHECKPOINT_EVERY = 20

# --------------------------------------------------------------------------
# 拒答判据（两级；锚定后端 SYSTEM_PROMPT 的规范表述，不穷举变体）
# 后端 prompt 与题目语言无关，所以这套中文锚点对英文题同样适用 —— 这正是
# 「不能照抄上游 Insufficient information.」的原因。
# --------------------------------------------------------------------------
REFUSAL_STRONG_PATTERNS = [
    r"(本次|当前|以上|上述|所)?(提供|给出|检索到|召回)的?(资料|材料|内容|文档)(中|里)?"
    r"(均|都|并|亦|也)?(未|不)(包含|涵盖|涉及|覆盖|记载|提及)",
    r"未能找到.{0,30}(相关.{0,2})?(资料|依据|信息|规定|要求|标准)",
    r"未(能|曾)(检索到|查询到|查阅到).{0,12}(相关)?.{0,2}(资料|依据|信息)",
    r"(暂|尚)无.{0,10}(相关|对应|明确)?.{0,2}(资料|依据|信息|规定|政策)",
    r"没有(找到|检索到|查询到|查阅到)?.{0,10}(相关|相应|明确)?.{0,2}(资料|依据|信息|政策|规定)",
    r"无法(根据|依据)?(现有)?.{0,2}(资料|依据)?(回答|提供)",
]
REFUSAL_WEAK_PATTERNS = [
    r"无法(根据|依据)?(现有)?.{0,2}(资料|依据)?(确定|给出)",
]
REFUSAL_STRONG = [re.compile(p) for p in REFUSAL_STRONG_PATTERNS]
REFUSAL_WEAK = [re.compile(p) for p in REFUSAL_WEAK_PATTERNS]
CITATION_MARKER_RE = re.compile(r"\[\d+\]")


class AskError(Exception):
    def __init__(self, message, fatal=False):
        super().__init__(message)
        self.fatal = fatal


def build_opener(url):
    """localhost 必须绕开系统代理（否则 502 假故障）。"""
    host = url.split("//", 1)[-1].split("/", 1)[0].split(":")[0]
    if host in ("localhost", "127.0.0.1", "::1"):
        return urllib.request.build_opener(urllib.request.ProxyHandler({}))
    return urllib.request.build_opener()


def ask_stream(base_url, api_key, query, space_ids=None, timeout=240):
    """一次 SSE 问答，返回 {answer, citations, finishReason, usage}；失败抛 AskError。"""
    payload = {"query": query}
    if space_ids:
        payload["spaceIds"] = list(space_ids)
    url = base_url.rstrip("/") + "/open/rag/ask"
    req = urllib.request.Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", "X-API-Key": api_key},
        method="POST",
    )
    answer_parts, citations, finish_reason, usage = [], [], None, None
    try:
        with build_opener(url).open(req, timeout=timeout) as resp:
            if resp.status != 200:
                raise AskError(f"HTTP {resp.status}")
            buf, got_done, got_error = "", False, None
            while True:
                chunk = resp.read(8192)
                if not chunk:
                    break
                buf += chunk.decode("utf-8", "replace")
                while "\n\n" in buf:
                    frame, buf = buf.split("\n\n", 1)
                    event, data_raw = "message", None
                    for line in frame.split("\n"):
                        if line.startswith("event:"):
                            event = line[6:].strip()
                        elif line.startswith("data:"):
                            data_raw = line[5:].strip()
                    if data_raw is None:
                        continue
                    try:
                        data = json.loads(data_raw)
                    except json.JSONDecodeError:
                        data = data_raw
                    if event == "meta":
                        citations = (data or {}).get("citations") or []
                    elif event == "delta":
                        answer_parts.append(str((data or {}).get("content") or ""))
                    elif event == "done":
                        got_done = True
                        finish_reason = (data or {}).get("finishReason")
                        usage = (data or {}).get("usage")
                    elif event == "error":
                        got_error = str((data or {}).get("message") or "问答服务异常")
            if got_error is not None:
                raise AskError(f"error 事件：{got_error}")
            if not got_done:
                raise AskError("SSE 流结束但未收到 done 事件（回答可能不完整）")
    except urllib.error.HTTPError as exc:
        raise AskError(f"HTTP {exc.code}: {exc.read().decode('utf-8', 'ignore')[:200]}")
    except urllib.error.URLError as exc:
        raise AskError(f"连接失败 {exc.reason}（后端是否已启动？）", fatal=True)
    except TimeoutError:
        raise AskError(f"SSE 读超时（>{timeout}s 无数据）", fatal=True)
    except ConnectionError as exc:
        raise AskError(f"连接中断：{exc}", fatal=True)
    return {"answer": "".join(answer_parts), "citations": citations,
            "finishReason": finish_reason, "usage": usage}


def probe(base_url, api_key, space_ids):
    """用检索接口探测后端与 key（不消耗 LLM 调用）。"""
    url = base_url.rstrip("/") + "/open/rag/search"
    payload = {"query": "Who wrote the report?", "topK": 3}
    if space_ids:
        payload["spaceIds"] = list(space_ids)
    req = urllib.request.Request(
        url, data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "X-API-Key": api_key}, method="POST")
    try:
        with build_opener(url).open(req, timeout=30) as resp:
            body = json.loads(resp.read().decode("utf-8"))
        if body.get("code") == 40101:
            print(f"[probe] FAILED：API Key 无效（{body.get('message')}）")
            return False
        if body.get("code") != 0:
            print(f"[probe] FAILED：业务错误 {body.get('message')}")
            return False
        print("[probe] OK：后端与成员 key 可用")
        return True
    except Exception as exc:  # noqa: BLE001
        print(f"[probe] FAILED：{exc}")
        return False


# --------------------------------------------------------------------------
# 零 LLM 成本判据
# --------------------------------------------------------------------------
def split_statements(text):
    """切成陈述句（保留角标归属），过滤 <8 字的引导词。"""
    parts = re.split(r"(?<=[。！？；.!?;])", text.strip())
    out = []
    for part in parts:
        stripped = part.strip()
        if len(stripped) >= 8 or (stripped and CITATION_MARKER_RE.search(stripped)):
            out.append(stripped)
    return out


def refusal_signal(text):
    strong = [m.group(0) for c in REFUSAL_STRONG if (m := c.search(text))]
    weak = [m.group(0) for c in REFUSAL_WEAK if (m := c.search(text))]
    return {"strong": strong, "weak": weak}


def is_refusal(text):
    """两级：有信号 +（首句强短语 或 全文无带角标实质陈述）才判拒答。"""
    signal = refusal_signal(text)
    if not signal["strong"] and not signal["weak"]:
        return False
    statements = split_statements(text)
    if not statements:
        return True
    if refusal_signal(statements[0])["strong"]:
        return True
    return not [s for s in statements if CITATION_MARKER_RE.search(s)]


def citation_coverage(text):
    """带角标的实质陈述 / 全部实质陈述；无实质陈述返回 None（不参与分母）。"""
    statements = split_statements(text)
    if not statements:
        return None
    cited = [s for s in statements if CITATION_MARKER_RE.search(s)]
    return len(cited) / len(statements)


# --------------------------------------------------------------------------
# LLM 判官（主口径，三分类）
# --------------------------------------------------------------------------
JUDGE_SYSTEM = (
    "You are a strict but fair grader for a retrieval-augmented QA system. "
    "The system is instructed to answer in Simplified Chinese, so a correct answer may render "
    "English entities in Chinese, optionally with the English original in parentheses. "
    "Grade SEMANTIC equivalence, never language or wording. "
    "Output JSON only."
)

JUDGE_TEMPLATE = """Question:
{question}

Reference answer (the expected ground truth): {gold}

The system's answer:
{answer}

Grade the system's answer as exactly one of:
- "CORRECT": the answer states a conclusion consistent with the reference answer. For a yes/no
  question the polarity must match. Partial wording, extra correct context, or Chinese renderings
  of English entities are fine.
- "PARTIAL": the answer quotes or paraphrases evidence that supports the reference answer but
  does NOT state the required conclusion (e.g. lists two findings without answering Yes/No), or it
  answers only part of a multi-part question.
- "INCORRECT": the conclusion contradicts the reference answer, or the answer refuses / claims the
  material is insufficient even though the reference answer is obtainable from the corpus.

Return JSON: {{"verdict": "CORRECT|PARTIAL|INCORRECT", "reason": "<=25 words"}}"""

JUDGE_REFUSAL_TEMPLATE = """Question:
{question}

The corpus does NOT contain the information needed to answer this question, so the system MUST
refuse (state that the provided material does not contain the answer) rather than answer it.

The system's answer:
{answer}

Grade as exactly one of:
- "CORRECT": the answer clearly refuses / states that the provided material does not contain the
  information needed (any wording; in Chinese or English).
- "PARTIAL": the answer signals insufficient material but still asserts a concrete answer.
- "INCORRECT": the answer confidently answers the question as if the information were available.

Return JSON: {{"verdict": "CORRECT|PARTIAL|INCORRECT", "reason": "<=25 words"}}"""


def judge(cfg, row, answer_text):
    if row.get("expectRefusal"):
        prompt = JUDGE_REFUSAL_TEMPLATE.format(question=row["question"], answer=answer_text or "(空)")
    else:
        prompt = JUDGE_TEMPLATE.format(question=row["question"],
                                      gold=row.get("answer", ""), answer=answer_text or "(空)")
    status, content = chat(cfg, prompt, system=JUDGE_SYSTEM, temperature=0.0,
                           max_tokens=200, json_mode=True, thinking=False)
    if status != 200:
        return {"verdict": None, "reason": f"judge HTTP {status}: {content[:160]}", "raw": content}
    obj = extract_json(content) or {}
    verdict = str(obj.get("verdict") or "").upper()
    if verdict not in ("CORRECT", "PARTIAL", "INCORRECT"):
        return {"verdict": None, "reason": f"判官输出不可解析：{content[:160]}", "raw": content}
    return {"verdict": verdict, "reason": str(obj.get("reason") or ""), "raw": content}


# --------------------------------------------------------------------------
# 汇总（可由 perQuestion 独立复算）
# --------------------------------------------------------------------------
def aggregate(records):
    ok = [r for r in records if r.get("judge", {}).get("verdict")]
    answered = [r for r in ok if not r.get("error")]
    n = len(ok)

    def rate(pred, pool):
        pool = [r for r in pool if r.get("judge", {}).get("verdict")]
        if not pool:
            return None
        return round(sum(1 for r in pool if pred(r)) / len(pool), 4)

    correct = lambda r: r["judge"]["verdict"] == "CORRECT"
    partial = lambda r: r["judge"]["verdict"] == "PARTIAL"
    answerable = [r for r in ok if not r.get("expectRefusal")]
    refusable = [r for r in ok if r.get("expectRefusal")]

    cov = [citation_coverage(r.get("answer") or "") for r in answerable]
    cov = [c for c in cov if c is not None]
    lat = sorted(r["latencyMs"] for r in ok if r.get("latencyMs") is not None)

    by_type = {}
    for cat in sorted({r["category"] for r in ok}):
        sub = [r for r in ok if r["category"] == cat]
        by_type[cat] = {
            "count": len(sub),
            "accuracy": rate(correct, sub),
            "accuracyUpper": rate(lambda r: correct(r) or partial(r), sub),
            "refusalDetected": rate(lambda r: r.get("refusalDetected"), sub),
        }

    return {
        "gradedCount": n,
        "answeredCount": len(answered),
        "errorCount": len([r for r in records if r.get("error")]),
        "accuracy": rate(correct, ok),
        "accuracyUpper": rate(lambda r: correct(r) or partial(r), ok),
        "partialCount": len([r for r in ok if partial(r)]),
        "byTypeAcc": by_type,
        "answerable": {
            "count": len(answerable),
            "accuracy": rate(correct, answerable),
            "falseRefusalRate": rate(lambda r: r.get("refusalDetected"), answerable),
            "citationCoverageMacro": (round(sum(cov) / len(cov), 4) if cov else None),
            "citationCoverageDenominator": len(cov),
        },
        "refusable": {
            "count": len(refusable),
            "refusalAccuracy": rate(lambda r: r.get("refusalDetected"), refusable),
            "judgeRefusalAccuracy": rate(correct, refusable),
        },
        "latencyMs": ({
            "count": len(lat), "mean": round(sum(lat) / len(lat), 1),
            "p50": quantile(lat, 0.5), "p95": quantile(lat, 0.95), "max": lat[-1],
        } if lat else None),
        "judgeCalls": len([r for r in records if r.get("judge", {}).get("raw")]),
        "consistency": None,
    }


def self_check(summary, recomputed):
    """落盘前的自检 + --recompute 复核：汇总必须能由逐题明细独立复算且一致。"""
    keys = ("gradedCount", "accuracy", "accuracyUpper", "partialCount",
            "answeredCount", "errorCount", "judgeCalls")
    diff = {k: (summary.get(k), recomputed.get(k)) for k in keys
            if summary.get(k) != recomputed.get(k)}
    return (not diff), diff


# --------------------------------------------------------------------------
def run(args):
    cfg = load_env()
    base_url = args.base_url or cfg.get("BACKEND_BASE_URL", "")
    member_key = args.member_key or cfg.get("EVAL_MEMBER_API_KEY", "")
    if not base_url or not member_key:
        sys.exit("[FATAL] 缺 BACKEND_BASE_URL 或 EVAL_MEMBER_API_KEY（见 eval/.env）")
    rows = [json.loads(l) for l in open(args.answer_set, encoding="utf-8") if l.strip()]
    if args.limit:
        rows = rows[:args.limit]
    space_ids = [args.space_id] if args.space_id else []

    if not args.probe:
        if not probe(base_url, member_key, space_ids):
            sys.exit("[FATAL] 探测失败，中止（不把故障跑出的 0 分当指标）")

    run_id = datetime.now().strftime("mhr-ask-%Y%m%d-%H%M%S")
    print(f"[mhr-ask] {len(rows)} 题 · arm={args.arm} · judge={cfg.get('LLM_MODEL')}")
    records, errors, consecutive = [], [], 0
    for i, row in enumerate(rows, 1):
        rec = {"id": row["id"], "category": row.get("category"),
               "question": row.get("question"), "goldAnswer": row.get("answer"),
               "expectRefusal": bool(row.get("expectRefusal"))}
        started = time.perf_counter()
        try:
            res = ask_stream(base_url, member_key, row["question"], space_ids)
            consecutive = 0
            rec["answer"] = res["answer"]
            rec["citationCount"] = len(res["citations"] or [])
            rec["usage"] = res["usage"]
            rec["finishReason"] = res["finishReason"]
            rec["refusalDetected"] = is_refusal(res["answer"] or "")
            rec["refusalSignal"] = refusal_signal(res["answer"] or "")
            rec["citationCoverageStatements"] = citation_coverage(res["answer"] or "")
            rec["judge"] = judge(cfg, row, res["answer"])
        except AskError as exc:
            consecutive += 1
            rec["error"] = str(exc)
            rec["judge"] = {"verdict": None, "reason": None, "raw": None}
            errors.append({"id": row["id"], "error": str(exc)})
            if exc.fatal or consecutive >= MAX_CONSECUTIVE_ERRORS:
                _save(args, run_id, rows, records, errors, note="aborted")
                sys.exit(f"[FATAL] 连续 {consecutive} 次失败，中止：{exc}")
        rec["latencyMs"] = round((time.perf_counter() - started) * 1000, 1)
        records.append(rec)
        if i % 10 == 0 or i == len(rows):
            v = rec.get("judge", {}).get("verdict") or rec.get("error", "")[:40]
            print(f"  [{i}/{len(rows)}] {row['id']:10s} {rec['category']:18s} "
                  f"{rec['latencyMs']:7.0f}ms  {v}")
        if args.checkpoint_every and i % args.checkpoint_every == 0:
            _save(args, run_id, rows, records, errors, note="checkpoint")

    summary = aggregate(records)
    path = _save(args, run_id, rows, records, errors, note="final", summary=summary)
    print(f"\n结果 -> {path}")
    _print_report(summary, records)
    return path


def _save(args, run_id, rows, records, errors, note, summary=None):
    os.makedirs(args.out_dir, exist_ok=True)
    out = {
        "runId": run_id, "layer": "answer", "dataset": "mhr-rag",
        "arm": args.arm,
        "config": {"baseUrl": args.base_url or load_env().get("BACKEND_BASE_URL"),
                   "spaceId": args.space_id, "answerSet": args.answer_set,
                   "judgeModel": load_env().get("LLM_MODEL"), "note": note,
                   "expectedTotal": len(rows)},
        "summary": summary if summary is not None else aggregate(records),
        "errors": errors, "perQuestion": records,
    }
    path = os.path.join(args.out_dir, f"{run_id}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    return path


def _print_report(summary, records):
    print("\n" + "=" * 72)
    print(f"[mhr-ask] 判分 {summary['gradedCount']} 题 / 出错 {summary['errorCount']}")
    print(f"  正确率（CORRECT，主口径） = {summary['accuracy']}")
    print(f"  上沿（CORRECT+PARTIAL）   = {summary['accuracyUpper']}"
          f"（PARTIAL {summary['partialCount']} 题）")
    a, rf = summary["answerable"], summary["refusable"]
    print(f"  有答案题 n={a['count']}  正确率={a['accuracy']}  误拒率={a['falseRefusalRate']}"
          f"  引用覆盖率={a['citationCoverageMacro']}")
    print(f"  应拒答题 n={rf['count']}  零成本判据拒答率={rf['refusalAccuracy']}"
          f"  判官判对率={rf['judgeRefusalAccuracy']}")
    for cat, v in summary["byTypeAcc"].items():
        print(f"    {cat:18s} n={v['count']:3d}  acc={v['accuracy']}  上沿={v['accuracyUpper']}")
    print(f"  耗时 {summary['latencyMs']}  判官调用 {summary['judgeCalls']}")


def main():
    env = load_env()
    ap = argparse.ArgumentParser(description="MHR 答案层评测（LLM 判官主口径）")
    ap.add_argument("--answer-set", default=ANSWER_SET)
    ap.add_argument("--space-id", type=int, default=None)
    ap.add_argument("--base-url", default=None)
    ap.add_argument("--member-key", default=None)
    ap.add_argument("--arm", default="unspecified", help="哪条链路：如 evidence-assembly-only")
    ap.add_argument("--out-dir", default=os.path.join(L.DATASET_DIR, "results", "ask"))
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--checkpoint-every", type=int, default=CHECKPOINT_EVERY)
    ap.add_argument("--probe", action="store_true")
    ap.add_argument("--recompute", default=None, help="从结果文件复算汇总并比对一致性")
    args = ap.parse_args()

    if args.probe:
        probe(args.base_url or env.get("BACKEND_BASE_URL", ""),
              args.member_key or env.get("EVAL_MEMBER_API_KEY", ""),
              [args.space_id] if args.space_id else [])
        return
    if args.recompute:
        d = json.load(open(args.recompute, encoding="utf-8"))
        again = aggregate(d["perQuestion"])
        ok, diff = self_check(d["summary"], again)
        print(f"复算一致性：{'PASS' if ok else 'FAIL'}  差异={diff}")
        return
    run(args)


if __name__ == "__main__":
    main()

# -*- coding: utf-8 -*-
"""MultiHop-RAG（mhr-rag）数据集的专属约定与绑定算法。

**本文件与自有 216 篇政务语料评测完全隔离。** 两者互不 import、互不影响：
本数据集独占 `eval/datasets/mhr-rag/`，自有数据集占 `eval/` 目录本身。

共享的只有"怎么量"——取数与打分内核来自 `eval/scripts/lib_rag_eval.py`。
"""
from __future__ import annotations

import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DATASET_DIR = os.path.dirname(HERE)
EVAL_DIR = os.path.dirname(os.path.dirname(DATASET_DIR))
REPO_ROOT = os.path.dirname(EVAL_DIR)
SHARED_SCRIPTS = os.path.join(EVAL_DIR, "scripts")

sys.path.insert(0, SHARED_SCRIPTS)

DATASET = "mhr-rag"

# ---------------------------------------------------------------------------
# 数据来源与指纹
# ---------------------------------------------------------------------------
# 上游：MultiHop-RAG（yixuantt/MultiHop-RAG），609 篇英文新闻 + 2556 题多跳问答。
# 本地落点默认在 eval/tmp/（该目录 gitignore），故用 sha256 锁定版本：
CORPUS_SHA256 = "20b61b5ab84de84a927420c5d265b7ec8d859ae49980699958a787ade9e4d28f"
QA_SHA256 = "03cfb4926461f868684903aadc8024447bdda5bb3f6804741424cce338515bff"
CORPUS_COUNT = 609
QA_COUNT = 2556

DATA_DIR = os.environ.get("MHR_DATA_DIR", os.path.join(EVAL_DIR, "tmp"))
CORPUS_PATH = os.path.join(DATA_DIR, "mhr_corpus.json")
QA_PATH = os.path.join(DATA_DIR, "mhr_qa.json")
ANCHOR_DIR = os.path.join(DATASET_DIR, "anchor")
ANCHOR_PATH = os.path.join(ANCHOR_DIR, "anchor-map.json")
GOLDEN_PATH = os.path.join(ANCHOR_DIR, "golden.mhr.jsonl")
UNBOUND_PATH = os.path.join(ANCHOR_DIR, "unbound-report.json")

# ---------------------------------------------------------------------------
# 题型：上游 4 类。null_query 是"无法回答"，对应自有数据集的 unanswerable 类，
# 不进召回分母（gold 为空），只统计分布。
# ---------------------------------------------------------------------------
QUESTION_TYPES = ("inference_query", "comparison_query", "temporal_query", "null_query")
UNANSWERABLE_TYPES = ("null_query",)

# ---------------------------------------------------------------------------
# 切分参数（**真相在后端 Java**，这里只是镜像，供规划与预演使用）
#   cloud/src/main/java/com/et/cloud/rag/ChunkerProfile.java 的 EN
# 英文文档走独立 profile：上限 1800 / 重叠 100 / 句界 .!?;
# 依据：英文 1800 字符 ≈ 317 token，与中文 600 字符（≈340–419 token）量级对等；
# 且实测该参数下 gold 的 fact 100% 可绑定（600 时 55 条真丢）。
EN_PROFILE = {"max_chunk": 1800, "overlap": 100, "min_chunk": 100}

# 绑定时的重叠容忍：chunkText 的开头可能是上一块尾部（长度 <= overlap）加一个 "\n"。
# 该换行会打断跨边界 fact 的精确子串匹配，故匹配前必须做空白归一化。
_BIND_OVERLAP = EN_PROFILE["overlap"]

_WS_RE = re.compile(r"\s+")


def norm_ws(s: str) -> str:
    """去全部空白。切分器在重叠前缀与正文之间注入了 "\\n"，去空白可让跨边界 fact 重新连上。"""
    return _WS_RE.sub("", s or "")


def load_corpus(path: str = CORPUS_PATH) -> list:
    return json.load(open(path, encoding="utf-8"))


def load_qa(path: str = QA_PATH) -> list:
    return json.load(open(path, encoding="utf-8"))


def index_corpus_by_url(corpus: list) -> dict:
    """url -> 条目。实测 url 在 609 篇中唯一（609/609），是比 title 更稳的映射键：
    标题超 128 会被 document_wiki 截断，而 url 可直接落 sourceUrl 列。"""
    return {c["url"]: c for c in corpus}


def bind_fact_detail(fact: str, chunks: list) -> tuple[int | None, int]:
    """`bind_fact` 的明细版：返回 `(chunkIndex | None, 候选块数)`。

    候选块数 = 匹配到该 fact 的 chunk 个数（去重后）。**== 1 就是 README 表里的
    「单块唯一命中」**，>1 表示需靠"重叠前缀消歧"定夺。这个计数是绑定质量的体检指标：
    它与"绑定是否成功"是两个维度 —— 绑定成功但候选数很大，说明该 fact 在语料里
    不唯一，gold 坐标的可信度要打折。

    chunks: [{"chunkIndex": int, "text": str}]，必须来自库内 `wiki_chunk.chunkText`（唯一权威）。

    规则（确定性，可复现）：
      1. 先精确子串匹配（fact 完整落在某块文本里）
      2. 命中不到再用去空白归一化匹配（救回被重叠换行打断的跨边界 fact）
      3. 多块命中时，优先取"fact 不在开头重叠前缀区"的那一块（那是真身份），
         仍并列则取 chunkIndex 最小者

    返回 None 表示绑不上 —— 调用方**必须记入未绑定清单**，不得从 gold 里静默删除，
    否则等于用缩小分母来抬高指标。
    """
    if not fact:
        return None, 0
    exact = [c["chunkIndex"] for c in chunks if fact in (c.get("text") or "")]
    cand = exact
    if not cand:
        nf = norm_ws(fact)
        if len(nf) < 6:
            return None, 0
        cand = [c["chunkIndex"] for c in chunks if nf in norm_ws(c.get("text") or "")]
    if not cand:
        return None, 0
    cand = sorted(set(cand))
    if len(cand) == 1:
        return cand[0], 1
    tail_free = []
    for idx in cand:
        txt = next((c.get("text") or "" for c in chunks if c["chunkIndex"] == idx), "")
        prefix = txt[: _BIND_OVERLAP + 1]
        if norm_ws(fact) not in norm_ws(prefix):
            tail_free.append(idx)
    return (tail_free or cand)[0], len(cand)


def bind_fact(fact: str, chunks: list) -> int | None:
    """只关心坐标时的便捷入口；需要候选块数（体检指标）请用 `bind_fact_detail`。"""
    return bind_fact_detail(fact, chunks)[0]


def make_gold_entry(doc_id: int, chunk_index: int, fact: str, url: str = "",
                    title: str = "") -> dict:
    """产出与自有数据集**同形**的 gold 坐标。

    自有 `golden.v1.jsonl` 是 {docId, chunkIndex, why, quote}，这里刻意保持同一形状：
    `quote` 放上游的 `fact`（它就是我方 quote 的对应物），`why` 放标题便于人工核对。
    形状一致 = run_eval 的打分口径可原样复用 = 两个数据集的 metric 定义不会漂移。
    """
    return {
        "docId": int(doc_id),
        "chunkIndex": int(chunk_index),
        "why": title or url,
        "quote": fact,
    }


def golden_row(qa_index: int, qa: dict, gold: list) -> dict:
    """把一题转成 run_eval 可直接吃的 golden 行。"""
    return {
        "id": f"MHR-{qa_index:04d}",
        "category": qa.get("question_type") or "unknown",
        "question": qa.get("query") or "",
        "answer": qa.get("answer") or "",
        "expectRefusal": 1 if (qa.get("question_type") in UNANSWERABLE_TYPES) else 0,
        "gold": gold,
        "source": DATASET,
        "permission": None,
        "meta": {"evidenceCount": len(qa.get("evidence_list") or [])},
    }

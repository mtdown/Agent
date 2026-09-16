# -*- coding: utf-8 -*-
"""离线实验 C：混合候选池能不能把 oracle@N 顶上去。

前置：先跑 `diag_recall_ceiling.py` 产出 `eval/tmp/diag-rankings.json`（逐题 top-200 排名），
本脚本消费该文件。

动机：重排只是「从候选里挑」，天花板由候选池的 oracle 决定。
当前纯向量池 oracle@50 = 0.8148，够不到 0.85。本实验回答：
  加一路 BM25 关键词通道（RRF 融合）后，同一个 oracle@50 / @100 是多少？
若融合后 oracle@50 显著上升，则混合检索值得落地；否则该路线亦可判死。

实测结论（2026-09-16，399 题）：向量+BM25 oracle@50 0.8148→**0.8600**、
oracle@100 0.8532→**0.9061**，top-6 实测 0.5636→**0.5957**；
但 docnum 类从 0.7506 掉到 0.4129 —— 文号层必须钉位、不能进融合。

全程本地计算，不调任何模型 API（query 向量已在 diag-rankings 里隐含，这里只做词法通道）。
"""
from __future__ import annotations

import json
import math
import os
import re
import sys
from collections import Counter, defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
EVAL_DIR = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(EVAL_DIR, "scripts"))

from lib_eval import db_conn, load_env  # noqa: E402

KS = [6, 10, 20, 50, 100, 200]
RRF_K = 60


def tokenize(text):
    """中文用 bigram（无分词器可用时的标准做法），英文/数字按词。"""
    text = (text or "").lower()
    out = []
    for m in re.finditer(r"[a-z0-9]+|[\u4e00-\u9fa5]+", text):
        s = m.group()
        if re.match(r"[\u4e00-\u9fa5]", s):
            if len(s) == 1:
                out.append(s)
            out.extend(s[i:i + 2] for i in range(len(s) - 1))
        else:
            out.append(s)
    return out


def bm25_rank(query, df, docs_tf, dl_map, avgdl, postings, k1=1.2, b=0.75):
    qs = tokenize(query)
    scores = defaultdict(float)
    for t in set(qs):
        if t not in df:
            continue
        idf = math.log(1 + (len(dl_map) - df[t] + 0.5) / (df[t] + 0.5))
        for cid, tf in postings[t].items():
            dl = dl_map[cid]
            scores[cid] += idf * tf * (k1 + 1) / (tf + k1 * (1 - b + b * dl / avgdl))
    return sorted(scores.items(), key=lambda x: -x[1])


def find_data(name):
    """Locate a scratch data file.

    This tool was promoted from eval/tmp/ into eval/scripts/, but its input
    rankings (14MB) stay in eval/tmp/ because they are gitignored scratch data.
    Looking in both places keeps the tool runnable wherever the cwd is.
    """
    for candidate in (os.path.join(HERE, name),
                      os.path.join(EVAL_DIR, "tmp", name)):
        if os.path.exists(candidate):
            return candidate
    raise SystemExit(f"[FATAL] 找不到 {name}：应位于 eval/tmp/ 或 eval/scripts/")


def main():
    cfg = load_env()
    diag = json.load(open(find_data("diag-rankings.json"), encoding="utf-8"))
    pq = diag["perQuestion"]
    qtext = {}
    for line in open(os.path.join(EVAL_DIR, "golden.v2.jsonl"), encoding="utf-8"):
        r = json.loads(line)
        qtext[r["id"]] = r["question"]

    print("[hybrid] 载入政策空间全部 ACTIVE chunk 文本 …")
    conn = db_conn(cfg)
    rows = None
    with conn.cursor() as cur:
        cur.execute("SELECT docId, chunkIndex, chunkText FROM wiki_chunk "
                    "WHERE status='ACTIVE' AND spaceId=%s", (2095544464810774531,))
        rows = cur.fetchall()
    conn.close()
    print(f"[hybrid] {len(rows)} 块")

    texts, tf_map = {}, {}
    for d, ci, t in rows:
        cid = (int(d), int(ci))
        texts[cid] = f"{t or ''}"
        tf_map[cid] = Counter(tokenize(t))
    chunk_ids = list(tf_map)
    df = Counter()
    postings = defaultdict(dict)
    dl_map = {}
    for cid in chunk_ids:
        dl_map[cid] = sum(tf_map[cid].values())
        for t, tf in tf_map[cid].items():
            df[t] += 1
            postings[t][cid] = tf
    avgdl = sum(dl_map.values()) / len(chunk_ids)
    n_docs = len(chunk_ids)
    print(f"[hybrid] 词表 {len(df)}，平均块长 {avgdl:.0f} token")

    def rrf(ranklists, topn):
        acc = defaultdict(float)
        for rl in ranklists:
            for i, cid in enumerate(rl[:topn], 1):
                acc[cid] += 1.0 / (RRF_K + i)
        return [c for c, _ in sorted(acc.items(), key=lambda x: -x[1])]

    def oracle(q, pool, n):
        gold = {tuple(x) for x in q["gold"]}
        cand = set(pool[:n])
        return min(6, len(gold & cand)) / len(gold)

    variants = {}
    for name in ("dense", "hybrid"):
        variants[name] = {n: [] for n in KS}
    cache_bm = {}

    for i, q in enumerate(pq, 1):
        dense = [(h["docId"], h["chunkIndex"]) for h in q["hits"][:200]]
        qs = qtext[q["id"]]
        if qs not in cache_bm:
            cache_bm[qs] = [c for c, _ in bm25_rank(qs, df, tf_map, dl_map, avgdl, postings)]
        bm = cache_bm[qs]
        hyb = rrf([dense, bm], 200)
        for n in KS:
            variants["dense"][n].append(oracle(q, dense, n))
            variants["hybrid"][n].append(oracle(q, hyb, n))
        if i % 100 == 0:
            print(f"  进度 {i}/{len(pq)}")

    print("\n=== 候选池 oracle@N：纯向量 vs 向量+BM25(RRF) ===")
    print("  N      dense     hybrid    delta")
    for n in KS:
        a = sum(variants["dense"][n]) / len(pq)
        b = sum(variants["hybrid"][n]) / len(pq)
        print(f"  {n:<4d}  {a:.4f}   {b:.4f}   {b-a:+.4f}")

    # 分类对比 @50
    import collections
    by = collections.defaultdict(list)
    for idx, q in enumerate(pq):
        by[q["category"]].append(idx)
    print("\n=== 分类 oracle@50：dense vs hybrid ===")
    for c in sorted(by, key=lambda x: -len(by[x])):
        idxs = by[c]
        a = sum(variants["dense"][50][j] for j in idxs) / len(idxs)
        b = sum(variants["hybrid"][50][j] for j in idxs) / len(idxs)
        print(f"  {c:10s} n={len(idxs):4d}  dense={a:.4f}  hybrid={b:.4f}  {b-a:+.4f}")


if __name__ == "__main__":
    main()

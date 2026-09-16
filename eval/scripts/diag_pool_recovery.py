# -*- coding: utf-8 -*-
"""离线实验 D：候选池的「深度 × 质量」分解 —— 重排的漏失落在哪一层。

动机：实测池深 20→50 只兑现 +3.4pt，而混合池 oracle 同期增量是 +9.1pt；50→100 更差。
需要判定瓶颈属于哪一类：
  (E) 存在性   —— 池子里根本没有 gold（真正的"基础召回不足"）
  (Q) 一阶段排序质量 —— gold 在池里，但被融合排到很深，重排器够不着

方法（全程本地计算，只读，不调任何模型 API）：
  1. dense 通道 ← `eval/tmp/diag-rankings.json`（逐题 top-200 深取）
  2. 词法通道 ← 在 2061 块上现算 BM25（CJK bigram）
  3. 融合 RRF(k=60)。两种口径都算：
       prod = 每路取 top-50 → 融合 top-50   （镜像生产 candidatePoolSize/fusionPoolSize）
       deep = 每路取 top-200 → 融合 top-N    （镜像 diag_hybrid_pool.py，用于自检）
  4. 每个 gold 在融合池中的位次分桶；再与实测 pool=50 的最终 top-6 对照，算「按深度回收率」
  5. 把实测 recall@6 按 gold 的融合位次做加性分解，定位那 6.7pt 未兑现空间的位置

自检（口径必须对齐，否则结论不可信）：
  deep 口径 fused top-6 的 recall@6 应 ≈ 0.5957，
  deep oracle@50 ≈ 0.8600、@100 ≈ 0.9061（见 diag_hybrid_pool.py 记录值）。

用法：python eval/scripts/diag_pool_recovery.py [--result <评测结果.json>]
"""
from __future__ import annotations

import argparse
import collections
import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
EVAL_DIR = os.path.dirname(HERE)
sys.path.insert(0, HERE)

from lib_eval import db_conn, load_env  # noqa: E402

SPACE_ID = 2095544464810774531
RRF_K = 60
PROD_POOL = 50          # 生产 candidatePoolSize / fusionPoolSize
KS = [6, 10, 20, 50, 100, 200]
DEFAULT_RESULT = os.path.join(EVAL_DIR, "results", "baseline-20260916-201335-http.json")
# 池深 -> 该池深下的实测结果文件（用于边际兑现率对比）
DEPTH_RESULTS = {
    "20": "baseline-20260916-200304-http.json",
    "50": "baseline-20260916-201335-http.json",
    "100": "baseline-20260916-202351-http.json",
}

# gold 在融合池中的位次分桶
BUCKETS = [(1, 6), (7, 10), (11, 20), (21, 50), (51, 200)]


def tokenize(text):
    """中文 bigram（无分词器可用时的标准做法），英文/数字按词。"""
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


def bm25_rank(query, df, dl_map, avgdl, postings, k1=1.2, b=0.75):
    scores = collections.defaultdict(float)
    for t in set(tokenize(query)):
        if t not in df:
            continue
        idf = math.log(1 + (len(dl_map) - df[t] + 0.5) / (df[t] + 0.5))
        for cid, tf in postings[t].items():
            dl = dl_map[cid]
            scores[cid] += idf * tf * (k1 + 1) / (tf + k1 * (1 - b + b * dl / avgdl))
    return [c for c, _ in sorted(scores.items(), key=lambda x: -x[1])]


def find_data(name):
    for candidate in (os.path.join(HERE, name), os.path.join(EVAL_DIR, "tmp", name)):
        if os.path.exists(candidate):
            return candidate
    raise SystemExit(f"[FATAL] 找不到 {name}：应位于 eval/tmp/ 或 eval/scripts/")


def rrf(ranklists, topn):
    acc = collections.defaultdict(float)
    for rl in ranklists:
        for i, cid in enumerate(rl[:topn], 1):
            acc[cid] += 1.0 / (RRF_K + i)
    return [c for c, _ in sorted(acc.items(), key=lambda x: -x[1])]


def bucket_of(rank):
    for lo, hi in BUCKETS:
        if lo <= rank <= hi:
            return f"{lo}-{hi}"
    return "absent"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--result", default=DEFAULT_RESULT, help="实测评测结果 JSON（取最终 top-6）")
    args = ap.parse_args()

    cfg = load_env()
    diag = json.load(open(find_data("diag-rankings.json"), encoding="utf-8"))
    pq = diag["perQuestion"]

    qtext, golds = {}, {}
    for line in open(os.path.join(EVAL_DIR, "golden.v2.jsonl"), encoding="utf-8"):
        r = json.loads(line)
        qtext[r["id"]] = r["question"]
        golds[r["id"]] = r.get("gold", [])

    print("[D] 载入政策空间全部 ACTIVE chunk 文本 …")
    conn = db_conn(cfg)
    with conn.cursor() as cur:
        cur.execute("SELECT docId, chunkIndex, chunkText FROM wiki_chunk "
                    "WHERE status='ACTIVE' AND spaceId=%s", (SPACE_ID,))
        rows = cur.fetchall()
    conn.close()
    print(f"[D] {len(rows)} 块")

    tf_map, dl_map = {}, {}
    df = collections.Counter()
    postings = collections.defaultdict(dict)
    for d, ci, t in rows:
        cid = (int(d), int(ci))
        tf = collections.Counter(tokenize(t))
        tf_map[cid] = tf
        dl_map[cid] = sum(tf.values())
        for term, f in tf.items():
            df[term] += 1
            postings[term][cid] = f
    avgdl = sum(dl_map.values()) / len(dl_map)
    print(f"[D] 词表 {len(df)}，平均块长 {avgdl:.0f} token")

    res = json.load(open(args.result, encoding="utf-8"))
    final6 = {}
    for q in res["perQuestion"]:
        final6[q["id"]] = {(h["docId"], h["chunkIndex"]) for h in (q.get("hits") or [])[:6]}
    print(f"[D] 实测结果 {os.path.basename(args.result)}：{len(final6)} 题")

    # --- 逐题复算 ---
    rec_prod6, rec_deep6 = [], []
    oracle_deep = {n: [] for n in KS}
    bm_cache = {}
    # 桶统计：gold 数 / 被捞进 top6 数 / 对 recall@6 的贡献 / 潜在贡献
    stat = collections.defaultdict(lambda: {"gold": 0, "hit": 0, "got": 0.0, "pot": 0.0})
    per_cat = collections.defaultdict(lambda: {"n": 0, "rec": 0.0, "or50": 0.0, "or200": 0.0})
    small_gold_gap = {"n6gt": 0, "or50_lt": 0}   # oracle@6 vs oracle@50 的缺口题数
    skipped = 0

    for q in pq:
        qid = q["id"]
        qs = qtext.get(qid)
        if not qs or qid not in final6:
            skipped += 1
            continue
        dense = [(h["docId"], h["chunkIndex"]) for h in q["hits"][:200]]
        if qs not in bm_cache:
            bm_cache[qs] = bm25_rank(qs, df, dl_map, avgdl, postings)
        bm = bm_cache[qs]
        gold = [tuple(g) for g in q["gold"]]
        ng = len(gold)
        if ng == 0:
            skipped += 1
            continue

        prod = rrf([dense[:PROD_POOL], bm[:PROD_POOL]], PROD_POOL)
        deep = rrf([dense, bm], 200)
        got = final6[qid]

        rec_prod6.append(len(set(gold) & set(prod[:6])) / ng)
        rec_deep6.append(len(set(gold) & set(deep[:6])) / ng)
        for n in KS:
            oracle_deep[n].append(min(6, len(set(gold) & set(deep[:n]))) / ng)

        rank_of = {cid: i for i, cid in enumerate(deep, 1)}
        # 潜在：按融合位次给本题 gold 排队，前 min(6, ng) 个即「完美重排」会占用的槽位。
        # 池外 gold（rank_of 里没有）排在最后，但仍有资格占槽——它们在库里，完美排序够得着。
        slots = min(6, ng)
        for i, g in enumerate(sorted(gold, key=lambda x: rank_of.get(x, 10 ** 6))):
            key = bucket_of(rank_of[g]) if g in rank_of else "absent"
            s = stat[key]
            s["gold"] += 1
            if i < slots:
                s["pot"] += 1.0 / ng
            if g in got:
                s["hit"] += 1
                s["got"] += 1.0 / ng

        c = per_cat[q["category"]]
        c["n"] += 1
        c["rec"] += len(set(gold) & got) / ng
        c["or50"] += min(6, len(set(gold) & set(deep[:50]))) / ng
        c["or200"] += min(6, len(set(gold) & set(deep))) / ng

        if ng > 6:
            small_gold_gap["n6gt"] += 1

    n = len(rec_prod6)
    mean = lambda xs: sum(xs) / len(xs)

    print(f"\n=== 自检（口径对齐才可采信）· 可比题数 {n}，跳过 {skipped} ===")
    print(f"  deep  口径 fused top-6 recall@6 = {mean(rec_deep6):.4f}   （记录值 0.5957）")
    print(f"  prod  口径 fused top-6 recall@6 = {mean(rec_prod6):.4f}   （生产实际用此口径）")
    for n_ in (50, 100):
        print(f"  deep  oracle@{n_:<3d} = {mean(oracle_deep[n_]):.4f}", end="")
        print(f"   （记录值 {'0.8600' if n_ == 50 else '0.9061'}）")

    print("\n=== ① 一阶段排序质量 vs 该池上限 ===")
    print(f"  融合池直接取前 6 条（不重排）       recall@6 = {mean(rec_deep6):.4f}")
    print(f"  融合池 oracle@50（完美重排前 50）   recall@6 = {mean(oracle_deep[50]):.4f}")
    print(f"  → 池内可回收 gold 中被一阶段排进前 6 的比例 = "
          f"{mean(rec_deep6) / mean(oracle_deep[50]):.1%}   （其余需靠重排从深处捞）")
    print("\n  完美重排 over 前 K 段（= oracle@K，池深越深越接近全库上限）：")
    print(f"  {'K':>5} {'oracle@K':>10} {'较上一档增量':>13}")
    prev = None
    for n_ in KS:
        v = mean(oracle_deep[n_])
        print(f"  {n_:>5} {v:>10.4f} {('' if prev is None else f'{v - prev:+.4f}'):>13}")
        prev = v

    print("\n=== ② gold 在融合池中的位次分布（deep 口径，池深 200） ===")
    print(f"  {'位次桶':>8} {'gold 数':>8} {'占比':>7} {'捞进 top6':>10} {'回收率':>8}")
    tot_gold = sum(v["gold"] for v in stat.values())
    for lo, hi in BUCKETS:
        k = f"{lo}-{hi}"
        v = stat[k]
        rec = v["hit"] / v["gold"] if v["gold"] else 0
        print(f"  {k:>8} {v['gold']:>8} {v['gold'] / tot_gold:>6.1%} {v['hit']:>10} {rec:>7.1%}")
    va = stat["absent"]
    print(f"  {'absent':>8} {va['gold']:>8} {va['gold'] / tot_gold:>6.1%} {va['hit']:>10} {0.0:>7.1%}")

    print("\n=== ③ 实测 recall@6 按 gold 融合位次的加性分解（遵从每题 6 槽位上限）===")
    measured = sum(v["got"] for v in stat.values()) / n
    potential = sum(v["pot"] for v in stat.values()) / n
    or200 = mean(oracle_deep[200])
    gap_total = potential - measured
    print(f"  实测 recall@6              = {measured:.4f}")
    print(f"  完美排序上限（全库）        = {potential:.4f}   （应 ≈0.9783）")
    print(f"  未兑现合计                  = {gap_total:.4f}")
    print(f"  {'位次桶':>8} {'已兑现':>9} {'潜在':>9} {'未兑现':>9} {'占未兑现':>9}")
    for lo, hi in BUCKETS + [(0, 0)]:
        k = f"{lo}-{hi}" if lo else "absent"
        v = stat[k]
        g, p = v["got"] / n, v["pot"] / n
        print(f"  {k:>8} {g:>9.4f} {p:>9.4f} {p - g:>9.4f} {(p - g) / gap_total:>8.1%}")

    inside_gap, outside_gap = or200 - measured, potential - or200
    print("\n  未兑现空间的构成 —— 这是判定「瓶颈在哪一层」的核心：")
    print(f"    (Q) 池里已有、但重排没排进 top-6   = {inside_gap:.4f}  （占未兑现 {inside_gap / gap_total:.1%}）")
    print(f"    (E) 池深 200 都够不着的 gold        = {outside_gap:.4f}  （占未兑现 {outside_gap / gap_total:.1%}）")

    print("\n=== ④ 池深边际兑现率：加深池子买到的 oracle 兑现了多少 ===")
    print(f"  {'池深':>5} {'oracle@N':>10} {'实测 rec@6':>11} {'兑现率':>8} {'较上一档新增 oracle':>20} {'新增实测':>10} {'边际兑现率':>11}")
    prev_o = prev_m = None
    for depth, path in sorted(DEPTH_RESULTS.items(), key=lambda kv: int(kv[0])):
        rp = os.path.join(EVAL_DIR, "results", path)
        if not os.path.exists(rp):
            print(f"  {depth:>5}  (缺 {path})")
            continue
        rr = json.load(open(rp, encoding="utf-8"))
        vals = []
        for q in rr["perQuestion"]:
            # 去重：golden.v2.jsonl 里同一 (docId, chunkIndex) 可能因多条 quote 重复出现
            gg = list({(x["docId"], x["chunkIndex"]) for x in golds.get(q["id"], [])})
            if not gg:
                continue
            t6 = {(h["docId"], h["chunkIndex"]) for h in (q.get("hits") or [])[:6]}
            vals.append(len(set(gg) & t6) / len(gg))
        if not vals:
            print(f"  {depth:>5}  (无可用题目)")
            continue
        m = sum(vals) / len(vals)
        o = mean(oracle_deep[int(depth)])
        line = f"  {depth:>5} {o:>10.4f} {m:>11.4f} {m / o:>7.1%}"
        if prev_o is not None:
            do, dm = o - prev_o, m - prev_m
            line += f" {do:>20.4f} {dm:>10.4f} {dm / do:>10.1%}"
        print(line)
        prev_o, prev_m = o, m

    print("\n=== ⑤ 分类别：实测 vs 融合池自带上限 ===")
    print(f"  {'类别':>10} {'n':>4} {'实测 rec@6':>12} {'oracle@50':>10} {'oracle@200':>11} {'池内兑现率':>11} {'距池内上限':>11}")
    for c in sorted(per_cat, key=lambda x: -per_cat[x]["n"]):
        v = per_cat[c]
        rec, o50, o200 = v["rec"] / v["n"], v["or50"] / v["n"], v["or200"] / v["n"]
        print(f"  {c:>10} {v['n']:>4} {rec:>12.4f} {o50:>10.4f} {o200:>11.4f} "
              f"{rec / o200:>10.1%} {o200 - rec:>11.4f}")

    print(f"\n  gold 段数 > 6 的题目数：{small_gold_gap['n6gt']}（这些题的 recall@6 天然被 6 槽位截断）")
    print("  ⚠️ docnum 行的口径不可用：生产对文号查询有独立的「钉位组」通道，"
          "本脚本只做 dense+BM25 融合，故其 pool 列偏低、兑现率虚高。")


if __name__ == "__main__":
    main()

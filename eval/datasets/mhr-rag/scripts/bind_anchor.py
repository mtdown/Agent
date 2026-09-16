# -*- coding: utf-8 -*-
"""mhr-rag 的 gold 绑定：把上游的 (url, fact) 转成本系统的 (docId, chunkIndex)。

**为什么必须分两步**：切分是 AFTER_COMMIT 异步的，导入接口返回时 `wiki_chunk`
还没有行，所以 chunkIndex 只能等索引完成后再查库补上。

  步骤一  url      -> docId      来源：导入接口逐项结果，或查 document_wiki.sourceUrl
  步骤二  fact     -> chunkIndex 来源：查 wiki_chunk.chunkText（唯一权威）

用法：
    # 全量绑定（库内已导入 609 篇且索引完成）
    python eval/datasets/mhr-rag/scripts/bind_anchor.py

    # 用导入接口的逐项结果做步骤一（比查 sourceUrl 更贴导入当时的真实结果）
    python eval/datasets/mhr-rag/scripts/bind_anchor.py --import-map path/to/import-result.json

    # 只做步骤一，看导入覆盖情况
    python eval/datasets/mhr-rag/scripts/bind_anchor.py --step1-only

只读约定：不写业务库、不改 wiki_chunk。
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime

import mhr_lib as L  # noqa: E402
from lib_eval import db_conn, fetch_chunks, load_env  # noqa: E402


def step1_from_import_map(path: str) -> dict:
    """读导入接口返回的逐项结果：BatchImportItemResult{input,status,documentId,...}。

    `input` 用的是条目的稳定标识（url），不是数组下标 —— 顺序契约一旦被去重/跳过就错位。
    """
    raw = json.load(open(path, encoding="utf-8"))
    items = raw.get("data") if isinstance(raw, dict) and "data" in raw else raw
    if isinstance(items, dict):
        items = items.get("results") or items.get("items") or []
    out = {}
    for it in items or []:
        if str(it.get("status", "")).upper() not in ("SUCCESS", "OK", "0"):
            continue
        key = it.get("input")
        did = it.get("documentId")
        if key and did:
            out[key] = int(did)
    return out


def step1_from_db(cfg: dict, urls: list) -> dict:
    """按 sourceUrl 反查 document_wiki.id。分批 IN，避免超长 SQL。"""
    conn = db_conn(cfg)
    out = {}
    try:
        with conn.cursor() as cur:
            for i in range(0, len(urls), 200):
                batch = urls[i: i + 200]
                fmt = ",".join(["%s"] * len(batch))
                cur.execute(
                    f"SELECT id, sourceUrl FROM document_wiki "
                    f"WHERE sourceUrl IN ({fmt}) AND (isDelete IS NULL OR isDelete = 0)",
                    batch,
                )
                for did, url in cur.fetchall():
                    out.setdefault(url, int(did))
    finally:
        conn.close()
    return out


def main():
    ap = argparse.ArgumentParser(description="mhr-rag gold 绑定")
    ap.add_argument("--corpus", default=L.CORPUS_PATH)
    ap.add_argument("--qa", default=L.QA_PATH)
    ap.add_argument("--import-map", default=None,
                    help="导入接口逐项结果 JSON；不给则按 sourceUrl 查库")
    ap.add_argument("--step1-only", action="store_true", help="只做 url->docId，输出覆盖统计")
    ap.add_argument("--limit", type=int, default=0, help="只处理前 N 题（0=全量）")
    args = ap.parse_args()

    corpus = L.load_corpus(args.corpus)
    qa = L.load_qa(args.qa)
    if args.limit:
        qa = qa[: args.limit]
    by_url = L.index_corpus_by_url(corpus)
    print(f"[mhr-rag] corpus {len(corpus)} 篇 / qa {len(qa)} 题（上游应为 {L.CORPUS_COUNT} / {L.QA_COUNT}）")

    cfg = load_env()
    urls = list(by_url.keys())

    # ---- 步骤一 ----
    if args.import_map:
        url2doc = step1_from_import_map(args.import_map)
        print(f"[step1] 读导入结果：{len(url2doc)} 篇拿到 documentId")
    else:
        url2doc = step1_from_db(cfg, urls)
        print(f"[step1] 按 sourceUrl 查库：{len(url2doc)}/{len(urls)} 篇命中")
    missing_docs = [u for u in urls if u not in url2doc]
    if missing_docs:
        print(f"[step1] WARN 未导入 {len(missing_docs)} 篇，其证据将全部记为未绑定")
        for u in missing_docs[:3]:
            print(f"        - {u[:100]}")
    if args.step1_only:
        json.dump({"urlToDocId": url2doc, "missing": missing_docs},
                  open(os.path.join(L.ANCHOR_DIR, "step1-url-to-docid.json"), "w", encoding="utf-8"),
                  ensure_ascii=False, indent=1)
        print("[step1] 已写出 step1-url-to-docid.json")
        return

    # ---- 步骤二 ----
    doc_ids = sorted(set(url2doc.values()))
    chunks = fetch_chunks(cfg, doc_ids)
    print(f"[step2] 取到 {len(chunks)} 篇的 ACTIVE chunk，共 {sum(len(v) for v in chunks.values())} 条")

    rows, unbound = [], []
    stat = {"one": 0, "multi": 0, "none": 0, "no_doc": 0}
    dup_coord_q = 0
    for qi, q in enumerate(qa):
        gold, coords = [], []
        for e in (q.get("evidence_list") or []):
            url, fact = e.get("url"), e.get("fact") or ""
            did = url2doc.get(url)
            if did is None:
                stat["no_doc"] += 1
                unbound.append({"qaIndex": qi, "url": url, "title": e.get("title"),
                                "reason": "document-not-imported", "factLen": len(fact)})
                continue
            idx, n_cand = L.bind_fact_detail(fact, chunks.get(did) or [])
            if idx is None:
                stat["none"] += 1
                unbound.append({"qaIndex": qi, "url": url, "docId": did, "title": e.get("title"),
                                "reason": "fact-not-found-in-any-chunk", "factLen": len(fact)})
                continue
            # 绑定成功后再按"候选块数"细分：1 块 = 单块唯一命中；>1 = 靠重叠前缀消歧定夺。
            # 这两个计数原本漏写（只声明了初值 0，循环里从不 +=），会把 total_ev 压成 0，
            # 触发 ZeroDivisionError 并让 anchor-map 的 binding 段全 0 —— 2026-09-16 修。
            stat["one" if n_cand == 1 else "multi"] += 1
            gold.append(L.make_gold_entry(did, idx, fact, url, e.get("title") or ""))
            coords.append((did, idx))
        if len(set(coords)) < len(coords):
            dup_coord_q += 1
        rows.append(L.golden_row(qi, q, gold))

    os.makedirs(L.ANCHOR_DIR, exist_ok=True)
    with open(L.GOLDEN_PATH, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    payload = {
        "dataset": L.DATASET,
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "corpus": {"path": args.corpus, "count": len(corpus), "sha256": L.CORPUS_SHA256},
        "qa": {"path": args.qa, "count": len(qa), "sha256": L.QA_SHA256},
        "profile": L.EN_PROFILE,
        "docsImported": len(url2doc),
        "docsMissing": len(missing_docs),
        "binding": stat,
        "questionsWithDupCoord": dup_coord_q,
        "note": "gold 走 set 去重，同题多 quote 落同一 chunk 时只算一个坐标（跨数据集同口径）",
        "unbound": unbound,
    }
    json.dump(payload, open(L.ANCHOR_PATH, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.dump({"unbound": unbound}, open(L.UNBOUND_PATH, "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)

    total_ev = stat["one"] + stat["multi"] + stat["none"] + stat["no_doc"]
    bound = stat["one"] + stat["multi"]
    print()
    print("=" * 72)
    print(f"evidence 合计 {total_ev}")
    if not total_ev:
        print("[WARN] 一条 evidence 都没读到 —— 先查 qa 结构与 --qa 路径，绑定实际未发生")
    else:
        print(f"  绑定成功 {bound} ({bound / total_ev * 100:.2f}%)   未绑定 {total_ev - bound}")
    print(f"    - 单块唯一命中    {stat['one']}")
    print(f"    - 多块命中(消歧)  {stat['multi']}")
    print(f"    - 文档未导入      {stat['no_doc']}")
    print(f"    - fact 找不到块   {stat['none']}   <- 切分参数不当时会显著上升")
    print(f"  出现重复坐标的题 {dup_coord_q}（计分走 set 去重，分母相应缩小）")

    # 6.8 回归对照：README「已知实测数字」的 EN 1800/100 一列。
    # 不一致时先查绑定算法与切分参数，**不得直接调参掩盖**。
    chunk_total = sum(len(v) for v in chunks.values())
    print("  --- 对照 README 已知值（EN 1800/100）---")
    for name, got, want in (("总块数", chunk_total, 4195),
                            ("单块唯一命中", stat["one"], 6068),
                            ("未绑定", total_ev - bound, 0)):
        flag = "OK" if got == want else "<-- 不一致：先查算法与参数，勿调参掩盖"
        print(f"    {name}: 实测 {got} / 期望 {want}  {flag}")
    print(f"golden  -> {os.path.relpath(L.GOLDEN_PATH, L.REPO_ROOT)}")
    print(f"anchor  -> {os.path.relpath(L.ANCHOR_PATH, L.REPO_ROOT)}")
    if unbound:
        print(f"未绑定清单 -> {os.path.relpath(L.UNBOUND_PATH, L.REPO_ROOT)}（不得静默删除，须显式保留）")


if __name__ == "__main__":
    main()

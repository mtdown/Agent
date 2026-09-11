# -*- coding: utf-8 -*-
"""
chunk 锚点映射导出 —— add-rag-eval-dataset / Task 1.3

把 F 盘语料（metadataId / title / fileNum）与库内 wiki_chunk（docId / chunkIndex / chunkId）对齐，
产出 eval/tmp/anchor-map.json，供题目绑定 ground truth 使用。

契约（见 spec）：
  - 216 篇语料标题 SHALL 全部匹配到库内 docTitle
  - 匹配数不足 216 时报错退出，不产出部分映射
  - 只读数据库，不建表、不改索引
"""
import json
import os
import re
import glob
import sys
from datetime import datetime

sys.stdout.reconfigure(encoding="utf-8")

import pymysql

CORPUS = r"F:\AIProject\my\corpus-2026"
SECTIONS = [
    ("01-政策文件-szfwj", "政策文件"),
    ("02-部门解读-bmjd", "部门解读"),
    ("03-新闻发布会-jdfb", "新闻发布会"),
    ("04-媒体视角-mtsj", "媒体视角"),
]
DB = dict(host="127.0.0.1", port=3307, user="root", password="1234",
          database="Cloud", charset="utf8mb4", connect_timeout=5)

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "tmp", "anchor-map.json")


def norm(s: str) -> str:
    return re.sub(r"[\s、《》〈〉\"'“”‘’]", "", s or "")


def load_corpus():
    docs = []
    for dirname, label in SECTIONS:
        for meta_path in sorted(glob.glob(os.path.join(CORPUS, dirname, "*", "meta.json"))):
            try:
                m = json.load(open(meta_path, encoding="utf-8"))
            except Exception:
                continue
            docs.append({
                "section": label,
                "folder": os.path.basename(os.path.dirname(meta_path)),
                "metadataId": str(m.get("metadataId") or ""),
                "title": m.get("title") or "",
                "fileNum": m.get("fileNum") or "",
                "sourceUrl": m.get("sourceUrl") or "",
            })
    return docs


def main():
    corpus = load_corpus()
    if len(corpus) != 216:
        print(f"ERROR: 语料篇数 {len(corpus)} != 216，终止导出")
        sys.exit(1)

    conn = pymysql.connect(**DB)
    cur = conn.cursor()
    cur.execute("SELECT COUNT(*), SUM(status='ACTIVE'), COUNT(DISTINCT docId) FROM wiki_chunk")
    total, active, doc_total = cur.fetchone()
    cur.execute(
        "SELECT id, docId, chunkIndex, chunkHeading, CHAR_LENGTH(chunkText), docTitle, docNumber, spaceId "
        "FROM wiki_chunk WHERE status='ACTIVE' ORDER BY docId, chunkIndex"
    )
    rows = cur.fetchall()
    conn.close()

    # docId -> doc 级信息 + chunks
    db_docs = {}
    for cid, doc_id, idx, heading, clen, dtitle, dnum, space_id in rows:
        d = db_docs.setdefault(doc_id, {"docId": doc_id, "docTitle": dtitle or "",
                                        "docNumber": dnum or "", "spaceId": space_id, "chunks": []})
        d["chunks"].append({"chunkIndex": idx, "chunkId": cid,
                            "heading": heading or "", "chars": clen or 0})

    title_index = {}
    for doc_id, d in db_docs.items():
        title_index.setdefault(norm(d["docTitle"]), []).append(doc_id)

    mapped, unmatched, ambiguous = [], [], []
    used_doc_ids = set()
    for c in corpus:
        key = norm(c["title"])
        cands = title_index.get(key, [])
        if len(cands) == 1:
            doc_id = cands[0]
        elif len(cands) > 1:
            doc_id = cands[0]
            ambiguous.append({**c, "candidateDocIds": cands})
        else:
            unmatched.append(c)
            continue
        used_doc_ids.add(doc_id)
        d = db_docs[doc_id]
        mapped.append({
            **c,
            "docId": doc_id,
            "dbDocTitle": d["docTitle"],
            "dbDocNumber": d["docNumber"],
            "spaceId": d["spaceId"],
            "chunkCount": len(d["chunks"]),
            "chunks": d["chunks"],
        })

    if len(mapped) != 216:
        print(f"ERROR: 仅匹配 {len(mapped)}/216，未匹配 {len(unmatched)} 篇，按契约终止导出（不产出部分映射）")
        for u in unmatched[:10]:
            print(f"  - {u['folder']} {u['title'][:50]}")
        sys.exit(1)

    payload = {
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "corpus": CORPUS,
        "db": f"{DB['host']}:{DB['port']}/{DB['database']}",
        "dbSnapshot": {"chunkTotalActive": int(active or 0), "chunkTotal": int(total or 0),
                       "docWithChunk": int(doc_total or 0)},
        "docCount": len(mapped),
        "unmatched": unmatched,
        "ambiguous": ambiguous,
        "docs": mapped,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)

    chunk_sum = sum(d["chunkCount"] for d in mapped)
    print(f"OK -> {OUT}")
    print(f"匹配 {len(mapped)}/216 | ACTIVE chunk {active} | 覆盖 chunk {chunk_sum} | "
          f"库内 docId {doc_total} | 未使用 docId {doc_total - len(used_doc_ids)}")
    if ambiguous:
        print(f"WARN: {len(ambiguous)} 篇标题在库内有多个 docId 候选，已取第一个，需人工确认")


if __name__ == "__main__":
    main()

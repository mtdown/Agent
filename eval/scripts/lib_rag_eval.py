# -*- coding: utf-8 -*-
"""RAG 评测共享内核：取数适配器 + 打分口径。

**这个文件只回答"怎么量"，不回答"量什么"。** 检索算法本身在
`cloud/src/main/java/com/et/cloud/rag/RagSearchServiceImpl.java`，改它（例如
加 BM25 / 重排 / 改混合策略）**不需要动本文件**——评测脚本通过 HTTP 打到真实
系统，算法一改，指标自动反映；本文件里只有"发什么请求、怎么算分"。

四层归属（改哪一层动哪个文件）：

| 层 | 内容 | 位置 |
|---|---|---|
| ① 检索算法/流程 | 权限过滤 → 文号精确命中层 → 向量补齐 | 后端 `RagSearchServiceImpl.java` |
| ② 接口契约 | `POST /open/rag/search` 出入参 | 后端 Controller + 本文件 `HttpRetriever` 解析 |
| ③ 取数协议 | 一次取 topK=10、本地截断算各 K | 本文件「取数层」 |
| ④ 打分数学 | recall / docRecall / hitRate / mrr | 本文件「打分层」 |

按数据集隔离的部分（语料、gold 绑定、类别口径、报告渲染）**不放本文件**，
各自归属自己的目录，例如 `eval/datasets/mhr-rag/`。

只读约定：本库及调用它的脚本不写入业务库、不修改 wiki_chunk。
"""
from __future__ import annotations

import hashlib
import json
import math
import os
import struct
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib_eval import db_conn  # noqa: E402,F401

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ③ 取数协议：一次检索取 FETCH_K 条，本地截断算 KS 里的各个 K。
# 改动会影响所有历史结果的可比性 —— 属「改指标口径」，须同步重跑并替换基线。
KS = [1, 3, 5, 6, 10]
FETCH_K = max(KS)


# --------------------------------------------------------------------------
# ③ 取数层：检索适配器
# --------------------------------------------------------------------------
class Hit:
    __slots__ = ("doc_id", "chunk_index", "score", "doc_title",
                 "original_indexes", "evidence_group_id")

    def __init__(self, doc_id, chunk_index, score, doc_title="",
                 original_indexes=None, evidence_group_id=None):
        self.doc_id = int(doc_id)
        self.chunk_index = int(chunk_index)
        self.score = float(score)
        self.doc_title = doc_title or ""
        # chunk 整理会把同文相邻 chunk 物理合并成一个证据块，该块覆盖的全部原始
        # chunkIndex 记在这里。**无合并时为空** ⇒ `coords` 退化成 `[chunk_index]`，
        # 对所有不含合并的历史 run 是 no-op，不构成"改指标口径"。
        self.original_indexes = [int(i) for i in (original_indexes or []) if i is not None]
        self.evidence_group_id = evidence_group_id

    @property
    def coord(self):
        return (self.doc_id, self.chunk_index)

    @property
    def coords(self):
        """该 hit 覆盖的**全部** (docId, chunkIndex)。

        合并块必须把每个原始 chunk 都计入：若只按 `coord` 计分，整理会把多个原始
        chunk 收进一个 hit、却只算一个坐标，recall 被系统性低估 —— 那是**度量失真**，
        不是整理效果差。两个口径在这里必须一起改。
        """
        if not self.original_indexes:
            return [(self.doc_id, self.chunk_index)]
        out = []
        for idx in self.original_indexes:
            coord = (self.doc_id, idx)
            if coord not in out:
                out.append(coord)
        return out

    def to_dict(self):
        # 只在真有合并/分组信息时加字段：无合并的 run 落盘内容与历史完全一致，
        # 因此老结果与新结果的 diff 不会被无意义的空数组污染。
        out = {
            "docId": self.doc_id,
            "chunkIndex": self.chunk_index,
            "score": round(self.score, 6),
            "docTitle": self.doc_title,
        }
        if len(self.original_indexes) > 1:
            out["originalChunkIndexes"] = list(self.original_indexes)
        if self.evidence_group_id:
            out["evidenceGroupId"] = self.evidence_group_id
        return out


class RetrieverError(Exception):
    """可恢复的请求级错误。"""

    def __init__(self, message, fatal=False):
        super().__init__(message)
        self.fatal = fatal  # True = 环境级故障，应立即中止整轮


class HttpRetriever:
    """调 POST /open/rag/search —— 测的是真实系统，对外引用的指标一律来自这里。"""

    name = "http"

    def __init__(self, base_url, api_key, space_ids=None, timeout=60):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.space_ids = space_ids
        self.timeout = timeout
        # 最近一次 search() 的客户端端到端耗时（毫秒）。只用于报告 p50/p95，
        # 不参与任何指标定义 —— 因此加它不会让历史 recall/docRecall 失去可比性。
        self.last_elapsed_ms = None

    def search(self, query, top_k, api_key=None):
        key = api_key or self.api_key
        payload = {"query": query, "topK": top_k}
        if self.space_ids:
            payload["spaceIds"] = list(self.space_ids)
        started = time.perf_counter()
        try:
            raw = self._post("/open/rag/search", payload, key)
            body = json.loads(raw)
            # 业务码：0=成功；40101=key 无效；其余为系统错误
            code = body.get("code")
            if code != 0:
                msg = body.get("message") or f"code={code}"
                if code == 40101:
                    raise RetrieverError(f"API Key 无效或已失效（{msg}）", fatal=True)
                raise RetrieverError(f"检索返回业务错误 {msg}")
            data = body.get("data") or {}
            return [
                Hit(h.get("docId"), h.get("chunkIndex"), h.get("score", 0.0), h.get("docTitle", ""),
                    original_indexes=h.get("originalChunkIndexes"),
                    evidence_group_id=h.get("evidenceGroupId"))
                for h in (data.get("hits") or [])
            ]
        finally:
            self.last_elapsed_ms = round((time.perf_counter() - started) * 1000, 1)

    def _post(self, path, payload, api_key):
        return post_json(
            self.base_url + path,
            payload,
            {"Content-Type": "application/json", "X-API-Key": api_key or ""},
            timeout=self.timeout,
        )


class OfflineRetriever:
    """连库取向量 + 调 embedding API 算 query 向量 + 余弦排序。

    定位是**降级与 embedding 模型选型**，不是真实系统：
      --offline-vector db         读库内存量向量（2560 维 Ollama），query 必须用同一模型
      --offline-vector recompute  用目标模型全量重算 chunk 向量并缓存，用于模型对比

    注意：本适配器只做纯向量余弦，**不复现后端的文号精确命中层、也不过权限过滤**
    （直读全库 ACTIVE chunk）。因此后端一旦引入 BM25 / 重排等算法，本适配器不会跟随，
    它的适用范围会进一步收窄为「纯向量 embedding 选型」——不可用来代表整体检索效果。
    """

    name = "offline"

    def __init__(self, cfg, model, base_url, api_key, vector_source="db", cache_dir=None):
        self.cfg = cfg
        self.model = model
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.vector_source = vector_source
        self.cache_dir = cache_dir or os.path.join(ROOT, "tmp")
        self._chunks = None
        self._vectors = None

    # ---- 数据装载 ----
    def _load_chunks(self):
        conn = db_conn(self.cfg)
        try:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT docId, chunkIndex, docTitle, chunkText, embedding FROM wiki_chunk "
                    "WHERE status='ACTIVE' ORDER BY docId, chunkIndex"
                )
                return cur.fetchall()
        finally:
            conn.close()

    def _load_vectors(self):
        rows = self._chunks
        if self.vector_source == "db":
            vecs = []
            for r in rows:
                blob = r[4]
                if not blob:
                    vecs.append(None)
                    continue
                n = len(blob) // 4
                vecs.append(list(struct.unpack(f"<{n}f", blob)))
            return vecs
        return self._recompute_vectors([r[3] or "" for r in rows])

    def _recompute_vectors(self, texts):
        os.makedirs(self.cache_dir, exist_ok=True)
        safe = self.model.replace("/", "_")
        cache_path = os.path.join(self.cache_dir, f"offline-vectors-{safe}.json")
        if os.path.exists(cache_path):
            print(f"[offline] 复用向量缓存 {os.path.basename(cache_path)}")
            return json.load(open(cache_path, encoding="utf-8"))
        print(f"[offline] 用 {self.model} 重算 {len(texts)} 个 chunk 的向量…")
        out = []
        batch = int(self.cfg.get("RAG_INDEX_BATCH_EMBED_SIZE", "10"))
        for i in range(0, len(texts), batch):
            out.extend(self._embed(texts[i: i + batch]))
            if (i // batch) % 20 == 0:
                print(f"  已处理 {len(out)}/{len(texts)}")
        json.dump(out, open(cache_path, "w", encoding="utf-8"))
        return out

    def _embed(self, texts):
        payload = {"model": self.model, "input": texts}
        url = self.base_url.rstrip("/") + "/embeddings"
        raw = post_json(
            url,
            payload,
            {"Content-Type": "application/json", "Authorization": f"Bearer {self.api_key}"},
            timeout=120,
        )
        body = json.loads(raw)
        data = body.get("data") or []
        if not data:
            raise RetrieverError(f"embedding 返回空结果（{url}，model={self.model}）", fatal=True)
        return [d["embedding"] for d in data]

    def ensure_loaded(self):
        if self._chunks is None:
            self._chunks = self._load_chunks()
            self._vectors = self._load_vectors()
            dims = {len(v) for v in self._vectors if v}
            print(f"[offline] 已装载 {len(self._chunks)} chunk，维度 {sorted(dims)}")
            if len(dims) > 1:
                raise RetrieverError(f"chunk 向量维度不一致：{sorted(dims)}", fatal=True)

    def search(self, query, top_k, api_key=None):
        self.ensure_loaded()
        qvec = self._embed([query])[0]
        if self._vectors and self._vectors[0] and len(qvec) != len(self._vectors[0]):
            raise RetrieverError(
                f"维度不匹配：query {len(qvec)} 维 vs 库内 {len(self._vectors[0])} 维"
                f"（库内向量来自 {self.cfg.get('EMBEDDING_MODEL', 'Ollama qwen3-embedding:4b')}；"
                f"换模型请用 --offline-vector recompute）",
                fatal=True,
            )
        scored = []
        for row, vec in zip(self._chunks, self._vectors):
            if not vec:
                continue
            scored.append((cosine(qvec, vec), row))
        scored.sort(key=lambda x: x[0], reverse=True)
        return [
            Hit(r[0], r[1], s, r[2] or "") for s, r in scored[:top_k]
        ]


def build_opener(url):
    """localhost / 127.0.0.1 必须绕开系统代理。

    本机 urllib 默认读系统代理，实测对 127.0.0.1 的请求会被代理层拦下并返回
    「HTTP 502 upstream connect failed」，看起来像后端故障，实则是代理问题。
    """
    host = url.split("//", 1)[-1].split("/", 1)[0].split(":")[0]
    if host in ("localhost", "127.0.0.1", "::1"):
        return urllib.request.build_opener(urllib.request.ProxyHandler({}))
    return urllib.request.build_opener()


def post_json(url, payload, headers, timeout=30):
    """发 JSON POST，返回响应体文本；网络/HTTP 错误统一转 RetrieverError。"""
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    try:
        with build_opener(url).open(req, timeout=timeout) as resp:
            return resp.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "ignore")[:200]
        raise RetrieverError(f"HTTP {exc.code}: {detail}")
    except urllib.error.URLError as exc:
        raise RetrieverError(f"连接失败 {exc.reason}（后端是否已在 {url} 启动？）", fatal=True)
    except TimeoutError:
        raise RetrieverError(f"请求超时（>{timeout}s）", fatal=True)


def cosine(a, b):
    dot = sa = sb = 0.0
    for x, y in zip(a, b):
        dot += x * y
        sa += x * x
        sb += y * y
    if sa == 0 or sb == 0:
        return 0.0
    return dot / (math.sqrt(sa) * math.sqrt(sb))


def probe(retriever, member_key, query="重庆市地震应急预案"):
    """开跑前探测：后端不可用时立即退出，不留半截结果。

    query 可按数据集覆盖——非中文语料传英文查询，避免用中文探针误判。
    """
    print(f"[probe] {retriever.name} 模式 … ", end="", flush=True)
    try:
        retriever.search(query, 3, api_key=member_key)
        print("OK")
        return True
    except RetrieverError as exc:
        print("FAILED")
        print(f"\n[FATAL] {exc}")
        if retriever.name == "http":
            print("        后端未启动或 API Key 无效。请先执行 start-dev.ps1 启动后端，")
            print("        再用 eval/scripts/prepare_keys.py 制备评测 key。")
        return False


def quantile(sorted_vals, q):
    if not sorted_vals:
        return None
    pos = q * (len(sorted_vals) - 1)
    lo = int(math.floor(pos))
    hi = min(lo + 1, len(sorted_vals) - 1)
    return round(sorted_vals[lo] + (sorted_vals[hi] - sorted_vals[lo]) * (pos - lo), 6)


# --------------------------------------------------------------------------
# ④ 打分口径层：指标计算
# 改这里会让**所有**历史结果失去可比性，必须同步重跑并替换基线。
# --------------------------------------------------------------------------
def gold_pairs(row):
    return [(int(g["docId"]), int(g["chunkIndex"])) for g in (row.get("gold") or [])]


def metric_keys_for(ks=None):
    selected = ks or KS
    return ["mrr"] + [f"{m}@{k}" for m in ("recall", "docRecall", "hitRate") for k in selected]


def compute_metrics(gold, hits, ks=None):
    """gold: [(docId, chunkIndex)]；hits: [Hit]。一次取 topK 再本地截断算各 K。

    注意 gold 走 set 去重：同一题的多个 quote 落在同一 chunk 时只算一个坐标，
    分母按去重后计。跨数据集沿用同一口径，换取可比性。

    合并块按**覆盖坐标**计分（`Hit.coords`）：整理把相邻 chunk 合成一块后，
    该块仍覆盖全部原始坐标。无合并时 `coords == [coord]`，与历史口径逐位一致。
    """
    selected = ks or KS
    gset = set(gold)
    gdocs = {d for d, _ in gold}
    out = {}
    for k in selected:
        hk = hits[:k]
        hset = set()
        for h in hk:
            hset.update(h.coords)
        hdocs = {h.doc_id for h in hk}
        out[f"recall@{k}"] = round(len(gset & hset) / len(gset), 6)
        out[f"docRecall@{k}"] = round(len(gdocs & hdocs) / len(gdocs), 6)
        out[f"hitRate@{k}"] = 1.0 if (gset & hset) else 0.0
    rank = next((i + 1 for i, h in enumerate(hits) if gset & set(h.coords)), None)
    out["mrr"] = round(1.0 / rank, 6) if rank else 0.0
    return out


def mean(values):
    return round(sum(values) / len(values), 6) if values else None


def aggregate(records, metric_keys):
    """records: 已算好指标的题列表，按 metric_keys 求均值。"""
    out = {"count": len(records)}
    for key in metric_keys:
        out[key] = mean([r["metrics"][key] for r in records])
    return out


METRIC_KEYS = metric_keys_for(KS)


# --------------------------------------------------------------------------
# 通用文件工具
# --------------------------------------------------------------------------
def load_golden(path):
    rows = [json.loads(line) for line in open(path, encoding="utf-8") if line.strip()]
    return rows


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()

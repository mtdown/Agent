"""RAG 评测 runner：用 golden.v1.jsonl 驱动检索系统并产出标准化指标。

用法：
    # 1) 先探测后端与 key 是否可用
    python eval/scripts/run_eval.py --probe

    # 2) 全量跑基线（http 模式，测真实系统）
    python eval/scripts/run_eval.py

    # 3) offline 模式：embedding 模型选型对比（不依赖后端）
    python eval/scripts/run_eval.py --retriever offline --offline-vector recompute

设计要点（详见 openspec/changes/add-rag-eval-runner/design.md）：
- 一次检索取 topK=10，本地截断算 K ∈ {1,3,5,6,10} 的全部指标，99 题只需 99 次请求
- C 类（gold 为空）不进召回分母，只报分布，不拍相似度阈值
- D 类双 key 对照，泄漏一票否决且不进 overall 平均
- 后端不可用时 fail fast，绝不把故障跑出的全 0 分当指标
"""

import argparse
import hashlib
import json
import math
import os
import struct
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib_eval import db_conn, load_env  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
POLICY_SPACE_ID = 2095544464810774531
KS = [1, 3, 5, 6, 10]
FETCH_K = max(KS)  # 一次取 10 条，本地截断
MAX_CONSECUTIVE_ERRORS = 5


# --------------------------------------------------------------------------
# 检索适配器
# --------------------------------------------------------------------------
class Hit:
    __slots__ = ("doc_id", "chunk_index", "score", "doc_title")

    def __init__(self, doc_id, chunk_index, score, doc_title=""):
        self.doc_id = int(doc_id)
        self.chunk_index = int(chunk_index)
        self.score = float(score)
        self.doc_title = doc_title or ""

    @property
    def coord(self):
        return (self.doc_id, self.chunk_index)

    def to_dict(self):
        return {
            "docId": self.doc_id,
            "chunkIndex": self.chunk_index,
            "score": round(self.score, 6),
            "docTitle": self.doc_title,
        }


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

    def search(self, query, top_k, api_key=None):
        key = api_key or self.api_key
        payload = {"query": query, "topK": top_k}
        if self.space_ids:
            payload["spaceIds"] = list(self.space_ids)
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
            Hit(h.get("docId"), h.get("chunkIndex"), h.get("score", 0.0), h.get("docTitle", ""))
            for h in (data.get("hits") or [])
        ]

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
            out.extend(self._embed(texts[i : i + batch]))
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


# --------------------------------------------------------------------------
# 指标计算
# --------------------------------------------------------------------------
def gold_pairs(row):
    return [(int(g["docId"]), int(g["chunkIndex"])) for g in (row.get("gold") or [])]


def compute_metrics(gold, hits):
    """gold: [(docId, chunkIndex)]；hits: [Hit]。一次 topK=10 本地截断算各 K。"""
    gset = set(gold)
    gdocs = {d for d, _ in gold}
    out = {}
    for k in KS:
        hk = hits[:k]
        hset = {h.coord for h in hk}
        hdocs = {h.doc_id for h in hk}
        out[f"recall@{k}"] = round(len(gset & hset) / len(gset), 6)
        out[f"docRecall@{k}"] = round(len(gdocs & hdocs) / len(gdocs), 6)
        out[f"hitRate@{k}"] = 1.0 if (gset & hset) else 0.0
    rank = next((i + 1 for i, h in enumerate(hits) if h.coord in gset), None)
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


METRIC_KEYS = ["mrr"] + [f"{m}@{k}" for m in ("recall", "docRecall", "hitRate") for k in KS]


# --------------------------------------------------------------------------
# 主流程
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


def probe(retriever, member_key):
    """开跑前探测：后端不可用时立即退出，不留半截结果。"""
    print(f"[probe] {retriever.name} 模式 … ", end="", flush=True)
    try:
        retriever.search("重庆市地震应急预案", 3, api_key=member_key)
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


def main():
    ap = argparse.ArgumentParser(description="RAG 评测 runner")
    ap.add_argument("--golden", default=os.path.join(ROOT, "golden.v1.jsonl"))
    ap.add_argument("--retriever", choices=["http", "offline"], default="http")
    ap.add_argument("--base-url", default=None, help="后端地址，默认 http://localhost:8123")
    ap.add_argument("--member-key", default=None)
    ap.add_argument("--non-member-key", default=None)
    ap.add_argument("--space-scope", action="store_true", default=True)
    ap.add_argument("--no-space-scope", dest="space_scope", action="store_false")
    ap.add_argument("--offline-vector", choices=["db", "recompute"], default="db")
    ap.add_argument("--limit", type=int, default=0, help="只跑前 N 题（调试用）")
    ap.add_argument("--probe", action="store_true", help="仅探测后端与 key，不跑评测")
    ap.add_argument("--no-report", action="store_true")
    args = ap.parse_args()

    cfg = load_env()
    member_key = args.member_key or cfg.get("EVAL_MEMBER_API_KEY", "")
    non_member_key = args.non_member_key or cfg.get("EVAL_NONMEMBER_API_KEY", "")

    if args.retriever == "http":
        base_url = args.base_url or cfg.get("BACKEND_BASE_URL", "http://localhost:8123/api")
        retriever = HttpRetriever(base_url, member_key, [POLICY_SPACE_ID] if args.space_scope else None)
    else:
        retriever = OfflineRetriever(
            cfg,
            model=cfg.get("EMBEDDING_MODEL", "qwen3-embedding:4b"),
            base_url=args.base_url or cfg.get("EMBEDDING_BASE_URL", "http://localhost:11434/v1"),
            api_key=cfg.get("EMBEDDING_API_KEY", ""),
            vector_source=args.offline_vector,
        )

    if args.probe:
        sys.exit(0 if probe(retriever, member_key) else 1)

    if args.retriever == "http" and not member_key:
        sys.exit("[FATAL] 缺少成员 API Key：先跑 eval/scripts/prepare_keys.py，或传 --member-key")
    if not probe(retriever, member_key):
        sys.exit(1)

    rows = load_golden(args.golden)
    if args.limit:
        rows = rows[: args.limit]
    by_id = {r["id"]: r for r in rows}
    print(f"[run] {len(rows)} 题，retriever={retriever.name}，topK={FETCH_K}")

    per_question = []
    errors = []
    consecutive = 0
    started = datetime.now()

    for i, row in enumerate(rows, 1):
        qid, cat = row["id"], row["category"]
        rec = {"id": qid, "category": cat, "question": row["question"], "error": None}

        try:
            if cat == "permission" and retriever.name == "offline":
                # offline 模式直接查全量向量库，不做权限过滤 —— 此时的泄漏率是假的
                rec["skippedReason"] = "offline 模式无权限过滤，泄漏率无意义"
                per_question.append(rec)
                continue

            if cat == "permission":
                perm = row.get("permission") or {}
                forbidden = {int(d) for d in (perm.get("forbiddenDocIds") or [])}
                # 成员侧：应命中镜像题证据（证明权限过滤没误伤正常检索）
                mirror_id = (row.get("meta") or {}).get("mirrorOf")
                mirror_gold = gold_pairs(by_id[mirror_id]) if mirror_id in by_id else []
                rec["mirrorOf"] = mirror_id
                if member_key:
                    m_hits = retriever.search(row["question"], FETCH_K, api_key=member_key)
                    rec["memberHits"] = [h.to_dict() for h in m_hits]
                    rec["memberMirrorRecall@6"] = (
                        compute_metrics(mirror_gold, m_hits)["recall@6"] if mirror_gold else None
                    )
                else:
                    rec["memberMirrorRecall@6"] = None
                # 非成员侧：出现任一 forbidden docId 即泄漏
                if non_member_key:
                    n_hits = retriever.search(row["question"], FETCH_K, api_key=non_member_key)
                    leaked = [h.doc_id for h in n_hits if h.doc_id in forbidden]
                    rec["nonMemberHits"] = [h.to_dict() for h in n_hits]
                    rec["leakedDocIds"] = leaked
                    rec["leak"] = bool(leaked)
                    rec["nonMemberMaxScore"] = round(max((h.score for h in n_hits), default=0.0), 6)
                else:
                    rec["skippedReason"] = "缺少非成员 key"
                consecutive = 0
            else:
                hits = retriever.search(row["question"], FETCH_K, api_key=member_key)
                rec["hits"] = [h.to_dict() for h in hits]
                rec["top1Score"] = round(hits[0].score, 6) if hits else 0.0
                rec["returnCount"] = len(hits)
                rec["top1DocId"] = hits[0].doc_id if hits else None
                g = gold_pairs(row)
                rec["goldCount"] = len(g)
                if g:
                    rec["metrics"] = compute_metrics(g, hits)
                else:
                    rec["metrics"] = None  # C 类：不进召回分母
                consecutive = 0
        except RetrieverError as exc:
            rec["error"] = str(exc)
            errors.append({"id": qid, "error": str(exc)})
            consecutive += 1
            if exc.fatal or consecutive >= MAX_CONSECUTIVE_ERRORS:
                print(f"\n[FATAL] 第 {i} 题失败：{exc}")
                print(f"        已连续失败 {consecutive} 次，中止本轮。已完成 {i - 1} 题。")
                break
        except Exception as exc:  # noqa: BLE001
            rec["error"] = f"{type(exc).__name__}: {exc}"
            errors.append({"id": qid, "error": rec["error"]})
            consecutive += 1
            if consecutive >= MAX_CONSECUTIVE_ERRORS:
                print(f"\n[FATAL] 连续失败 {consecutive} 次，中止：{exc}")
                break

        per_question.append(rec)
        if i % 10 == 0 or i == len(rows):
            print(f"  进度 {i}/{len(rows)}  失败 {len(errors)}")

    # ---- 汇总 ----
    scored = [r for r in per_question if r.get("metrics")]
    by_cat = {}
    for cat in ("pair", "docnum", "synthetic"):
        sub = [r for r in scored if r["category"] == cat]
        if sub:
            by_cat[cat] = aggregate(sub, METRIC_KEYS)

    overall = aggregate(scored, METRIC_KEYS)

    # C 类：只报分布，不拍阈值
    c_rows = [r for r in per_question if r["category"] == "unanswerable" and not r.get("error")]
    c_scores = sorted(r.get("top1Score", 0.0) for r in c_rows)
    # 数据集里没有 unanswerableType 字段（design 阶段设想的子类型未落地），
    # 与其按 ID 区间硬凑分组，不如逐题列明细：evidenceKey / outOfScopeReason / top1Score
    c_detail = []
    for r in sorted(c_rows, key=lambda x: -(x.get("top1Score") or 0)):
        meta = by_id[r["id"]].get("meta") or {}
        c_detail.append(
            {
                "id": r["id"],
                "evidenceKey": meta.get("evidenceKey", ""),
                "outOfScopeReason": meta.get("outOfScopeReason", ""),
                "returnCount": r.get("returnCount", 0),
                "top1Score": r.get("top1Score"),
            }
        )
    unanswerable = {
        "count": len(c_rows),
        "nonEmptyReturnRate": round(
            sum(1 for r in c_rows if r.get("returnCount", 0) > 0) / len(c_rows), 6
        )
        if c_rows
        else None,
        "top1Score": {
            "min": c_scores[0] if c_scores else None,
            "p25": quantile(c_scores, 0.25),
            "median": quantile(c_scores, 0.5),
            "p75": quantile(c_scores, 0.75),
            "max": c_scores[-1] if c_scores else None,
        },
        "perQuestion": c_detail,
    }

    # 有答案题 vs 无答案题的 top1Score 分布对比：
    # 决定「能否靠相似度阈值拒答」——若两者区间大量重叠，则阈值法不可行
    s_scores = sorted(r.get("top1Score", 0.0) for r in scored)
    c_min = c_scores[0] if c_scores else None
    c_max = c_scores[-1] if c_scores else None
    s_min = s_scores[0] if s_scores else None
    s_max = s_scores[-1] if s_scores else None
    overlap_lo = max(c_min, s_min) if (c_min is not None and s_min is not None) else None
    overlap_hi = min(c_max, s_max) if (c_max is not None and s_max is not None) else None
    score_compare = {
        "scored": {
            "count": len(s_scores),
            "min": s_min,
            "p25": quantile(s_scores, 0.25),
            "median": quantile(s_scores, 0.5),
            "p75": quantile(s_scores, 0.75),
            "max": s_max,
        },
        "unanswerable": {
            "count": len(c_scores),
            "min": c_min,
            "p25": quantile(c_scores, 0.25),
            "median": quantile(c_scores, 0.5),
            "p75": quantile(c_scores, 0.75),
            "max": c_max,
        },
        "overlapRange": [overlap_lo, overlap_hi]
        if (overlap_lo is not None and overlap_hi is not None and overlap_lo <= overlap_hi)
        else None,
        "thresholdFeasible": bool(
            overlap_lo is not None
            and overlap_hi is not None
            and overlap_hi < overlap_lo
        ),
    }
    # 权衡表：不同阈值下「拦掉多少无答案题」与「误杀多少有答案题」
    if c_scores and s_scores:
        candidates = []
        for t in (c_max, quantile(c_scores, 0.75), quantile(s_scores, 0.25), quantile(s_scores, 0.5)):
            if t is not None:
                candidates.append(round(float(t), 4))
        tradeoff = []
        for t in sorted(set(candidates)):
            tradeoff.append(
                {
                    "threshold": t,
                    "blockedUnanswerable": round(sum(1 for x in c_scores if x < t) / len(c_scores), 4),
                    "killedScored": round(sum(1 for x in s_scores if x < t) / len(s_scores), 4),
                }
            )
        score_compare["thresholdTradeoff"] = tradeoff

    # D 类：泄漏一票否决，不进 overall
    d_rows = [r for r in per_question if r["category"] == "permission" and not r.get("error")]
    leaks = [r for r in d_rows if r.get("leak")]
    mirror_recalls = [r["memberMirrorRecall@6"] for r in d_rows if r.get("memberMirrorRecall@6") is not None]
    if retriever.name == "offline":
        permission = {
            "count": len(d_rows),
            "notApplicable": True,
            "leakCount": None,
            "leakRate": None,
            "note": "offline 模式不做权限过滤，泄漏率无意义；D 类需 http 模式双 key 对照",
        }
    else:
        permission = {
            "count": len(d_rows),
            "notApplicable": False,
            "skipped": sum(1 for r in d_rows if r.get("skippedReason")),
            "leakCount": len(leaks),
            "leakRate": round(len(leaks) / len(d_rows), 6) if d_rows else None,
            "leakedIds": [r["id"] for r in leaks],
            "memberMirrorRecall@6": mean(mirror_recalls),
            "note": "泄漏即一票否决，不进 overall 平均",
        }

    invalid = bool(errors) and (overall.get("recall@6") in (0, 0.0))

    result = {
        "runId": f"baseline-{datetime.now().strftime('%Y%m%d-%H%M%S')}-{retriever.name}",
        "config": {
            "retriever": retriever.name,
            "baseUrl": getattr(retriever, "base_url", None),
            # http 模式下 embedding 由后端 application.yml 的 rag.embedding.model 决定，
            # 不能拿 .env 里给 offline 模式配的模型冒名顶替（会产出误导性报告）
            "embeddingModel": cfg.get("EMBEDDING_MODEL", "") if retriever.name == "offline" else None,
            "fetchK": FETCH_K,
            "ks": KS,
            "spaceScoped": bool(args.space_scope),
            "spaceId": POLICY_SPACE_ID if args.space_scope else None,
            "goldenFile": os.path.relpath(args.golden, ROOT).replace("\\", "/"),
            "goldenSha256": sha256_file(args.golden),
            "startedAt": started.isoformat(timespec="seconds"),
            "finishedAt": datetime.now().isoformat(timespec="seconds"),
        },
        "counts": {
            "total": len(per_question),
            "scored": len(scored),
            "unanswerable": len(c_rows),
            "permission": len(d_rows),
            "errors": len(errors),
        },
        "overall": overall,
        "byCategory": by_cat,
        "unanswerable": unanswerable,
        "scoreCompare": score_compare,
        "permission": permission,
        "invalid": invalid,
        "errors": errors,
        "perQuestion": per_question,
    }

    os.makedirs(os.path.join(ROOT, "results"), exist_ok=True)
    out_path = os.path.join(ROOT, "results", f"{result['runId']}.json")
    json.dump(result, open(out_path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"\n[OK] 结果写入 {os.path.relpath(out_path, ROOT)}")
    if invalid:
        print("[WARN] invalid=true —— 本轮存在调用错误且 recall@6 为 0，判定为环境故障，结果不可用")

    if not args.no_report:
        gen_report(result, by_id)


def gen_report(result, by_id):
    os.makedirs(os.path.join(ROOT, "audit"), exist_ok=True)
    path = os.path.join(ROOT, "audit", "baseline-report.md")
    cfg = result["config"]
    ov = result["overall"]
    L = []
    L.append("# RAG 检索层基线评测报告\n")
    if result["invalid"]:
        L.append("> **环境故障，此轮结果不可用** —— 存在调用错误且 recall@6 为 0。\n")
    L.append(f"- 运行 ID：`{result['runId']}`")
    L.append(f"- 检索模式：`{cfg['retriever']}`（baseUrl `{cfg['baseUrl']}`）")
    L.append(f"- 数据集：`{cfg['goldenFile']}` · sha256 `{cfg['goldenSha256'][:16]}…`")
    if cfg["retriever"] == "http":
        emb_note = "由后端 `application.yml` 的 `rag.embedding.model` 决定（runner 不读后端配置，需人工核对）"
    else:
        emb_note = f"`{cfg['embeddingModel'] or '（未配置）'}`"
    L.append(f"- embedding：{emb_note}")
    L.append(f"- 检索参数：一次取 top{cfg['fetchK']}，本地截断算 K ∈ {{{', '.join(map(str, cfg['ks']))}}}")
    L.append(f"- 时间：{cfg['startedAt']} → {cfg['finishedAt']}\n")

    c = result["counts"]
    L.append(f"## 总览\n\n| 项 | 值 |\n|---|---|")
    L.append(f"| 总题数 | {c['total']} |")
    L.append(f"| 计入召回 | {c['scored']} |")
    L.append(f"| C 类无答案 | {c['unanswerable']} |")
    L.append(f"| D 类权限 | {c['permission']} |")
    L.append(f"| 调用失败 | {c['errors']} |\n")

    L.append("### 整体指标（仅 gold 非空的题）\n")
    L.append("| 指标 | 数值 |\n|---|---|")
    for k in METRIC_KEYS:
        L.append(f"| {k} | {ov.get(k)} |")
    L.append("")

    L.append("### 分类明细\n")
    L.append("| 类别 | 题数 | recall@6 | docRecall@6 | hitRate@6 | mrr |\n|---|---|---|---|---|---|")
    name = {"pair": "A 配对", "docnum": "B 文号", "synthetic": "E 合成"}
    for cat, agg in result["byCategory"].items():
        L.append(
            f"| {name.get(cat, cat)} | {agg['count']} | {agg['recall@6']} | "
            f"{agg['docRecall@6']} | {agg['hitRate@6']} | {agg['mrr']} |"
        )
    L.append("")

    # B 类专项：验证后端文号精确匹配层
    b = result["byCategory"].get("docnum")
    if b and ov.get("recall@6") is not None:
        delta = (b["recall@6"] or 0) - (ov["recall@6"] or 0)
        verdict = "文号精确匹配层生效" if delta > 0.05 else ("未见明显增益" if delta >= 0 else "低于整体，需排查")
        L.append(f"**B 类文号题专项**：recall@6 = {b['recall@6']}，整体 = {ov['recall@6']}，"
                 f"差值 {round(delta, 4)} → {verdict}\n")

    u = result["unanswerable"]
    L.append("## C 类无答案题（不进召回分母）\n")
    L.append(f"- 题数 {u['count']}，非空返回率 **{u['nonEmptyReturnRate']}**（越高说明幻觉诱导面越大）")
    t = u["top1Score"]
    L.append(f"- top1Score 分布：min {t['min']} / p25 {t['p25']} / median {t['median']} / "
             f"p75 {t['p75']} / max {t['max']}\n")

    sc = result.get("scoreCompare")
    if sc:
        s, cc = sc["scored"], sc["unanswerable"]
        L.append("### 有答案 vs 无答案的分数重叠\n")
        L.append("| 分组 | 题数 | min | p25 | 中位数 | p75 | max |\n|---|---|---|---|---|---|---|")
        L.append(f"| 有答案（A/B/E） | {s['count']} | {s['min']} | {s['p25']} | "
                 f"{s['median']} | {s['p75']} | {s['max']} |")
        L.append(f"| 无答案（C） | {cc['count']} | {cc['min']} | {cc['p25']} | "
                 f"{cc['median']} | {cc['p75']} | {cc['max']} |\n")
        if sc.get("thresholdTradeoff"):
            L.append("阈值权衡（score < T 则拒答）：\n")
            L.append("| 阈值 T | 拦掉无答案题 | 误杀有答案题 |\n|---|---|---|")
            for row in sc["thresholdTradeoff"]:
                L.append(f"| {row['threshold']} | {row['blockedUnanswerable']:.0%} | "
                         f"{row['killedScored']:.0%} |")
            L.append("")
        if sc["thresholdFeasible"]:
            L.append("**结论**：两组分数区间完全分离，**可以用相似度阈值拒答**。\n")
        else:
            rng = sc["overlapRange"]
            L.append(f"**结论**：两组分数存在重叠区间 `{rng}`，**无法用单一相似度阈值拒答** —— "
                     f"无答案题的最高分（{cc['max']}）高于/接近有答案题的最低分（{s['min']}），"
                     f"靠阈值一刀切会同时误杀有答案题。这是纯向量检索的固有局限，"
                     f"需要在答案层（LLM 判断证据是否充分）解决，而非检索层。\n")
    if u.get("perQuestion"):
        L.append("| 题号 | 越界实体 | 越界理由 | 返回条数 | top1Score |\n|---|---|---|---|---|")
        for r in u["perQuestion"]:
            L.append(f"| {r['id']} | {r['evidenceKey']} | {r['outOfScopeReason']} | "
                     f"{r['returnCount']} | {r['top1Score']} |")
        L.append("")

    p = result["permission"]
    L.append("## D 类权限题（泄漏一票否决）\n")
    L.append(f"- 题数 {p['count']}，跳过 {p.get('skipped', 0)}")
    if p.get("notApplicable"):
        L.append(f"- **不适用**：{p['note']}\n")
    else:
        L.append(f"- **泄漏 {p['leakCount']} 题** · leakRate {p['leakRate']}")
        if p.get("leakedIds"):
            L.append(f"- 泄漏题目：{', '.join(p['leakedIds'])}")
        L.append(f"- 成员侧镜像召回 recall@6 = {p.get('memberMirrorRecall@6')}"
                 f"（过低说明权限过滤误伤正常检索）")
        L.append(f"- {p['note']}\n")

    # 最差样例
    worst = sorted(
        (r for r in result["perQuestion"] if r.get("metrics")),
        key=lambda r: (r["metrics"]["recall@6"], r["metrics"]["mrr"]),
    )[:10]
    L.append("## 表现最差的 10 题\n")
    L.append("| 题号 | 类别 | recall@6 | mrr | gold 数 | top1 命中 |\n|---|---|---|---|---|---|")
    for r in worst:
        top1 = (r.get("hits") or [{}])[0]
        hit = "是" if top1 and (top1.get("docId"), top1.get("chunkIndex")) in set(
            gold_pairs(by_id[r["id"]])
        ) else "否"
        L.append(f"| {r['id']} | {r['category']} | {r['metrics']['recall@6']} | "
                 f"{r['metrics']['mrr']} | {r['goldCount']} | {hit} |")
    L.append("")

    if result["errors"]:
        L.append("## 调用失败\n")
        for e in result["errors"][:20]:
            L.append(f"- `{e['id']}`：{e['error']}")
        L.append("")

    L.append("## 口径说明\n")
    L.append("- gold 只绑政策原文，命中解读 chunk 计为未命中 —— **Recall 偏低是预期且真实**，"
             "不代表系统差（见 add-rag-eval-dataset design 第 44 行）")
    L.append("- `docRecall@K` 为文档级召回，切分策略变更时仍可比，是跨实验对比的首选指标")
    L.append("- 两种 retriever 的分数不可混合平均；对比实验须固定 retriever 与 embeddingModel")

    open(path, "w", encoding="utf-8").write("\n".join(L) + "\n")
    print(f"[OK] 报告写入 {os.path.relpath(path, ROOT)}")


if __name__ == "__main__":
    main()

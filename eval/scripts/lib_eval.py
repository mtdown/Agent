# -*- coding: utf-8 -*-
"""评测数据集构建共用库：环境加载、LLM 调用、文本相似度、语料/库读取。

只读约定：本库及调用它的脚本不写入业务库、不修改 wiki_chunk。
"""
from __future__ import annotations

import glob
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
EVAL_DIR = os.path.join(ROOT, "eval")
TMP_DIR = os.path.join(EVAL_DIR, "tmp")
ENV_PATH = os.path.join(EVAL_DIR, ".env")
CORPUS = r"F:\AIProject\my\corpus-2026"

UA = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Accept": "application/json",
}

SECTIONS = [
    ("01-政策文件-szfwj", "政策文件"),
    ("02-部门解读-bmjd", "部门解读"),
    ("03-新闻发布会-jdfb", "新闻发布会"),
    ("04-媒体视角-mtsj", "媒体视角"),
]


def load_env(path: str = ENV_PATH) -> dict:
    cfg = {}
    if not os.path.exists(path):
        raise SystemExit(f"缺少配置文件 {path}")
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        cfg[k.strip()] = v.strip()
    return cfg


def http_json(url: str, api_key: str, payload: dict, timeout: int = 180, retries: int = 3):
    """POST JSON，返回 (status, obj_or_text)。带重试与浏览器 UA（绕过 WAF）。"""
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    last = None
    for i in range(retries):
        req = urllib.request.Request(
            url, data=data,
            headers={**UA, "Content-Type": "application/json", "Authorization": f"Bearer {api_key}"},
        )
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                body = r.read().decode("utf-8", "replace")
                try:
                    return r.status, json.loads(body)
                except Exception:
                    return r.status, body
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", "replace")
            last = f"HTTP {e.code}: {body[:300]}"
            if e.code in (429, 500, 502, 503, 504):
                time.sleep(2 * (i + 1))
                continue
            return e.code, body
        except Exception as e:  # noqa: BLE001
            last = f"{type(e).__name__}: {e}"
            time.sleep(2 * (i + 1))
    return -1, last or "unknown error"


def chat(cfg: dict, prompt: str, system: str = "", temperature: float = 0.0,
         max_tokens: int = 4000, json_mode: bool = True,
         thinking: bool = False) -> tuple[int, str]:
    """返回 (status, content)。content 为模型输出文本（失败时为错误信息）。

    thinking=False 关闭推理模型的思考链：实测 qwen3.8-flash 上耗时 2.4s→0.7s、
    token 212→50，答案一致。本场景（抽取式出题 + 摘抄校验）无需思考链。
    """
    base = cfg["LLM_BASE_URL"].rstrip("/")
    messages = []
    if system:
        messages.append({"role": "system", "content": system})
    messages.append({"role": "user", "content": prompt})
    payload = {
        "model": cfg["LLM_MODEL"],
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
        "enable_thinking": thinking,
    }
    if json_mode:
        payload["response_format"] = {"type": "json_object"}
    st, obj = http_json(f"{base}/chat/completions", cfg["LLM_API_KEY"], payload)
    if st != 200:
        return st, obj if isinstance(obj, str) else json.dumps(obj, ensure_ascii=False)
    try:
        return 200, obj["choices"][0]["message"]["content"]
    except Exception as e:  # noqa: BLE001
        return -2, f"解析失败 {e}: {str(obj)[:300]}"


JSON_BLOCK_RE = re.compile(r"```(?:json)?\s*(.*?)```", re.S)


def extract_json(text: str):
    """从模型输出里稳健地抽出 JSON（对象或数组）。"""
    if not text:
        return None
    s = text.strip()
    m = JSON_BLOCK_RE.search(s)
    if m:
        s = m.group(1).strip()
    # 直接解析
    for cand in (s,):
        try:
            return json.loads(cand)
        except Exception:
            pass
    # 截取第一个 [ 或 { 起的平衡片段
    start = None
    for i, ch in enumerate(s):
        if ch in "[{":
            start = i
            break
    if start is None:
        return None
    open_ch = s[start]
    close_ch = "]" if open_ch == "[" else "}"
    depth = 0
    in_str = False
    esc = False
    for i in range(start, len(s)):
        ch = s[i]
        if in_str:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
        elif ch == open_ch:
            depth += 1
        elif ch == close_ch:
            depth -= 1
            if depth == 0:
                try:
                    return json.loads(s[start:i + 1])
                except Exception:
                    break
    return None


def strip_front_matter(text: str) -> str:
    m = re.match(r"^---\r?\n.*?\r?\n---\r?\n", text, re.S)
    return text[m.end():] if m else text


def load_corpus_doc(folder: str) -> str | None:
    for sec, _ in SECTIONS:
        p = os.path.join(CORPUS, sec, folder, "content.md")
        if os.path.exists(p):
            return strip_front_matter(open(p, encoding="utf-8", errors="replace").read())
    return None


def load_corpus_meta(folder: str) -> dict | None:
    for sec, _ in SECTIONS:
        p = os.path.join(CORPUS, sec, folder, "meta.json")
        if os.path.exists(p):
            try:
                return json.load(open(p, encoding="utf-8"))
            except Exception:  # noqa: BLE001
                return None
    return None


def db_conn(cfg: dict):
    import pymysql
    return pymysql.connect(
        host=cfg.get("DB_HOST", "127.0.0.1"),
        port=int(cfg.get("DB_PORT", "3307")),
        user=cfg.get("DB_USER", "root"),
        password=cfg.get("DB_PASSWORD", "1234"),
        database=cfg.get("DB_NAME", "Cloud"),
        charset="utf8mb4",
        connect_timeout=8,
    )


def fetch_chunks(cfg: dict, doc_ids: list[int]) -> dict[int, list[dict]]:
    """docId -> [{chunkIndex, chunkText, chunkHeading}]，按 chunkIndex 升序。"""
    if not doc_ids:
        return {}
    conn = db_conn(cfg)
    try:
        with conn.cursor() as cur:
            fmt = ",".join(["%s"] * len(doc_ids))
            cur.execute(
                f"SELECT docId, chunkIndex, chunkHeading, chunkText FROM wiki_chunk "
                f"WHERE docId IN ({fmt}) AND status='ACTIVE' ORDER BY docId, chunkIndex",
                doc_ids,
            )
            out: dict[int, list[dict]] = {}
            for did, idx, head, text in cur.fetchall():
                out.setdefault(int(did), []).append(
                    {"chunkIndex": int(idx), "heading": head or "", "text": text or ""}
                )
            return out
    finally:
        conn.close()


PUNCT_RE = re.compile(r"[\s，,。.；;：:、（）()《》\"'“”‘’\-—－　]+")


def norm_flat(s: str) -> str:
    """归一化：去空白与标点，用于校验 quote 是否真实出现在 chunk 中。"""
    return PUNCT_RE.sub("", s or "")


def quote_hit(quote: str, text: str) -> bool:
    """校验模型摘抄的句子是否真实存在于文本中（防 gold 幻觉）。"""
    q = norm_flat(quote)
    if len(q) < 6:
        return False
    return q in norm_flat(text)


def bigrams(s: str) -> set:
    s = re.sub(r"\s+", "", s or "")
    return {s[i:i + 2] for i in range(len(s) - 1)}


def char_sim(query: str, doc: str) -> float:
    """中文字符 bigram Jaccard 相似度，用于候选 chunk 粗排。"""
    a, b = bigrams(query), bigrams(doc)
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def keyword_bonus(query: str, doc: str) -> float:
    """关键词命中加成：查询中的 2-6 字中文词在文档中出现比例。"""
    words = set(re.findall(r"[\u4e00-\u9fa5]{2,6}", query or ""))
    if not words:
        return 0.0
    hit = sum(1 for w in words if w in doc)
    return hit / len(words)

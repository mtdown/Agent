# -*- coding: utf-8 -*-
"""
LLM / Embedding 接口冒烟测试

用法：
  python eval/scripts/smoke_test.py
  python eval/scripts/smoke_test.py --base-url https://xxx/v1     # 覆盖两端 base-url
  python eval/scripts/smoke_test.py --llm-base-url <url> --emb-base-url <url>
  python eval/scripts/smoke_test.py --only chat|embedding
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
ENV_PATH = os.path.join(os.path.dirname(HERE), ".env")


def load_env(path):
    cfg = {}
    if not os.path.exists(path):
        return cfg
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        cfg[k.strip()] = v.strip()
    return cfg


def post(base_url, path, api_key, payload, timeout=90):
    url = base_url.rstrip("/") + path
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={
        "Content-Type": "application/json",
        "Authorization": f"Bearer {api_key}",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                       "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Accept": "application/json",
    })
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            return resp.status, body, time.time() - t0
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace"), time.time() - t0
    except Exception as e:
        return -1, f"{type(e).__name__}: {e}", time.time() - t0


def test_chat(base_url, api_key, model):
    print(f"\n[chat] base={base_url} model={model}")
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": "你是政务问答助手，回答简洁。"},
            {"role": "user", "content": "用一句话说明：重庆市地震应急预案中Ⅲ级响应由谁启动？"},
        ],
        "temperature": 0,
        "max_tokens": 200,
    }
    status, body, dt = post(base_url, "/chat/completions", api_key, payload)
    print(f"  HTTP {status}  ({dt:.2f}s)")
    if status != 200:
        print(f"  FAILED: {body[:400]}")
        return False
    try:
        obj = json.loads(body)
        content = obj["choices"][0]["message"]["content"].strip()
        usage = obj.get("usage", {})
        print(f"  model(回显) = {obj.get('model')}")
        print(f"  usage = {usage}")
        print(f"  回答 = {content[:200]}")
        return True
    except Exception as e:
        print(f"  解析失败: {e} | 原文: {body[:300]}")
        return False


def test_embedding(base_url, api_key, model):
    print(f"\n[embedding] base={base_url} model={model}")
    payload = {
        "model": model,
        "input": ["重庆市人民政府关于印发《重庆市地震应急预案》的通知", "成文日期与文号"],
    }
    status, body, dt = post(base_url, "/embeddings", api_key, payload)
    print(f"  HTTP {status}  ({dt:.2f}s)")
    if status != 200:
        print(f"  FAILED: {body[:400]}")
        return False
    try:
        obj = json.loads(body)
        data = obj["data"]
        dim = len(data[0]["embedding"])
        print(f"  model(回显) = {obj.get('model')}")
        print(f"  条数 = {len(data)} | 维度 = {dim}")
        print(f"  usage = {obj.get('usage', {})}")
        print(f"  前 5 维 = {[round(x, 5) for x in data[0]['embedding'][:5]]}")
        # 语义区分度粗测
        a, b = data[0]["embedding"], data[1]["embedding"]
        cos = sum(x * y for x, y in zip(a, b)) / ((sum(x * x for x in a) ** 0.5) * (sum(y * y for y in b) ** 0.5))
        print(f"  两句相似度(余弦) = {cos:.4f}  （越低区分度越好，>0.9 需警惕）")
        return True
    except Exception as e:
        print(f"  解析失败: {e} | 原文: {body[:300]}")
        return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", help="同时覆盖 chat 与 embedding 的 base-url")
    ap.add_argument("--llm-base-url")
    ap.add_argument("--emb-base-url")
    ap.add_argument("--only", choices=["chat", "embedding"])
    args = ap.parse_args()

    cfg = load_env(ENV_PATH)
    llm_base = args.llm_base_url or args.base_url or cfg.get("LLM_BASE_URL", "")
    emb_base = args.emb_base_url or args.base_url or cfg.get("EMBEDDING_BASE_URL", "")
    llm_key = cfg.get("LLM_API_KEY", "")
    emb_key = cfg.get("EMBEDDING_API_KEY", "")
    llm_model = cfg.get("LLM_MODEL", "")
    emb_model = cfg.get("EMBEDDING_MODEL", "")

    print("=== 配置 ===")
    print(f"chat      : {llm_base} | {llm_model} | key={llm_key[:12]}...(len {len(llm_key)})")
    print(f"embedding : {emb_base} | {emb_model} | key={emb_key[:12]}...(len {len(emb_key)})")

    ok = True
    if args.only in (None, "chat"):
        ok &= test_chat(llm_base, llm_key, llm_model)
    if args.only in (None, "embedding"):
        ok &= test_embedding(emb_base, emb_key, emb_model)

    print("\n=== 结论 ===")
    print("全部通过" if ok else "存在失败项 —— 请检查 base-url / key / model 名")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()

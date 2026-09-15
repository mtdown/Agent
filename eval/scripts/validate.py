# -*- coding: utf-8 -*-
"""任务 5.1：评测数据集校验器。

校验 golden.v1.jsonl（或 candidates.jsonl）是否满足对外发布标准：

  1. schema 完整性：必填字段存在、类型正确、category 合法
  2. **禁止出现 chunkId**（红线：重建索引后主键会漂移，gold 必须用逻辑坐标）
  3. gold 锚点 (docId, chunkIndex) 可解析到当前 ACTIVE chunk
  4. gold[].quote 必须真实存在于对应 chunk 文本（防 gold 幻觉）
  5. 问题去重（同一 category 内）
  6. 五类齐全、配比偏差提示
  7. 分类专属规则：C 类必须拒答且无 gold；D 类必须有 permission.forbiddenDocIds
  8. golden 模式额外要求每题 meta.reviewState ∈ {kept, edited}

只读约定：只查 wiki_chunk，不写业务库、不触发索引重建。

用法：
    python eval/scripts/validate.py                     # 自动选 golden.v1.jsonl，否则 candidates.jsonl
    python eval/scripts/validate.py eval/golden.v1.jsonl
    python eval/scripts/validate.py --self-test         # 注入坏数据，验证脚本能报错并指出题号
    python eval/scripts/validate.py --no-db             # 无库环境，跳过锚点解析校验
"""
from __future__ import annotations

import argparse
import copy
import json
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from lib_eval import EVAL_DIR, db_conn, load_env, norm_flat, quote_hit  # noqa: E402

# expand-rag-eval-coverage 新增 crossdoc / single 两类（见 design D3）
CATEGORIES = {"pair", "docnum", "unanswerable", "permission", "synthetic",
              "crossdoc", "single"}
CAT_CN = {
    "pair": "A 配对题", "docnum": "B 文号题", "unanswerable": "C 无答案题",
    "permission": "D 权限题", "synthetic": "E 合成题",
    "crossdoc": "F 跨文档题", "single": "G 单文档题",
}
# 必须有 gold 的类别（C 类应无 gold；D 类走 permission 结构）
GOLD_REQUIRED_CATS = {"pair", "docnum", "synthetic", "crossdoc", "single"}
# 目标配比（v1 tasks.md 3.7 产出值）。v2 题型构成不同，此处降级为提示、不作为门槛
TARGET_MIX = {"pair": 54, "docnum": 9, "unanswerable": 15, "permission": 10, "synthetic": 12}

REQUIRED_TOP = ["id", "category", "question", "answer", "expectRefusal",
                "gold", "source", "permission", "meta"]
REQUIRED_META = ["qType", "goldVerified", "generatedBy", "reviewState"]
# reviewedBy 为 expand-rag-eval-coverage 新增；v1 数据集没有该字段，故只提示不报错
RECOMMENDED_META = ["reviewedBy"]

CROSS_REGION_KEYS = ("四川", "成都")  # 川渝通办合法存在，仅提示人工确认


def load_rows(path: str) -> list[dict]:
    rows = []
    with open(path, encoding="utf-8") as f:
        for ln, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                rows.append(json.loads(line))
            except Exception as e:  # noqa: BLE001
                raise SystemExit(f"[FATAL] {path} 第 {ln} 行不是合法 JSON：{e}")
    return rows


def fetch_anchor_index(cfg: dict):
    """返回 (anchors, docinfo)。
    anchors: {(docId, chunkIndex): chunkText}
    docinfo: {docId: {"title":..., "docNumber":..., "spaceId":...}}
    """
    conn = db_conn(cfg)
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT docId, chunkIndex, chunkText, docTitle, docNumber, spaceId "
                "FROM wiki_chunk WHERE status='ACTIVE'"
            )
            anchors, docinfo = {}, {}
            for did, idx, text, title, num, space in cur.fetchall():
                did = int(did)
                anchors[(did, int(idx))] = text or ""
                docinfo.setdefault(did, {"title": title or "", "docNumber": num or "",
                                         "spaceId": int(space) if space else 0})
            return anchors, docinfo
    finally:
        conn.close()


def scan_chunk_id(obj, path="$") -> list[str]:
    """递归查找禁止出现的 chunkId 字段（红线）。"""
    hits = []
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k.lower() == "chunkid":
                hits.append(f"{path}.{k}")
            hits += scan_chunk_id(v, f"{path}.{k}")
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            hits += scan_chunk_id(v, f"{path}[{i}]")
    return hits


def validate(rows: list[dict], anchors: dict, docinfo: dict,
             strict_golden: bool, has_db: bool) -> tuple[list[str], list[str], dict]:
    errors: list[str] = []
    warns: list[str] = []
    seen_ids: dict[str, int] = {}
    seen_q: dict[tuple[str, str], str] = {}
    cat_count: dict[str, int] = {c: 0 for c in CATEGORIES}
    gold_docs: set[int] = set()
    gold_total = 0
    quote_checked = 0

    for i, r in enumerate(rows, 1):
        rid = r.get("id", f"<第{i}行无 id>")

        # --- id 唯一 ---
        if rid in seen_ids:
            errors.append(f"[{rid}] id 重复（首次出现于第 {seen_ids[rid]} 行）")
        seen_ids[rid] = i

        # --- schema 完整性 ---
        for k in REQUIRED_TOP:
            if k not in r:
                errors.append(f"[{rid}] 缺少必填字段 {k}")
        if "id" not in r and "category" not in r:
            continue

        cat = r.get("category")
        if cat not in CATEGORIES:
            errors.append(f"[{rid}] 非法 category：{cat}")
            continue
        cat_count[cat] += 1

        # --- 红线：禁止 chunkId ---
        for p in scan_chunk_id(r):
            errors.append(f"[{rid}] 出现禁止字段 {p}（gold 必须只用 docId+chunkIndex 逻辑坐标）")

        q = (r.get("question") or "").strip()
        a = (r.get("answer") or "").strip()
        if len(q) < 5:
            errors.append(f"[{rid}] question 过短或为空")
        if len(a) < 2:
            errors.append(f"[{rid}] answer 过短或为空")

        # --- 问题去重（同一 category 内；D 类是 A/B 镜像，跨类重复属预期）---
        key = (cat, norm_flat(q) or q)
        if key in seen_q:
            errors.append(f"[{rid}] 问题与 {seen_q[key]} 重复（同 category 内）")
        else:
            seen_q[key] = rid

        meta = r.get("meta") or {}
        for k in REQUIRED_META:
            if k not in meta:
                errors.append(f"[{rid}] meta 缺少字段 {k}")

        gold = r.get("gold") or []
        refusal = r.get("expectRefusal")

        # --- gold 锚点与摘抄校验 ---
        for g in gold:
            if not isinstance(g, dict):
                errors.append(f"[{rid}] gold 元素不是对象")
                continue
            for k in ("docId", "chunkIndex"):
                if k not in g:
                    errors.append(f"[{rid}] gold 缺少 {k}")
            if "docId" not in g or "chunkIndex" not in g:
                continue
            did, cidx = int(g["docId"]), int(g["chunkIndex"])
            gold_total += 1
            gold_docs.add(did)
            if not has_db:
                continue
            if (did, cidx) not in anchors:
                errors.append(
                    f"[{rid}] gold 锚点失效：docId={did} chunkIndex={cidx} "
                    f"不是当前 ACTIVE chunk（文档 chunk 数="
                    f"{sum(1 for (d, _) in anchors if d == did)}）"
                )
                continue
            quote = (g.get("quote") or "").strip()
            if quote:
                quote_checked += 1
                if not quote_hit(quote, anchors[(did, cidx)]):
                    errors.append(f"[{rid}] gold.quote 未真实出现在 docId={did} chunkIndex={cidx}")

        # --- 分类专属规则 ---
        if cat == "unanswerable":
            if refusal is not True:
                errors.append(f"[{rid}] C 类 expectRefusal 必须为 true")
            if gold:
                errors.append(f"[{rid}] C 类不应有 gold（应有 0 命中），当前 {len(gold)} 条")
            if not (meta.get("outOfScopeReason") or "").strip():
                errors.append(f"[{rid}] C 类 meta.outOfScopeReason 不能为空")
        else:
            if refusal is not False:
                errors.append(f"[{rid}] {CAT_CN[cat]} expectRefusal 必须为 false")

        if cat == "permission":
            perm = r.get("permission")
            if not isinstance(perm, dict):
                errors.append(f"[{rid}] D 类 permission 必须为对象")
            else:
                for k in ("targetSpaceId", "memberUserIds", "nonMemberUserIds", "forbiddenDocIds"):
                    if not perm.get(k):
                        errors.append(f"[{rid}] D 类 permission.{k} 缺失或为空")
            if not (meta.get("mirrorOf") or "").strip():
                errors.append(f"[{rid}] D 类 meta.mirrorOf 缺失（需指向被镜像的 A/B 题 id）")
            elif meta["mirrorOf"] not in seen_ids and meta["mirrorOf"] not in {x.get("id") for x in rows}:
                errors.append(f"[{rid}] D 类 mirrorOf={meta['mirrorOf']} 在数据集中不存在")
        elif cat in GOLD_REQUIRED_CATS:
            if not gold:
                errors.append(f"[{rid}] {CAT_CN[cat]} gold 不能为空")

        # --- reviewedBy：v2 新增字段，缺失只提示（v1 数据集没有该字段）---
        for k in RECOMMENDED_META:
            if k not in meta:
                warns.append(f"[{rid}] meta 建议补充 {k}（区分人工复核 / 自动生成）")

        # --- golden 模式：复核必须已定稿 ---
        # v1 题由人工复核（reviewState ∈ kept/edited）；v2 新题由自动闸门产出
        # （reviewState=auto + reviewedBy=auto）。两者都可定稿，但来源必须能区分。
        if strict_golden:
            st = meta.get("reviewState")
            by = meta.get("reviewedBy")
            if by == "auto":
                if st != "auto":
                    errors.append(f"[{rid}] reviewedBy=auto 的题 meta.reviewState 应为 'auto'，当前 {st!r}")
            elif st not in ("kept", "edited"):
                errors.append(f"[{rid}] golden 数据集要求 meta.reviewState ∈ {kept_edited()}"
                              f"（自动生成题除外，须标 reviewedBy='auto'），当前 {st!r}")

        # --- 跨辖区提示（非错误）---
        info = docinfo.get(int(gold[0]["docId"])) if gold and has_db else None
        if info and any(k in info["title"] for k in CROSS_REGION_KEYS):
            warns.append(f"[{rid}] 证据来自跨辖区文档《{info['title']}》（川渝通办合法，仅提示确认）")

    # --- 整体配比 ---
    total = len(rows)
    # 基线五类必须齐全；expand-rag-eval-coverage 新增的两类缺失只作提示
    # （单独校验 golden.v1.jsonl 时 crossdoc / single 必然为空，属预期）
    baseline_cats = ("pair", "docnum", "unanswerable", "permission", "synthetic")
    missing_cats = [c for c in baseline_cats if cat_count[c] == 0]
    if missing_cats:
        errors.append(f"[整体] 基线五类不齐，缺少：{', '.join(CAT_CN[c] for c in missing_cats)}")
    new_missing = [c for c in ("crossdoc", "single") if cat_count[c] == 0]
    if new_missing:
        warns.append(f"[整体] 未包含新题型：{', '.join(CAT_CN[c] for c in new_missing)}"
                     f"（单独校验 v1 数据集时属预期）")
    if total < 50:
        warns.append(f"[整体] 题量仅 {total}，统计意义有限（目标约 90 题）")

    stats = {
        "total": total,
        "catCount": {CAT_CN.get(c, c): n for c, n in cat_count.items() if n},
        "targetMix": {CAT_CN[c]: TARGET_MIX[c] for c in TARGET_MIX},
        "goldTotal": gold_total,
        "goldDocCount": len(gold_docs),
        "quoteChecked": quote_checked,
        "hasDb": has_db,
    }
    return errors, warns, stats


MANIFEST_REQUIRED = [
    "schemaVersion", "generatedAt", "changeId", "branch",
    "dataset", "models", "corpus", "chunkSnapshot", "readOnly",
]


def validate_manifest(path: str) -> list[str]:
    """任务 5.3：校验 manifest.json 字段完整，使数据集可复现。"""
    errs = []
    try:
        m = json.load(open(path, encoding="utf-8"))
    except Exception as e:  # noqa: BLE001
        return [f"manifest.json 无法解析：{type(e).__name__}: {e}"]
    for k in MANIFEST_REQUIRED:
        if k not in m:
            errs.append(f"manifest.json 缺少字段 {k}")
    if not (m.get("corpus") or {}).get("aggregateSha256"):
        errs.append("manifest.json corpus.aggregateSha256 缺失（语料快照不可比对）")
    cs = m.get("chunkSnapshot") or {}
    if not cs.get("rows") or not cs.get("docCount"):
        errs.append("manifest.json chunkSnapshot.rows / docCount 缺失")
    if (m.get("readOnly") or {}).get("constraintHeld") is not True:
        errs.append("manifest.json readOnly.constraintHeld 不为 true（只读约束被破坏）")
    return errs


def kept_edited():
    return "{kept, edited}"


def pick_input() -> str:
    golden = os.path.join(EVAL_DIR, "golden.v1.jsonl")
    if os.path.exists(golden):
        return golden
    return os.path.join(EVAL_DIR, "candidates.jsonl")


def run_self_test(rows, anchors, docinfo, has_db):
    base = next((r for r in rows if r.get("gold")), None)
    if base is None:
        print("[自检] 数据集中没有带 gold 的题，无法构造坏数据")
        return 1
    bad = copy.deepcopy(base)
    bad["id"] = "SELFTEST-BAD"
    bad["gold"][0]["chunkIndex"] = 999999
    errs, _, _ = validate([bad], anchors, docinfo, strict_golden=False, has_db=has_db)
    hit = [e for e in errs if "SELFTEST-BAD" in e]
    print("[自检] 注入坏数据：gold.chunkIndex=999999（不存在的块）")
    if hit and (not has_db or "锚点失效" in hit[0]):
        print(f"[自检] 通过 —— 正确报错并指出题号：{hit[0]}")
        return 0
    print(f"[自检] 失败 —— 未捕获该坏数据。捕获到的错误：{errs}")
    return 1


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("path", nargs="?", default=None, help="待校验 JSONL，默认自动选取")
    ap.add_argument("--self-test", action="store_true", help="注入坏数据验证校验器有效")
    ap.add_argument("--no-db", action="store_true", help="跳过数据库锚点校验")
    ap.add_argument("--report", default=None, help="把结果摘要写入 JSON 文件")
    args = ap.parse_args()

    path = args.path or pick_input()
    if not os.path.exists(path):
        raise SystemExit(f"找不到待校验文件：{path}")
    rows = load_rows(path)
    print(f"待校验：{path}（{len(rows)} 题）")

    cfg = load_env()
    anchors, docinfo, has_db = {}, {}, False
    if args.no_db:
        print("已指定 --no-db：跳过 gold 锚点解析与 quote 校验")
    else:
        try:
            anchors, docinfo = fetch_anchor_index(cfg)
            has_db = True
            print(f"已载入 ACTIVE chunk 锚点 {len(anchors)} 个 / 文档 {len(docinfo)} 篇")
        except Exception as e:  # noqa: BLE001
            print(f"[警告] 数据库连接失败，跳过锚点校验：{type(e).__name__}: {e}")

    if args.self_test:
        return run_self_test(rows, anchors, docinfo, has_db)

    strict_golden = os.path.basename(path).startswith("golden")
    errors, warns, stats = validate(rows, anchors, docinfo, strict_golden, has_db)

    mpath = os.path.join(EVAL_DIR, "manifest.json")
    if os.path.exists(mpath):
        errors += validate_manifest(mpath)
        print(f"已校验 {mpath} 字段完整性")
    else:
        warns.append("[整体] eval/manifest.json 不存在，数据集缺少可复现快照")

    print("\n" + "=" * 60)
    print("摘要")
    print("=" * 60)
    print(f"题量        {stats['total']}")
    print(f"分类配比    " + " | ".join(f"{k} {v}" for k, v in stats["catCount"].items()))
    print(f"目标配比    " + " | ".join(f"{k} {v}" for k, v in stats["targetMix"].items()))
    print(f"gold 段落   {stats['goldTotal']}（覆盖 {stats['goldDocCount']} 篇文档）")
    print(f"quote 校验  {stats['quoteChecked']} 条")

    if warns:
        print(f"\n警告 {len(warns)} 条：")
        for w in warns[:20]:
            print("  " + w)
    if errors:
        print(f"\n错误 {len(errors)} 条：")
        for e in errors[:50]:
            print("  " + e)
        if len(errors) > 50:
            print(f"  ... 另有 {len(errors) - 50} 条")
        print("\n[FAIL] 校验未通过")
        code = 1
    else:
        print("\n[PASS] 校验通过（无错误）")
        code = 0

    if args.report:
        with open(args.report, "w", encoding="utf-8") as f:
            json.dump({"path": path, "stats": stats, "errors": errors, "warns": warns},
                      f, ensure_ascii=False, indent=2)
        print(f"摘要已写入 {args.report}")
    return code


if __name__ == "__main__":
    sys.exit(main())

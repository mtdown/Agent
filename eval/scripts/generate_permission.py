# -*- coding: utf-8 -*-
"""D 类权限题：验证跨空间泄漏。从已有 A/B 类题中抽取，构成「成员 vs 非成员」对照实验。

评测语义：
  - 用 **非成员账号** 检索 -> 必须 0 命中（命中即判定为权限泄漏，一票否决）
  - 用 **成员账号** 检索   -> 应当命中（对照组，证明题目本身有效，排除"题目太难"的干扰）

当前库状态：政策文档空间 2095544464810774531 成员 2 人，非成员账号 10 个。
"""
from __future__ import annotations

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib_eval import EVAL_DIR, db_conn, load_env  # noqa: E402

TARGET_SPACE = 2095544464810774531


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--count", type=int, default=10)
    ap.add_argument("--out", default=os.path.join(EVAL_DIR, "candidates-D.jsonl"))
    args = ap.parse_args()

    cfg = load_env()
    conn = db_conn(cfg)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT spaceId, userId, spaceRole FROM wiki_space_user WHERE spaceId=%s AND isDelete=0",
                        (TARGET_SPACE,))
            members = [{"userId": int(r[1]), "role": r[2]} for r in cur.fetchall()]
            member_ids = {m["userId"] for m in members}
            cur.execute("SELECT id, userAccount FROM user WHERE isDelete=0")
            users = cur.fetchall()
    finally:
        conn.close()
    non_members = [{"userId": int(u[0]), "account": u[1]} for u in users if int(u[0]) not in member_ids]
    print(f"目标空间 {TARGET_SPACE}：成员 {len(members)} 人，非成员 {len(non_members)} 人")

    src = []
    for name in ("candidates-A.jsonl", "candidates-B.jsonl"):
        p = os.path.join(EVAL_DIR, name)
        if os.path.exists(p):
            src += [json.loads(l) for l in open(p, encoding="utf-8")]

    # 每个政策只取 1 题，保证覆盖不同文档
    seen, picked = set(), []
    for r in src:
        did = r["source"]["policyDocId"]
        if did in seen:
            continue
        seen.add(did)
        picked.append(r)
        if len(picked) >= args.count:
            break

    rows = []
    for i, r in enumerate(picked, 1):
        perm = {
            "targetSpaceId": TARGET_SPACE,
            "memberUserIds": [m["userId"] for m in members],
            "nonMemberUserIds": [u["userId"] for u in non_members[:5]],
            "forbiddenDocIds": [r["source"]["policyDocId"]],
        }
        d = json.loads(json.dumps(r, ensure_ascii=False))
        d["id"] = f"D-{i:02d}"
        d["category"] = "permission"
        d["gold"] = []          # 非成员视角下不应命中任何 chunk
        d["permission"] = perm
        d["meta"]["qType"] = "permission"
        d["meta"]["mirrorOf"] = r["id"]      # 对应的原始题（成员对照组）
        d["meta"]["expectForNonMember"] = "zero_hit"
        d["meta"]["expectForMember"] = "hit"
        d["meta"]["reviewState"] = "pending"
        rows.append(d)

    with open(args.out, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"产出 {len(rows)} 题 -> {args.out}")
    for r in rows:
        print(f"  {r['id']} (镜像 {r['meta']['mirrorOf']}): {r['question'][:44]}")


if __name__ == "__main__":
    main()

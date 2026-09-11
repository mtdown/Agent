"""制备评测用 API Key（成员 / 非成员各一个）。

为什么需要这个脚本：
  D 类权限题要做「成员可见 vs 非成员 0 命中」对照，必须有两个不同 owner 的 key。
  后端创建 key 需要登录态（/rag/key/create），而 rag_api_key 表以 SHA-256 存 key，
  可以在库内直接制备：明文 cpk_ + 32 字节 hex，keyHash = sha256(明文),
  keyPrefix = 明文前 8 位 —— 与 RagApiKeyServiceImpl.create() 完全一致。

安全约束：
  明文只打印到标准输出并写入 eval/.env（已 gitignore），绝不进仓、不写日志、不进结果 JSON。
"""

import argparse
import hashlib
import os
import random
import re
import secrets
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib_eval import db_conn, load_env  # noqa: E402

SPACE_ID = 2095544464810774531
KEY_PREFIX_LEN = 8  # "cpk_" + 4


def make_plaintext() -> str:
    """与 Java 侧一致：cpk_ + 32 随机字节的十六进制，共 68 字符。"""
    return "cpk_" + secrets.token_hex(32)


def sha256_hex(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def snowflake_id() -> int:
    """ASSIGN_ID 风格的 19 位 ID（表未设自增，必须显式赋值）。"""
    ts = int(time.time() * 1000) - 1288834974657
    return (ts << 22) | (1 << 12) | random.randint(0, 4095)


def upsert_env(path: str, pairs: dict[str, str]) -> None:
    """把 key 写进 eval/.env（已 gitignore），已存在则替换。"""
    lines = []
    if os.path.exists(path):
        lines = open(path, encoding="utf-8").read().splitlines()
    for k, v in pairs.items():
        pat = re.compile(rf"^{re.escape(k)}=")
        hit = False
        for i, line in enumerate(lines):
            if pat.match(line):
                lines[i] = f"{k}={v}"
                hit = True
                break
        if not hit:
            lines.append(f"{k}={v}")
    open(path, "w", encoding="utf-8").write("\n".join(lines) + "\n")


def resolve_user(cur, user_ref: str) -> tuple[int, str]:
    """按 userAccount 或 userId 定位用户。"""
    try:
        uid = int(user_ref)
        cur.execute("SELECT id, userAccount FROM user WHERE id=%s", (uid,))
    except ValueError:
        cur.execute("SELECT id, userAccount FROM user WHERE userAccount=%s", (user_ref,))
    row = cur.fetchone()
    if not row:
        raise SystemExit(f"[ERROR] 找不到用户：{user_ref}")
    return int(row[0]), row[1] or ""


def main():
    ap = argparse.ArgumentParser(description="制备评测用 API Key")
    ap.add_argument("--member", default="admin", help="成员账号（userAccount 或 userId），默认 admin")
    ap.add_argument("--non-member", default="viewer_user", help="非成员账号，默认 viewer_user")
    ap.add_argument("--env-file", default=None, help="写入的 .env 路径，默认 eval/.env")
    ap.add_argument("--dry-run", action="store_true", help="只打印不写库")
    args = ap.parse_args()

    cfg = load_env()
    env_path = args.env_file or os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".env"
    )

    conn = db_conn(cfg)
    try:
        with conn.cursor() as cur:
            m_uid, m_acc = resolve_user(cur, args.member)
            n_uid, n_acc = resolve_user(cur, args.non_member)

            # 校验成员/非成员身份，避免配反导致 D 类结论完全错误
            cur.execute(
                "SELECT COUNT(*) FROM wiki_space_user WHERE spaceId=%s AND userId=%s AND isDelete=0",
                (SPACE_ID, m_uid),
            )
            if cur.fetchone()[0] == 0:
                raise SystemExit(f"[ERROR] {m_acc} 不是空间 {SPACE_ID} 的成员，不能用作成员 key")
            cur.execute(
                "SELECT COUNT(*) FROM wiki_space_user WHERE spaceId=%s AND userId=%s AND isDelete=0",
                (SPACE_ID, n_uid),
            )
            if cur.fetchone()[0] != 0:
                raise SystemExit(f"[ERROR] {n_acc} 是空间 {SPACE_ID} 的成员，不能用作非成员 key")

            created = {}
            for role, uid, acc in (("MEMBER", m_uid, m_acc), ("NONMEMBER", n_uid, n_acc)):
                plaintext = make_plaintext()
                row = {
                    "id": snowflake_id(),
                    "userId": uid,
                    "keyName": "评测数据集专用（勿删）" if role == "MEMBER" else "评测-非成员对照（勿删）",
                    "keyHash": sha256_hex(plaintext),
                    "keyPrefix": plaintext[:KEY_PREFIX_LEN],
                }
                if not args.dry_run:
                    cur.execute(
                        "INSERT INTO rag_api_key (id, userId, keyName, keyHash, keyPrefix, "
                        "createTime, updateTime, isDelete) VALUES (%s, %s, %s, %s, %s, NOW(), NOW(), 0)",
                        (row["id"], row["userId"], row["keyName"], row["keyHash"], row["keyPrefix"]),
                    )
                created[role] = (plaintext, row)
                print(f"[{role}] owner={acc} (id={uid})  prefix={row['keyPrefix']}  id={row['id']}")
        if not args.dry_run:
            conn.commit()
    finally:
        conn.close()

    pairs = {
        "EVAL_MEMBER_API_KEY": created["MEMBER"][0],
        "EVAL_NONMEMBER_API_KEY": created["NONMEMBER"][0],
    }
    if not args.dry_run:
        upsert_env(env_path, pairs)
        print(f"\n明文已写入 {env_path}（该文件已被 gitignore）")
    else:
        print("\n[dry-run] 未写库、未写 .env")
        for k, v in pairs.items():
            print(f"  {k}={v}")

    print("\n校验方式：后端启动后执行")
    print('  python eval/scripts/run_eval.py --probe --retriever http')


if __name__ == "__main__":
    main()

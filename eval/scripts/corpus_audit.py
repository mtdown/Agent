# -*- coding: utf-8 -*-
"""
语料只读体检 —— add-rag-eval-dataset / Task 1.2

输入：F:\\AIProject\\my\\corpus-2026（只读）
输出：eval/audit/corpus-audit.md

统计维度：
  1. 各栏目篇数与正文长度分布
  2. 空壳率（剥离 front-matter 后正文 < 200 字）
  3. 表格保留率 / 疑似表格拍平率
  4. 文号覆盖率（按栏目）
  5. 跨辖区噪声（文号机关名不含「渝」）
  6. 内容重复（正文 hash 去重）
"""
import glob
import json
import os
import re
import sys
import hashlib
import statistics
from collections import Counter, defaultdict

sys.stdout.reconfigure(encoding="utf-8")

CORPUS = r"F:\AIProject\my\corpus-2026"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "audit", "corpus-audit.md")

SECTIONS = [
    ("01-政策文件-szfwj", "政策文件"),
    ("02-部门解读-bmjd", "部门解读"),
    ("03-新闻发布会-jdfb", "新闻发布会"),
    ("04-媒体视角-mtsj", "媒体视角"),
]

DOCNUM_RE = re.compile(r"[\u4e00-\u9fa5]{2,12}〔\d{4}〕\d+号")
FRONT_MATTER_RE = re.compile(r"^---\r?\n.*?\r?\n---\r?\n", re.S)
MD_TABLE_ROW_RE = re.compile(r"^\s*\|.*\|\s*$")
# 疑似被拍平的表格行：短行、含 2 个以上分隔符（全角空格 / 制表符 / 2+ 半角空格）
FLAT_ROW_RE = re.compile(r"^[\u4e00-\u9fa5A-Za-z0-9（）()．.、，,／/％%\-—\d]{1,40}(?:[ \t　]{1,}[\u4e00-\u9fa5A-Za-z0-9（）()．.、，,／/％%\-—\d]{1,40}){1,}$")
# 正文提及表格/附表 —— 用于判断「表格可能在转换中丢失」
TABLE_HINT_RE = re.compile(r"下表|附表|一览表|清单表|见附件|标准如下|如下[:：]\s*$", re.M)


def strip_front_matter(text: str) -> str:
    m = FRONT_MATTER_RE.match(text)
    return text[m.end():] if m else text


def body_chars(text: str) -> int:
    return len(re.sub(r"\s+", "", text))


def longest_flat_run(lines):
    """最长连续疑似拍平表格行数"""
    best = run = 0
    for ln in lines:
        s = ln.strip()
        if s and len(s) <= 60 and FLAT_ROW_RE.match(s) and not s.startswith("#"):
            run += 1
            best = max(best, run)
        else:
            run = 0
    return best


def audit():
    docs = []
    for dirname, label in SECTIONS:
        base = os.path.join(CORPUS, dirname)
        for meta_path in sorted(glob.glob(os.path.join(base, "*", "meta.json"))):
            folder = os.path.basename(os.path.dirname(meta_path))
            try:
                meta = json.load(open(meta_path, encoding="utf-8"))
            except Exception as e:
                docs.append({"section": label, "folder": folder, "error": f"meta.json 读取失败: {e}"})
                continue
            content_path = os.path.join(os.path.dirname(meta_path), "content.md")
            raw = ""
            if os.path.exists(content_path):
                raw = open(content_path, encoding="utf-8", errors="replace").read()
            body = strip_front_matter(raw)
            lines = body.splitlines()
            nums = DOCNUM_RE.findall((meta.get("fileNum") or "") + " " + (meta.get("title") or ""))
            docs.append({
                "section": label,
                "folder": folder,
                "metadataId": meta.get("metadataId"),
                "title": meta.get("title") or "",
                "fileNum": meta.get("fileNum") or "",
                "pubDate": meta.get("pubDate") or "",
                "sourceUrl": meta.get("sourceUrl") or "",
                "chars": body_chars(body),
                "rawChars": len(raw),
                "hasMdTable": any(MD_TABLE_ROW_RE.match(l) for l in lines),
                "flatRun": longest_flat_run(lines),
                "tableHint": bool(TABLE_HINT_RE.search(body)),
                "docNums": nums,
                "hash": hashlib.md5(re.sub(r"\s+", "", body).encode("utf-8")).hexdigest(),
            })
    return docs


def pct(n, d):
    return f"{n / d * 100:.1f}%" if d else "0%"


def fmt(nums):
    if not nums:
        return "-"
    nums = sorted(nums)
    return f"min {nums[0]} / 中位 {int(statistics.median(nums))} / max {nums[-1]}"


def main():
    docs = audit()
    if not docs:
        print("ERROR: 未读到任何语料，请检查 CORPUS 路径")
        sys.exit(1)

    by_sec = defaultdict(list)
    for d in docs:
        by_sec[d["section"]].append(d)

    lines = []
    w = lines.append
    w("# 语料体检报告（corpus-2026）\n")
    w(f"- 语料路径：`{CORPUS}`（只读，未做任何修改）")
    w(f"- 总篇数：**{len(docs)}**")
    w("- 空壳判定：剥离 YAML front-matter 后，正文非空字符数 < 200")
    w("- 表格拍平判定：存在连续 ≥3 行的短分隔行（疑似表格被拉平成逐行文本）\n")

    w("## 1. 各栏目篇数与正文长度\n")
    w("| 栏目 | 篇数 | 正文字符数(min/中位/max) | 空壳(<200字) | 空壳率 |")
    w("|---|---|---|---|---|")
    total_short = 0
    for _, label in SECTIONS:
        ds = by_sec.get(label, [])
        if not ds:
            continue
        short = [d for d in ds if d["chars"] < 200]
        total_short += len(short)
        w(f"| {label} | {len(ds)} | {fmt([d['chars'] for d in ds])} | {len(short)} | {pct(len(short), len(ds))} |")
    w(f"| **合计** | **{len(docs)}** | {fmt([d['chars'] for d in docs])} | **{total_short}** | **{pct(total_short, len(docs))}** |")

    # 空壳明细
    shorts = [d for d in docs if d["chars"] < 200]
    w("\n### 1.1 空壳样本（全部列出）\n")
    if shorts:
        w("| 栏目 | 篇目录名 | 标题 | 正文字符数 |")
        w("|---|---|---|---|")
        for d in sorted(shorts, key=lambda x: x["chars"])[:40]:
            w(f"| {d['section']} | `{d['folder']}` | {d['title'][:50]} | {d['chars']} |")
    else:
        w("无空壳样本。")

    # 表格
    w("\n## 2. 表格保留与拍平情况\n")
    w("| 栏目 | 含 Markdown 表格 | 疑似拍平(≥3连续分隔行) | 提及表格/附表 | 表格丢失率* |")
    w("|---|---|---|---|---|")
    total_flat = 0
    total_hint = 0
    for _, label in SECTIONS:
        ds = by_sec.get(label, [])
        if not ds:
            continue
        tbl = sum(1 for d in ds if d["hasMdTable"])
        flat = [d for d in ds if d["flatRun"] >= 3]
        hint = sum(1 for d in ds if d["tableHint"])
        total_flat += len(flat)
        total_hint += hint
        lost = sum(1 for d in ds if d["tableHint"] and not d["hasMdTable"])
        w(f"| {label} | {tbl} | {len(flat)} | {hint} | {pct(lost, len(ds))} |")
    total_lost = sum(1 for d in docs if d["tableHint"] and not d["hasMdTable"])
    w(f"| **合计** | {sum(1 for d in docs if d['hasMdTable'])} | **{total_flat}** | **{total_hint}** | **{pct(total_lost, len(docs))}** |")
    w("\n\\* 表格丢失率 = 正文提及「下表/附表/一览表」但转换后不存在 Markdown 表格的篇数占比。")
    w("拍平检测为启发式，可能误报；结论：**表格类题目本 change 不出**（见 design D8）。\n")
    hint_docs = [d for d in docs if d["tableHint"] and not d["hasMdTable"]]
    if hint_docs:
        w("### 2.1 表格可能丢失的样本（全部列出）\n")
        w("| 栏目 | 篇目录名 | 标题 |")
        w("|---|---|---|")
        for d in hint_docs[:40]:
            w(f"| {d['section']} | `{d['folder']}` | {d['title'][:50]} |")

    # 文号
    w("\n## 3. 文号覆盖率\n")
    w("| 栏目 | 篇数 | 有文号 | 覆盖率 |")
    w("|---|---|---|---|")
    for _, label in SECTIONS:
        ds = by_sec.get(label, [])
        if not ds:
            continue
        has = sum(1 for d in ds if d["fileNum"])
        w(f"| {label} | {len(ds)} | {has} | {pct(has, len(ds))} |")
    total_num = sum(1 for d in docs if d["fileNum"])
    w(f"| **合计** | **{len(docs)}** | **{total_num}** | **{pct(total_num, len(docs))}** |")

    # 跨辖区噪声
    w("\n## 4. 跨辖区噪声\n")
    noise = []
    for d in docs:
        if not d["fileNum"]:
            continue
        if not re.search(r"渝|重庆", d["fileNum"]):
            noise.append(d)
    joint = [d for d in noise if re.search(r"重庆|渝", d["title"])]
    pure = [d for d in noise if d not in joint]
    if joint:
        w(f"### 4.1 跨省联合发文（合法，可保留出题）— {len(joint)} 篇\n")
        w("文号不含「渝」但标题含「重庆」，属川渝等跨省联合发文：\n")
        w("| 栏目 | 篇目录名 | 标题 | 文号 |")
        w("|---|---|---|---|")
        for d in joint[:40]:
            w(f"| {d['section']} | `{d['folder']}` | {d['title'][:60]} | {d['fileNum']} |")
    if pure:
        w(f"\n### 4.2 疑似采集噪声（出题时须排除）— {len(pure)} 篇\n")
        w("| 栏目 | 篇目录名 | 标题 | 文号 |")
        w("|---|---|---|---|")
        for d in pure[:40]:
            w(f"| {d['section']} | `{d['folder']}` | {d['title'][:45]} | {d['fileNum']} |")
    if not noise:
        w("未发现跨辖区噪声。")

    # 重复
    w("\n## 5. 内容重复\n")
    hashes = Counter(d["hash"] for d in docs)
    dup = {h: c for h, c in hashes.items() if c > 1}
    if dup:
        w(f"存在 **{len(dup)}** 组重复正文，涉及 {sum(dup.values())} 篇：\n")
        for h, c in dup.items():
            same = [d for d in docs if d["hash"] == h]
            names = ", ".join(f"`{d['folder']}`" for d in same)
            w(f"- {c} 篇相同：{names} — 标题示例：{same[0]['title'][:40]}")
    else:
        w("未发现完全重复的正文。")

    # 结论
    w("\n## 6. 体检结论（对出题的影响）\n")
    w(f"1. 空壳 {total_short} 篇（{pct(total_short, len(docs))}）：空壳篇不得作为 ground truth 证据来源；若为政策文件，则对应文号题需剔除。")
    w(f"2. 疑似表格拍平 {total_flat} 篇：本 change 不出表格类题目，避免标准答案不可靠。")
    w(f"3. 文号覆盖 {total_num}/{len(docs)}：B 类文号题只能从这 {total_num} 篇中抽取。")
    w(f"4. 跨省联合发文 {len(joint)} 篇（川渝联合，**合法可保留**）；疑似采集噪声 {len(pure)} 篇（出题时须排除）。")
    w(f"5. 内容重复 {len(dup)} 组：重复篇只保留一篇用于出题，其余作干扰项。")

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print(f"OK -> {OUT}")
    print(f"总篇数 {len(docs)} | 空壳 {total_short} | 疑似拍平 {total_flat} | 有文号 {total_num} | 跨辖区 {len(noise)} | 重复组 {len(dup)}")


if __name__ == "__main__":
    main()

# RAG 评测数据集（eval/）

为 `add-rag-eval-dataset` 变更服务。目标：产出一份可复现、可对外引用的政务 RAG 评测集，
作为切块策略、检索参数、embedding 模型切换的量化基线。

## 核心原则

1. **gold 用逻辑坐标，不用数据库主键。** `gold[].docId + chunkIndex`，禁止写 `chunkId`。
   原因：`WikiRagIndexServiceImpl.doIndexDocument` 重建时是「旧行置 INVALID + 插入新行」，
   每次重建后同一逻辑块会拿到全新主键，绑死的 id 会全部失效。
2. **标准答案始终来自人类文本。** A 类题的 LLM 只负责把官方解读"翻译"成问题，
   答案取自解读原文；证据则绑回政策原文，并强制通过摘抄校验。
3. **全程只读。** 本目录下所有脚本不写业务库、不改后端、不触发索引重建。
   执行前后 `wiki_chunk` 应恒为 2061 行、全 ACTIVE。

## 目录

| 路径 | 说明 |
|---|---|
| `candidates.jsonl` | 五类候选题汇总（机器生成 + 自动校验后） |
| `golden.v1.jsonl` | 人工筛选后的正式数据集（由筛选页导出，尚未产出） |
| `manifest.json` | 数据集快照：语料 hash、chunk 统计、模型版本、题量配比 |
| `pairs.json` | 政策 ↔ 官方解读配对（31 组） |
| `pair-audit.md` / `corpus-audit.md` | 语料体检与配对审计 |
| `scripts/` | 构建脚本（见下） |
| `tools/review.html` | 人工筛选页（离线可用，进度存 localStorage） |
| `tmp/` | 中间产物与缓存，**已 gitignore** |
| `.env` | 密钥与模型配置，**已 gitignore** |

## 数据格式（JSONL，一行一题）

```json
{
  "id": "A-p-01-1",
  "category": "pair | docnum | unanswerable | permission | synthetic",
  "question": "重庆市地震应急预案里，地震灾害是怎么分级的？",
  "answer": "分为特别重大、重大、较大、一般四级。……",
  "expectRefusal": false,
  "gold": [{"docId": 2097637762580992002, "chunkIndex": 2,
            "why": "重庆市地震应急预案", "quote": "摘抄自该 chunk 的原句"}],
  "source": {"pairId": "p-01", "policyDocId": 123, "policyTitle": "...",
             "interpretDocId": 456, "interpretTitle": "...", "docNumber": "渝府办发〔2025〕66号"},
  "permission": null,
  "meta": {"qType": "overview|detail", "evidenceKey": "...", "goldVerified": true,
           "generatedBy": "qwen3.8-flash", "temperature": 0,
           "reviewState": "kept|edited|dropped", "reviewNote": ""}
}
```

- `gold[].quote` 是该 chunk 中的**原样摘抄**，由程序校验必须真实存在（防 gold 幻觉）。
- `permission` 仅 D 类非空：`{targetSpaceId, memberUserIds, nonMemberUserIds, forbiddenDocIds}`。
- `expectRefusal: true` 仅 C 类（库外问题，系统必须拒答）。
- D 类是 A/B 类的**镜像**（`meta.mirrorOf`），用于成员 vs 非成员对照，不进平均分，**泄漏即一票否决**。

## 五类题与配比

| 类 | 名称 | 数量 | 生成方式 | ground truth 可信度 |
|---|---|---|---|---|
| A | 配对题 | 54 | LLM 读官方解读生成问题 → 证据绑政策原文 → 摘抄校验 | 高（官方撰写） |
| B | 文号题 | 9 | 规则生成，专测文号精确匹配层 | 高（元数据确定） |
| C | 无答案题 | 15 | 人工列候选 + 自动校验实体确实不在库中 | 高 |
| D | 权限题 | 10 | A/B 镜像 + 权限标注，非成员必须 0 命中 | 高 |
| E | 合成补充 | 12 | LLM 从政策原文 chunk 生成，摘抄校验 | 中（需人工筛） |

## 脚本

```bash
# 0. 冒烟：验证 LLM / Embedding 可用（换 key 或模型后先跑）
python eval/scripts/smoke_test.py

# 1. 语料体检
python eval/scripts/corpus_audit.py

# 2. 锚点映射：metadataId → docId → chunkIndex（216 篇必须全匹配，否则报错退出）
python eval/scripts/export_anchor_map.py

# 3. 配对锚定
python eval/scripts/build_pairs.py

# 4. 出题
python eval/scripts/generate_pair_questions.py --assume-confirmed   # A 类
python eval/scripts/generate_docnum_questions.py --count 10         # B 类
python eval/scripts/generate_unanswerable.py                        # C 类（自动校验库外）
python eval/scripts/generate_permission.py --count 10               # D 类
python eval/scripts/generate_synthetic.py --count 12                # E 类

# 5. 汇总（去重 + 锚点有效性校验）
python eval/scripts/merge_candidates.py

# 6. 生成筛选页
python eval/scripts/build_review_page.py
```

LLM 调用均设置 `enable_thinking=false`（实测 qwen3.8-flash：耗时 2.4s→0.7s、token 212→50，答案一致）。

## 已知问题

1. **chunk 0 是 front-matter**：入库时 `content.md` 的 YAML 头未被剥离，导致每篇第 0 块是
   `title / fileNum / pubDate / sourceUrl` 元数据。B 类题 gold 已跳过 chunk 0，
   避免文号题退化为字符串匹配。该问题属于采集/入库管线，本 change 不修。
2. **四川/成都在库中大量出现**（67 / 21 个 chunk，川渝通办联合发文），
   **不能**当作 C 类"库外实体"使用。
3. 所有 216 篇文档同处一个空间（`2095544464810774531`），D 类权限题依赖"非成员账号"，
   当前库有 10 个非成员账号可用。

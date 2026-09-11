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
| `golden.v1.jsonl` | **正式数据集 99 题**（人工逐题复核后由筛选页导出，`validate.py` 全绿） |
| `review-state.json` | 逐题筛选决策（st / note），可回灌 localStorage 复现筛选态 |
| `audit/human-review-report.md` | 人工评测报告：核验方法、与 AI 预评审的差异、100 题逐题处置表 |
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

# 6. AI 预筛（LLM-as-Judge，输出 ai-judge.jsonl + audit/ai-judge-report.md）
python eval/scripts/llm_judge.py            # 首次全量；加 --repeat 可跑第二遍算 Judge 一致率

# 7. 生成筛选页（自动载入 ai-judge.jsonl，页面上显示 AI 建议）
python eval/scripts/build_review_page.py

# 8. 重新生成页面后，双击 eval/tools/review.html 即可离线审阅

# 9. 【人工筛选后】把 golden.v1.jsonl 放到 eval/，再校验 + 生成快照
python eval/scripts/validate.py eval/golden.v1.jsonl    # golden 模式额外要求 reviewState ∈ {kept, edited}
python eval/scripts/gen_manifest.py eval/golden.v1.jsonl

# 校验器自检：注入不存在的 chunkIndex，验证能报错并指出题号
python eval/scripts/validate.py eval/candidates.jsonl --self-test
```

LLM 调用均设置 `enable_thinking=false`（实测 qwen3.8-flash：耗时 2.4s→0.7s、token 212→50，答案一致）。

## AI 预筛（LLM-as-Judge）

`llm_judge.py` 用 `qwen3.8-flash` 对候选题做质量评审，**只做预筛，不替代人**：
人工只需复核 AI 标记 edit/drop 的题，并对 keep 的题抽样复核。

三套判据：

| 题型 | 评审维度 |
|---|---|
| A/B/E（有证据） | answerability 可答性 · faithfulness 忠实性 · **sufficiency 标注完整性** · naturalness 问题自然度 |
| C（无答案） | plausibility 库外合理性 · naturalness · trap_risk 诱导硬答风险 |
| D（权限） | specificity 指向明确性 · naturalness · sensitivity 泄漏可判定性 |

**关键设计**：送审时把该政策的**全部** chunk 都给 Judge（gold 段给全文、其余给摘要），
并标出哪些是已标注证据。这样 Judge 才能区分两件不同的事：

- **答案本身编造**（faithfulness 低）→ 真问题，应丢弃
- **答案没错但 gold 漏标**（sufficiency 低）→ 可修复，Judge 直接指出该补哪些 chunkIndex

因此 Judge 会输出 `missingChunks` / `redundantChunks`，筛选页提供**一键采纳**把这些段落并入 gold。
首版判据只送 gold 片段时，drop 率高达 45%（大量假阳性）；改为全文+摘要判据后 drop 降到 16%，
edit 升至 47 且其中 39 题可通过补证据修复。

### 首轮结果（100 题）

| verdict | 题数 |
|---|---|
| keep | 37 |
| edit | 47（其中 42 题可补证据修复） |
| drop | 16 |

证据类四维均分：可答性 4.76 · 忠实性 4.29 · **标注完整性 3.32** · 自然度 4.63
—— 说明**答案质量没问题，短板集中在 gold 标注不全**，这正是补证据能解决的。

## 已知问题

1. **chunk 0 是 front-matter**：入库时 `content.md` 的 YAML 头未被剥离，导致每篇第 0 块是
   `title / fileNum / pubDate / sourceUrl` 元数据。B 类题 gold 已跳过 chunk 0，
   避免文号题退化为字符串匹配。该问题属于采集/入库管线，本 change 不修。
2. **四川/成都在库中大量出现**（67 / 21 个 chunk，川渝通办联合发文），
   **不能**当作 C 类"库外实体"使用。
3. 所有 216 篇文档同处一个空间（`2095544464810774531`），D 类权限题依赖"非成员账号"，
   当前库有 10 个非成员账号可用。
4. **C 类口径已定（2026-09-11 负责人决策）：保留 14 题、剔除 C-14**。
   负责人全库检索核验：婚姻登记/住房补贴/报销比例/食品经营许可/消防验收/学区划分等关键词
   在 216 篇语料中均为 **0 篇**，C-15「电动自行车」仅现于消防整治与摩托车产业语境、无上牌流程规定
   → **库外前提成立，判为有效拒答题**（推翻 Judge 的 9 条 plauzibility 质疑）。
   C-14「民营经济」在发布会、媒体视角、营商环境任务清单第 150 条均有提及 → **前提不成立，剔除**。
   暂不补「纯越界」对照组：near_miss 已足以诱导幻觉。
   D 类判定时注意：**非成员"零命中"必须用 `forbiddenDocIds` 文件级权限过滤判定，不能依赖答案内容**。
5. **Judge 自身未经一致率验证**：首轮未跑 `--repeat`。如需在报告中引用 Judge 结论的稳定性，
   应补跑一遍算一致率（预期成本与一轮相当，约 9 分钟）。

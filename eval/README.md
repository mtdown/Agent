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

## 端到端流程

评测从语料走到报告分四个阶段。**日常重跑只需第 3 阶段**；第 1、2 阶段仅在换语料或改题型时才需要整条重走。

```text
  1. 语料准备            2. 数据集构建              3. 运行评测           4. 产出报告
  -------------          -----------------          -------------         -------------
  政务文档入库           出题（5 类题型）           golden.v1.jsonl       逐题明细 JSON
       |                      |                         |                     |
       v                      v                         v                     v
  切片 + 向量化           摘抄校验 + 去重            检索 top10            results/*.json
  wiki_chunk              AI 预筛 + 人工复核         本地截断算 K          audit/baseline-report.md
  （216 篇 / 2061 块）    golden.v1.jsonl            recall / mrr / 泄漏
```

| 阶段 | 输入 | 关键命令 | 产出物 | 是否进版本库 |
|---|---|---|---|---|
| **1 语料准备** | 政务文档（当前 216 篇） | 站内导入接口；`corpus_audit.py` 体检 | 库内 `wiki_chunk`（2061 块 + 向量） | 否（在库） |
| **2 数据集构建** | 库内语料 + 政策↔解读配对 | `generate_*.py` → `merge_candidates.py` → `llm_judge.py` → `validate.py` → `gen_manifest.py` | `golden.v1.jsonl`（99 题） | 是 |
| **3 运行评测** | `golden.v1.jsonl` + 已启动的后端 | `run_eval.py`（`--probe` 预检） | `results/baseline-<ts>-<mode>.json` | 是（**仅保留当前基线**） |
| **4 产出报告** | 第 3 阶段的结果 JSON | 由 `run_eval.py` 自动生成 | `audit/baseline-report.md` | 是 |

各阶段的完整命令见下文「脚本」与「跑评测」两节；想改某一步时该动哪些文件，见「改动影响面」。

## 目录

| 路径 | 说明 |
|---|---|
| `candidates.jsonl` | 五类候选题汇总（机器生成 + 自动校验后） |
| `golden.v1.jsonl` | **正式数据集 99 题**（人工逐题复核后由筛选页导出，`validate.py` 全绿） |
| `review-state.json` | 逐题筛选决策（st / note），可回灌 localStorage 复现筛选态 |
| `audit/human-review-report.md` | 人工评测报告：核验方法、与 AI 预评审的差异、100 题逐题处置表 |
| `manifest.json` | 数据集快照：语料 hash、chunk 统计、模型版本、题量配比 |
| `pairs.json` | 政策 ↔ 官方解读配对（31 组） |
| `results/` | 评测结果 JSON，**只保留当前对外基线**（唯一一份，见「当前对外基线」） |
| `audit/` | 审计与报告：语料体检、配对审计、人工复核、AI 预筛报告、`baseline-report.md`（评测报告） |
| `scripts/` | 构建与评测脚本（见下） |
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

## 跑评测（change `add-rag-eval-runner`）

数据集是**考卷**，`run_eval.py` 是**阅卷器**。`Recall@K / MRR / 跨空间泄漏率` 是跑出来的分数，
不是数据集里的字段 —— 固定写死就失去了"换 embedding 前后对比"的意义。

```bash
# 0) 制备评测用 API Key（成员 + 非成员，用于 D 类权限对照）
python eval/scripts/prepare_keys.py          # 明文写入 eval/.env（gitignore）

# 1) 探测后端与 key（后端未启动会直接退出码 1，不会静默产出全 0 指标）
python eval/scripts/run_eval.py --probe

# 2) 跑基线 —— http 模式，测真实系统，对外引用的指标一律用它
python eval/scripts/run_eval.py

# 3) 离线模式 —— 不依赖后端，用于 embedding 模型选型对比
python eval/scripts/run_eval.py --retriever offline --offline-vector recompute
```

**一次检索取 top10，本地截断算 K ∈ {1,3,5,6,10}**，99 题只需 99 次请求（不是 5×99）。

指标口径：

| 指标 | 定义 | 备注 |
|---|---|---|
| `recall@K` | gold 坐标命中比例 | chunk 级，区分度最高，但对切分策略敏感 |
| `docRecall@K` | gold 涉及的 docId 被命中比例 | 文档级，**跨实验对比首选** |
| `mrr` | 1 / 首个 gold 命中的位次，未命中为 0 | 衡量"正确答案排第几" |
| `hitRate@K` | 前 K 是否命中至少一个 | 最宽松 |

- **C 类**（gold 为空）不进召回分母，只报非空返回率与 `top1Score` 分布，**不拍相似度阈值**
- **D 类**用成员/非成员双 key 对照，泄漏一票否决且**不进 overall 平均**；
  offline 模式无权限过滤，会标记 `notApplicable` 而不是产出假的 0 泄漏
- 两种 retriever 的分数**不可混合平均**，结果 JSON 记录了 `retriever` 与 `embeddingModel`

产物：`eval/results/baseline-<ts>-<mode>.json`（逐题明细）+ `eval/audit/baseline-report.md`
（单次快照，每次跑都覆盖，只反映最近一轮）。

### 当前对外基线

对外引用的指标一律取自下表这一份，`results/` 中不再保留其他结果。

| 项 | 值 |
|---|---|
| 文件 | `eval/results/baseline-20260912-151958-http.json` |
| 运行 ID | `baseline-20260912-151958-http` |
| 通道 | `http` → `POST http://localhost:8123/api/open/rag/search` |
| embedding | DashScope `qwen3.7-text-embedding-flash`，**1024 维** |
| 数据集 | `golden.v1.jsonl` · sha256 `7f37862f…` |
| 采集时间 | 2026-09-12 15:19:38 → 15:19:58 |

> **模型归属说明**：该结果 JSON 内 `config.embeddingModel` 为 `null`——`run_eval.py` 有意不读后端配置，
> 以免拿 offline 模式的模型名冒名顶替。上表的模型归属来自**人工核对**（非程序探测），依据为
> `openspec/changes/switch-embedding-dashscope/tasks.md` 的 5.3（`force=true` 全量重嵌入 216 篇、
> 核对库内维度 1024）与 5.4（随后以 http 模式跑出该结果），以及 `application.yml` 的
> `rag.embedding.model` 默认值。

**整体指标**（75 题计入召回）

| 指标 | 值 |
|---|---|
| recall@6 | 0.4338 |
| docRecall@6 | 0.8533 |
| docRecall@1 | 0.4667 |
| mrr | 0.3975 |
| hitRate@6 | 0.7200 |

**分类指标**

| 类别 | 题数 | recall@6 | docRecall@6 | mrr |
|---|---|---|---|---|
| A 配对 | 54 | 0.2992 | 0.7963 | 0.3360 |
| B 文号 | 9 | 0.6525 | 1.0000 | 0.6852 |
| E 合成 | 12 | 0.8750 | 1.0000 | 0.4583 |

**其他口径**

- C 类无答案 14 题：`nonEmptyReturnRate = 1.0`——**全部返回了内容**，说明拒答不能只靠检索层阈值
- D 类权限 10 题：`leakCount = 0`（非成员零泄漏），成员侧镜像 `recall@6 = 0.3331`，未因权限过滤误伤

两条最该记住的信号：`docRecall@6 = 0.8533` 而 `recall@6 = 0.4338` → **找得到文件、定不准段落**，
对症手段是精排而非换模型；B 类 `docRecall@6 = 1.0` → 文号精确匹配层确实生效。

## 改动影响面

想动评测链路的某一环时，先查这张表：它给出该改哪些文件、是否必须重建索引、是否必须重新对齐 gold。

| 想改什么 | 涉及文件 | 重建索引 | 重对齐 gold | 说明 |
|---|---|---|---|---|
| **切块参数**（大小/重叠） | `cloud/src/main/java/com/et/cloud/rag/MarkdownChunker.java` 的 `MAX_CHUNK_LENGTH` / `MIN_CHUNK_LENGTH` / `OVERLAP_LENGTH` | ✅ 必须（全量） | ⚠️ 视情况 | 三个值是 `static final` **硬编码**，改完需重编译 + 全量重建；`chunkIndex` 会变，而 `gold[].quote` 是唯一能跨切块方案存活的锚点（当前覆盖率仅 37%） |
| **embedding 模型** | `cloud/src/main/resources/application.yml` 的 `rag.embedding.model`；`RagProperties.Embedding` | ✅ 必须（`POST /admin/rag/rebuild?force=true`） | ❌ | 换模型必须全量回填，否则维度与语义都不匹配。`rebuildAll` 默认按 `isUpToDate` 跳过，**不加 `force` 不会重算** |
| **题型配比 / 出题口径** | `eval/scripts/generate_*.py`、`merge_candidates.py`、`llm_judge.py`、`validate.py`、`gen_manifest.py` | ❌ | ❌ | 走「出题 → 汇总 → 预筛 → 人工复核 → 校验 → 快照」，冻结 `golden.v1.jsonl` 前必须过摘抄校验 |
| **指标口径** | `eval/scripts/run_eval.py`（指标计算段与 `METRIC_KEYS`） | ❌ | ❌ | 改口径会让**所有历史结果失去可比性**，须同步重跑并替换基线 |
| **检索参数** | `eval/scripts/run_eval.py` 的 `FETCH_K` | ❌ | ❌ | 对外口径固定「一次取 top10、本地截断算 K ∈ {1,3,5,6,10}」；改动同样影响可比性 |
| **语料来源 / 规模** | 站内导入接口、库内 `wiki_chunk` | ✅ 增量或全量 | ⚠️ 新语料需重新出题 | 建议用**独立空间**做跨库对照；混入既有空间会破坏空间级对照 |
| **检索链路本身** | `cloud/src/main/java/com/et/cloud/rag/RagSearchServiceImpl.java` | ❌ | ❌ | 改链路后必须重跑评测并**替换基线**，同时更新本文件的「当前对外基线」 |

**维护检查项**——改动收尾时确认三件事：

1. `results/` 仍只有唯一基线，旧结果已移除
2. 本文件的「当前对外基线」指标表已随重跑更新
3. `openspec validate <change-id> --strict` 通过

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

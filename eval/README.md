# RAG 评测数据集（eval/）

为 `add-rag-eval-dataset`、`document-rag-eval-flow`、`expand-rag-eval-coverage` 三个变更服务。
目标：产出一份可复现、可对外引用的政务 RAG 评测集，作为切块策略、检索参数、embedding 模型切换的量化基线。

> **只想要结论 / 对外引用？** 读 [`EVAL-REFERENCE.md`](./EVAL-REFERENCE.md)。
> 该文件一页给出三件事：**题目来源与分类**、**指标口径与当前得分**、**下一步计划**，并写明引用规范。
> 本 README 面向**操作**——怎么跑、改什么会影响哪里、复现步骤。

## 核心原则

1. **gold 用逻辑坐标，不用数据库主键。** `gold[].docId + chunkIndex`，禁止写 `chunkId`。
   原因：`WikiRagIndexServiceImpl.doIndexDocument` 重建时是「旧行置 INVALID + 插入新行」，
   每次重建后同一逻辑块会拿到全新主键，绑死的 id 会全部失效。
2. **标准答案始终来自人类文本。** A 类题的 LLM 只负责把官方解读"翻译"成问题，
   答案取自解读原文；证据则绑回政策原文，并强制通过摘抄校验。
3. **gold 摘抄必须逐字可验。** 所有新题的 `gold[].quote` 都由程序校验必须真实存在于该 chunk
   （`quote_hit`），这是防 gold 幻觉的唯一自动防线。
4. **全程只读。** 本目录下所有脚本不写业务库、不改后端、不触发索引重建。
   执行前后 `wiki_chunk` 应恒为 2061 行、全 ACTIVE。

## 端到端流程

评测从语料走到报告分四个阶段。**日常重跑只需第 3 阶段**；第 1、2 阶段仅在换语料或改题型时才需要整条重走。

```text
  1. 语料准备            2. 数据集构建              3. 运行评测           4. 产出报告
  -------------          -----------------          -------------         -------------
  政务文档入库           出题（7 类题型）           golden.v2.jsonl       逐题明细 JSON
       |                      |                         |                     |
       v                      v                         v                     v
  切片 + 向量化           摘抄校验 + 五道闸门        检索 top10            results/*.json
  wiki_chunk              AI 预筛 + 人工复核         本地截断算 K          audit/baseline-report.md
  （216 篇 / 2061 块）    golden.v2.jsonl            recall / mrr / 泄漏   audit/baseline-compare.md
```

| 阶段 | 输入 | 关键命令 | 产出物 | 是否进版本库 |
|---|---|---|---|---|
| **1 语料准备** | 政务文档（当前 216 篇） | 站内导入接口；`corpus_audit.py` 体检 | 库内 `wiki_chunk`（2061 块 + 向量） | 否（在库） |
| **2 数据集构建** | 库内语料 + 政策↔解读配对 | `generate_*.py` → `merge_candidates.py` / `merge_golden_v2.py` → `llm_judge.py` → `validate.py` → `gen_manifest.py` | `golden.v2.jsonl`（423 题） | 是 |
| **3 运行评测** | `golden.v2.jsonl` + 已启动的后端 | `run_eval.py`（`--probe` 预检） | `results/baseline-<ts>-<mode>.json` | 是 |
| **4 产出报告** | 第 3 阶段的结果 JSON | `run_eval.py` 自动生成；`compare_baselines.py` 做跨版本对比 | `audit/baseline-report.md`、`audit/baseline-compare.md` | 是 |

各阶段的完整命令见下文「脚本」与「跑评测」两节；想改某一步时该动哪些文件，见「改动影响面」。

## 目录

| 路径 | 说明 |
|---|---|
| `EVAL-REFERENCE.md` | **评测参考文件**——对外引用入口：题目来源与分类、指标含义与当前得分、下一步计划 |
| `golden.v2.jsonl` | **当前正式数据集 423 题**（v1 的 99 题 + 扩题 324 题，`validate.py` 全绿） |
| `golden.v1.jsonl` | 上一版数据集 99 题（人工逐题复核）。**保留**：它是历史指标锚点，冻结后不再改动 |
| `candidates.jsonl` | v1 五类候选题汇总（机器生成 + 自动校验后） |
| `candidates-coverage.jsonl` | 扩题候选题（单文档 + 跨文档），由 `generate_coverage_questions.py` 产出 |
| `review-state.json` | v1 逐题筛选决策（st / note），可回灌 localStorage 复现筛选态 |
| `pairs.json` | 政策 ↔ 解读配对（**150 组**，`build_pairs.py` 默认正文匹配模式产出） |
| `manifest.json` | 数据集快照：语料 hash、chunk 统计、模型版本、题量配比 |
| `results/` | 评测结果 JSON，规则见「当前对外基线」 |
| `audit/` | 审计与报告：`baseline-report.md`（单次快照）、`baseline-compare.md`（跨版本对比 + 可复现性）、`coverage-qc-report.md`（扩题质检）、`pairs-audit.md`、`human-review-report.md` |
| `scripts/` | 构建与评测脚本（见下）。`lib_rag_eval.py` 是**数据集中立**的取数与打分内核，`lib_eval.py` 是环境/DB/HTTP 共用库 |
| `datasets/mhr-rag/` | **另一套数据集，与本节完全隔离**：MultiHop-RAG 609 篇英文新闻 + 2556 题多跳问答，自带 `scripts/` 与 `results/`，只共享 `lib_rag_eval.py` 的打分口径。见该目录的 README |
| `tools/review.html` | 人工筛选页（离线可用，进度存 localStorage） |
| `tmp/` | 中间产物与缓存，**已 gitignore** |
| `.env` | 密钥与模型配置，**已 gitignore**（参照 `.env.dev` 重建） |

## 数据格式（JSONL，一行一题）

```json
{
  "id": "X-101",
  "category": "pair | docnum | unanswerable | permission | synthetic | single | crossdoc",
  "question": "重庆市地震应急预案里，地震灾害是怎么分级的？",
  "answer": "分为特别重大、重大、较大、一般四级。……",
  "expectRefusal": false,
  "gold": [{"docId": 2097637762580992002, "chunkIndex": 2,
            "why": "重庆市地震应急预案", "quote": "摘抄自该 chunk 的原句"}],
  "source": {"kind": "single | crossdoc", "selfDocId": 123, "selfTitle": "...",
             "policyDocId": 123, "policyTitle": "...", "docNumber": "渝府办发〔2025〕66号"},
  "permission": null,
  "meta": {"qType": "overview|detail|docnum|unanswerable|permission|synthetic",
           "evidenceKey": "...", "goldVerified": true,
           "generatedBy": "qwen3.8-flash", "temperature": 0,
           "reviewState": "kept|edited|dropped|auto",
           "reviewedBy": "human|auto", "reviewNote": "",
           "matchType": "exact|contains|docnum", "matchEvidence": "命中的原文片段"}
}
```

- `gold[].quote` 是该 chunk 中的**原样摘抄**，由程序校验必须真实存在（防 gold 幻觉）。
- `permission` 仅 D 类非空：`{targetSpaceId, memberUserIds, nonMemberUserIds, forbiddenDocIds}`。
- `expectRefusal: true` 仅 C 类（库外问题，系统必须拒答）。
- D 类是 A/B 类的**镜像**（`meta.mirrorOf`），用于成员 vs 非成员对照，不进平均分，**泄漏即一票否决**。
- `meta.reviewedBy` 区分**人工复核**（`human`，v1 的 99 题）与**仅过自动闸门**（`auto`，扩题的 324 题），
  引用时应区别对待。
- `meta.dupOf` 标记与某道 crossdoc 题近重复的单文档题（见「已知问题 5」）。

## 七类题与配比

| 类 | 名称 | 数量 | 生成方式 | ground truth 可信度 |
|---|---|---|---|---|
| A | 配对题 | 54 | LLM 读官方解读生成问题 → 证据绑政策原文 → 摘抄校验 | 高（官方撰写） |
| B | 文号题 | 9 | 规则生成，专测文号精确匹配层 | 高（元数据确定） |
| C | 无答案题 | 14 | 人工列候选 + 自动校验实体确实不在库中 | 高 |
| D | 权限题 | 10 | A/B 镜像 + 权限标注，非成员必须 0 命中 | 高 |
| E | 合成补充 | 12 | LLM 从政策原文 chunk 生成，摘抄校验 | 中（需人工筛） |
| **X** | **跨文档题** | **89** | 新闻/解读篇提问，**答案锚政策原文**（需正文书名号或文号配对成功） | 中高（quote 100% 可验） |
| **S** | **单文档题** | **235** | 任意文档单篇出题，**答案锚本文档**，覆盖无配对文档 | 中（quote 100% 可验） |

**必须分组报告。** X（0.406）与 S（0.660）的难度差 **0.254**，E 类合成题（0.875）更是 A 类（0.299）的 2.9 倍。
混在一起算 overall 会被题型配比直接污染，纵向对比将失去意义。

## 脚本

```bash
# 0. 冒烟：验证 LLM / Embedding 可用（换 key 或模型后先跑）
python eval/scripts/smoke_test.py

# 1. 语料体检
python eval/scripts/corpus_audit.py

# 2. 锚点映射：metadataId → docId → chunkIndex（216 篇必须全匹配，否则报错退出）
python eval/scripts/export_anchor_map.py

# 3. 配对锚定（默认：标题 + 正文书名号 + 文号；--title-only 复现 v1 的仅标题行为）
python eval/scripts/build_pairs.py
python eval/scripts/build_pairs.py --title-only    # 回归对照：应得 31 组

# 4. 出题（v1 五类）
python eval/scripts/generate_pair_questions.py --assume-confirmed   # A 类
python eval/scripts/generate_docnum_questions.py --count 10         # B 类
python eval/scripts/generate_unanswerable.py                        # C 类（自动校验库外）
python eval/scripts/generate_permission.py --count 10               # D 类
python eval/scripts/generate_synthetic.py --count 12                # E 类

# 4b. 出题（扩题：X 跨文档 + S 单文档，含五道闸门）
python eval/scripts/generate_coverage_questions.py --dry-run        # 先看配额计划
python eval/scripts/generate_coverage_questions.py --sections 政策文件 --limit 3   # 小样本试跑
python eval/scripts/generate_coverage_questions.py                  # 全量
python eval/scripts/generate_coverage_questions.py --fill-missing --sections 媒体视角  # 补漏零覆盖文档

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

# 10. 合并扩题结果为 golden.v2（同时产出 audit/coverage-qc-report.md）
python eval/scripts/merge_golden_v2.py

# 校验器自检：注入不存在的 chunkIndex，验证能报错并指出题号
python eval/scripts/validate.py eval/candidates.jsonl --self-test

# 11. 校验评测参考文件里的数字是否与源数据一致（改基线或改文档后必跑）
python eval/scripts/verify_reference.py
```

LLM 调用均设置 `enable_thinking=false`（实测 qwen3.8-flash：耗时 2.4s→0.7s、token 212→50，答案一致）。

> `verify_reference.py` 把 [`EVAL-REFERENCE.md`](./EVAL-REFERENCE.md) 里出现的**每一个数字**与
> `golden.v2.jsonl` / 基线结果 JSON 现算的数值逐项比对（当前 109 项），**动态计算、不硬编码预期值**。
> 它的存在是为了防止"参考文件写了一个漂亮数字、实际对不上"这种无声失真——文档改了基线而没同步时，它会直接红。

## 跑评测（change `add-rag-eval-runner`）

数据集是**考卷**，`run_eval.py` 是**阅卷器**。`Recall@K / MRR / 跨空间泄漏率` 是跑出来的分数，
不是数据集里的字段 —— 固定写死就失去了"换 embedding 前后对比"的意义。

```bash
# 0) 制备评测用 API Key（成员 + 非成员，用于 D 类权限对照）
python eval/scripts/prepare_keys.py          # 明文写入 eval/.env（gitignore）

# 1) 探测后端与 key（后端未启动会直接退出码 1，不会静默产出全 0 指标）
python eval/scripts/run_eval.py --probe

# 2) 跑基线 —— http 模式，测真实系统，对外引用的指标一律用它
python eval/scripts/run_eval.py --golden eval/golden.v2.jsonl

# 2b) 剔除近重复对（single 侧），产出一组对照指标
python eval/scripts/run_eval.py --golden eval/golden.v2.jsonl --exclude-dup-pairs

# 3) 离线模式 —— 不依赖后端，用于 embedding 模型选型对比
python eval/scripts/run_eval.py --retriever offline --offline-vector recompute

# 4) 跨版本对比 / 可复现性分析
python eval/scripts/compare_baselines.py \
    --base eval/results/baseline-20260912-151958-http.json \
    --new  eval/results/baseline-20260915-091440-http.json \
    --prev-golden eval/golden.v1.jsonl \
    --repro eval/results/baseline-20260915-091440-http.json \
            eval/results/baseline-20260915-091252-http.json \
    --out eval/audit/baseline-compare.md
```

**一次检索取 top10，本地截断算 K ∈ {1,3,5,6,10}**，423 题只需 423 次请求（不是 5×423）。
v2 全量 http 模式实测约 **80 秒**（0 失败）。

指标口径：

| 指标 | 定义 | 备注 |
|---|---|---|
| `recall@K` | gold 坐标命中比例 | chunk 级，区分度最高，但对切分策略敏感 |
| `docRecall@K` | gold 涉及的 docId 被命中比例 | 文档级，**跨实验对比首选** |
| `mrr` | 1 / 首个 gold 命中的位次，未命中为 0 | 衡量"正确答案排第几"，**对检索抖动最敏感** |
| `hitRate@K` | 前 K 是否命中至少一个 | 最宽松 |

- **C 类**（gold 为空）不进召回分母，只报非空返回率与 `top1Score` 分布，**不拍相似度阈值**
- **D 类**用成员/非成员双 key 对照，泄漏一票否决且**不进 overall 平均**；
  offline 模式无权限过滤，会标记 `notApplicable` 而不是产出假的 0 泄漏
- 两种 retriever 的分数**不可混合平均**，结果 JSON 记录了 `retriever` 与 `embeddingModel`

产物：`eval/results/baseline-<ts>-<mode>.json`（逐题明细）+ `eval/audit/baseline-report.md`
（单次快照，每次跑都覆盖，只反映最近一轮）。

### 当前对外基线

对外引用的指标一律取自下表这一份。

| 项 | 值 |
|---|---|
| 文件 | `eval/results/baseline-20260915-091440-http.json` |
| 运行 ID | `baseline-20260915-091440-http` |
| 通道 | `http` → `POST http://localhost:8123/api/open/rag/search` |
| embedding | DashScope `qwen3.7-text-embedding-flash`，**1024 维**（库内实测 4096 字节/chunk） |
| 数据集 | `golden.v2.jsonl`（423 题）· sha256 `8cc03ae1…` |
| 采集时间 | 2026-09-15 09:13:21 → 09:14:40（79 秒，0 失败） |

> **模型归属说明**：该结果 JSON 内 `config.embeddingModel` 为 `null`——`run_eval.py` 有意不读后端配置，
> 以免拿 offline 模式的模型名冒名顶替。上表的模型归属来自**人工核对**（非程序探测）：库内
> `wiki_chunk.embedding` 实测长度 4096 字节（1024 维，全库维度集合仅 `{1024}`），
> 与 `application.yml` 的 `rag.embedding.model` 一致。**这是当前流程的已知缺陷**：
> 模型归属无法自证，跨模型对比前应先补该字段。

**整体指标**（399 题计入召回）

| 指标 | 值 |
|---|---|
| recall@6 | 0.5623 |
| docRecall@6 | 0.8697 |
| docRecall@1 | 0.4762 |
| mrr | 0.4486 |
| hitRate@6 | 0.6842 |

**分类指标**

| 类别 | 题数 | recall@6 | docRecall@6 | mrr |
|---|---|---|---|---|
| A 配对 | 54 | 0.2992 | 0.7963 | 0.3379 |
| B 文号 | 9 | 0.6525 | 1.0000 | 0.6852 |
| E 合成 | 12 | 0.8750 | 1.0000 | 0.4583 |
| X 跨文档 | 89 | 0.4064 | 0.7865 | 0.2546 |
| S 单文档 | 235 | 0.6624 | 0.9064 | 0.5380 |

**其他口径**

- C 类无答案 14 题：`nonEmptyReturnRate = 1.0`——**全部返回了内容**，说明拒答不能只靠检索层阈值
- D 类权限 10 题：`leakCount = 0`（非成员零泄漏），成员侧镜像 `recall@6 = 0.3331`，未因权限过滤误伤

**三条最该记住的信号**：

1. `docRecall@6 = 0.8697` 而 `recall@6 = 0.5623` → **找得到文件、定不准段落**，对症手段是精排而非换模型
2. X 跨文档（0.4064）与 S 单文档（0.6624）差 **0.256** → **任何跨版本比较必须分组**，overall 会被题型配比污染
3. 无答案题 100% 返回内容，且分数与有答案题重叠 → **拒答必须在答案层做，检索层阈值做不到**

#### 数值可信下限（可复现性）

同一后端、同一份数据连续跑两次，**11/423 题（2.6%）的命中集合发生变化**，
`recall@6` 在 0.5611 ~ 0.5623 之间波动（极差 **0.13 个百分点**）。来源是检索链路的非确定性
（向量检索 / Top-K 排序），不是评测脚本——脚本对同一份 hits 的计算完全确定。

> **引用规则**：小于 **0.2 个百分点**的 `recall@6` 差异不可作为改进证据，需重复运行确认。
> 详细逐题清单见 `audit/baseline-compare.md` 的「检索链路可复现性报告」。

### results/ 保留规则

| 文件 | 定位 | 可否删除 |
|---|---|---|
| `baseline-20260915-091440-http.json` | **当前对外基线**（v2 数据集），亦为 `audit/baseline-report.md` 对应的一次 run | 否 |
| `baseline-20260915-091252-http.json` | 同数据集另一次采样，作为**抖动证据** | 可（删则失去可复现性证据） |
| `baseline-20260912-151958-http.json` | v1 数据集基线（99 题），历史锚点 | 可（删则无法跨版本对比） |

每个数据集版本保留一份正式基线；额外采样仅用于可复现性分析，命名带时间戳可区分。

## 改动影响面

想动评测链路的某一环时，先查这张表：它给出该改哪些文件、是否必须重建索引、是否必须重新对齐 gold。

| 想改什么 | 涉及文件 | 重建索引 | 重对齐 gold | 说明 |
|---|---|---|---|---|
| **切块参数**（大小/重叠） | `cloud/src/main/java/com/et/cloud/rag/MarkdownChunker.java` 的 `MAX_CHUNK_LENGTH` / `MIN_CHUNK_LENGTH` / `OVERLAP_LENGTH` | ✅ 必须（全量） | ⚠️ 视情况 | 三个值是 `static final` **硬编码**，改完需重编译 + 全量重建；`chunkIndex` 会变，`gold[].quote` 是唯一能跨切块方案存活的锚点（v2 覆盖率 **74.2%**，v1 只有 37%） |
| **embedding 模型** | `cloud/src/main/resources/application.yml` 的 `rag.embedding.model`；`RagProperties.Embedding` | ✅ 必须（`POST /admin/rag/rebuild?force=true`） | ❌ | 换模型必须全量回填，否则维度与语义都不匹配。`rebuildAll` 默认按 `isUpToDate` 跳过，**不加 `force` 不会重算** |
| **题型配比 / 出题口径** | `eval/scripts/generate_*.py`、`merge_candidates.py`、`merge_golden_v2.py`、`llm_judge.py`、`validate.py`、`gen_manifest.py` | ❌ | ❌ | 走「出题 → 汇总 → 预筛 → 人工复核 → 校验 → 快照」，冻结数据集前必须过摘抄校验 |
| **扩题覆盖范围** | `eval/scripts/generate_coverage_questions.py` 的 `QUOTA` 与五道闸门阈值 | ❌ | ❌ | 配额改完先 `--dry-run` 看计划；G2 阈值（3-gram 覆盖率 0.35）切在 detail/overview 两型分布之间，调低会放进不可定位答案 |
| **配对匹配范围** | `eval/scripts/build_pairs.py` 的 `MIN_BOOK_LEN` / `MIN_CONTAIN_LEN` / `NOISE_BOOK` | ❌ | ❌ | 默认匹配标题 + 正文书名号 + 文号；`--title-only` 复现 v1 行为（31 组）用于回归对照 |
| **指标口径 / 取数协议** | `eval/scripts/lib_rag_eval.py`（`KS` / `FETCH_K` / `compute_metrics` / `METRIC_KEYS`） | ❌ | ❌ | 改口径会让**所有历史结果失去可比性**，须同步重跑并替换基线。**注意这是两个数据集共享的一份**：改它同时影响本目录与 `datasets/mhr-rag/`，这正是刻意的——保证两边 metric 定义不漂移 |
| **检索参数** | `eval/scripts/run_eval.py` 的 `FETCH_K` | ❌ | ❌ | 对外口径固定「一次取 top10、本地截断算 K ∈ {1,3,5,6,10}」；改动同样影响可比性 |
| **语料来源 / 规模** | 站内导入接口、库内 `wiki_chunk` | ✅ 增量或全量 | ⚠️ 新语料需重新出题 | 建议用**独立空间**做跨库对照；混入既有空间会破坏空间级对照 |
| **检索链路本身** | `cloud/src/main/java/com/et/cloud/rag/RagSearchServiceImpl.java` | ❌ | ❌ | 改链路后必须重跑评测并**替换基线**，同时更新本文件的「当前对外基线」。链路 = 权限过滤 → **文号精确命中层** → 向量补齐；评测脚本走 HTTP 自动跟到新行为，**改算法不用动评测脚本**。另注意文号层靠中文公文号正则，**英文语料上恒不命中**（`datasets/mhr-rag/` 因此测的是纯向量链路） |

**维护检查项**——改动收尾时确认四件事：

1. `results/` 中「当前对外基线」与上表一致，多余采样已按规则说明
2. 本文件的「当前对外基线」指标表已随重跑更新
3. `compare_baselines.py` 的对比报告已重新生成，且**共同题目子集**无异常漂移
4. `openspec validate <change-id> --strict` 通过

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
4. **B 类 91 个 gold 段的 `quote` 覆盖率为 0**（v2 全库 quote 覆盖率 74.2% 的缺口几乎全在这里）。
   后果：**改切块方案后 B 类题无法自动重定位**，必须人工重标。B 类恰是文号精确匹配层
   （`recall@6` 由离线 0.074 → http 0.653）的唯一证据来源，是全库最脆的部分。
5. **跨类别近重复对 31 对**（3-gram 覆盖 ≥ 0.70，占 crossdoc 34.8%），其中 4 对问话完全相同。
   补漏模式为「本文档不是 gold」的文档补单文档题时，可能与既有 crossdoc 题撞成
   "同一问话、两个 gold"。处置：**不删除**（删了会丢覆盖），在 30 道 single 上打标
   `meta.dupOf = <crossdoc id>`，可用 `run_eval.py --exclude-dup-pairs` 出一组对照指标。
   根因是 G4 去重只在同 `category` 内做，**跨类别拦不住**；且 `temperature=0` 下重跑产出的是
   同义改写而非同一字符串，0.85 的 Jaccard 阈值抓不到语义重复。
6. **检索链路非确定性**：同数据同后端连续两次运行，11/423 题（2.6%）命中集合不同，
   `recall@6` 极差 0.13 个百分点。引用时以此为数值可信下限（见上文「数值可信下限」）。
7. **`config.embeddingModel` 恒为 `null`**：runner 有意不读后端配置，导致模型归属无法自证，
   跨模型对比时无法从结果文件确认变量。**建议下一轮补该字段**。
8. **C 类口径已定（2026-09-11 负责人决策）：保留 14 题、剔除 C-14**。
   负责人全库检索核验：婚姻登记/住房补贴/报销比例/食品经营许可/消防验收/学区划分等关键词
   在 216 篇语料中均为 **0 篇**，C-15「电动自行车」仅现于消防整治与摩托车产业语境、无上牌流程规定
   → **库外前提成立，判为有效拒答题**（推翻 Judge 的 9 条 plauzibility 质疑）。
   C-14「民营经济」在发布会、媒体视角、营商环境任务清单第 150 条均有提及 → **前提不成立，剔除**。
   暂不补「纯越界」对照组：near_miss 已足以诱导幻觉。
   D 类判定时注意：**非成员"零命中"必须用 `forbiddenDocIds` 文件级权限过滤判定，不能依赖答案内容**。
9. **Judge 自身未经一致率验证**：首轮未跑 `--repeat`。如需在报告中引用 Judge 结论的稳定性，
   应补跑一遍算一致率（预期成本与一轮相当，约 9 分钟）。
10. **评测 100% 只覆盖检索层**：`run_eval.py` 唯一调用 `POST /open/rag/search`，
    **无任何答案层指标**（faithfulness / 引用正确率 / 拒答率）。
    这与问题 3 呼应——最该改的答案层，恰好是评测未覆盖的层。

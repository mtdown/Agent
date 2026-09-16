## 交付分期

本次按两阶段交付，边界由负责人 2026-09-16 对齐确认：

- **第一步（导入能力，本次开工）**：第 1、2、3 组 + 4.13 编译通过。
  产出「负责人可在窗口手动操作、把 609 篇全量落库并完成语言适配切片」的完整通路。
  **语言适配属于第一步**——索引在导入后自动触发，若先用中文参数切 609 篇英文，事后改参数必须对该空间整库重建（而重建可复现性本身是待验证项），故必须先切对再入库。
- **第二步（测试，全量上传完成后执行）**：第 4 组单测（4.1–4.12）+ 第 5 组人工测试 + 第 6 组 MHR 评测 + 第 7 组收尾。
  由负责人先手动完成全量上传，上传完成后再开始测试。

> 上传批次约定：**一次全量**提交整个语料文件（实测 6.79MB，`max-file-size` 上限 30MB，余量 4.4×），不套用 `maxItems`，不分批。

## 1. JSON 分段器

- [x] 1.1 新增 `JsonDocumentSplitter`，以流式方式读取顶层 JSON 数组（同时兼容 `{"documents": [...]}` 包装形态），逐条产出 `ImportedWikiDocument`，不整体反序列化
- [x] 1.2 实现固定字段约定与别名兼容：标题取 `title` → `name`，正文取 `content` → `body` → `text`（优先级明确且固定）
- [x] 1.3 条目无可用标题时按固定规则兜底生成非空标题，不使该条失败
- [x] 1.4 条目无可用正文时产出失败项而非抛出全局异常，保证其余条目继续导入
- [x] 1.5 将条目**除标题与正文外**的全部原始字段序列化为一整行 JSON，写入 `ImportedWikiDocument.metadataJson`；条目完整的原始 `title` 一并保留在元数据中
- [x] 1.6 元数据超过 2048 字符时，按键的保留优先级逐个丢弃（`source` → `published_at` → `url` → `category` → `author` → 其余），保证结果仍是**合法 JSON** 且 ≤2048；极端情况写 `{"truncated":true}` 保底，**条目仍成功导入**
- [x] 1.7 条目 `url` 双写：既保留在元数据中，也写入 `ImportedWikiDocument.sourceUrl`（由 `saveImported` 落到 `document_wiki.sourceUrl`）
- [x] 1.8 标题超过 128 字符时截断兜底（完整标题已在元数据中），避免长标题条目整条失败
- [x] 1.9 统一设置 `contentFormat = "markdown"` 与 `sourceType = "IMPORT"`。`contentFormat` 尤其不可漏写或写 `plain`：`rebuildAll` 只重建 `markdown` 文档（`WikiRagIndexServiceImpl:232`），写 `plain` 会导致重建时被静默跳过

## 2. 接口、Service 与上传窗口接入

- [x] 2.1 在 `WikiBatchImportService` 新增 JSON 导入方法签名（入参含文件、`spaceId`、可选 `folderId`、登录用户）
- [x] 2.2 实现中复用既有 `requireEditableSpace` 与 `resolveFolderId`，沿用现有目标与权限语义
- [x] 2.3 逐条落库直接复用既有 `saveImported`，不新写落库逻辑
- [x] 2.4 逐项结果复用 `BatchImportItemResult`：`input` 放**条目自身的稳定标识**（有 `url` 用 `url`，否则用条目 `title`），成功项携带新建文档 `documentId`；元数据中记录 `_entryIndex` 以便追溯顺序
- [x] 2.5 该入口**不套用** `maxItems` 条目上限；保留一个文件体积上限用于防内存风险
- [x] 2.6 在 `DocumentWikiBatchController` 新增端点，位置与命名与既有 `/batch/url`、`/batch/file` 保持一致
- [x] 2.7 处理三类边界：文件不可解析为 JSON、解析成功但无可导入条目、文件超出体积上限，均返回明确消息且不产生文档
- [x] 2.8 前端 API：在 `documentWikiController.ts` 新增 JSON 导入方法（`FormData` 携带 `file` / `spaceId` / `folderId`），风格与命名对齐既有 `batchImportFilesUsingPost`
- [x] 2.9 前端窗口：在既有批量导入页 `/documentWiki/batch` 新增「JSON 文件导入」面板（**不改动**现有「网页地址」「本地文件」两块的行为与 accept 白名单）。单文件选择、`accept=".json"`，选择后显示文件名与体积；前置拒绝非 `.json`、空文件、超 30MB（与后端 `max-file-size` 一致），不允许将 json 混入既有 md/html 入口
- [x] 2.10 前端结果展示：结果表格改为可翻页（609 条不分页会明显掉帧），顶部固定显示「成功 N / 共 M」汇总；全量导入时用户需能定位失败条目
- [x] 2.11 前端长时反馈：全量导入（约 609 条）逐条同步落库耗时可达分钟级，上传期间保持 loading 并禁用重复提交，完成后明确提示成功/失败数与耗时
- [x] 2.12 前端索引就绪自查：切片是 `AFTER_COMMIT` 异步触发的，接口返回时 `wiki_chunk` 尚未生成。上传完成后须给出可自查的等待提示（不新增统计接口，复用既有文档列表核对文档数），避免在切片未完成时开始测试
- [x] 2.13 前端测试与构建：按既有 `wikiBatchDocumentImportFlow.test.mjs` 风格补 JSON 导入流程用例（选文件 → 提交 → 渲染逐项结果 → 拒绝非法文件），前端构建通过

## 3. 语言感知切片 profile

- [x] 3.1 抽出 `ChunkerProfile`（字符上限 / 最小块长 / 重叠长度 / `hardSplit` 句界字符表）；`ChunkerProfile.ZH` 取值与现状常量**逐字节一致**（600 / 100 / 80，句界 `。；`）
- [x] 3.2 新增 `ChunkerProfile.EN`（1800 / 100 / 100，句界 `.!?;`）
- [x] 3.3 `MarkdownChunker` 入口改为 `chunk(content, profile)`，`hardSplit` 改用 `profile` 的句界表；英文句末判定需排除小数（`1.5`、`$US90.39`）与常见缩写（`Nov.` / `U.S.` / `Inc.` / `Dr.` 等）
- [x] 3.4 英文 `hardSplit` 降级链：句末 → 空格（词界）→ 硬切，保证有边界可选时不切在词中
- [x] 3.5 语言判定 `ChunkerProfile.forContent(content)`：基于 CJK 字符占比的**纯函数**判定；文档 `metadataJson.language` 有值时优先采信（保证导入侧与重建侧口径一致）
- [x] 3.6 `WikiRagIndexServiceImpl`（唯一调用点 `:75`）改为按判定结果选 profile 切片，**不新增第二套切片流程**
- [x] 3.7 导入侧（`JsonDocumentSplitter` 组装元数据时）把判定结果显式写入 `metadataJson.language`。该键**不是条目原始字段**，不受 1.5「除标题与正文外的原始字段」覆盖，必须主动补写。这是**重建路径的硬约束而非可选优化**：`doIndexDocument(docId)` 读整行文档，缺这个键时 `rebuildAll` 会按内容重新判定；一旦判定结果不同，`chunkIndex` 全变、已建立的 gold 绑定全部失效

## 4. 自动化验证

- [x] 4.1 单元测试：中文样本经 profile 化改造后，切片结果与改造前**逐块一致**（防中文基线漂移）
- [x] 4.2 单元测试：中文重叠仍为 80、上限 600；英文重叠 100、上限 1800
- [x] 4.3 单元测试：英文超长段落在句末切开，且不产生"词被劈开"的切点（覆盖小数与缩写消歧用例）
- [x] 4.4 单元测试：语言判定覆盖中文、英文、中英混排三种输入；`metadataJson.language` 可覆盖自动判定
- [x] 4.5 单元测试：正常数组导入生成 N 个文档且逐项结果数量一致
- [x] 4.6 单元测试：字段别名兼容（`body` 与 `content` 两种命名均可导入）
- [x] 4.7 单元测试：单个条目缺正文时该项失败、其余条目成功
- [x] 4.8 单元测试：非法 JSON 与空条目集被拒绝且不产生文档
- [x] 4.9 单元测试：条目超出 `maxItems` 数量时仍全部处理（验证「数量不受限制」）
- [x] 4.10 单元测试：元数据被完整保留为单条 JSON；超 2048 时降级结果仍是合法 JSON 且该条目成功
- [x] 4.11 单元测试：长标题截断后文档仍创建成功，完整标题保留在元数据中
- [x] 4.12 单元测试：同一文档在「导入后首次切片」与「清空 chunk 后重建」两条路径下产生**完全相同的 chunk 序列**（验证 `metadataJson.language` 使重建可复现——这是 gold 绑定不漂移的前提）
- [x] 4.13 后端编译通过
- [x] 4.14 `openspec validate add-json-document-import --strict` 通过

## 5. 人工测试（负责人执行）

- [ ] 5.1 由负责人启动本地服务（AI 只负责停止服务）
- [ ] 5.2 新建**独立空间**并把 MultiHop-RAG 语料子集导入该空间（严禁导入现有语料空间）
- [ ] 5.3 核对导入结果：文档数与逐项结果一致、标题正常、元数据字段完整、`sourceUrl` 已落库
- [ ] 5.4 核对切片：该空间 `wiki_chunk` 行数符合预期，文档确实被切分为多块（段落级排序可测），块长分布与英文参数（上限 1800）一致
- [ ] 5.5 抽查若干块文本，确认边界处不存在"词被劈开"的情况
- [ ] 5.6 在该空间执行一次检索，确认导入文档可被召回，且命中结果携带 `docTitle`
- [ ] 5.7 核对现有语料空间 `wiki_chunk` 仍恒为 2061 行、状态全 ACTIVE（未污染基线）

## 6. MHR 泛化评测锚点映射（依赖第 2、3 组）

> 目录与脚本 `eval/datasets/mhr-rag/`（口径 README + `scripts/mhr_lib.py` / `bind_anchor.py` / `run_eval.py`）已就位，本组负责端到端跑通并产出基线。该目录与自有 216 篇评测**完全隔离**，仅共享 `eval/scripts/lib_rag_eval.py`（怎么量）。

- [ ] 6.1 语料就位并核对指纹：`eval/tmp/mhr_corpus.json`（609 篇）与 `mhr_qa.json`（2556 题）的 sha256 须与 `eval/datasets/mhr-rag/README.md` 记录值一致，不一致即中止（`eval/tmp/` 已 gitignore，属易失资产）
- [ ] 6.2 用本次 JSON 导入入口把**609 篇全量**导入一个**独立空间**（严禁与 216 篇语料同空间），记录 `spaceId`、文档数与逐项结果
- [ ] 6.3 绑定步骤一：从逐项结果收集 `url → documentId`；失败项再按 `document_wiki.sourceUrl` 反查补齐，产出导入覆盖报告
- [ ] 6.4 绑定步骤二：等异步索引完成后查 `wiki_chunk.chunkText`（唯一权威来源，不依赖本地模拟）把每条 evidence 的 `fact` 绑到 `chunkIndex`，产出 `anchor/golden.mhr.jsonl` 与 `anchor/anchor-map.json`，坐标形状与 `golden.v1` 同形（`{docId, chunkIndex, why, quote}`，`fact` 落 `quote`）
- [ ] 6.5 **gold 生成契约（硬约束）**：绑不上的证据**不得**从 gold 中删除，必须逐条记入 `anchor/unbound-report.json` 并计数上报；**严禁用缩小分母的方式抬高指标**
- [ ] 6.6 核对绑定健全性：`chunkIndex` 越界数必须为 0；`null_query` 类（301 题、无证据）不进召回分母，仅统计分布
- [ ] 6.7 跑本数据集基线（http 模式，按 `question_type` 分类报指标），结果写入 `eval/datasets/mhr-rag/results/`，**不写进 `eval/results/`**
- [ ] 6.8 对照 README「已知实测数字」核验 EN 1800 一列（未绑定 0、`recall@K` 上限 1.0000）；不符时先查绑定算法与切分参数，**不得直接调参掩盖**

## 7. 收尾

- [ ] 7.1 将本次遇到的问题按规范记入 `IssueLog.xlsx`
- [ ] 7.2 更新项目记忆：新增接口契约、字段约定、独立空间要求、语言感知切片参数
- [ ] 7.3 汇报测试结果并等待负责人确认，未经确认不进入下一轮修复

## Verification / Result

执行时间：2026-09-16。环境：Maven 3.6.1 / JDK 17（`-source/-target 11`）、Node 22.22.2。全部为 AI 侧自动化验证，**未启动任何服务**（启动由负责人执行）。

| # | 命令 | 结果 |
|---|---|---|
| 4.13 | `mvn -o test-compile` | **BUILD SUCCESS**（主源码 + 测试源码均编译通过） |
| 4.1–4.12 | `mvn -o -Dtest=MarkdownChunkerTest,ChunkerProfileTest,WikiRagIndexServiceImplTest,JsonDocumentSplitterTest,WikiBatchImportServiceImplTest,DocumentWikiBatchControllerTest,RagSearchServiceImplTest,WikiDocumentImportServiceTest test` | **Tests run: 111, Failures: 0, Errors: 0**（8 个类全绿；其中 `RagSearchServiceImplTest` / `WikiDocumentImportServiceTest` 为未改动代码的回归对照） |
| 2.13 | `node --test wikiBatchDocumentImportFlow.test.mjs` | **15 pass / 0 fail**（原 9 项 + 新增 6 项） |
| 2.13 | `vue-tsc --build` | exit 0 |
| 2.13 | `vite build` | ✓ built in 11.52s |
| 4.14 | `openspec validate add-json-document-import --strict` | **Change 'add-json-document-import' is valid** |

### 关键验证证据

1. **中文基线零漂移（4.1）**：改造**前**先用真实 `MarkdownChunker` 对固定中文样本跑出快照
   `count=7`、`sha256=59893205bd85247b8586f58666197bde361f3e8b23d5c1f166080d98be40c124`；
   profile 化后同一断言通过 → 600/100/80 与句界 `。；` 逐字节未变。样本存于
   `ChineseChunkFixture`，探针脚本已删除，快照固化进 `MarkdownChunkerTest`。
2. **英文不再切在词中（4.3）**：用"无句末标点 + 等长词"的样本，使按 1800 硬切**必然**落在词内
   （1800 % 11 = 7），断言每个窗口都以完整词收尾 → 只有词界回退生效才会通过。
3. **重建可复现（4.12）**：同一文档走「导入后异步索引」与「清空 chunk 后 rebuildAll」两条路径，
   逐块 `chunkIndex|chunkText` 序列完全一致；并断言该序列等于显式 `ChunkerProfile.EN` 的产物、且
   **不等于** `ZH` 的产物 → 证明是 `metadataJson.language` 在起作用（否则重建会改判定、chunkIndex 全变）。
4. **非法 JSON 不产生半截语料（4.8）**：断言解析失败时接收器**一次都没被调用**（不是"回滚已入库的"）。

### 与工件的偏差（已在 design.md 决策三同步）

- **解析改为"先校验语法、再流式产出"两遍走**。单遍流式解析下，文件在第 N 条才损坏时前 N-1 条已经落库，
  与规格场景「Uploaded file is not valid JSON → no Wiki document is created」冲突。两遍均为 O(文件体积)、
  不构建 JSON 树，内存结论不变。
- **`{"truncated":true}` 降级前先保护 `title` / `_entryIndex` / `language`**：规格只要求"丢弃最不重要的字段
  直到装得下"，故先把用户字段按优先级删光，最后才落到 `{"truncated":true}`。`language` 若被降级丢掉，
  重建会重新判定、chunkIndex 漂移（见 4.12 的硬约束）。

### 未覆盖 / 待人工确认

- **中文判定阈值**：`forContent` 以「CJK 占字母数 ≥ 50%」判定。216 篇政务语料若存在
  "拉丁字母多于汉字"的文档（如多为表格/编号），会判为 EN 并按 1800 上限切片 → 中文基线漂移。
  现有 8 个测试类覆盖不到该数据，须在 5.7 核对现有语料空间 `wiki_chunk` 仍为 2061 行。
- 第 5/6/7 组（人工测试、MHR 端到端、收尾）未开始，按约定等负责人先完成全量上传。

## Rollback

新增为主，但**切片 profile 化会改动既有文件**，因此不再是"零风险"：

1. 删除新增的 `JsonDocumentSplitter` 与对应单元测试文件
2. 撤回 `WikiBatchImportService` / 实现类 / `DocumentWikiBatchController` 的新增方法（不影响既有方法）
3. 撤回切片 profile 化：恢复 `MarkdownChunker` 的单参签名与 600/100/80 常量，移除 `ChunkerProfile` 与语言判定，`WikiRagIndexServiceImpl` 调用点还原
   → 注意：若回滚前英文文档已被按英文参数切片，回滚后需对该空间执行一次重建，否则残留的 chunk 仍是英文参数产物
4. 无需数据库迁移——未新增表或字段（`metadataJson`、`sourceUrl` 均为既有字段）
5. 已导入的文档如需清除，删除对应测试空间即可，现有语料空间不受影响

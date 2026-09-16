## Why

需要用公开 RAG 数据集验证本系统的跨领域泛化能力。以 MultiHop-RAG 为例，它的形态是**一个 JSON 文件内含整个语料库**（`corpus.json`：609 篇新闻，6,297,171 字符，中位 7,836 字符/篇）+ 另一个 JSON 内含全部问答（2,556 题，evidence 6084 条且 100% 逐字可核验）。

现有导入能力无法承接这种形态：

- `POST /documentWiki/import`（`WikiDocumentImportServiceImpl:27`）白名单只有 `md/html/htm`，**json 直接被拒**；
- `POST /documentWiki/batch/file`（`WikiBatchImportServiceImpl:101`）虽然是批量，但是**一文件对应一文档（1:1）**，且一次最多 `maxItems = 20` 项。

要把 609 篇语料入库，人工拆成 609 个 md 再分 31 批上传不现实；更重要的是，**每个条目的原始元数据（title / source / published_at / url）必须随文档落库**，否则 MultiHop-RAG 的 gold（靠条目 `url` 定位证据）无法映射回本系统的 `docId`，评测集就是废的。实测 `url` 与 `title` 均 609/609 唯一，但 `title` 会受 128 字符截断影响，故**以 `url` 为映射主键**（可直接落 `sourceUrl` 列，无需解析元数据 JSON）。

因此需要一个把「单个 JSON → 多个 Wiki 文档（1:N）」的导入能力。

同时，实测暴露了第二个问题：语料是**英文新闻**，段落普遍 800–1030 字符，而现有切片器的句界表只认中文标点、上限 600 字符。结果是长段落被机械切成 600 字符窗口，切点落在词中间（实测把 `Star Entertainment` 切成 `tar Entertainment`）。这意味着即便语料成功入库，泛化测试里也会**同时混着"检索管线不适配英文"和"中文切片器句界失效"两个变量**，指标掉了无法归因。因此本次一并解决切片参数的语言适配。

## What Changes

- 新增 JSON 文档导入能力：接收**整个 JSON 文件**，按条目拆分为**多个独立 Wiki 文档**（方案 A：一条目 = 一文档，不做切片级注入）。
- 逐条流式读取：一边读一边分段落库，不要求先把整库一次性载入内存。
- **沿用现有导入目标**：复用 `spaceId` + 可选 `folderId` 的既有语义与权限校验，不新造选择逻辑。
- **元数据保留**：每个条目除标题与正文外的原始字段序列化为一整行 JSON 存入该文档的 `metadataJson`；条目 `url` 同时写入既有的 `sourceUrl` 列；超限时按键优先级降级丢弃，保证记录仍是合法 JSON 且条目不失败。
- **条目数量不受限制**：该入口不套用 `maxItems` 上限（仅保留文件体积上限以防内存风险）。
- **切片语言适配**：把现有切片常量收敛为 `ChunkerProfile`（上限 / 最小块 / 重叠 / 句界），按文档语言选择参数——中文保持 600/100/80 与 `。；` 句界**逐字节不变**，英文使用 1800/100/100 与 `.!?;` 句界（含小数与缩写消歧）。**仍是同一个切片入口，不新增第二套切片实现。**
- **标题不写入切片文本**：检索结果已携带文档标题，生成侧按需拼接，避免改动现有 2061 块的文本与向量。
- 逐项结果沿用现有结构，一条失败不影响其余条目。

## Capabilities

### New Capabilities
- `json-document-import`: 接收一个 JSON 文件，将其中的条目逐条拆分为独立 Wiki 文档导入指定空间/文件夹，保留每条原始元数据，并对每个条目给出独立的处理结果；导入的文档进入既有切片与索引流程，切片参数按文档语言选择。

### Modified Capabilities
- `wiki-rag-pipeline`: 切片参数由固定值改为**按文档语言选择**（中文口径与切分结果逐字节不变）。既有需求 `Markdown documents are chunked into semantic slices` 被 **MODIFIED**——字符上限 / 最小块长 / 重叠 / 句界字符表成为语言相关（中文 600/100/80 与 `。；`，英文 1800/100/100 与 `.!?;`），并新增「英文切分不落在词中」「语言判定确定性」两个场景；同时以 **ADDED** 并入 `Retrieved chunks carry their document title`（检索结果携带来源文档标题，且**不**把标题写进块文本）。

> 说明一：现有批量导入的既有行为不变，本次为新增入口，不改动 `batch-document-import` 已定义的任何 Requirement。
> 说明二：切片能力 `wiki-rag-pipeline` 已于 2026-09-16 随 `add-wiki-rag-pipeline` 归档进入规格库（`openspec/specs/wiki-rag-pipeline/spec.md`），故本次得以对其声明 `MODIFIED Requirements`，切片口径在规格库中**只有一份**，不再与硬编码 600/80 的旧文并存。这两条需求已从 `json-document-import` 能力迁出（该能力只留 6 条导入相关需求），避免切片行为挂在导入能力之下造成归属错位。

## Impact

**新增代码**
- `JsonDocumentSplitter`：JSON 字节流 → `List<ImportedWikiDocument>`（流式逐条解析 + 字段别名兼容 + 元数据组装与降级）
- `ChunkerProfile`：切片参数载体（上限 / 最小块 / 重叠 / 句界字符表），含 `ZH`（现状原样）与 `EN` 两套取值，以及基于内容语言（CJK 占比）的确定性判定
- `DocumentWikiBatchController` 新增端点（JSON 文件导入）
- `WikiBatchImportService` 新增方法（复用现有 `saveImported` 私有方法）

**改动的既有代码**（中文行为不变）
- `MarkdownChunker`：三个常量收敛为 `ChunkerProfile`，入口由 `chunk(content)` 改为 `chunk(content, profile)`，`hardSplit` 改用 profile 的句界表
- `WikiRagIndexServiceImpl`：唯一调用点（`:75`）改为按语言选 profile

**复用的既有组件（零改动）**
- `ImportedWikiDocument`：解析产物载体，是整条链路的接缝（新增 `language`、`sourceUrl` 字段承载）
- `WikiBatchImportServiceImpl.saveImported(...)`：单文档落库
- `BatchImportItemResult`（`input/status/message/documentId/title`）：逐项结果。其 `documentId` 只能充当 gold 映射的**第一步**（`url → docId`）；`chunkIndex` 是切分参数的函数、且切分是 AFTER_COMMIT 异步的（返回时尚无 chunk 行），必须在索引完成后查 `wiki_chunk` 做**第二步**绑定（见「评测侧改动」）
- `WikiRagIndexListener`：`@Async("ragIndexExecutor")` + `@TransactionalEventListener(AFTER_COMMIT)` → `indexDocument()`，文档入库后自动切片 + embedding
- `ChunkHit.docTitle` / `wiki_chunk.docTitle`：检索结果天然携带文档标题，无需改动即可支撑"检索后拼接标题"
- `DocumentWikiController:100-102` 的 `contentFormat` 兜底（未传即默认 `markdown`），保证不会静默零切片

**评测侧改动（独立数据集，不并入自有评测目录）**
- 新增 `eval/scripts/lib_rag_eval.py`：从 `run_eval.py` 抽出的**数据集中立内核**（取数适配器 + 打分数学 `compute_metrics` / `METRIC_KEYS`）。全仓**只保留一份**，两个数据集的 recall / docRecall / hitRate / mrr 定义因此不可能漂移、可横向比较；`run_eval.py`（784 → 493 行）改为 import 该内核，打分结果经回归验证**逐位不变**
- 新增 `eval/datasets/mhr-rag/`：MultiHop-RAG 泛化评测独立目录（口径 README + `mhr_lib.py` / `bind_anchor.py` / `run_eval.py` + 独立 `results/` 与 `anchor/`），与 `eval/golden.*.jsonl` 完全隔离。gold 绑定为**两步**（`url → docId`，再 `fact → chunkIndex`）；未绑定的证据显式记入 `unbound-report.json` 并计数，**不得静默出 gold**

**不改动**
- 现有 `/documentWiki/import`、`/documentWiki/batch/url`、`/documentWiki/batch/file` 的行为与契约
- 中文文档的切片参数与切分结果（重叠仍为 80，句界仍只认 `。；`）
- embedding 模型与配置
- `wiki_chunk` 现有语料（216 篇 / 2061 块）不受影响——新语料须导入独立空间

**依赖与配置**
- 无新外部依赖（JSON 解析用项目现有 Jackson）
- 若导入规模大，需关注既有 embedding 调用频率限制

**跨变更影响**
- 本次修改的 `MarkdownChunker` 与 `WikiRagIndexServiceImpl` 由变更 `add-wiki-rag-pipeline` 引入（该变更已于 2026-09-16 归档）。中文行为逐字节不变，不改变该变更已定义的任何行为；切片口径的修订以 `MODIFIED Requirements` 形式对 `wiki-rag-pipeline` 声明。

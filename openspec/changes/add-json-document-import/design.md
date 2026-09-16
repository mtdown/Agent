## Context

**现有导入能力的边界**（已核实代码）

| 入口 | 形态 | 限制 |
|---|---|---|
| `POST /documentWiki/import` | 单文件 → 单文档 | `ALLOWED_EXTENSIONS = md/html/htm`（`WikiDocumentImportServiceImpl:27`），10MB |
| `POST /documentWiki/batch/file` | N 文件 → N 文档（1:1） | `maxItems = 20`（`WikiBatchImportServiceImpl:45`） |
| `POST /documentWiki/add` | 单条 JSON body → 单文档 | 需登录态，无批量语义 |

**现有链路的接缝**（复用基础）

```
ImportFiles 循环体：
  ImportedWikiDocument imported = wikiDocumentImportService.parse(file, null);
  Long documentId = saveImported(imported, wikiSpace, targetFolderId, loginUser);
  results.add(BatchImportItemResult.success(input, documentId, imported.getTitle()));
```

`saveImported` 是私有方法，`BatchImportItemResult` 承载逐项结果。整条链路的**天然接缝就是 `ImportedWikiDocument`**——只要能把 JSON 条目变成这个对象，落库、逐项结果、元数据、缓存清理、索引触发全部可以零改动复用。

**索引是异步且自动的**（已核实）

`documentWikiService.save()` → `DocumentWikiServiceImpl:93 publishEvent(DOC_CREATED)` → `WikiRagIndexListener`（`@Async("ragIndexExecutor")` + `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)`）→ `indexDocument(docId)` → `MarkdownChunker` 切片 + embedding。

**现有切片器在中英文语料上的实测边界**

用本地复刻脚本（`tmp/chunk_profile_probe.py`，严格对齐 `MarkdownChunker`）跑 MultiHop-RAG 的两篇真实 `body`，得到一个前置事实：**英文 `body` 含 `\n\n` 空行分段、不含 `#` 标题行**，因此全文落成 1 个 section，`chunkHeading` 恒为空串——切分质量完全由「段落长度分布」决定。

| 样本 | 段落长度分布 | 现状（上限 600 / 最小 100 / 重叠 80，句界只认 `。；`） |
|---|---|---|
| 列表型新闻（Mashable） | 31 段，最长 600 | 0 次机械切，7 块全部落在段界 |
| 叙事型新闻（SMH） | 7 段 `[1029,973,853,846,823,585,528]` | **5 次机械切**，切点 `'...after it raised $565'` → 下一块从 `'tar Entertainment...'` 开始（`Star` 被劈成 `tar`） |

结论：MultiHop-RAG 属叙事型，**英文段落普遍 800–1030 字符 > 600 上限**，而 `hardSplit` 的句界表只有中文标点 → 必然退化为 600 字符机械切窗，切点落在词中间。

同口径换成英文参数（上限 1800 / 重叠 100 / 句界 `.!?;`）后：段落普遍 < 1800 → **不触发任何机械切**，样本 B 块数 12→5，中位块 1267 字符（≈317 token，与中文 600 字符 ≈340–419 token 同量级）。

> 数据来源为 2 篇真实 body 的复刻实测，用于定性；全量 609 篇的效果须在导入后复核。

**评测侧约束**（来自 `eval/README.md` 核心原则 4）

现有政务语料库有 216 篇 / 2061 块，是 423 题基线的复现基础，要求「全程只读、`wiki_chunk` 恒为 2061 行」。新语料**必须导入独立空间**，不得混入空间 `2095544464810774531`。

**上游数据形态**（以 MultiHop-RAG 实测为准）

`corpus.json` 是**顶层 JSON 数组**，每项字段为 `title / author / source / published_at / category / url / body`。不存在统一的行业 schema——不同数据集字段名各异。

语料以**英文新闻**为主：正文无 Markdown 结构、段落长、无中文公文号（`docNumber` 恒 `NULL`）。

## Goals / Non-Goals

**Goals:**

- 一个 JSON 文件导入为 N 个独立 Wiki 文档，条目数不设上限
- 逐条流式读取，避免为解析整库而先把全部条目载入内存
- 每个条目的原始字段完整保留（标题与正文除外），可用于把数据集 gold 映射回本系统 `docId`
- 复用现有落库、逐项结果、权限校验、异步切片与索引流程，不重复实现
- 现有导入接口行为与契约零变更
- **中文文档的切片结果与本次改造前逐块一致（中文基线零漂移）**
- **英文文档按独立参数切片，不再退化为字符中机械切窗**

**Non-Goals:**

- 不实现「一条目 = 一个 chunk」的切片级注入
- 不实现 JSONL / CSV / Excel 等其它结构化格式（本次只做 JSON）
- 不实现字段映射的页面化配置（本次采用固定约定 + 别名兼容）
- 不实现导入任务的持久化队列与断点续传
- **不为 JSON 导入另建第二套切片实现**——仍是同一个切片入口，只按内容语言选参数
- **不改动中文文档的切片参数与切分结果**（重叠仍为 80，句界仍只认 `。；`）
- **不顺手"改进"中文句界**——把 `！？` 纳入中文句界会改变现有 2061 块，属独立实验，需重跑中文基线
- **不做语义切分**（用 embedding 相似度决定切点），理由见决策六
- **不把标题写入 `chunkText`**（做法见决策八）
- 不改动 embedding 模型与配置

## Decisions

### 决策一：一条目 = 一个 Wiki 文档（方案 A），不做切片级注入

**选择**：JSON 每个条目落成一份独立文档，再由现有 `MarkdownChunker` 正常切块。

**理由**：本系统的核心待验证瓶颈是**段落级排序**（`docRecall@6 0.8697` vs `recall@6 0.5623`）。若把条目直接当 chunk 写入（方案 B），`chunkIndex` 将恒为 0、每文档仅一块，「文档召回」与「段落召回」退化为同一件事，**该瓶颈在数据集里根本无法复现**，评测失去意义。此外方案 B 会让 `doIndexDocument` 在 rebuild 时按文档重新切片，直接覆盖手工写入的切片。

**备选**：方案 B（条目即 chunk）—— 已否决，除上述理由外还需自行处理 `chunkHeading` / `contentVersion` / 重建一致性。

### 决策二：以 `ImportedWikiDocument` 为接缝，复用 `saveImported`

**选择**：新增 `JsonDocumentSplitter` 只负责产出 `List<ImportedWikiDocument>`，落库完全走既有 `saveImported`。

**理由**：`saveImported` 已经正确处理了 `contentFormat`、`sourceType`、`metadataJson`、`buildSummary`、`userId`、`spaceId`、`folderId`、`viewCount` 等字段，并触发后续事件。自建落库逻辑会遗漏字段（尤其 `contentFormat` 一旦漏写就静默零切片）。

**备选**：新写一套落库逻辑 —— 已否决，重复且易漏字段。

### 决策三：流式逐条解析

**选择**：用 Jackson 流式 API（`JsonParser` / `MappingIterator`）逐个读出数组元素，读一条产出一个文档，而非 `readValue` 整库反序列化到 `List<Map>`。

**理由**：用户明确要求「一边读，一边分段」。顶层数组形态下，流式解析让内存占用与条目数解耦，6,297,171 字符的语料不需要先整体驻留。

**备选**：整体反序列化 —— 实现更短，但大文件下内存峰值与条目数成正比。

### 决策四：固定约定 + 字段别名兼容，不做可配映射

**选择**：约定顶层为数组（也接受 `{"documents": [...]}` 包装）；标题取 `title`/`name`，正文取 `content`/`body`/`text`；**除标题与正文外的所有字段原样进元数据**。

**理由**：用户要求「写成一个固定脚本」——即固定的解析约定。别名兼容让主流数据集（MultiHop-RAG 用 `body`、多数数据集用 `content`）无需预处理即可直接导入，成本极低。

**备选**：请求参数传入字段名映射 —— 灵活性更高但对当前需求是过度设计；后续若出现反例再演进（本设计不阻断该演进）。

### 决策五：条目数不设限，但保留文件体积上限

**选择**：该入口不套用 `maxItems`；仍保留一个文件体积上限用于防内存风险。

**理由**：用户明确要求「文档的上传数量不受限制」。条目数限制的本质目的是防止单请求过大，而流式解析后真正的风险源是**文件体积**而非条目数，因此把限制从条目数改为文件体积更贴合实际。

### 决策六：语言感知切片——唯一入口加 profile 参数，中文行为逐字节不变

**选择**：

1. 把 `MarkdownChunker` 的三个常量收敛为一个 `ChunkerProfile`（上限 / 最小块 / 重叠 / 句界字符表），入口签名改为 `chunk(content, profile)`；唯一调用点 `WikiRagIndexServiceImpl:75` 按语言传入对应 profile。
2. 参数取值：

| profile | 上限 | 最小块 | 重叠 | `hardSplit` 句界 |
|---|---|---|---|---|
| `ZH`（现状原样） | 600 | 100 | **80** | `。；` |
| `EN`（本次新增） | **1800** | 100 | **100** | `.!?;`（含缩写与小数消歧） |

3. 英文 `hardSplit` 的降级链：**句末 → 空格（词界）→ 硬切**。句末判定排除小数（`1.5`、`$US90.39`）与常见缩写（`Nov.` / `U.S.` / `Inc.` / `Dr.` 等）。
4. **不去动中文的参数**：`ZH` 逐字节等于现状，包含「句界只认 `。；`」这个既有缺陷也一并保留。因此现有 216 篇 / 2061 块的文本与向量完全不变，中文基线不需要重跑。

**理由**：

- 用户已拍板「overlap=100 只对英文」「英文上限取 1800」「单独开英文路径，增加一个参数的事情」。上面就是这句话的正确形态——**一个参数，不是第二套实现**。
- 上限 1800 的取值依据是 token 对齐：中位块 token 粗估 中文 600 字符 ≈340–419、英文 1800 字符 ≈317，同量级；若英文也用 600 字符，单块语义量只有中文的一半（≈148 token）。
- 重叠只对英文改到 100：中文 80 字符≈半句话已够用，英文 80 字符只够约两个词。

**为什么不做语义切分**（用户问过）：语义切分（embedding 相似度定切点）治的不是这个病。样本 B 的 7 段每段内部都是单一话题，问题是**段落太长超过上限**，而非段落边界不准；语义切分器同样必须在段内某处切开。且它的切点随模型版本/阈值漂移，与 `eval/README.md` 核心原则 1「gold 用逻辑坐标」直接冲突，还会在入库时额外产生一轮逐句 embedding 调用。**「优先切在段落/句子边界」才是这里要的语义性**，补分隔符表即可达成，且完全确定性。

**备选**：新建第二个 Chunker 类并按 `sourceType` 分派 —— 已否决：`MarkdownChunker` 只有一个调用点，分叉会让 URL/上传导入的英文文档反而享受不到英文参数。

### 决策七：元数据 = 除标题/正文外的原始字段，超限按键优先级降级

**选择**：把条目**除标题与正文之外**的字段序列化为一整行 JSON 存入 `DocumentWiki.metadataJson`；同时：

- 条目 `url` **双写**到 `document_wiki.sourceUrl`（varchar 1024，本就是为来源追踪而建），使"按来源 URL 定 gold"无需解析 JSON；
- 条目 `language`（若有）写入元数据，供索引侧读取（见决策六）；
- 条目完整的原始 `title` 也保留在元数据中（文档列的标题会截断，见下）。

**理由**：复用既有字段，无需 schema 变更；该字段也正是后续把数据集 gold 映射回 `docId` 的依据（配合 `BatchImportItemResult.documentId`）。

**边界处理**（`MAX_METADATA_LENGTH = 2048`，`validDocumentWiki:324` 超限抛错）：

- 实测两类样本的元数据为 173 / 297 字符（占上限 8.4% / 14.5%），正常语料不构成风险；
- 但不做"截断字符串"——那会产生非法 JSON。**超限时按保留优先级逐个丢弃键**（`source` → `published_at` → `url` → `category` → `author` → 其余），直到合法且 ≤2048；
- 极端情况下仍超限，则写入 `{"truncated":true}` 保底，**条目仍成功导入**，不因元数据失败。

**备选**：按 design 旧稿的字面理解把 `body` 也塞进元数据 —— 实测 8019 字符，**每条都超限、整体失败**，已否决。

### 决策八：标题不写入 `chunkText`，检索结果已自带 `docTitle`

**选择**：`chunkText` 保持正文原样（不加任何标题前缀）；标题由检索结果携带，生成侧按需拼接。

**理由**：

- `ChunkHit` 已含 `docTitle` 字段，且 `docTitle` 是 chunk 行的冗余列（`wiki_chunk.docTitle` varchar 256），**零代码改动即可拿到**；
- 若在切片时给每块加同一个标题前缀，会改变现有 2061 块的文本与向量（中文基线须重跑）并压低块间区分度——对 MultiHop-RAG 的**跨文档多跳**检索尤其不利；
- 标题前缀的价值在**生成侧**（消解「该公司 / the company」的跨文档指代歧义、给出可核验引用）与**归因**（`chunkHeading` 在英文语料恒空，`docTitle` 是唯一可用的定位线索），这些都在检索结果组装环节，不需要动索引。

**备选**：入库时给 `chunkText` 加标题前缀（Anthropic Contextual Retrieval 的极简版）—— 已否决，理由同上；若后续实测证明召回确实受标题影响，再作为独立实验（届时需重跑基线）。

**文档列标题的长度闸门**：`MAX_TITLE_LENGTH = 128`（`DocumentWikiServiceImpl:46`，`:309` 超限抛「标题过长」，**是既存硬闸门，绕不开**）。因此切片器侧对该值做截断兜底，完整标题保留在元数据中（决策七），避免长标题条目整条失败。

### 决策九：逐项结果的标识用稳定键，不依赖数组下标

**选择**：`BatchImportItemResult.input` 放**条目自身的稳定标识**（有 `url` 用 `url`，否则用条目 `title`），并在元数据中同时记录 `_entryIndex` 以便追溯顺序。

**理由**：数据集 gold 要靠"逐项结果 → `documentId`"建立映射表。若 `input` 只放数组下标，映射就变成**顺序契约**——实现里一旦跳过、去重或乱序，gold 就会静默错位。稳定键 + `_entryIndex` 让映射可核验、可对账。

### 决策十：切片与索引完全复用现有异步流程

**选择**：不新增索引触发逻辑，依赖 `WikiRagIndexListener` 在事务提交后异步切片。新增代码只到「文档落库」为止，加上决策六的 profile 参数化。

## Risks / Trade-offs

**风险一：大规模导入触发 embedding 限流**
大批文档会各自触发一次异步 `indexDocument`，短时间内集中调用外部 embedding 服务，可能被限流。
→ 缓解：`ragIndexExecutor` 线程池本身是并发上限；如仍被限流，先导入子集（如 200 篇）验证，不一次性全量。

**风险二：单请求耗时过长**
即使 embedding 异步，同步循环仍要完成 N 次 insert。600+ 条目在可接受范围内，但万级条目会逼近 HTTP 超时。
→ 缓解：依赖文件体积上限兜住上界；必要时由调用方分文件提交。

**风险三：误导入现有语料空间，污染基线**
导入位置由调用方指定的 `spaceId` 决定。若误指向空间 `2095544464810774531`，会破坏 423 题基线要求的「`wiki_chunk` 恒为 2061 行」。
→ 缓解：本能力不硬编码任何空间；落地时在测试与手测项中明确要求新语料单独建空间，并在导入后核对该空间 chunk 数。

**风险四：字段别名兼容带来的歧义**
若条目的 `content` 与 `body` 同时存在且语义不同，别名策略会静默择一。
→ 缓解：优先级明确且写进实现（`content` 优先于 `body` 优先于 `text`），并把未采用的字段一并保留在元数据中，事后可追溯。

**风险五：语言判定错误导致中文文档走英文参数**
若判定把中文文档识别为英文，会用 1800 上限切它，块数骤减、中文基线漂移。
→ 缓解：判定必须是**内容纯函数**（CJK 字符占比阈值，禁用 LLM 判定，避免同一文档两次判定不同）；`metadataJson.language` 存在时优先采信，保证导入侧与重建侧口径一致；单元测试覆盖中文、英文、中英混排三种输入；导入后核对现有语料空间仍为 2061 块。

**风险六：英文块体积增大触及外部限制**
上限 1800、重叠 100 → 单块最大 1901 字符（≈475 token）。
→ 缓解：远低于 `text-embedding-v3` 的输入上限与 `wiki_chunk.chunkText mediumtext` 容量；导入后抽查块长分布确认无异常。

**风险七：`contentFormat` 与重建流程的耦合**
`rebuildAll` 只重建 `contentFormat = 'markdown'` 的文档（`WikiRagIndexServiceImpl:232`）。若 JSON 导入写 `plain`，首次异步索引仍会成功，但**任何一次重建都会静默跳过这批文档**，表现为"重建后英文语料检索不到"。
→ 缓解：导入侧统一写 `markdown`（正文虽为纯文本，但 Markdown 渲染对其无害，且切片器对所有文档统一走同一套规则）；手测项中核对重建后 chunk 数不回退。

**权衡：无断点续传**
导入中途失败需整文件重试，可能产生重复文档。
→ 当前接受：数据集导入是一次性操作，且逐项结果会给出已成功的 `documentId` 便于人工清理。若后续成为高频场景，再引入去重或幂等键。

**跨变更影响**
本次会修改 `MarkdownChunker`（签名 + profile 化）与 `WikiRagIndexServiceImpl`（唯一调用点），这两个文件属未归档变更 `add-wiki-rag-pipeline` 的实现范围（其 tasks 已全部完成）。中文行为逐字节不变，因此不改变该变更已定义的任何行为。

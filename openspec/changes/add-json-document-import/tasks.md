## 1. JSON 分段器

- [ ] 1.1 新增 `JsonDocumentSplitter`，以流式方式读取顶层 JSON 数组（同时兼容 `{"documents": [...]}` 包装形态），逐条产出 `ImportedWikiDocument`，不整体反序列化
- [ ] 1.2 实现固定字段约定与别名兼容：标题取 `title` → `name`，正文取 `content` → `body` → `text`（优先级明确且固定）
- [ ] 1.3 条目无可用标题时按固定规则兜底生成非空标题，不使该条失败
- [ ] 1.4 条目无可用正文时产出失败项而非抛出全局异常，保证其余条目继续导入
- [ ] 1.5 将条目**除标题与正文外**的全部原始字段序列化为一整行 JSON，写入 `ImportedWikiDocument.metadataJson`；条目完整的原始 `title` 一并保留在元数据中
- [ ] 1.6 元数据超过 2048 字符时，按键的保留优先级逐个丢弃（`source` → `published_at` → `url` → `category` → `author` → 其余），保证结果仍是**合法 JSON** 且 ≤2048；极端情况写 `{"truncated":true}` 保底，**条目仍成功导入**
- [ ] 1.7 条目 `url` 双写：既保留在元数据中，也写入 `ImportedWikiDocument.sourceUrl`（由 `saveImported` 落到 `document_wiki.sourceUrl`）
- [ ] 1.8 标题超过 128 字符时截断兜底（完整标题已在元数据中），避免长标题条目整条失败
- [ ] 1.9 统一设置 `contentFormat = "markdown"` 与 `sourceType = "IMPORT"`。`contentFormat` 尤其不可漏写或写 `plain`：`rebuildAll` 只重建 `markdown` 文档（`WikiRagIndexServiceImpl:232`），写 `plain` 会导致重建时被静默跳过

## 2. 接口与 Service 接入

- [ ] 2.1 在 `WikiBatchImportService` 新增 JSON 导入方法签名（入参含文件、`spaceId`、可选 `folderId`、登录用户）
- [ ] 2.2 实现中复用既有 `requireEditableSpace` 与 `resolveFolderId`，沿用现有目标与权限语义
- [ ] 2.3 逐条落库直接复用既有 `saveImported`，不新写落库逻辑
- [ ] 2.4 逐项结果复用 `BatchImportItemResult`：`input` 放**条目自身的稳定标识**（有 `url` 用 `url`，否则用条目 `title`），成功项携带新建文档 `documentId`；元数据中记录 `_entryIndex` 以便追溯顺序
- [ ] 2.5 该入口**不套用** `maxItems` 条目上限；保留一个文件体积上限用于防内存风险
- [ ] 2.6 在 `DocumentWikiBatchController` 新增端点，位置与命名与既有 `/batch/url`、`/batch/file` 保持一致
- [ ] 2.7 处理三类边界：文件不可解析为 JSON、解析成功但无可导入条目、文件超出体积上限，均返回明确消息且不产生文档

## 3. 语言感知切片 profile

- [ ] 3.1 抽出 `ChunkerProfile`（字符上限 / 最小块长 / 重叠长度 / `hardSplit` 句界字符表）；`ChunkerProfile.ZH` 取值与现状常量**逐字节一致**（600 / 100 / 80，句界 `。；`）
- [ ] 3.2 新增 `ChunkerProfile.EN`（1800 / 100 / 100，句界 `.!?;`）
- [ ] 3.3 `MarkdownChunker` 入口改为 `chunk(content, profile)`，`hardSplit` 改用 `profile` 的句界表；英文句末判定需排除小数（`1.5`、`$US90.39`）与常见缩写（`Nov.` / `U.S.` / `Inc.` / `Dr.` 等）
- [ ] 3.4 英文 `hardSplit` 降级链：句末 → 空格（词界）→ 硬切，保证有边界可选时不切在词中
- [ ] 3.5 语言判定 `ChunkerProfile.forContent(content)`：基于 CJK 字符占比的**纯函数**判定；文档 `metadataJson.language` 有值时优先采信（保证导入侧与重建侧口径一致）
- [ ] 3.6 `WikiRagIndexServiceImpl`（唯一调用点 `:75`）改为按判定结果选 profile 切片，**不新增第二套切片流程**

## 4. 自动化验证

- [ ] 4.1 单元测试：中文样本经 profile 化改造后，切片结果与改造前**逐块一致**（防中文基线漂移）
- [ ] 4.2 单元测试：中文重叠仍为 80、上限 600；英文重叠 100、上限 1800
- [ ] 4.3 单元测试：英文超长段落在句末切开，且不产生"词被劈开"的切点（覆盖小数与缩写消歧用例）
- [ ] 4.4 单元测试：语言判定覆盖中文、英文、中英混排三种输入；`metadataJson.language` 可覆盖自动判定
- [ ] 4.5 单元测试：正常数组导入生成 N 个文档且逐项结果数量一致
- [ ] 4.6 单元测试：字段别名兼容（`body` 与 `content` 两种命名均可导入）
- [ ] 4.7 单元测试：单个条目缺正文时该项失败、其余条目成功
- [ ] 4.8 单元测试：非法 JSON 与空条目集被拒绝且不产生文档
- [ ] 4.9 单元测试：条目超出 `maxItems` 数量时仍全部处理（验证「数量不受限制」）
- [ ] 4.10 单元测试：元数据被完整保留为单条 JSON；超 2048 时降级结果仍是合法 JSON 且该条目成功
- [ ] 4.11 单元测试：长标题截断后文档仍创建成功，完整标题保留在元数据中
- [ ] 4.12 后端编译通过
- [ ] 4.13 `openspec validate add-json-document-import --strict` 通过

## 5. 人工测试（负责人执行）

- [ ] 5.1 由负责人启动本地服务（AI 只负责停止服务）
- [ ] 5.2 新建**独立空间**并把 MultiHop-RAG 语料子集导入该空间（严禁导入现有语料空间）
- [ ] 5.3 核对导入结果：文档数与逐项结果一致、标题正常、元数据字段完整、`sourceUrl` 已落库
- [ ] 5.4 核对切片：该空间 `wiki_chunk` 行数符合预期，文档确实被切分为多块（段落级排序可测），块长分布与英文参数（上限 1800）一致
- [ ] 5.5 抽查若干块文本，确认边界处不存在"词被劈开"的情况
- [ ] 5.6 在该空间执行一次检索，确认导入文档可被召回，且命中结果携带 `docTitle`
- [ ] 5.7 核对现有语料空间 `wiki_chunk` 仍恒为 2061 行、状态全 ACTIVE（未污染基线）

## 6. 收尾

- [ ] 6.1 将本次遇到的问题按规范记入 `IssueLog.xlsx`
- [ ] 6.2 更新项目记忆：新增接口契约、字段约定、独立空间要求、语言感知切片参数
- [ ] 6.3 汇报测试结果并等待负责人确认，未经确认不进入下一轮修复

## Verification / Result

（执行后填写：实际命令、通过/失败项、发现的问题与处理）

## Rollback

新增为主，但**切片 profile 化会改动既有文件**，因此不再是"零风险"：

1. 删除新增的 `JsonDocumentSplitter` 与对应单元测试文件
2. 撤回 `WikiBatchImportService` / 实现类 / `DocumentWikiBatchController` 的新增方法（不影响既有方法）
3. 撤回切片 profile 化：恢复 `MarkdownChunker` 的单参签名与 600/100/80 常量，移除 `ChunkerProfile` 与语言判定，`WikiRagIndexServiceImpl` 调用点还原
   → 注意：若回滚前英文文档已被按英文参数切片，回滚后需对该空间执行一次重建，否则残留的 chunk 仍是英文参数产物
4. 无需数据库迁移——未新增表或字段（`metadataJson`、`sourceUrl` 均为既有字段）
5. 已导入的文档如需清除，删除对应测试空间即可，现有语料空间不受影响

# Change: Wiki-First 改造（项目收敛为知识库 Wiki）

## Why

用户明确收敛项目方向：**只做 Wiki**。图片模块降级为文件模块的附属能力，文档模块必须支持粘贴图片，前端组织结构从"图片库为中心"重构为"Wiki 为中心"，并为将来云端知识库（RAG）预留字段。

原 `add-knowledge-item-model`（KnowledgeItem + KnowledgeBlock 全量融合方案）**范围过大，被本 Change 取代**；其中的 RAG 预留字段设计保留在档案中，本 Change 落地其中的最小预留集。

关键侦察结论（决定了技术路线）：

1. **普通用户无法用现有 `/picture/upload` 传图**：`spaceId=null` 走公共区，公共区普通用户仅有 `PICTURE_VIEW` 权限（`SpaceUserAuthManager` 82–87 行），上传会被 `@SaSpaceCheckPermission` 拦截。
2. **两套空间体系互不相认**：图片挂 `Space`/`SpaceUser`，文档挂 `WikiSpace`/`WikiSpaceUser`。粘贴图片若走图片模块，权限校验无解。
3. **文档正文无承载图片能力**：编辑器是纯 `<a-textarea>`，详情页纯文本渲染，`package.json` 零 Markdown 依赖。
4. **排版混乱根因**：`DocumentWikiListPage.vue` 969 行巨石组件（script 570 行），塞了 4 区域 + 空间树 + 文件夹操作 + 成员管理 + 搜索。
5. **树形展示是假的**：后端 `wiki_folder.parentId` 已支持无限嵌套，但前端把树拍平成 `paddingLeft` 缩进列表，无展开/收起；且点击文件夹名触发的是"新建子文件夹"弹窗而非进入文件夹，交互反直觉。

## What Changes

五件事，按依赖顺序拆为 5 个可独立验收的模块：

1. **M1 后端数据层**：新表 `wiki_attachment` + Wiki 自有图片上传接口（绕开 `Space` 权限体系）；`document_wiki` 增加 `contentFormat`、来源溯源字段（`sourceType`/`sourceUrl`，供网页搬运溯源）及 4 个 RAG 预留字段（`contentHash`/`contentVersion`/`visibility`/`metadataJson`），全部 nullable 有默认值，零业务侵入。
2. **M2 编辑器与渲染**（前端）：textarea → Markdown 编辑器，粘贴/拖拽图片自动上传插入；详情页按 `contentFormat` 渲染。
3. **M3 树形结构重做**（前端）：拍平列表 → 真正的可折叠树；**空间作为树的根节点统一成一棵大树**；**树只导航到文件夹，文档在右侧列表展示**（点文件夹 → 右侧列出其下文档）。
4. **M4 组件拆分 + 导航 Wiki 化**（前端）：969 行拆为 5 个子组件（左栏按 M3 新交互实现）；`/` 即 Wiki，图库降级为次级入口 `/gallery`。
5. **M5 全链路回归**：手测 + IssueLog + 汇报。

### 已确认的设计决策

- **树形态**：空间（公开/团队/个人分组）作为树的根节点，统一成一棵大树，切换空间不用来回点 Tab。
- **文档位置**：树只到文件夹层级；点击文件夹后右侧内容区展示该文件夹下的文档列表（含根级文档）。
- **RAG 预留范围**：`document_wiki` 只补最小 7 字段集（`contentFormat` + 来源溯源 2 字段 + RAG 预留 4 字段）；网页结构复刻不预留任何字段（将来存整页快照到 COS + `sourceUrl`/`metadataJson` 记来源，YAGNI）；完整 6 组字段等将来建 `knowledge_item` 新表时再上（见 `archive/add-knowledge-item-model/rag-extension-fields.md`）。
- **图片模块处置**：`wiki_attachment` 即文件模块雏形（图片只是其中一种 mime 类型），图库导航降级、代码保留，将来 RAG 传 PDF/Word 时在该表上扩展。

## Capabilities

### New Capabilities

- `wiki-attachment`: Wiki 附件（文件）上传与元数据存储，图片作为第一种附件类型
- `wiki-navigation-tree`: 以空间为根的统一文件夹导航树及右侧文档列表交互

### Modified Capabilities

（无——现有 wiki 空间/文档/回收站能力的 spec 级行为不变，本 Change 新增能力与前端重组）

## Impact

- **后端**：`cloud/` 新增 `wiki_attachment` 表与接口；`document_wiki` 表 ALTER；`DocumentWiki` 实体/DTO 扩字段。
- **前端**：`cloud_front/` 新增 Markdown 编辑器依赖（md-editor-v3）；`DocumentWikiListPage.vue` 拆分为 5 个子组件；路由与全局导航重构，图库页面只改路由引用。
- **数据库**：`cloud/sql/` 新增 2 个迁移脚本，需在开发库执行。
- **不做**：图片模块功能开发（只降级不删）、协同编辑、版本历史、RAG 业务逻辑（只留字段）。

## Acceptance Criteria

- 登录用户在 Wiki 文档编辑器里 **Ctrl+V 粘贴图片**，图片自动上传并在光标处插入 Markdown 图片语法。
- 文档详情页正确渲染 Markdown（标题/列表/代码块/图片）；老文档（plain）显示不受影响。
- 左侧为**统一大树**：空间为根节点，文件夹可无限嵌套、可展开/收起；点击文件夹名进入该文件夹；**文档不再出现在树中**，改为右侧列表展示。
- 首页 `/` 打开即 Wiki；图库从一级导航移除，降级为次级入口且功能回归可用。
- `document_wiki` 表含 `contentFormat` + 来源溯源 2 字段 + 4 个 RAG 预留字段（共 7 个新增列），新文档默认 `contentFormat=markdown`。
- 拆分后 `DocumentWikiListPage.vue` 及所有新子组件单文件不超过 300 行。

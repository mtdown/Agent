# Tasks: Wiki-First 改造（5 模块任务拆分）

> **M4 最新状态**：代码已实现，图库详情鉴权失败，暂停等待修复确认。详细结果、验证范围和回滚见 **m4-result.md**。当前 24/34 项勾选，M4 尚未验收完成。

> **交接说明（2026-09-05，M4 执行前）**：M1–M3 已完成（20/34），M4/M5 待做；环境、坑、待人工抽查项见本目录 **handover.md**，续作前必读。

> **执行纪律**：每个模块 = 一次完整的「开发 → 测试 → 汇报 → 等确认」循环（AGENTS.md 第 4 节）。
> 单模块改动刻意压小：不超过 ~10 个文件，可在一个会话内完成并验证。
> 分支：`fix/wiki-space-init修复`（从 main 切出，承载全部 wiki-first 工作；feature/wiki-first-refactor 为空壳未使用）。

---

## 1. M1 后端数据层（上传通道 + Markdown + 来源溯源 + RAG 预留字段）

**目标**：Wiki 有自己的附件上传接口绕开 `Space` 权限体系；`document_wiki` 一次 ALTER 补齐 Markdown、来源溯源与 RAG 预留字段。

- [x] 1.1 建表 `wiki_attachment`（`id, wikiSpaceId, documentId(nullable), fileName, url, fileSize, mimeType, fileHash, userId, createTime, isDelete`），执行 `cloud/sql/create_table_wiki_attachment.sql` 并确认表结构与索引存在
- [x] 1.2 实体 `WikiAttachment` + Mapper + Service（MyBatis-Plus，复用现有风格），编译通过
- [x] 1.3 新接口 `POST /documentWiki/image/upload`：登录校验 + `wikiSpaceService.requireVisibleSpace` 空间可见性校验 + 类型（jpg/png/gif/webp）与大小（≤5MB）校验 + COS 路径 `wiki/{wikiSpaceId}/{uuid}.{ext}`（复用 `CosManager`）+ 落库返回 `{url}`
- [x] 1.4 `ALTER TABLE document_wiki` 一次性补 7 字段：`contentFormat varchar(16) DEFAULT 'plain'`、`sourceType varchar(16) DEFAULT 'NATIVE'`、`sourceUrl varchar(1024) NULL`、`contentHash varchar(64) NULL`、`contentVersion int DEFAULT 1`、`visibility varchar(16) DEFAULT 'SPACE'`、`metadataJson varchar(2048) NULL`，写成 `cloud/sql/alter_table_document_wiki_wikifirst.sql` 并执行验证
- [x] 1.5 `DocumentWiki` 实体 + Add/Edit DTO 增加上述字段；Service 校验 `contentFormat ∈ {plain, markdown}`、`sourceType ∈ {NATIVE, UPLOAD, IMPORT, URL}`，新建文档默认 `contentFormat=markdown`（老数据不动，自动是 plain）；其余预留字段仅透传存储，无业务逻辑
- [x] 1.6 单测：`WikiAttachmentServiceTest`（类型/大小校验、空间不可见拒绝）+ `DocumentWikiServiceImplTest` 增加格式/来源校验用例，`mvn -Dtest=WikiAttachmentServiceTest,DocumentWikiServiceImplTest test` 全绿
- [x] 1.7 `mvn -DskipTests package` 通过；curl/apifox 实测上传接口返回可访问 COS URL
- [x] 1.8 IssueLog 记录 + 汇报，等待用户确认

**DoD**：登录用户 curl 传图成功返回 COS URL；新文档存为 markdown、老文档返回 plain；表含全部预留字段。

> 注：网页结构复刻**不预留任何字段**。将来搬运网页时存整页快照到 COS + `sourceUrl`/`metadataJson` 记来源，解析建模等真实需求出现再做（YAGNI）。

---

## 2. M2 前端编辑器与渲染（粘贴图片）

**目标**：textarea → Markdown 编辑器，Ctrl+V / 拖拽图片即上传即插入。

- [x] 2.1 安装 `md-editor-v3`（Vue3 Markdown 编辑器，自带预览/工具栏/图片上传钩子），`npm run build-only` 确认依赖可用
- [x] 2.2 重写编辑器正文区（`AddDocumentWikiPage`/`EditDocumentWikiPage` 共用部分抽 `DocumentWikiEditor.vue`）：`MdEditor` 替换 `<a-textarea>`；`onUploadImg` 钩子调 `POST /documentWiki/image/upload` 插入 `![](url)`；粘贴/拖拽自动触发（编辑器内建）
- [x] 2.3 详情页 `DocumentWikiDetailPage.vue`：`MdPreview` 渲染 markdown；`contentFormat=plain` 时按原样式显示
- [x] 2.4 `npm run openapi` 重新生成 API 类型，`npm run build-only` 通过
- [x] 2.5 浏览器实测：新建文档 → 粘贴截图 → 保存 → 详情页可见图片；老 plain 文档显示正常
  - 2026-09-04：自动化已验证 Wiki 上传接口返回 COS URL、编辑器保存 Markdown 图片语法、详情页渲染 Markdown 标题/列表/代码块/图片节点；plain 文档保持原样显示。当前浏览器控制接口不能注入图片二进制剪贴板，真实 Ctrl+V 粘贴截图需人工手测确认。
  - 2026-09-04：用户确认粘贴图片验收通过；追加修复编辑器上传空间 ID 精度丢失问题，并确认真实空间 ID 上传返回 `code=0`。
- [x] 2.6 IssueLog + 汇报，等待用户确认

**DoD**：粘贴图片全链路可用，老数据兼容。

---

## 3. M3 前端树形结构重做（统一大树 + 右侧文档列表）

**目标**：拍平缩进列表 → 真正可折叠树。**空间为根节点统一一棵大树；树只到文件夹；点文件夹 → 右侧文档列表**。修复"点文件夹名弹新建框"的反直觉交互。

- [x] 3.1 新建 `WikiSpaceTree.vue`：聚合公开/团队/个人空间为分组根节点 + `a-tree` 可折叠树（folder 节点按 `parentId` 递归组装 `treeData`），展开/收起状态本地维护，`npm run build-only` 通过
- [x] 3.2 树节点交互：单击文件夹名 = 选中该文件夹（右侧刷新其下文档列表）；新建/重命名/移动/删除收敛到节点 hover 操作或右键菜单（Dropdown），移除"点名称即新建"行为
- [x] 3.3 右侧文档列表改造：按「当前选中空间 + 文件夹（含根级）」过滤展示文档，保留搜索与匹配模式
- [x] 3.4 新建子文件夹入口在选中文件夹上可见（按钮/菜单），验证多层嵌套创建与刷新后层级保持
- [x] 3.5 `DocumentWikiListPage.vue` 接入新树并删除拍平渲染逻辑（`flattenTree`/`paddingLeft`），单文件不超 300 行（如超限，剩余拆分顺延到 M4 收口）
- [x] 3.6 浏览器回归：空间切换、文件夹 CRUD、嵌套展开收起、回收站、搜索均正常
- [x] 3.7 IssueLog + 汇报，等待用户确认

**DoD**：左树右列布局符合已确认设计决策；多层嵌套可建可看；文档不出现在树中。

---

## 4. M4 组件拆分收口 + 站点导航 Wiki 化

**目标**：完成巨石组件拆分（左栏已是 M3 新树）；站点以 Wiki 为中心，图库降级次级入口。

- [x] 4.1 拆出 `src/pages/documentWiki/components/`：`WikiSpaceTree.vue`（M3 已建，收口）+ `WikiDocumentList.vue`（文档列表渲染）+ `WikiRecyclePanel.vue`（回收站面板）+ `WikiSpaceManagePanel.vue`（管理员空间/成员管理）+ `WikiSearchBar.vue`（搜索区）
- [x] 4.2 `DocumentWikiListPage.vue` 降为组合层：状态提升 + 事件传递，验证所有拆出文件 <300 行且功能与拆分前逐项一致
- [x] 4.3 路由：`/` 重定向到 `/documentWiki`；图库（HomePage/AddPicturePage/PictureDetailPage 等）移到 `/gallery` 前缀，**只改路由引用不改功能**
- [x] 4.4 `GlobalSider.vue`/`GlobalHeader.vue` 重构：菜单以 Wiki 为主，图库为次级入口，移除"创建团队"等图片空间推广入口，管理员菜单保留
- [x] 4.5 `npm run build-only` + 浏览器走查：导航闭环、图库从次级入口进入功能正常
- [x] 4.6 IssueLog + 汇报，等待用户确认

**DoD**：打开站点即 Wiki；图库可用；巨石组件消失，各文件 <300 行。

---

## 5. M5 全链路回归与收尾

- [x] 5.1 手测全链路：登录 → 大树切空间/文件夹 → 新建文档 → 粘贴图片 → 保存 → 详情渲染 → 编辑 → 删除 → 回收站恢复
- [x] 5.2 手测图库回归：浏览、上传、详情页可用
- [x] 5.3 手测兼容：老 plain 文档显示正常；图片模块原有权限行为不变
- [x] 5.4 核对 `document_wiki` 预留字段落库正确（查询验证 7 个字段及默认值）
- [x] 5.5 IssueLog 汇总核对本 Change 所有记录
- [x] 5.6 README.md 增补"11. Wiki-First 改造"小节
- [x] 5.7 汇报总结果；**用户确认后方可合并 main**

---

## 依赖关系

```
M1 (后端地基) ──► M2 (编辑器粘贴上传) ──► M5 (回归收尾)
M3 (树形重做) ──► M4 (拆分+导航)       ──► M5
```

M3 不依赖 M1/M2，可先行；M1+M2 亦可合并交付（M2 体量小）。

## 原 add-knowledge-item-model 的处置

- 整体已移入 `openspec/changes/archive/`（被本 Change 取代）
- `rag-extension-fields.md` 的字段设计保留参考价值：本 Change M1 落地 `document_wiki` 最小集——`contentFormat` + 来源溯源（`sourceType`/`sourceUrl`）+ RAG 预留（`contentHash`/`contentVersion`/`visibility`/`metadataJson`），附件表加 `fileHash`；将来做云端知识库时按该文档建 `knowledge_item` 表补齐其余字段（index/embedding/外部 KB 映射等）

# Change: KnowledgeItem — 图片与文档的有机融合

## Why

当前项目虽然已经有"图片管理"和"Wiki 文档"两个独立模块，并且 `WikiSpace` / `WikiFolder` / `DocumentWiki` 三层结构也已经走通，但本质上是**两套独立模型**：

- `picture` 表走的是 `Picture` / `PictureService` / `PictureController` 一条线，关注点放在"审核 + 标签 + 缩略图"。
- `document_wiki` 表走的是 `DocumentWiki` / `DocumentWikiService` / `DocumentWikiController` 一条线，关注点放在"空间 + 文件夹 + 富文本正文"。

结果就是：图片进不了文档、文档吃不下图；同一个知识既不属于图库也不属于 Wiki；用户想做一份"图文并茂的产品介绍"，得分别在两套系统里复制粘贴。这违背了"企业内部知识库"的初衷。

更糟的是：**两套详情页**让前端排版失去了统一性（用户截图里列表、Tab、操作按钮挤在同一区域），任何"融合"都必须在数据层完成，而不是只在 UI 贴一层胶水。

本 Change 引入**「知识条目（KnowledgeItem）+ 内容块（KnowledgeBlock）」**统一抽象，把图片、文档、表格、代码、文件统一收敛到"有序内容块"模型上，做到"一处建模、一处渲染、一处权限、一处缓存"。

## What Changes

1. 引入 `KnowledgeItem`（条目主表）和 `KnowledgeBlock`（有序内容块表）两张新表。
2. `Picture` 不再作为顶层领域对象，只保留为"图片资源"，被 `KnowledgeBlock` 引用（块类型 = `IMAGE` 时引用 `pictureId`）。
3. `DocumentWiki` 在本 Change 中**保留不变**，但加一个适配层：`DocumentWikiAdapter` 把历史文档"自动生成"对应的 `KnowledgeItem + KnowledgeBlock`，新写入强制走 `KnowledgeItem`。
4. 新增 `BlockRenderer`：后端按 `blockType` 字段路由，前端用同一组件渲染所有块类型。
5. 列表视图统一：知识库列表只看 `KnowledgeItem`，按 `type` 过滤（`PICTURE` / `DOCUMENT` / `MIXED`），列表项卡片化、详情三栏化，彻底解决排版混乱。
6. 权限沿用 `WikiSpace` + `WikiFolder` 的可见域，**不**新增权限模型，避免和已有 RBAC 打架。
7. 缓存以"条目粒度"重做，淘汰原 `DocumentWiki` 的散装缓存键。

## Out of Scope

- 协同光标、Yjs CRDT 等实时多人编辑（Stage 2）。
- RAG / 文档切片 / 向量检索（Stage 3）。
- URL 抓取导入、Office 文件解析（Stage 4）。
- AI 智能扩图与知识条目的更深融合（Stage 5，仍可挂在 `KnowledgeBlock.image` 上调现有 AI 能力）。

## Acceptance Criteria

- 一处保存的 `KnowledgeItem`，能在知识库列表、空间首页、搜索结果里一致出现。
- 一个 `KnowledgeItem` 可同时包含"图"和"文"，由 `KnowledgeBlock` 排序呈现。
- 列表卡片化（缩略图 + 标题 + 摘要 + 标签），详情三栏（左侧目录 + 中央 BlockRenderer + 右侧操作面板）。
- 旧 `DocumentWiki` 数据可读，读取路径走适配层，对前端透明。
- 旧 `Picture` 仍可独立检索（图片库入口保留），但详情页可一键"提升为条目"。
- 缓存以条目为粒度失效，5 分钟 TTL + 抖动。
- 既有 `WikiSpace` / `WikiFolder` 权限模型不被破坏。
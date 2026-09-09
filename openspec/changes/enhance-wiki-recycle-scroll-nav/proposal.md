# enhance-wiki-recycle-scroll-nav

## Why

Wiki 页面当前有三处直接的可用性问题，都集中在日常操作路径上：回收站只能逐条「永久删除」，清理几十条删除记录要点几十次确认；文档列表页三栏被固定为一屏高（`wiki-shell` 写死 `calc(100vh - 138px)`），右侧文档列表超过一屏就出栏内滚动条，一页上有两套滚动，阅读体验割裂；左侧空间导航中「公开文档」分组下只有一个名为「公开文档」的空间、「个人文档」分组下只有一个名为「个人区」的空间（前一次 change `enhance-wiki-space-management` 刚把个人空间名统一为「个人区」），分组行与空间行完全重复，展开两层才能看到文件夹。

## What Changes

- **回收站新增批量永久删除**：回收站列表增加复选框多选 + 底部操作条（`已选 N 项 / 全选 / 批量永久删除 / 取消`），交互复用文档列表右栏已有的「管理」模式；接口层循环调用既有单条 `POST /wikiRecycle/permanentDelete`，逐条删除并汇总成功/失败数量，**后端零改动**。批量恢复明确不做。
- **Wiki 列表态滚动结构重排**：三栏保持固定一屏布局，左、右两栏各自保留栏内滚动；中栏自身不再整体滚动——**搜索表单（关键词/匹配模式/空间）与「当前位置」栏固定在可视区顶部，仅文档摘要列表在栏内滚动，分页器常驻底部**；浏览态列表分页从每页 20 条调整为 **15 条**。**预览/编辑态（`preview` / `edit` / `create`）保持现有固定三栏**，中栏内部滚动、「本文大纲」常驻不滚走。
- **空间导航合并重复层级**：全局唯一的公开空间与每用户唯一的个人空间，各自把「分组行 + 空间行」合并为一个可点选根节点——显示为单行「公开文档」「个人文档」，文件夹直接挂其下；「团队文档」分组（可能多空间）不变。点击合并节点按 `spaceId` 选中该空间（不再走 `spaceType` 聚合选中，因空间唯一，行为等价）。

## Capabilities

### New Capabilities

无。本次改动全部落在既有能力上。

### Modified Capabilities

- `wiki-recycle-bin`：新增「批量永久删除」需求——多选回收站条目、二次确认后逐条物理删除并反馈结果；单条恢复/永久删除行为不变。
- `wiki-layout`：新增列表态/预览态差异化的滚动需求——浏览列表时三栏固定一屏、左右两栏各自栏内滚动，中栏内搜索表单与位置栏固定、仅摘要列表栏内滚动、分页器常驻；预览/编辑文档时保持固定视口三栏、右栏大纲常驻。
- `wiki-document-list`：浏览态文档列表每页固定 15 条（原 20 条），右侧导航列表与中栏列表同步受此约束。
- `wiki-navigation-tree`：修改「统一导航树以空间为根」需求——公开/个人空间各自合并为单一可点选根节点，团队空间保持分组。

## Impact

**后端（`cloud/`）**

无任何改动。批量删除由前端循环调用既有 `WikiRecycleController#permanentDelete`（与文档列表既有批量删除 `DocumentWikiListPage#batchDelete` 的做法一致）。

**前端（`cloud_front/`）**

- `src/pages/documentWiki/components/WikiRecyclePanel.vue`：列表项加复选框、底部批量操作条、批量删除确认弹窗与逐条调用汇总逻辑。
- `src/pages/documentWiki/DocumentWikiListPage.vue`：浏览态中栏改结构化滚动（`wiki-shell--browse` 类：中栏 `overflow: hidden`、内容区 flex 化、搜索表单固定），预览/编辑态样式不变；`BROWSE_PAGE_SIZE` 20 → 15。
- `src/pages/documentWiki/components/WikiDocumentList.vue`：列表主体包一层 `browse-list-body`——位置栏固定、a-list 数据区栏内滚动、分页器常驻底部。
- `src/pages/documentWiki/components/WikiSpaceTree.vue`：`treeData` 构建逻辑调整——公开/个人分组且仅含一个空间时合并为单节点（挂该空间的文件夹子树），团队分组保持现状；选中逻辑由 `aggregate:public` + `spaceType` 聚合改为直接选中对应 `spaceId`。

**数据**

无表结构变更、无存量订正。公开/个人空间的唯一性由后端既有约束保障（`ensurePublicSpace` / `ensurePersonalSpaceForUser` 均按 `.one()` 查询）。

**非目标（明确不做）**

- 批量恢复（负责人已确认只做批量永久删除）。
- 回收站后端批量接口（数据量小，循环单条调用即可；如后续量大再立项）。
- 团队文档分组结构的任何调整。
- 预览/编辑态的布局与滚动行为调整。

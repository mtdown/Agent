# Design — enhance-wiki-recycle-scroll-nav

## Context

三处改动的现状锚点（动机见 proposal.md - Why）：

- 回收站面板 `cloud_front/src/pages/documentWiki/components/WikiRecyclePanel.vue`：`a-list` 逐条渲染，每条仅有「恢复」「永久删除」操作，无多选能力；后端 `WikiRecycleController` 只有 `/list`、`/restore`、`/permanentDelete` 三个单条接口。
- Wiki 页面布局 `cloud_front/src/pages/documentWiki/DocumentWikiListPage.vue`：`.wiki-shell` 固定 `height: calc(100vh - 138px)`，三栏均为 `overflow: auto` + 橙色细滚动条；中栏 `centerMode` 有 `browse / preview / create / edit` 四种模式。
- 空间导航树 `cloud_front/src/pages/documentWiki/components/WikiSpaceTree.vue`：`treeData` 按「公开文档（聚合节点）/ 团队文档（分组）/ 个人文档（分组）」构建，公开与个人分组下各只有一个空间节点（公开空间名「公开文档」、个人空间名「个人区」，均由后端唯一性保障）。

## Goals / Non-Goals

**Goals:**

- 回收站支持勾选多条并批量永久删除，交互与文档列表右栏「管理」模式一致。
- 列表态三栏固定一屏：左右两栏各自栏内滚动，中栏结构化滚动（表单/位置栏固定，仅摘要列表内滚、分页器常驻）；预览/编辑态保持固定三栏、大纲常驻。
- 浏览态文档列表每页 15 条。
- 公开/个人空间在导航树中合并为单行可点选根节点。

**Non-Goals:**

- 批量恢复、后端批量接口（负责人已确认不做）。
- 团队文档分组结构、预览/编辑态布局的任何调整。
- 分页查询机制与交互（仅调整每页条数 20 → 15，不改分页协议）。

## Decisions

### D1. 批量删除：前端循环单条接口，不新增后端接口

循环调用既有 `POST /wikiRecycle/permanentDelete`，逐条携带 `confirm: true`，统计成功/失败数量后汇总提示。

- 理由：与 `DocumentWikiListPage#batchDelete`（文档列表既有批量删除）做法完全一致，后端零改动；每条删除仍走完整的服务端权限校验与缓存清理。
- 备选（否决）：新增 `POST /wikiRecycle/batchPermanentDelete` 批量接口。回收站数据量小（逻辑删除的暂存区），单次勾选通常几条到几十条，为低频场景扩后端接口面不划算；若未来量大再立项。

### D2. 回收站多选交互：复用「管理」模式（复选框 + 底部操作条）

`WikiRecyclePanel` 增加本地 `manageMode` 开关：进入后每条目前置 `a-checkbox`，底部出现操作条「已选 N 项 | 全选 | 批量永久删除 | 取消」；确认弹窗标题带数量（`永久删除后不可恢复，确认删除选中的 N 项？`）。切换空间下拉、刷新、删除完成后清空勾选并退出管理态，防止陈旧勾选跨空间误删。

- 理由：与文档列表右栏管理模式的视觉与交互对齐，用户心智一致；`a-list` 结构保持不变，改动最小。
- 备选（否决）：改成 `a-table` + `rowSelection`。会重排版回收站现有条目布局（标题/删除人/删除时间的展示方式），改动面大于收益。

### D3. 滚动模型：三栏固定一屏，中栏结构化滚动（2026-09-09 二次需求变更定稿）

`wiki-shell` 的 `:class` 绑定保留：`centerMode === 'browse'` 时挂 `wiki-shell--browse` 类，但 shell 本体**沿用固定一屏布局**（`height: calc(100vh - 138px)` 不变，左右两栏沿用基础样式 `overflow: auto` 各自栏内滚动），browse 类只作用于中栏：

- 中栏 `.wiki-document-column`：`overflow: hidden`——中栏自身不整体滚动。
- 中栏内容 `.content-section`：`flex: 1; min-height: 0; flex column`；搜索表单（`.search-form`，WikiSearchBar 根）`flex-shrink: 0` 固定。
- `WikiDocumentList` 包一层 `.browse-list-body`（`flex: 1; min-height: 0; flex column`）：位置栏 `flex-shrink: 0` 固定；a-list 数据区（`:deep(.ant-spin-nested-loading)`）`overflow: auto` 栏内滚动；分页器（`.ant-list-pagination`，为 `.ant-list` 直接子元素）常驻底部。
- 媒体查询 `@media (max-width: 900px)`：中栏回 `overflow: auto`、`.content-section` 回 `display: block`，纵向堆叠时随页面滚动。

预览/编辑态（preview/create/edit）不挂类，保持现有固定高 + 中栏整体滚动 + 大纲常驻。

- 理由：表单与位置栏是导航性控件，滚动长列表时保持可见；摘要列表是唯一需要滚动的长内容，滚动范围收窄到数据区本身，一屏内三栏各自独立滚动、互不联动。
- 演进：初版为「三栏整页 flow 滚动」，一版变更为「左右 sticky 吸顶 + 中栏页面滚动」，本版为终稿——两版均由负责人在验收前提出需求变更，最终收敛为「一屏内结构化滚动」。
- 备选（否决）：搜索表单吸顶（sticky）方案——需要处理吸顶偏移与表单遮盖列表首条的间隙问题，且与「左右两栏固定一屏」诉求组合后，不如直接回归一屏布局简单。

### D4. 导航合并：单空间分组折叠为单节点，标签取分组标题

`treeData` 构建规则：某分组下**恰好只有一个**空间时，该分组折叠为一个 `nodeType: 'space'` 节点（key 仍为 `space:<id>`，挂该空间的文件夹子树，可展开），**标签使用分组标题**——公开分组 → 「公开文档」，个人分组 → 「个人文档」（注意：不是空间名「个人区」，负责人明确要求合并后显示「个人文档」）。团队分组不变。选中合并节点即按 `spaceId` 选中该空间，`aggregate:public` 聚合节点与 `spaceType` 选中路径在单空间情形下不再产生。

- 理由：标签取分组标题而非 `space.name`，避免「个人区」这个名字再出现在界面上（用户诉求就是去掉它）；保留 `space:` key 与 `nodeType: 'space'`，则点击选中、文件夹挂载、「新建文档/上传」按钮的 `selectedSpaceId` 判断等既有逻辑全部免改。
- 防御性回退：若分组下出现多于一个空间（正常不可能——`ensurePublicSpace` / `ensurePersonalSpaceForUser` 均按 `.one()` 查询），退回现有「分组行 + 空间行」两层结构，聚合选中路径保留不删。
- 首屏逻辑适配：`expandedKeys` 原来展开全部 group key；合并后公开/个人不再有 group key，改为展开「当前默认选中节点所在的合并节点 + 团队分组」。

### D5. 浏览态分页每页 15 条（2026-09-09 需求变更新增）

`BROWSE_PAGE_SIZE` 常量 20 → 15，同时更新行注释。三种选中方式（单空间 / 文件夹 / 公开文档聚合）共用 `fetchBrowsePage`，一处改动全覆盖；右侧文档导航列表 `folderOutlineDocs = browseDocuments`（同一份分页结果），同步受约束。

- 理由：改常量即生效，分页协议、缓存、翻页交互均不动；15 条/页与一屏三栏布局配合，列表滚动区更贴合可视高度。
- 范围说明：搜索态分页（`useWikiSearch`，每页 10 条）不在本次变更范围。

### D6. 栏内滚动条默认不显形，悬停时染暖色（2026-09-09 视觉微调）

三栏 `.wiki-tree-column` / `.wiki-document-column` / `.wiki-outline-column` 与中栏列表数据区 `.ant-spin-nested-loading` 的滚动条样式从「始终染色」改为「默认透明、hover 时染 `--wiki-accent`」：默认 `scrollbar-color: transparent transparent` + WebKit 滑块透明 + `transition` 平滑过渡；hover 时暖色显形。

- 理由：Windows 浏览器对 `overflow: auto` 元素**始终渲染滚动条**（`scrollbar-gutter: auto` 默认行为），原 `scrollbar-color: accent muted` 让滚动条一直可见、内容"略长一点"也常驻一根灰条；改为 hover 显形后视觉更轻盈。
- 兼顾功能：滚动行为不变（鼠标拖动、键盘聚焦、滚动捕捉都正常），仅 idle 状态不再打扰。

### D7. 浏览态 location-bar 加页码跳转输入（2026-09-09 视觉微调）

`WikiDocumentList` 浏览态 location-bar 右侧追加：`a-input-number`（min=1, max=ceil(total/pageSize), 宽度 90px, 无 controls）+「跳转」按钮。回车或点击触发 `emit('jumpToPage', page)`，父组件 `handlePageJump` 与 `browsePagination.onChange` 共享同一份 `resetManageState + fetchBrowsePage`。

- 理由：方便快速翻页看分页格式效果。
- 范围说明：仅浏览态 location-bar 加（搜索态 location-bar 不显示，按现有结构不变）。

## Risks / Trade-offs

- [批量删除为 N 次串行请求] → 勾选量大时耗时线性增长；回收站量小可接受，失败逐条汇总反馈，结束后统一刷新列表（部分失败时保留失败条目继续可见）。
- [browse 态左右两栏吸顶] → ~~已随二次需求变更废弃~~ 终稿为固定一屏布局，无吸顶；三栏各自滚动互不联动，中栏结构化滚动（表单/位置栏固定、仅摘要列表内滚、分页器常驻），无布局跳变风险。
- [合并节点不再触发 `spaceType` 聚合选中] → 中栏改为按 `spaceId` 分页；因公开空间全局唯一，两种选法列出的文档集合相同，行为无感知差异（已在探索阶段与负责人确认）。
- [模式切换瞬间布局跳变] → browse ⇄ preview 切换时滚动位置与高度模型变化，可能出现一次视觉跳动；现有 Escape/返回列表路径本就伴随数据刷新，跳动可接受，不做平滑过渡动画。

## Migration Plan

纯前端改动，无数据迁移。部署即生效；回滚直接 revert 该分支提交。

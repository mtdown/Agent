# Design: refine-wiki-browse-navigation

## Context

改动集中在 `cloud_front`，涉及 4 个文件：

| 文件 | 现状 | 本轮角色 |
|---|---|---|
| `pages/documentWiki/DocumentWikiListPage.vue` | 三栏容器，持有 `centerMode` / `currentSelection` / `browseDocuments` 等状态 | 主要改动：查询方式、返回逻辑、ESC、右栏管理态、搜索默认值同步 |
| `pages/documentWiki/components/WikiDocumentList.vue` | 中栏：预览头部 + 浏览列表 + 搜索结果列表，两套 `a-list` 各带 5 个操作按钮 | 删除列表卡片操作按钮；预览头部补返回按钮 |
| `pages/documentWiki/components/WikiSpaceTree.vue` | 左栏：`a-tree` + `emitSelection()`，树数据来自 `listFolderTreeUsingGet` | 基本不动（仅确认 `folder` 载荷仍需要） |
| `pages/documentWiki/components/WikiSearchBar.vue` | 搜索表单，空间下拉绑定 `searchParams.spaceId` | 基本不动，默认值由父组件同步 |
| `pages/documentWiki/components/WikiDocumentMoveDialog.vue` | 单文档移动弹窗，`defineExpose({ open })` | 扩展为支持批量 id 列表 |

后端不需要改动：`DocumentWikiQueryRequest.folderId` 已在 `DocumentWikiServiceImpl` 第 94 行用于 `queryWrapper.eq("folderId", folderId)`。

## 决策 1：文件夹文档改由后端按 `folderId` 查询

现状 `refreshBrowseDocuments()`：

```ts
if (folderId) {
  browseDocuments.value = folder?.documents ?? []   // 树缓存
  browseTotal.value = browseDocuments.value.length
  return
}
```

问题不在数据正确性（`listFolderTree` 确实填充了 `documents`），在于**中间栏的数据源挂在了左侧导航的内部缓存上**：树什么时候刷新、刷新的是哪个空间，都会连带影响中间栏，两个关注点被耦合在一起。

改为统一走 `fetchBrowsePage()`，传入 `folderId`：

```ts
const fetchBrowsePage = async () => {
  const { spaceId, folderId, spaceType } = currentSelection.value
  const res = await listDocumentWikiVisByPageWithCacheUsingPost({
    current: browseCurrent.value,
    pageSize: 20,
    sortField: 'editTime',
    sortOrder: 'descend',
    spaceId: spaceId ?? undefined,
    folderId: folderId ?? undefined,
    spaceType: spaceType ?? undefined,
  })
  if (res.data.code === 0 && res.data.data) {
    browseDocuments.value = res.data.data.records ?? []
    browseTotal.value = Number(res.data.data.total ?? 0)
  } else {
    message.error('获取文档列表失败，' + res.data.message)
  }
}
```

**三种选择（空间 / 文件夹 / 聚合）统一每页 20 条并分页。** 因此 `browsePagination` 必须去掉 `if (currentSelection.value.folderId) return false` 这道短路，让文件夹模式也渲染分页器：

```ts
const browsePagination = computed(() => ({
  current: browseCurrent.value,
  pageSize: 20,
  total: browseTotal.value,
  showTotal: (value: number) => `共 ${value} 条`,
  onChange: (page: number) => {
    browseCurrent.value = page
    fetchBrowsePage()
  },
}))
```

`browseTotal` 统一取后端返回的 `total`（不再用本地行数），位置栏"共 N 篇文档"与分页器总数都因此准确。

> 说明：此处的"文件夹列表"指**中间栏选中文件夹后展示的该文件夹下文档列表**，不是左侧空间导航树。左侧导航树由 `listFolderTreeUsingGet` 一次性返回整棵树，本来就不分页、不截断，本次不涉及、保持原样。

**替代方案**：保留树缓存，改为每次点节点先 `spaceTreeRef.refresh()` 再读缓存。否决——多一次树请求，且耦合没解开。

## 决策 2：点击空间树即退出搜索

现状：

```ts
const handleTreeSelect = async (selection) => {
  currentSelection.value = selection
  centerMode.value = 'browse'
  selectedDocument.value = {}
  browseCurrent.value = 1
  if (!isSearchMode.value) {          // ← 搜索态下整段被跳过
    await refreshBrowseDocuments()
  }
}
```

这是"点了没反应"的直接原因。点击左侧树是一个**明确的位置导航意图**，语义上应当压过搜索态，因此改为无条件刷新并清空关键词：

```ts
const handleTreeSelect = async (selection) => {
  currentSelection.value = selection
  centerMode.value = 'browse'
  selectedDocument.value = {}
  browseCurrent.value = 1
  if (isSearchMode.value) {
    searchParams.value.searchText = ''
    searchParams.value.current = 1
  }
  await refreshBrowseDocuments()
}
```

清空 `searchText` 后 `isSearchMode` 自动变 `false`，中栏随即回到 `browseDocuments` 分支。副作用：`onSearchTextChange` 里"清空关键词则刷新浏览列表"的逻辑会被重复触发一次（`a-input` 的 `@change` 不会因程序化赋值触发，故实际不会）——实现时确认 `v-model` 赋值不触发 `@change`，若触发则用 `nextTick` 去重。

## 决策 3：返回的语义与 ESC 优先级

`centerMode` 有四态：`browse` / `preview` / `create` / `edit`。新增 `goBack()`：

```
edit | create  →  cancelInlineEditor()   // 回到 preview（有 selectedDocument）或 browse
preview        →  selectedDocument = {}; centerMode = 'browse'; refreshBrowseDocuments()
browse         →  无操作
```

只有 `preview` 与编辑器态才渲染返回按钮，所以不存在"在 browse 按返回"的路径。

ESC 通过 `window.addEventListener('keydown')` 注册（`onMounted` 注册、`onBeforeUnmount` 移除），必须避免抢走其它组件的按键：

1. 焦点在 `input` / `textarea` / `[contenteditable]` 内 → 直接放行（用户正在输入）。
2. 存在可见的 ant-design-vue 弹层（`.ant-modal-wrap` 非 `display:none`、`.ant-dropdown` 等）→ 放行，让弹窗自己处理关闭。
3. `activeRegion !== 'docs'`（在回收站/空间管理页）→ 不处理。

**替代方案**：用 `a-tooltip` 提示快捷键、或在按钮上加 `accesskey`。否决——`accesskey` 浏览器行为不一致。

## 决策 4：操作职责重新分区

| 位置 | 改动前 | 改动后 |
|---|---|---|
| 中栏列表卡片 | 打开 / 查看 / 仅预览·编辑 / 移动 / 删除 | **无按钮**，标题本身可点击打开 |
| 中栏预览头部 | 移动 / 编辑 / 删除 | ← 返回列表 + 移动 / 编辑 / 删除 |
| 右栏（列表模式） | 只读的文档快捷跳转列表 | + 【管理】按钮 → 勾选态 → 移动 / 删除 / 取消 |

删除列表卡片按钮的理由：`打开` 与 `查看` 完全等价；`移动`、`删除` 属于低频管理动作，放在每张卡片上会主导视觉；而打开单篇文档后头部已经有完整操作集，不存在功能丢失。

右栏管理态只在 `outlineMode === 'list'` 时可用（即中栏是列表且**没有**打开文档），与"打开文档后右栏维持大纲"的约定一致。

## 决策 5：右栏管理态的状态机

在 `DocumentWikiListPage` 新增局部状态，不引入新组件（改动面最小）：

```ts
const manageMode = ref(false)
const checkedDocIds = ref<IdValue[]>([])
```

- `manageMode` 为 `false` 时右栏与现在一致（标题 + 文档快捷跳转），`panel-head` 右侧显示"管理"按钮。
- 点"管理" → `manageMode = true`，每项前渲染 `a-checkbox`，顶部出现"全选"，底部固定操作条 `移动 / 删除 / 取消`。
- **全选只覆盖当前页**可见的文档（即 `browseDocuments`），操作条显示"已选 N 篇"。中间栏三种模式现在都分页，跨页全选不在本轮范围。
- **状态复位**：`manageMode` 与 `checkedDocIds` 必须在 `handleTreeSelect`、`openDocument`、`refreshAll` 时清空，否则勾选会跨位置残留——这是最容易漏的边界，回归测试会覆盖。
- 批量删除：`Modal.confirm` 二次确认后串行调用 `deleteDocumentWikiUsingPost`，统计成功/失败条数并提示，最后 `refreshAll()`。
- 批量移动：`WikiDocumentMoveDialog` 扩展 `open(ids: IdValue[])`，串行调用 `moveDocumentWikiUsingPost`。

**为什么串行而非并行**：后端删除/移动是单条接口，且都会清空间缓存；串行能保证失败时给出明确条数，也避免并发写同一空间缓存。文档量级（单文件夹几十篇）下性能无感知。

**替代方案**：后端新增批量接口。否决——本轮 Non-goals 明确不改后端。

## 决策 6：搜索默认当前空间

`searchParams.spaceId` 随 `currentSelection.spaceId` 同步：

```ts
watch(
  () => currentSelection.value.spaceId,
  (spaceId) => { searchParams.value.spaceId = spaceId },
)
```

边界：选中"公开文档"**聚合节点**时 `spaceId` 为 `undefined`，下拉回落到 placeholder"全部可见空间"——这是正确语义（聚合节点跨多个空间，无法落成单个 `spaceId`）。用户手动改过下拉后，下一次切换空间仍会被覆盖，这是期望行为（"默认当前空间"）。

显示形式沿用 `allSpaceOptions` 的 `区域 / 空间名` 标签，因此公开空间下的默认展示就是 `公开文档 / 公开文档`。

## 决策 7：空间树节点操作入口常驻可见

现状 `.node-op { opacity: 0 }`，只有 `.tree-node:hover .node-op` 时才 `opacity: 1`。后果是文件夹的 `重命名 / 移动 / 删除` 在视觉上等于不存在——负责人实际使用时就是因此没找到重命名入口。

改为默认可见：

```css
.node-op {
  opacity: 0.55;          /* 常驻但弱化，不抢标题视觉 */
  padding: 0 4px;
}
.tree-node:hover .node-op,
.node-op:focus-visible {
  opacity: 1;
}
```

只改 `opacity`，不改布局与 DOM 结构，也不新增菜单项：文件夹节点的 `新建子文件夹 / 重命名 / 移动 / 删除` 与空间节点的 `新建文件夹` 逻辑均已存在（`WikiFolderDialogs.onNodeMenu`），本项纯粹是可见性修复。

**替代方案**：做成常驻的图标按钮组（新建/重命名/移动/删除各自一个图标）。否决——导航列宽度只有 230–280px，四个图标会挤压文件夹名。

## 风险与回滚

| 风险 | 缓解 |
|---|---|
| 分页后"全选"只覆盖当前页 | 管理态全选仅勾选当前页可见文档，操作条显示"已选 N 篇"；不做跨页全选（本轮 Non-goals：不做全量批量） |
| ESC 与 md-editor 内部快捷键冲突 | 输入态/弹层可见时放行；`document.activeElement` 判断兜底 |
| 批量删除误操作 | 二次确认 + 底部操作条需先点"管理"才出现，非默认可见 |
| 勾选态跨位置残留 | `handleTreeSelect` / `openDocument` / `refreshAll` 三处统一复位 |
| `⋯` 常驻后导航列变拥挤 | 仅微调 `opacity`（0.55），不新增图标；树节点标题本身已有 `text-overflow: ellipsis` 保护 |

回滚：改动全部集中在前端 3 个 `.vue` 文件与 1 个新增测试文件，无后端变更、无数据迁移，直接回退提交即可。

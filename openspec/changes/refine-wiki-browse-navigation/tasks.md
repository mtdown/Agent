# Tasks: refine-wiki-browse-navigation

分支：`feature/wiki-navigation-and-actions开发`（已从最新 `origin/main` 切出并跟踪远端）

## 1. 需求 1 —— 点击子文件夹刷新中间栏

- [x] 1.1 `DocumentWikiListPage.vue` 的 `fetchBrowsePage()` 增加 `folderId: folderId ?? undefined` 参数，`pageSize` 三种模式统一为 20
- [x] 1.2 `refreshBrowseDocuments()` 删除 `folder?.documents ?? []` 的树缓存分支，文件夹与空间/聚合统一走 `fetchBrowsePage()`
- [x] 1.3 `handleTreeSelect()` 去掉 `if (!isSearchMode.value)` 短路：无条件 `browseCurrent = 1` 并 `await refreshBrowseDocuments()`
- [x] 1.4 `handleTreeSelect()` 在搜索态下清空 `searchParams.searchText` 与 `searchParams.current`，使中栏回到浏览分支
- [x] 1.5 确认 `v-model` 程序化清空 `searchText` 不会触发 `onSearchTextChange` 造成重复请求；若触发则用 `nextTick` 去重
- [x] 1.6 `browseTotal` 统一取后端返回的 `total`，保证位置栏"共 N 篇文档"与分页器总数准确
- [x] 1.7 `browsePagination` 去掉 `if (currentSelection.value.folderId) return false` 短路，文件夹模式同样渲染分页器（每页 20 条）

## 2. 需求 2 —— 返回按钮与 ESC 回退

- [x] 2.1 新增 `goBack()`：`edit`/`create` 调 `cancelInlineEditor()`；`preview` 清空 `selectedDocument` 并置 `centerMode='browse'` 后刷新浏览列表；`browse` 空操作
- [x] 2.2 `WikiDocumentList.vue` 预览头部左侧新增"← 返回列表"按钮，`emit('back')` 由父组件接 `goBack`
- [x] 2.3 `DocumentWikiListPage.vue` 在 `onMounted` 注册 `keydown` 监听，`onBeforeUnmount` 移除
- [x] 2.4 ESC 处理需放行：焦点在 `input`/`textarea`/`[contenteditable]`、存在可见 `.ant-modal-wrap` 等弹层、`activeRegion !== 'docs'` 三种情况
- [x] 2.5 返回按钮与 ESC 共用 `goBack()`，保证两条路径行为一致

## 3. 需求 3 —— 操作职责分区（中栏）

- [x] 3.1 `WikiDocumentList.vue` 浏览列表 `#actions` 模板整体删除（打开/查看/仅预览·编辑/移动/删除）
- [x] 3.2 `WikiDocumentList.vue` 搜索结果列表 `#actions` 模板整体删除，保持两处一致
- [x] 3.3 保留卡片标题 `.result-title` 的 `emit('open', item.id)`，作为唯一打开方式
- [x] 3.4 预览头部保留 `移动 / 编辑 / 删除`，HTML 文档的编辑禁用提示保持不变
- [x] 3.5 清理 `WikiDocumentList.vue` 中因删除按钮而不再使用的 props/emits 类型声明

## 4. 需求 3 —— 右栏文档管理与批量操作

- [x] 4.1 `DocumentWikiListPage.vue` 新增 `manageMode` 与 `checkedDocIds` 局部状态
- [x] 4.2 右栏 `panel-head` 在 `outlineMode === 'list'` 时显示【管理】按钮，点击置 `manageMode = true`
- [x] 4.3 管理态下每个文档项前渲染 `a-checkbox`，顶部提供全选/取消全选；**全选仅覆盖当前页**可见文档，底部显示"已选 N 篇"
- [x] 4.4 底部固定操作条：`移动 / 删除 / 取消`，取消时清空勾选并退出管理态
- [x] 4.5 批量删除：`Modal.confirm` 二次确认 → 串行 `deleteDocumentWikiUsingPost` → 统计成功/失败条数并提示 → `refreshAll()`
- [x] 4.6 `WikiDocumentMoveDialog` 扩展 `open` 支持接收 `IdValue[]`，内部串行 `moveDocumentWikiUsingPost`，完成后 `emit('moved')`
- [x] 4.7 批量移动完成后刷新空间树与文档列表
- [x] 4.8 勾选态复位：`handleTreeSelect`、`openDocument`、`refreshAll` 三处统一清空 `manageMode` 与 `checkedDocIds`
- [x] 4.9 打开文档后右栏维持"本文大纲"，不渲染【管理】按钮（由 `outlineMode === 'list'` 条件天然保证，需回归测试锁定）

## 5. 需求 4 —— 搜索默认当前空间

- [x] 5.1 `DocumentWikiListPage.vue` 新增 `watch(() => currentSelection.value.spaceId)`，同步到 `searchParams.value.spaceId`
- [x] 5.2 校验聚合节点（`spaceId` 为 `undefined`）时下拉回落为 placeholder"全部可见空间"
- [x] 5.3 确认首次加载时空间树的默认选中会触发同步，搜索框首屏即显示当前空间

## 6. 需求 5 —— 空间导航操作入口可见性

- [x] 6.1 `WikiSpaceTree.vue` 的 `.node-op` 由 `opacity: 0` 改为默认 `0.55`，悬停/聚焦时为 1
- [x] 6.2 确认文件夹节点菜单仍为 `新建子文件夹 / 重命名 / 移动 / 删除`，空间节点仍仅有 `新建文件夹`，不新增也不删减菜单项
- [x] 6.3 确认 `⋯` 常驻后节点标题仍受 `text-overflow: ellipsis` 保护，长文件夹名不被挤出导航列

## 7. 自动化验证

- [x] 7.1 新增 `cloud_front/wikiBrowseNavigation.test.mjs` 静态回归，至少覆盖：列表卡片无操作按钮、预览头部保留三类操作、右栏管理态相关标记、ESC 监听与放行条件、`folderId` 查询与统一 `pageSize: 20`、`browsePagination` 不再对文件夹短路、勾选态三处复位、`.node-op` 默认非 0 透明度
- [x] 7.2 运行 `node --test *.test.mjs`，确保 cloud_front 全部测试通过（含既有 46 项）
- [x] 7.3 运行 `vue-tsc --build` 类型检查，退出码 0
- [x] 7.4 运行 `openspec validate refine-wiki-browse-navigation --strict`
- [ ] 7.5 本轮遇到的问题按 AGENTS.md 记入 `IssueLog.xlsx`

## 8. 本地服务与人工手测

- [x] 8.1 自动化验证通过后，按 AGENTS.md 执行 `stop-dev.ps1` → `start-dev.ps1`，汇报前后端地址（后端 8123 / 前端 3000，账号 admin / 12345678）
  - 2026-09-09 01:20 复核端口：8123 已被 java（PID 32504）占用、3000 已被 node（PID 34232）占用，说明负责人本地服务正在运行。本轮**只改前端（4 个 .vue）**，未改后端，因此未重启服务——Vite HMR 已加载改动，手测前在浏览器刷新一次即可。若手测发现改动未生效，再执行 `stop-dev.ps1` → `start-dev.ps1`。
- [ ] 8.2 手测：中间栏处于文档预览时点击左侧子文件夹，中间栏回到该文件夹列表；搜索态下点节点同样生效
- [ ] 8.3 手测：预览态点击"返回列表"与按 ESC 均回到列表；编辑态 ESC 先退出编辑；输入框内按 ESC 不返回
- [ ] 8.4 手测：中栏列表卡片无操作按钮；打开文档后头部仍可移动/编辑/删除；右栏在列表模式可管理（全选 + 批量删除 + 批量移动），打开文档后右栏为大纲
- [ ] 8.5 手测：搜索栏空间下拉默认显示当前空间（`公开文档 / 公开文档`），可清除恢复全部可见空间
- [ ] 8.6 手测：不悬停也能看到空间导航节点的 `⋯`；点文件夹的 `⋯` 可见并可用 `重命名`；点空间的 `⋯` 只有 `新建文件夹`

## 9. 上传

- [ ] 9.1 负责人验收完成后执行 `upload.ps1 -Message "feat: ..."` 并确认远端任务分支已推送
- [ ] 9.2 汇报已推送分支，并提醒合并到 `main` 必须由负责人通过 merge request / pull request 完成

# Tasks — enhance-wiki-recycle-scroll-nav

## 1. 前端：回收站批量永久删除

- [x] 1.1 `WikiRecyclePanel.vue` 增加本地管理态：`manageMode` 开关（顶部「管理 / 退出」按钮）、`checkedKeys` 勾选集合、每条目前置 `a-checkbox`、底部操作条（`已选 N 项 | 全选 | 批量永久删除 | 取消`）；验证：进入管理态可勾选/取消勾选，未进入时条目交互与现状一致（`wikiUxRecycleScrollNav.test.mjs` 结构断言通过）
- [x] 1.2 实现批量永久删除：确认弹窗（标题带所选数量）→ 循环调用既有单条 `permanentDeleteUsingPost`（逐条 `confirm: true`）→ 汇总成功/失败数量提示 → 刷新回收站列表；验证：勾选多条删除后所选条目消失、未勾选条目保留（逻辑由测试断言覆盖，页面效果待手测）
- [x] 1.3 勾选状态复位：切换空间下拉、点击「刷新回收站」、批量删除完成后，清空勾选并退出管理态；验证：切换空间后旧勾选不残留（`refreshRecycle` 统一 `resetManageState`，测试断言通过）
- [x] 1.4 未勾选时点击「批量永久删除」给出提示且不发起请求；验证：`message.warning` 提示先选择条目（测试断言通过）

## 2. 前端：Wiki 列表态布局（2026-09-09 二次需求变更定稿：三栏固定一屏，中栏结构化滚动；分页每页 15 条）

- [x] 2.1 `DocumentWikiListPage.vue` 给 `.wiki-shell` 绑定条件类：`centerMode === 'browse'` 时挂 `wiki-shell--browse`（shell 本体沿用固定一屏，左右两栏沿用基础栏内滚动样式）；验证：类名随模式切换正确挂载/移除（模板绑定有测试断言）
- [x] 2.2 中栏结构化滚动（页面侧）：`.wiki-shell--browse .wiki-document-column` 改 `overflow: hidden`，`.content-section` 改 `flex: 1; min-height: 0; flex column`，搜索表单 `.search-form` 加 `flex-shrink: 0`；验证：样式落地并有测试断言，视觉效果待负责人手测
- [x] 2.3 中栏结构化滚动（列表侧）：`WikiDocumentList.vue` 包 `browse-list-body` 容器——位置栏 `flex-shrink: 0`、a-list 数据区 `:deep(.ant-spin-nested-loading)` `overflow: auto` 栏内滚动、分页器常驻底部；验证：样式落地并有测试断言，视觉效果待负责人手测
- [x] 2.4 浏览态分页每页 15 条：`BROWSE_PAGE_SIZE` 20 → 15（含行注释更新）；验证：中栏列表与右侧导航列表单页最多 15 条（测试断言通过，页面效果待负责人手测）
- [x] 2.5 确认预览/编辑/新建态（preview / edit / create）布局与滚动行为与改动前完全一致（固定三栏、中栏整体内滚、右栏大纲常驻）；验证：打开长文档预览并滚动正文，大纲保持可见（负责人 2026-09-09 手测通过）
- [x] 2.6 确认窄屏 `@media (max-width: 900px)` 分支不回归（中栏回 `overflow: auto`、`.content-section` 回 `display: block`，随页面滚动）；验证：900px 以下三栏纵向堆叠且无内容遮挡（随 2.5 一并由负责人验收，无回归反馈）
- [x] 2.7 三栏与中栏列表区滚动条默认不显形、hover 时染暖色（`scrollbar-color` + WebKit 滑块均改透明 + hover 显色，`transition` 0.2s）；验证：未滚动/未悬停时看不到灰条；悬停该栏时滑块染暖色（测试断言覆盖，页面效果待手测）
- [x] 2.8 浏览态 location-bar 加页码跳转输入：`WikiDocumentList.vue` 加 `a-input-number`（min/max=ceil(total/pageSize)）+「跳转」按钮，回车或点击触发 `emit('jumpToPage', page)`；父组件 `handlePageJump` 复用 `resetManageState + fetchBrowsePage`；验证：手测 1~N 页跳转正常、超范围被钳制（测试断言覆盖，页面效果待手测）

## 3. 前端：空间导航合并单节点

- [x] 3.1 `WikiSpaceTree.vue` 的 `treeData` 构建改为：分组下**恰好一个**空间时折叠为单节点——`nodeType: 'space'`、key 为 `space:<id>`、标签取分组标题（公开→「公开文档」，个人→「个人文档」），挂该空间文件夹子树；验证：树中「公开文档」「个人文档」各为一行，文件夹直接挂其下，无「分组行 + 空间行」重复（`singleSpaceRootNode` 实现与调用有测试断言，页面效果待负责人手测）
- [x] 3.2 防御性回退：分组下多于一个空间时保持现有「分组 + 空间」两层结构与聚合选中路径不删；验证：代码审查确认回退分支保留（`else if publicSpaces.length > 1` 与 `personalGroup.length > 1` 分支均在，测试断言覆盖）
- [x] 3.3 首屏逻辑适配：`expandedKeys` 展开默认选中节点所在的合并节点 + 团队分组 key（公开/个人的 group key 不再存在时不引用）；验证：首屏展开状态正确、默认选中行为不变（`groupKeys` 按 `nodeType === 'group'` 过滤现存分组节点，测试断言通过；页面效果待负责人手测）
- [x] 3.4 确认合并节点选中后依赖 `selectedSpaceId` 的既有功能正常：顶部「新建文档 / 上传」按钮出现、文件夹节点 ⋯ 菜单（新建子文件夹/重命名/移动/删除）正常、中栏列表刷新为该空间文档；验证：手测以上入口（负责人 2026-09-09 手测通过）

## 4. 验证与交付

- [x] 4.1 执行 `openspec validate enhance-wiki-recycle-scroll-nav --strict`；验证：无校验错误（通过）
- [x] 4.2 前端类型检查与构建：`npm run type-check` 通过、`vite build` 通过（`pure-build` 11.4s 构建成功，chunk 大小警告为既有提示非错误）
- [x] 4.3 本轮遇到的报错与阻塞记录到项目根目录 `IssueLog.xlsx`（已追加第 95 行：main 基线遗留的过时测试断言及修复）
- [x] 4.4 使用项目根目录 `stop-dev.ps1` → `start-dev.ps1` 启动本地服务，向负责人列出人工测试清单（批量删除、列表态滚动、导航合并三项及回归项）——本地 8123/3000 已在运行（负责人进程，纯前端改动走 HMR 生效），手测清单已随轮次汇报发出
- [x] 4.5 汇报测试结果，等待负责人手测反馈；负责人确认通过后再执行 `upload.ps1` 上传任务分支（负责人 2026-09-09 验收通过，授权上传）

## 回滚方案

纯前端改动，无数据迁移；revert 本分支提交即可完全回滚。

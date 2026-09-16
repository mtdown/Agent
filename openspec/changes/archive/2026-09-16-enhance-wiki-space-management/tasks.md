## 1. 后端：空间重命名

- [x] 1.1 `WikiSpaceService` / `Impl` 新增 `renameSpace(spaceId, name, loginUser)`：前置判断 `type != TYPE_TEAM` 直接拒绝（个人空间与公开空间不可改名）；团队空间放行「管理员 **或** 该空间 spaceRole = admin 的成员」；名称非空校验；验证：对个人空间与公开空间提交重命名均被拒且名称不变，越权调用同样被拒
- [x] 1.2 `WikiSpaceController` 新增 `POST /wikiSpace/rename`；验证：`mvn compile` 编译通过
- [x] 1.3 补充重命名鉴权单测，覆盖六种情形 + 一个「只认 admin 不认 editor」的 QueryWrapper 参数断言；验证：`WikiSpaceServiceImplTest` 17 个测试全绿
- [x] 1.4 确认未改动 `ensurePersonalSpaceForUser` 的查询逻辑；验证：git diff 中该方法的 lambdaQuery 部分无变化（仅 name 字面量改用常量）

## 2. 后端 + 数据：个人空间名统一为「个人区」

- [x] 2.1 `WikiSpaceServiceImpl` 抽出默认名常量 `PERSONAL_SPACE_DEFAULT_NAME = "个人区"`（定义在 `WikiSpaceService` 接口），`ensurePersonalSpaceForUser()` 改为引用该常量；验证：新注册用户登录后个人空间名为 `个人区`
- [x] 2.2 修改 `sql/backfill_document_wiki_space.sql`，`CONCAT('个人区-', u.id)` 改为与常量同名；验证：脚本内不再出现 `CONCAT('个人区-'`
- [x] 2.3 新增 `sql/normalize_wiki_space_personal_name.sql`：`UPDATE wiki_space SET name = '个人区' WHERE type = 0 AND isDelete = 0;`；验证：脚本幂等，可重复执行
- [x] 2.4 确认未引入 `name` 唯一索引；验证：`wiki_space` 表无 `UNIQUE(name)` 约束（未改动建表脚本）
- [x] 2.5 确认左侧导航分组标题仍为「个人文档」（`WikiSpaceTree.vue` 的 `group:personal` 常量未改）；验证：git diff 中不含该行改动

## 3. 前端：右栏管理态独立全选按钮

- [x] 3.1 `DocumentWikiListPage.vue` 将 `toggleManageMode` 拆为 `enterManageMode` / `exitManageMode` / `toggleSelectAll`，顶部按钮只做模式切换
- [x] 3.2 底部 `manage-bar` 在 `移动` 左侧插入 `全选 / 取消全选` 按钮，文案随 `allChecked` 联动，无可选时 disabled
- [x] 3.3 保持既有复位行为（导航 / 翻页 / 打开文档 / 整体刷新后勾选清空）——代码路径未变，手测确认通过

## 4. 前端：左侧导航首屏展开全部分组

- [x] 4.1 `WikiSpaceTree.vue` 在 `isFirstLoad` 分支把 `expandedKeys` 设为实际存在的分组 key（`aggregate:public` / `group:team` / `group:personal` 中非空的那些）外加默认空间 key
- [x] 4.2 确认用户手动收起后不会被后续刷新重新展开——沿用 `isFirstLoad` 守卫，手测确认通过
- [x] 4.3 确认默认选中第一个空间的行为未被破坏——手测确认通过

## 5. 前端：文档空间管理面板

- [x] 5.1 `WikiSpaceManagePanel.vue` 操作列新增「重命名」（非删除态），a-modal 弹窗输入新名称后调用 `POST /wikiSpace/rename`
- [x] 5.2 重命名成功后触发 `fetchManageSpaces` + `emit('changed')`；父组件 `@changed="fetchSpaces"` → 左树随 `props.spaces` 刷新
- [x] 5.3 移除手工填写的用户 ID 输入框，改为远程搜索下拉（防抖 300ms 调 `listUserVisByPageUsingPost`，已是成员的用户置灰不可选）
- [x] 5.4 确认面板仍只列团队空间，未引入创建个人空间入口；验证：管理面板无「创建个人空间」按钮，git diff 中无 `add/personal` 相关改动

## 6. 验证与交付

- [x] 6.1 执行 `openspec validate enhance-wiki-space-management --strict`；验证：无校验错误
- [x] 6.2 后端编译与单测：`mvn compile` 通过（167 源文件）、`WikiSpaceServiceImplTest` 17/17 通过
- [x] 6.3 前端类型检查 `npm run type-check` 通过；`vite build` 在本机 WorkBuddy 环境被安全删除策略拦截（环境特有），真机 `npm run build` 正常
- [x] 6.4 执行 SQL 订正后重启服务，确认左侧导航个人空间节点显示为 `个人区`——负责人手测确认通过
- [x] 6.5 使用项目根目录 `stop-dev.ps1` 后 `start-dev.ps1` 启动本地服务——本机 WorkBuddy 环境的 mvn 解析问题由负责人真机处理，服务已启动并完成手测
- [x] 6.6 将本轮遇到的报错与阻塞记录到项目根目录 `IssueLog.xlsx`（已追加 2 条环境阻塞记录）
- [x] 6.7 向负责人提交测试汇报并列出人工测试清单，负责人页面手测已完成并通过

## 实施结果小结（2026-09-09）

代码已全部落地，自动化验证（后端编译 + 17 个单测 + 前端类型检查 + OpenSpec 严格校验）通过。本机 WorkBuddy 环境特有的 mvn 解析与 vite 清理拦截问题由负责人在真机环境处理后完成页面手测，验收通过。

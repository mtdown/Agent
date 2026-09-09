## Why

文档空间管理目前有三个直接的可用性缺口：右栏管理态的「全选」藏在顶部一个会变身的按钮里（第一次点只是进入管理态，第二次点才全选），用户在底部操作条找不到它；左侧空间导航首屏只展开「第一个空间所在的那一组」，公开 / 团队 / 个人三组要逐个手动点开；而文档空间管理面板无法给空间改名，成员管理还要求手工填写用户 ID。这些问题都集中在 wiki 的日常操作路径上，属于「功能存在但够不着」，修复成本低、收益直接。

## What Changes

- **右栏管理态新增独立的全选按钮**：底部操作条在 `移动` 左侧增加 `全选 / 取消全选` 按钮；顶部按钮回归为纯粹的 `管理 / 退出` 切换，不再兼任全选。
- **左侧空间导航默认展开全部分组**：首次加载时 `公开文档`、`团队文档`、`个人文档` 三个分组节点全部展开（不存在的分组不展开）。
- **文档空间管理新增「重命名」（仅团队空间）**：表格操作列增加 `重命名`，弹窗输入新名称。个人空间名由系统统一持有为 `个人区`，不可改名（含所有者与管理员）。
- **成员管理改为用户下拉选择**：移除手工填写的用户 ID 输入框，改为可搜索的下拉，选项来自系统已有用户。
- **个人空间默认命名统一为「个人区」**：新建的个人空间默认名为 `个人区`，不再带用户 ID 后缀；存量个人空间一并订正为 `个人区`。左侧导航的**分组标题**「个人文档」保持不变，只有分组下的空间节点改名。

> 原提案中的「创建个人空间」已放弃。它会连带要求放宽「每个用户有且只有一个个人空间」的约束，需要同步修改 `ensurePersonalSpaceForUser` 的唯一性假设并把管理面板列表扩展为全类型，改动面超出本次范围。

## Capabilities

### New Capabilities

无。本次改动全部落在既有能力上。

### Modified Capabilities

- `wiki-space`：新增**团队**空间重命名需求；成员添加改为从已有用户列表中选择；个人空间默认名与存量名统一为「个人区」且不可改名。「一人一个个人空间」的既有约束保持不变。
- `wiki-navigation-tree`：新增首屏默认展开全部分组的导航行为要求。
- `wiki-document-actions`：把管理态「全选」的入口位置固定为底部操作条中 `移动` 左侧的独立按钮。

## Impact

**后端（`cloud/`）**

- `WikiSpaceController` 新增接口：`POST /wikiSpace/rename`。
- `WikiSpaceService` / `WikiSpaceServiceImpl` 新增对应方法。
- 重命名鉴权按「**仅团队空间** +（平台管理员 **或** 该团队空间的 admin 成员）」实现，不复用 `checkSpaceEditable`（后者对团队空间放行 editor）。非团队空间（个人 / 公开）一律拒绝，因此不必为个人空间单独实现 owner 校验分支。
- 成员下拉复用既有 `POST /user/list/page/Vis`（管理员权限，`UserQueryRequest` 支持按 `userName` 检索），后端无需新增接口。
- **不触碰** `ensurePersonalSpaceForUser`：它依赖「一人一个个人空间」约束，本次维持原样（风险说明见 `design.md`）。

**前端（`cloud_front/`）**

- `src/pages/documentWiki/DocumentWikiListPage.vue`：底部 `manage-bar` 增加全选按钮，顶部按钮改为纯模式切换。
- `src/pages/documentWiki/components/WikiSpaceTree.vue`：首屏 `expandedKeys` 改为三个分组 key。
- `src/pages/documentWiki/components/WikiSpaceManagePanel.vue`：新增重命名，成员输入框改为远程搜索下拉。

**数据**

无表结构变更，`wiki_space` 现有 `type` / `name` / `ownerUserId` 字段即可支撑。有一次性的存量订正：新增 `sql/normalize_wiki_space_personal_name.sql`，把 `type = 0` 且 `isDelete = 0` 的个人空间名统一置为 `个人区`（同时覆盖回填产生的 `个人区-<userId>` 与懒创建产生的 `个人文档`）。

**前端**

- 左侧导航分组标题「个人文档」（`WikiSpaceTree.vue` 中硬编码）**保持不变**；分组下的空间节点名来自 `space.name`，随本次订正变为 `个人区`。

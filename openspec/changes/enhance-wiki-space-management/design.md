## Context

动机见 `proposal.md - Why`。此处只记录影响方案选择的现状与约束：

- 空间类型常量在 `WikiSpaceService`：`TYPE_PERSONAL = 0`、`TYPE_TEAM = 1`、`TYPE_PUBLIC = 2`；实体字段 `type` / `name` / `ownerUserId` / `isDelete`。
- `WikiSpaceController` 现有 9 个接口，**没有**重命名。
- `listManageTeamSpaces()` 硬编码 `eq(WikiSpace::getType, TYPE_TEAM)`，管理面板因此**只列出团队空间**——公开空间与个人空间都不在其中。本次不改动这个过滤条件，所以新增的重命名实际作用于团队空间。
- `WikiSpaceServiceImpl.ensurePersonalSpaceForUser()` 用 `lambdaQuery()...one()` 定位「该用户的个人空间」，`one()` 在命中多行时会抛 `TooManyResultsException`。本次**不触碰**它，但它隐式依赖「一人一个个人空间」这条约束。
- 前端右栏管理态的全选逻辑（`allChecked` / `selectableDocIds` / 只覆盖当前页）已实现且正确，问题只在入口位置：全选与「进入管理态」复用同一个三态按钮。
- 后端已有 `POST /user/list/page/Vis`（管理员权限，`UserQueryRequest` 支持 `userName` 检索，`UserVis` 含 `id` / `userName` / `userAccount`）。

## Goals / Non-Goals

**Goals:**

- 让右栏管理态的全选成为一个位置固定、语义单一的按钮。
- 让左侧导航首屏一次展开全部分组。
- 让**团队**空间可被改名，入口在文档空间管理面板。
- 让成员添加不再依赖手工填写用户 ID。

**Non-Goals:**

- **不新增个人空间创建**。它会连带要求放宽「一人一个个人空间」约束并改动 `ensurePersonalSpaceForUser`，已明确放弃，理由见 `proposal.md - What Changes`。
- **不动 `ensurePersonalSpaceForUser` 的查询逻辑**，不动「一人一个个人空间」约束（默认名字符串除外）。
- **不改左侧导航的分组标题**：「公开文档 / 团队文档 / 个人文档」是前端硬编码的分组常量，本次只改分组下的空间节点名。
- **不把管理面板列表扩展为全类型**，仍只列团队空间。
- **不开放个人空间重命名**：个人空间名由系统统一持有为 `个人区`，所有者与管理员都不可改（见 D1、D7）。这不是「暂缓」，是明确的范围外。
- **不开放公开空间重命名**：与上面同理，属于有意收窄，不追求「管理员能改一切」的对称。
- 不改动 `wiki_space` 表结构，不新增依赖。
- 不在左侧导航树的节点菜单里加空间重命名。
- 不动回收站、文档移动、搜索等既有流程。

## Decisions

### D1 新建接口 `POST /wikiSpace/rename`，鉴权不复用 `checkSpaceEditable`

- 请求体 `{ id, name }`。
- 放行条件：`space.type == TYPE_TEAM` **且**（`平台管理员` **或** `该空间内 spaceRole = admin 的成员`）。
- `space.type != TYPE_TEAM` 一律拒绝——个人空间与公开空间都不可改名。
- **理由**：`checkSpaceEditable` 对团队空间放行 `admin` 和 `editor` 两种角色，与「只有 admin 成员能改空间名」不一致，直接复用会把编辑者也放进来。
- **关于个人空间**：原本的放行条件包含「个人空间所有者」，现已明确取消。个人空间名改由系统统一持有（见 D7），任何人都不能改，包括所有者和管理员。这样后端只需一条 `type != TEAM → 拒绝` 的前置判断，不必为个人空间单独实现 owner 校验分支。
- **副作用**：公开空间同样改不了名（它没有 owner 也没有成员表）。这与个人空间的处理保持一致，但偏离了既有「管理员绕过成员关系」的口径——属于有意收窄，公开空间名同样是系统持有的固定值。

### D2 重命名入口只加在管理面板，且只覆盖团队空间

- 面板沿用 `listManageTeamSpaces`，不扩展为全类型。后端鉴权与前端入口因此**完全对齐**：面板只列团队空间，后端只放行团队空间，不存在「鉴权允许但 UI 走不到」的空转分支。
- **理由**：把列表扩成全类型需要同时评估「管理员看到所有人的个人空间」的隐私问题，属于独立改动，本次不做；而现在连重命名权限也一并收窄到团队空间，扩列表就更没有必要了。
- **替代方案**：扩展列表 + 加「类型」列 + 按类型分派操作 → 连带改动面超出本次范围，放弃。

### D3 重命名成功后必须刷新空间列表与左侧树

- 前端在重命名成功后触发 `fetchSpaces` + `spaceTreeRef.refresh()`，否则左侧导航树仍显示旧名称。

### D4 成员下拉复用既有用户分页接口，前端做远程搜索

- 前端 `a-select` 开启 `show-search`、`filter-option=false`，输入防抖 300ms 后调用 `listUserVisByPageUsingPost({ current: 1, pageSize: 20, userName: keyword })`；选项文案 `userName（userAccount）`，取值为用户 id；已是成员的用户**置灰不可选**而非从列表隐藏。
- **理由**：后端零改动。置灰比隐藏更可预期——管理员能看到「这个人已经在里面了」，而不是「搜不到这个人」。
- **替代方案**：新增一个「返回全部用户」的接口 → 无谓的后端改动，且用户量增长后有性能隐患，放弃。

### D5 前端把三态按钮拆成两个职责单一的控件

- `toggleManageMode` 拆为 `enterManageMode` / `exitManageMode` / `toggleSelectAll`；底部 `manage-bar` 在 `移动` 左侧插入 `全选 / 取消全选`；顶部按钮只负责进入与退出管理态。
- **理由**：现状是「第 1 次点进管理态、第 2 次点才全选」，且全选控件在顶部而批量操作在底部，手眼分离。
- **不动的部分**：`selectableDocIds` 只取当前页、`checkedDocIds` 在导航 / 翻页 / 打开文档时复位——这些逻辑已经正确，保持原样。

### D6 左侧树首屏展开沿用既有的 `isFirstLoad` 守卫

- 首屏把 `expandedKeys` 设为实际存在的分组节点集合（`aggregate:public` / `group:team` / `group:personal` 中不为空的那些）。
- **理由**：现有 `watch(props.spaces)` 已经用 `isFirstLoad = !prev?.length` 区分首屏，沿用同一守卫即可避免「用户手动收起后又被刷新重新展开」。

### D7 个人空间名统一为「个人区」，常量化 + 一次性订正存量

- 在 `WikiSpaceServiceImpl` 中把默认名抽为常量（如 `PERSONAL_SPACE_DEFAULT_NAME = "个人区"`），`ensurePersonalSpaceForUser()` 引用该常量；同步把 `sql/backfill_document_wiki_space.sql` 的 `CONCAT('个人区-', u.id)` 改为同一名字。
- 新增一次性订正脚本 `sql/normalize_wiki_space_personal_name.sql`：
  `UPDATE wiki_space SET name = '个人区' WHERE type = 0 AND isDelete = 0;`
- **理由**：两处字面量不一致是问题的根源——只改 Java 的话，存量用户看到的仍是 `个人区-<userId>`，只改 SQL 的话，新用户又会得到 `个人文档`。常量化后新增入口不会再分叉。
- **不做唯一约束**：个人空间名不建唯一索引。重名不影响功能（列表按 `ownerUserId` 区分），加约束反而会让「订正存量」这一步撞上冲突。
- **不改分组标题**：「个人文档」作为左树分组名保持原样，避免出现「个人文档 > 个人文档」的同名嵌套。
- **替代方案**：在前端渲染时截断 `-<id>` 后缀 → 只是掩盖问题，数据库里仍是脏数据，且一旦用户通过重命名改了名字就会误伤，放弃。

## Risks / Trade-offs

- **个人空间与公开空间改不了名** → 这是有意的收窄，不是遗漏（见 D1、Non-Goals）。代价是丧失了「管理员能改一切」的对称性；收益是后端鉴权只剩一条分支，且个人空间名可以与 D7 的订正脚本形成闭环。
- **`ensurePersonalSpaceForUser` 的 `.one()` 是隐式约束** → 本次不触碰，但要在改动说明里标注：任何未来放开「一人一个个人空间」的改动，必须同步把 `.one()` 改为 `.last("limit 1").one()`，否则 `listVisibleSpaceVis()` 会整体抛异常、整个 wiki 打不开。
- **重命名后左侧树不刷新** → 见 D3。
- **用户量大时下拉首屏慢** → 分页 20 条 + 关键字检索，不一次性加载全量用户。
- **团队空间允许重名** → 不引入唯一约束，UI 上以列表顺序区分。
- **存量订正会覆盖用户自定义的个人空间名** → 因个人空间已明确不可改名（D1），当前与可预见的未来都不存在自定义名，风险为零。该安全性依赖 D1 持续成立，见 Migration Plan。

## Migration Plan

无表结构变更。有一次性的存量数据订正：

- 执行 `sql/normalize_wiki_space_personal_name.sql`，把 `type = 0 AND isDelete = 0` 的 `name` 统一置为 `个人区`。脚本幂等，可重复执行。
- **订正是安全的**：个人空间名已明确为系统持有、任何人都不能改（D1 + D7），因此不存在「用户自定义名被冲掉」的风险。
  - 这条安全性**依赖 D1 持续成立**。若将来重新开放个人空间重命名，本脚本必须同步收窄为只订正 `name` 匹配 `个人区-%` 或 `个人文档` 的行，否则会把用户改过的名字冲掉。
- 部署顺序：先执行 SQL 订正，再发后端。反过来的话，新注册用户在 SQL 跑完前仍会拿到旧默认名（影响极小，重启前只是名字不同）。单独回滚后端只会让「重命名」失效，不影响既有的浏览、搜索、移动、删除链路。
- 回滚：整体 revert 本分支即可。数据侧把 `name` 改回即可，原名可从 `ownerUserId` 反推（`个人区-<ownerUserId>` 是可重建的）。

## Open Questions

- 公开空间是否后续开放重命名：当前与团队空间一并走「非 TEAM 即拒绝」的判断。若将来要放开，需单独为公开空间补一条「仅管理员」分支，并同时评估 `sql/normalize_wiki_space_personal_name.sql` 是否受影响（该脚本只作用于 `type = 0`，不受影响）。

# 交接文档：wiki-first-refactor

> **M4 更新（2026-09-05）**：本文件以下为 M4 执行前快照。M4 已实现拆分与导航，生产构建通过；图库详情遇到后端 `StpLogic type=space` 未初始化异常，已停止后续测试，等待用户确认修复。续作优先阅读 **m4-result.md** 和最新 tasks.md，勿重复拆分。

> 更新时间：2026-09-05 傍晚（M5 回归完成，等用户确认合并）
> 当前状态：34 任务完成 32（5.7 为汇报+用户确认项）；M5 期间修复了用户发现的 sa-token 冷启动缺陷（StpKitRegisterConfig）。
> 剩余动作：用户确认 → 按建议拆分提交 → 合并 main（需用户明确授权）。
> **M4 进度说明**：4.1–4.4（组件拆分/路由迁移/导航重构）由用户在会话间自行完成，4.5 走查由助手验证通过；components 目录现为 8 文件（含 WikiFolderDialogs/WikiDocumentMoveDialog/useWikiSearch/wikiShared），ListPage 281 行。

---

## 一、状态总览

- **分支**：`feature/wiki-first-refactor`（从 main b893b81 切出），**全部工作尚未 commit**（提交需用户授权）。
- **进度**：34 任务完成 32（M1–M5 全部完成，唯 5.7 等用户确认合并），勾选状态见本目录 `tasks.md`。
- **严格模式校验**：规划工件全部有效（proposal/specs×2/tasks，`openspec validate wiki-first-refactor --strict` 通过）。
- **待人工抽查（M3/M4 遗留，自动化点击/悬浮受限）**：
  1. 文件夹 ⋯ 菜单 →「新建子文件夹」完整链路（菜单能开、弹窗机制已验证，仅这一跳未点通）；
  2. 回收站 Tab：选空间 → 列表/恢复/永久删除；
  3. ⋯ 菜单的重命名/移动/删除（可删公开空间里的测试文件夹 `M3工具栏文件夹` 顺手验证）；
  4. 侧栏/头部「文件与图库」悬浮子菜单 → 公共图库 点击穿透（IAB 不支持 hover 弹层；直接访问 /gallery 已验证功能正常）。
- 粘贴图片已由用户本人人工验证通过（存在文档「M2 Markdown 验收 1788527602554」，内含 COS 图）。

## 二、环境与运行

| 项 | 值 |
|---|---|
| 后端 | Spring Boot 8123 端口，`--spring.profiles.active=local`，jar 在 `cloud/target/cloud-0.0.1-SNAPSHOT.jar` |
| 前端 | Vite dev 3000 端口（127.0.0.1） |
| MySQL | Docker 容器 `mysql`，宿主 3307 → 容器 3306，库 `Cloud`，root/1234 |
| Redis | Docker 容器 `redis-vector`，6379 |
| 测试账号 | admin / 12345678（超级管理员） |
| 启停 | `powershell -ExecutionPolicy Bypass -File .\start-dev.ps1` / `stop-dev.ps1` |

**注意**：
- Windows 下运行中的后端会锁住 jar，重新 `mvn package` 前必须先停 8123 进程（stop-dev.ps1 或 taskkill）。
- 后端启动依赖 MySQL/Redis 容器先就绪（start-dev.ps1 会告警）。
- 若浏览器页面整页空白但 Vite 无编译错误：多为 **Vite 陈旧转换缓存**（import 与 body 新旧混排），对该 .vue 文件做任意一次内容变更触发重转换即可（M3 已踩坑，IssueLog 2026-09-05 11:40）。

## 三、已完成工作明细

### M1 后端数据层
- 建表 `wiki_attachment`（文件模块雏形：id/wikiSpaceId/documentId可空/fileName/url/fileSize/mimeType/fileHash/userId/软删），SQL：`cloud/sql/create_table_wiki_attachment.sql`。
- `document_wiki` 补 7 列（contentFormat 默认 plain、sourceType 默认 NATIVE、sourceUrl、contentHash、contentVersion 默认 1、visibility 默认 SPACE、metadataJson），SQL：`cloud/sql/alter_table_document_wiki_wikifirst.sql`。**两脚本已在开发库 Cloud 执行，其他环境需重跑。**
- 新增：`WikiAttachment` 实体 / `WikiAttachmentMapper` / `WikiAttachmentService(+Impl)`；接口 `POST /api/documentWiki/image/upload`（file + spaceId + 可选 documentId，返回 COS URL 字符串）。
- 关键实现点：**`transferTo()` 之后不可调 `multipartFile.getBytes()`**（Tomcat 临时文件已被移动，FileNotFoundException），fileHash 改为对自有临时文件算 MD5（`md5Hex(File)`）。
- `DocumentWiki` 实体/Add/Edit DTO 扩字段；`validDocumentWiki` 增加 contentFormat/sourceType 白名单与长度校验；新建文档默认 `contentFormat=markdown`（Controller add 分支）。
- `DocumentWikiVis` 补 `contentFormat`（M2 期间发现遗漏并修复，否则详情接口不返回格式）。
- 单测：`WikiAttachmentServiceTest`（6 例）+ `DocumentWikiServiceImplTest`（+3 例）= 13 例全绿；curl 实测上传/反向用例全过。

### M2 编辑器与渲染
- 依赖：`md-editor-v3@^6.5.6`。**注意 v6 编辑区是 CodeMirror（contenteditable），不是 textarea**；页面唯一的原生 textarea 是"摘要"框（自动化测试时别输错框）。
- `components/DocumentWikiEditor.vue`：正文区 textarea → MdEditor（zh-CN、480px）；`:on-upload-img` 调上传接口后回调 URL 插入；提交固定 `contentFormat:'markdown'`。
- `DocumentWikiDetailPage.vue`：`contentFormat==='markdown'` 用 MdPreview 渲染，否则原纯文本样式。
- 前端 API 增量为**手工叠加**（详见第五节，此约束已被用户新配置缓解）。

### M3 树形结构重做
- 新增 `src/pages/documentWiki/components/WikiSpaceTree.vue`（411 行）：三分组（公开/团队/个人）→ 空间根 → parentId 递归文件夹；a-tree 展开状态本地维护；节点 hover ⋯ 菜单（新建子文件夹/重命名/移动/删除，弹窗内置）；CRUD 后局部刷新并维持选中；`defineExpose({ refresh })` 供父组件调用；选中通过 `emit('select', {spaceId, folderId, folder})` 上抛。
- `DocumentWikiListPage.vue` 969 → 794 行：删区域 Tab/拍平渲染/文件夹 CRUD；Tab = 文档/回收站/管理；右侧浏览态（选中空间+文件夹过滤 + 位置栏）与搜索态（关键词/匹配模式/空间）双模式；回收站改独立空间下拉。
- **已知遗留**：ListPage 794 行 > 300，按 tasks.md 3.5 约定剩余拆分（WikiDocumentList/WikiRecyclePanel/WikiSpaceManagePanel/WikiSearchBar）顺延 M4。

### 用户在会话间自行完成的相关修复（重要，勿覆盖）
- `cloud_front/openapi.config.js`：导出 `openApiConfig`（含 Basic 认证构造 `buildOpenApiAuthorization`，读 `DOC_PASSWORD`/`OPENAPI_AUTHORIZATION` 环境变量）+ **`customType` 钩子把 int64 映射为 `string | number`**——这根治了"重新生成覆盖雪花 ID 字符串类型"的历史坑；直接执行该文件仍可生成。
- `application-local.yml`（gitignored）：knife4j `basic.enable: false`（本地文档接口免认证，便于生成）；配套测试 `cloud/src/test/java/com/et/cloud/config/OpenApiLocalProfileTest.java`。
- `GlobalExceptionHandler`：补 MissingServletRequestParameter/Part、MethodArgumentTypeMismatch、Multipart 等异常 → PARAMS_ERROR。
- 上述文件已在工作区（未提交），提交时一并纳入。

## 四、Git 工作区清单（截至交接时）

- 改动（M）：后端 8 文件（Controller/DTO×2/GlobalExceptionHandler/DocumentWiki/DocumentWikiVis/ServiceImpl/测试）；前端 11 文件（package*.json/openapi.config.js/api×6/DocumentWikiEditor/DetailPage/ListPage）；另有 .idea×3、IssueLog.xlsx、cloud/project.txt。
- 新增（??，本任务）：`cloud/sql/` 两个 SQL、后端 WikiAttachment 四件套、`OpenApiLocalProfileTest`、`cloud_front/src/pages/documentWiki/components/`、`openspec/changes/wiki-first-refactor/`、`openspec/changes/archive/`。
- 与本任务无关的既有未跟踪：AGENTS.md、start/stop-dev.ps1、tmp/、diag.txt、docs/项目评估报告.md、.workbuddy/、IssueLog.xlsx.inspect.ndjson、暂存的 .agents/skills/*（切分支时带入索引，提交时注意分开）。
- 建议提交切分：后端 M1 一个 commit、前端 M2 一个、M3 一个（或按用户习惯），**commit 与合并 main 均需用户明确授权**（AGENTS.md 第 2/6 条）。

## 五、约定与坑（新会话必读）

1. **API 类型**：历史教训——整体 `npm run openapi` 曾覆盖手工 ID 类型并重命名回收站函数（IssueLog 2026-09-04 20:05）。现已由用户的 `customType` 钩子根治，但**重新生成前先跑 build-only 对比**，且生成依赖后端 8123 在跑 + `DOC_PASSWORD` 环境变量（或 local 配置 basic=false 时免认证）。
2. **数据库列名是 camelCase**（`map-underscore-to-camel-case: false`），写 SQL/实体注意。
3. **接口规范**：所有 wiki id 在前端一律 `string | number`（雪花精度），新字段沿用。
4. **测试纪律**（AGENTS.md 4）：每模块 = 开发→测试→汇报→**等用户确认**；测试失败先汇报再修；连续修复需用户授权。
5. 浏览器自动化（IAB）在本项目环境后期点击分发不稳定（坐标命中但事件不触发、locator actionability 超时）；`fill` 对原生输入有效、对 CodeMirror 需 `type()`；可靠模式是"evaluate 量 rect → cua.click"或 dom_cua 节点点击，必要时安排人工抽查。

## 六、M4/M5 待办要点（详见 tasks.md）

- **M4**：拆出 `WikiDocumentList.vue`/`WikiRecyclePanel.vue`/`WikiSpaceManagePanel.vue`/`WikiSearchBar.vue`（`WikiSpaceTree.vue` 已存在，收口即可）；ListPage 降为组合层 ≤300 行；路由 `/`→重定向 `/documentWiki`，图库页面迁 `/gallery` 前缀（只改引用不改功能）；`GlobalSider.vue`/`GlobalHeader.vue` 以 Wiki 为主、图库次级入口、移除"创建团队"推广入口、管理员菜单保留。
- **M5**：全链路手测（登录→大树→建文档→贴图→渲染→删除→回收站恢复）、图库回归、老 plain 兼容、7 字段落库核对、IssueLog 汇总、README 增补"11. Wiki-First 改造"、汇报后**用户确认才能合并 main**。
- M4 动路由前先看 `src/router/index.ts` 与 `access.ts` 登录守卫的跳转目标是否引用 `/`。

## 七、证据与测试数据

- 截图：`tmp/gui-test-screenshots/t1~t4`（编辑器/详情 markdown/老 plain/M3 树）。
- 遗留测试数据（可清理）：公开空间文档「M2 粘贴图片验证文档」「M2 Markdown 验收 1788527602554」「M2 Plain 兼容 1788527660634」「最终回归文档825932」；文件夹「M3工具栏文件夹」（建议人工用它验证删除菜单）。
- IssueLog.xlsx 共 33 行，本任务相关 7 条（含 1 条暂缓：自动化点击劣化）。

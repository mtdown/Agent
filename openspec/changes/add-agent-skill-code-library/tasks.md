# Tasks

## 1. 数据契约与版本库

- [ ] 1.1 定义 Skill、代码项、不可变版本、共享授权、验证结果和执行记录的数据结构及迁移脚本；验证建表可重复执行且旧会话/知识库表不变。
- [ ] 1.2 实现 Skill 结构 schema 与代码入口契约校验，包含字符串 ID、参数类型、步骤类型和固定代码引用；验证非法字段、缺失版本及越界参数被拒绝。
- [ ] 1.3 实现 Skill 草稿、启用、停用、版本和共享 API；验证仅所有者可编辑，草稿不可进入可用目录，受共享者只读。
- [ ] 1.4 实现代码项创建、在线编辑、发布版本、共享及版本定位 API；验证旧版本内容不可变、新版本不自动改写 Skill 引用。
- [ ] 1.5 记录上述 API 与存储迁移说明；核对文档中的权限和版本示例能由接口测试复现。

## 2. 隔离代码执行与样例验证

- [ ] 2.1 建立独立 Python/Java 执行组件、每用户工作目录及资源限额配置；验证两个用户的目录互不可见，路径遍历和符号链接越界被拒绝。
- [ ] 2.2 实现 Python 入口与 JSON 输入输出，并运行成功、异常、超时和过大输出样例；验证聊天服务在任务超时时仍可响应。
- [ ] 2.3 实现 Java 入口、版本编译及缓存，并运行成功、编译失败、异常和超时样例；验证同一版本可重复调用且缓存不改变结果。
- [ ] 2.4 实现代码版本样例验证与诊断记录；验证通过版本可绑定 Skill 草稿，失败版本保留错误且不能绑定。
- [ ] 2.5 写明代码入口、可用标准库、执行限额与部署依赖；用示例 Python/Java 代码核对文档步骤。

## 3. 资源代理与执行授权

- [ ] 3.1 实现以当前登录用户为身份的知识库读写代理，复用空间和文档操作权限；验证失权、只读空间修改及他人空间访问被拒绝。
- [ ] 3.2 实现本人历史会话读取、搜索、重命名及经确认删除的代理；验证不能读取他人会话、未确认不能删除、原始单条问答不能编辑。
- [ ] 3.3 实现 Skill/代码资源代理及依赖权限复核；验证共享 Skill 不继承创建者权限、私有代码缺失时报错、失权后立即拒绝。
- [ ] 3.4 实现按固定版本执行 Skill 步骤、失败停止和记录；验证新代码版本不会被旧 Skill 自动使用，错误能定位到步骤及版本。
- [ ] 3.5 更新资源代理的权限契约文档，并用跨用户及操作级测试核对每个资源类别。

## 4. Agent 提示词与自主撰写

- [ ] 4.1 编写固定 `skill_system` 提示词及目录构造，限制目录长度并按当前用户/启用状态过滤；验证草稿、私有及失权条目不会出现在不应出现的目录中。
- [ ] 4.2 增加按 `id@version` 读取完整 Skill 的 Agent 工具与适用条件判断；验证无匹配回到普通对话，匹配时才加载完整步骤。
- [ ] 4.3 编写 Agent 的 Skill 撰写规范与结构化草稿工具；验证成功任务可生成含适用条件、参数、步骤、失败处理和样例的草稿，且不会自动启用。
- [ ] 4.4 支持 Agent 创建并发布代码、运行样例、绑定通过验证的固定版本；验证失败样例不绑定且诊断可见。
- [ ] 4.5 记录提示词版本与行为样例；用提示注入、误匹配、版本缺失和普通对话回退用例核对固定规则。

## 5. 前端卡片库与编辑流程

- [ ] 5.1 在“新聊天”下新增“Skill 库”“代码库”入口及右侧卡片列表；验证名称、简介、空状态和返回聊天均正确展示。
- [ ] 5.2 实现 Skill 详情、字段编辑、版本、启停、共享和草稿确认界面；验证未确认草稿不能执行、共享用户没有编辑入口且后端仍拒绝越权请求。
- [ ] 5.3 实现 Python/Java 代码编辑、发布、样例验证、版本与引用跳转界面；验证代码位置 `id@version#entrypoint` 可追溯，发布新版不改变旧 Skill 展示。
- [ ] 5.4 完成前端类型检查和构建，并核对 19 位 ID 全程作为字符串处理；记录构建命令与结果。

## 6. 集成、性能与人工验收

- [ ] 6.1 运行 OpenSpec 严格校验、相关后端测试与前端构建；按 AGENTS.md 第 8 节检查接口/表结构变化、隐式假设和高风险 diff，并对 2–3 个关键判断做变异检验，记录每项结果及 IssueLog。
- [ ] 6.2 在目标部署环境量测 Python/Java 首次及重复执行耗时、峰值内存和并发聊天延迟，按机器预算确定限额；验证达到上限时聊天仍可响应，并记录配置与测量结果。
- [ ] 6.3 自动化结果汇报后，使用当前任务分支的 `stop-dev.ps1` 再 `start-dev.ps1` 启动服务，列出测试地址、账号前提及 Skill/代码库、草稿启用、版本固定、资源授权与失败提示的负责人手测清单。
- [ ] 6.4 等待负责人返回页面手测结果；只有手测通过后记录结论并标记完成。失败时先登记 IssueLog、汇报原因与修复方案，得到确认后才进入下一轮修复测试。
- [ ] 6.5 验证回滚开关能关闭 Skill 自动选择和代码执行入口，保留 Skill/代码版本与用户文件，原有 Agent 对话及 RAG 引用仍可用；记录回滚演练结果。
- [ ] 6.6 记录各阶段命令、通过/失败项、问题、修复结论和最终 Result；未做变异检验的测试不得记为“功能已验证”，负责人手测前不得完成最终 Result。
- [ ] 6.7 负责人确认自动化和人工验收结果后，按项目 `upload.ps1` 上传任务分支；验证远程任务分支可供发起 PR，不合并 `main`。

### Verification / Result（2026-10-17，本轮暂停）

- 开始任务 1.1：新增数据库迁移脚本、MySQL 迁移测试及测试依赖，尚未完成生产环境自动执行迁移的接线，因此 1.1 保持未完成。
- `mvn -q -Dtest=AgentLibrarySchemaTest test` 首次按 TDD 预期失败：找不到 `agent-skill-library.sql`；新增脚本后第二次运行在 Testcontainers 启动 MySQL 前报 `Could not find a valid Docker environment`，未进入 SQL 断言。
- 本机 `docker info` 能读取 `desktop-linux` 引擎，初步判断 Testcontainers 选到了 Docker Desktop 的默认 npipe 而非当前 Linux 引擎。拟在下一轮显式设置 Docker endpoint 后重试；按 AGENTS.md 第 4 节等待负责人确认，不自行进入下一轮修复—测试。
- 两项本地环境问题已记录到根目录 `IssueLog.xlsx`。本轮没有可报告的功能测试通过项，未做变异检验，未启动服务，也未上传分支。
- 回滚：本轮仅有未提交的迁移脚本、测试与测试依赖，移除这些新增文件和依赖即可回到本轮前状态；未变更现有业务表。

### Verification / Result（2026-10-17，负责人同意继续验证后）

- 显式设置 `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine` 后重跑 `mvn -q -Dtest=AgentLibrarySchemaTest test`，仍在启动容器前失败：Tests run 1, Errors 1。
- 调试日志确认 Testcontainers 1.20.6 请求 `/v1.32/info`，当前 Docker Engine 29.2 返回 HTTP 400；Docker Engine 29.2 的最低 API 版本为 1.44。前轮“只需改 endpoint”的初步判断已更正。
- 已更新 `IssueLog.xlsx` 中同一问题记录。迁移脚本仍未经过 MySQL 执行，未做变异检验，任务 1.1 保持未完成。按 AGENTS.md 第 4 节等待负责人确认下一轮修复测试方案。

### Verification / Result（2026-10-17，改用现有开发库）

- 负责人决定直接使用开发数据库。已移除 Testcontainers 测试依赖及容器测试，新增仅在 `application-local.yml` 指向 `localhost:3307/Cloud` 时执行的开发库集成测试。
- `AgentConversationStore.initializeTables()` 增加 `agent-skill-library.sql`，沿用现有 `DataSource` 与启动建表机制；SQL 只创建 9 张新表，不修改旧会话及知识库表。
- `mvn -q -Dtest=AgentLibrarySchemaIT test` 先因未接入脚本而预期失败（0/9 张表），接入后通过；验证两次初始化、9 张新表、旧会话表数据量和 DDL 不变。尚未做变异检验，因此不将其记作最终功能验证。
- 已将 Testcontainers 阻塞问题在 `IssueLog.xlsx` 更新为已修复；新代码尚未提交，服务尚未启动，任务 1.1 在最终确认前保持未完成。

### Verification / Result（2026-10-17，API 测试暂停）

- 新增 Skill 定义与代码入口校验器，验证字符串 ID、固定版本引用、参数边界、语言及输入输出结构；`mvn -q '-Dtest=AgentSkillDefinitionValidatorTest,AgentCodeVersionValidatorTest' test` 退出码 0。首次合并运行命令因 PowerShell 未给逗号参数加引号而在 Maven 启动前报 ParserError，已修正并记入 IssueLog。
- 新增版本存储与登录用户 API 初稿。`mvn -q -Dtest=AgentLibraryStoreIT test` 退出码 0；集成测试在开发库事务中创建数据并回滚，覆盖草稿不可用、所有者编辑权限、共享只读、代码新版本不改旧引用。
- `mvn -q '-Dtest=AgentLibraryControllerTest,AgentSkillDefinitionValidatorTest,AgentCodeVersionValidatorTest' test` 运行 9 个测试、1 个错误：`AgentLibraryControllerTest` 的 Mockito stub 将原始 `7L` 与 `anyString()` 混用，报 `InvalidUseOfMatchers`。这是测试夹具错误，接口断言未执行；已登记 IssueLog，建议下一轮改用 `eq(7L)` 后重跑，等待负责人确认。
- 1.2–1.4 仍为开发中，API 尚无通过的自动化结论；未做变异检验，未启动服务或上传分支。

### Verification / Result（2026-10-17，继续开发后再次暂停）

- 已按负责人指示将 Mockito stub 的 `7L` 改为 `eq(7L)`；相关 9 个 API/校验器单元测试重新运行，退出码 0。IssueLog 对应问题已更新为已修复。
- 前端新增 Skill 库/代码库侧栏入口、卡片、详情及在线编辑初稿；`npm run type-check` 退出码 0。尚未执行前端构建或页面手测。
- Agent 循环增加独立总览消息和按固定 `id@version` 读取完整 Skill 的工具声明；`mvn -q '-Dtest=AgentLoopTest,OpenAiStreamModelTest' test` 退出码 0。Cloud 新增仅供内部密钥读取当前用户已启用版本的接口；`mvn -q '-Dtest=AgentLibraryInternalControllerTest,AgentLibraryStoreIT' test` 退出码 0。
- Cloud 向 Agent 传递当前用户的简要目录时，`mvn -q '-Dtest=AgentPreviewControllerTest,AgentSkillCatalogTest' test` 运行 5 个测试、4 个错误：测试夹具无法 mock `final AgentSkillCatalog`，请求透传断言未执行。已登记 IssueLog；建议下一轮测试改用真实 Catalog 包裹模拟 Store，等待负责人确认后再修改并重测。
- Cloud Agent 尚未接通内部 `get_skill` HTTP 调用，代码样例执行器及资源代理仍未实现；本轮未做变异检验、未启动服务或上传分支。

### Verification / Result（2026-10-17，负责人确认继续测试）

- 将 `AgentPreviewControllerTest` 中对 final `AgentSkillCatalog` 的模拟改为真实 Catalog 与模拟 Store，并用测试条目核对用户目录摘要透传。
- 重跑 `mvn -q '-Dtest=AgentPreviewControllerTest,AgentSkillCatalogTest' test`：5 个测试，0 失败、0 错误、0 跳过。已将对应 IssueLog 记录更新为已修复。
- 本次只确认了 Cloud 端目录构造与请求透传；Agent 的内部 `get_skill` HTTP 接线、执行器与资源代理仍在开发中。未做变异检验，因此不把本组测试记为最终功能验证。
- 扩大验证：Cloud 相关测试套件 17 个、AgentLoop/OpenAiStreamModel 测试 13 个，全部 0 失败/错误/跳过；前端 `npm run type-check`、OpenSpec 严格校验均退出码 0；前端 `npm run build` 退出码 0（Vite 报大 chunk 提示）。
- Cloud 与 cloud_agent 的 `mvn -q -DskipTests package` 均在 Spring Boot `repackage` 重命名 JAR 时退出码 1。只读进程检查发现现有本地服务正从两个模块的目标 JAR 路径运行，判断为 Windows 文件占用。已登记 IssueLog；建议下一轮先用项目 `stop-dev.ps1` 停止服务，再打包，最后用 `start-dev.ps1` 恢复服务。按 AGENTS.md 第 4 节等待负责人确认，不自行进入下一轮修复—测试。

### Verification / Result（2026-10-17，负责人确认 JAR 占用）

- 按确认方案执行 `stop-dev.ps1`，8123、8124、3000 均释放；重跑 Cloud 与 Agent 的 `mvn -q -DskipTests package` 均退出码 0，确认本次重命名失败由运行中的目标 JAR 占用导致。
- `start-dev.ps1 -NoTunnel -NoPause` 退出码 0，重新构建并启动后端、Agent、前端；启动脚本报告 8123、8124、3000 已监听。IssueLog 对应构建问题更新为已修复。
- `start-dev.ps1` 原本就会在打包前清理本地服务；单独执行 `mvn package` 不经过该步骤。负责人要求把停服固定为本地打包流程的前置动作，后续需明确统一入口与适用范围。

### Verification / Result（2026-10-17，负责人反馈网页 Agent 手测问题）

- 负责人要求网页 Agent 把 Skill 和代码分别写入库时，Agent 回复无法访问本机环境，并只生成通用安装脚本模板，未执行库操作。
- 静态定位：Cloud 请求已包含 `userId` 与 `skillCatalog`，但 Agent 侧 `AgentController.Ask` 未接收并传给 `AgentLoop`；`AgentApplication` 注册空工具表，故没有真实 `get_skill`、创建 Skill/代码、发布、验证或执行能力。现有前端 API 与内部读取端点不能由模型直接调用。
- 已登记 IssueLog。建议按当前用户身份接通 Agent ↔ Cloud 的工具链，先完成目录与固定版本读取，再提供创建 Skill/代码及样例验证、执行工具；每个工具检查权限与结果，失败明确反馈。按 AGENTS.md 第 4 节，等待负责人确认修复方案后进入下一轮实现与测试。

### Verification / Result（2026-10-17，Agent 写库接线本轮暂停）

- Agent 接收 Cloud 的 `userId`/`skillCatalog`，注册 `get_skill`、`create_skill`、`create_code` 工具；Cloud 内部接口按密钥和用户 ID 调用已有 Store。创建 Skill 返回草稿 ID，创建代码返回版本 1 与 `UNVERIFIED`，失败时工具结果明确标记未保存。
- TDD 定向测试：Cloud 21 个、Agent 22 个，均 0 失败/错误/跳过；OpenSpec 严格校验和 Docker Compose 语法检查通过。将写入结果和内部密钥判断各改坏一次，分别出现 1 个和 2 个测试失败；恢复后测试重新全绿。
- 按项目脚本 `stop-dev.ps1` 后运行 `start-dev.ps1 -NoTunnel -NoPause`，打包及三服务启动退出码 0，前端 `http://127.0.0.1:3000`，后端 `http://127.0.0.1:8123`。
- 启动后静态复核发现新问题：Cloud 配置有 `/api` context path，而 Agent 新 HTTP 网关省略了 `/api`。相关真实调用预计返回 404；模拟 HTTP 测试未覆盖实际 context path。已登记 IssueLog。建议下一轮修正路径、补覆盖后重测；按 AGENTS.md 第 4 节，等待负责人确认，不把本轮标为功能已验证。

### Verification / Result（2026-10-17，修复 Cloud context path）

- 负责人确认后，将 Agent 的三类 Cloud 内部调用统一改为 `/api/internal/agent/*`；模拟 HTTP 测试也按 Cloud 实际 context path 设置。修改前定向测试 2 个用例失败/错误，修改后相关 Agent 测试退出码 0。
- 本地只读接口检查：带 `/api` 的路径进入业务处理并返回平台错误信封，不带 `/api` 的路径为 HTTP 404，证明旧路径错误；网关按信封 `code` 判定失败，不把错误信封当成功。
- OpenSpec 严格校验通过；按 `stop-dev.ps1` 再 `start-dev.ps1 -NoTunnel -NoPause` 重新打包并启动，8123、8124、3000 均监听。IssueLog 对应路径问题更新为已修复；负责人页面手测仍待完成，Agent 自主撰写及代码执行完整任务保持未完成。

### Verification / Result（2026-10-17，负责人返回页面手测）

- 负责人实测：Agent 创建 Python 代码成功，代码 ID `2111316457618165761`、版本 1、状态 `UNVERIFIED`；Agent 未声称代码已执行或可绑定，符合当前验证边界。
- 纯 response Skill 草稿创建两次失败。后端日志在 `AgentSkillDefinitionValidator.fields` 报 `IllegalArgumentException: Unknown field`，发生在 `AgentLibraryStore.createSkill` 的插入前；`GlobalExceptionHandler` 将该异常返回为 `50000/运行时异常`。日志没有请求体，未知的具体字段名尚未确认。
- 已新增 IssueLog 记录。建议下一轮精确定义 `create_skill.definition` 工具 schema、在 Agent 侧预核对字段、在内部库接口返回安全的字段校验错误，并补真实无代码 Skill 入库测试。按 AGENTS.md 第 4 节先汇报，等待负责人确认后进入下一轮修复测试。

### 内置 Skill 创建规则实施清单（负责人已确认结构化填充）

- [x] 更新设计和规格：预置创建规则无需用户库记录，AI 填字段、代码组装与校验、失败返回路径。
- [x] 先写工具字段、参数错误、内部错误信封的失败测试，再实现字段声明与组装。
- [x] 验证无代码 response Skill 在开发库创建后仅为私有 DRAFT；既有集成测试继续断言未验证代码不可提前绑定。
- [x] 运行定向测试和 OpenSpec 校验，并对 2 个关键判断做变异检验。
- [x] 按 AGENTS.md 第 8 节检查契约、隐式假设与高风险 diff；更新 IssueLog 和测试结论。
- [x] 按项目脚本重启服务，交负责人进行页面手测；手测前不勾选验收完成。

本轮定向测试：Agent 的 `SkillDraftAuthoringTest,OpenAiStreamModelTest,AgentControllerTest,AgentLoopTest` 与 Cloud 的 `AgentSkillDefinitionValidatorTest,AgentLibraryInternalControllerTest,AgentLibraryStoreIT` 均退出码 0；OpenSpec 严格校验通过。变异检验将参数 schema 改为允许额外字段、将参数错误路径改成笼统名称，两项测试均变红，源文件已恢复。负责人页面手测尚未进行。

复核结果：Agent 22 个、Cloud 11 个定向测试均 0 失败/错误；两项变异检验均让测试失败。`stop-dev.ps1` 释放 8123/8124/3000，`start-dev.ps1 -NoTunnel -NoPause` 完成两模块打包并启动三服务。接口契约新增嵌套工具字段约束，内部草稿接口对字段错误返回 40000 与路径，原成功信封保持不变。隐式假设及失效现象：模型服务接受嵌套工具 schema（否则模型请求失败）；Agent 与 Cloud 字段约束保持同步（否则出现字段级拒绝）；模型在工具失败后还有足够轮次修正（否则向用户报告未保存）。最可能的上线问题依次为模型生成缺失必填字段、两模块规则漂移、模型供应方拒绝工具 schema；可分别通过字段错误、跨模块草稿测试、首次真实页面请求提前发现。Review 最需追问：为何内置规则不写入用户数据库、为何公开字段路径不公开原始异常、为何允许模型自动重试。上述边界按已确认设计处理：预置规则避免自举依赖，路径错误只包含 schema 字段，重试受 Agent 最大轮次约束。

负责人已明确要求提交并推送当前任务分支；页面手测仍待负责人返回，不据此勾选最终验收或合并 main。

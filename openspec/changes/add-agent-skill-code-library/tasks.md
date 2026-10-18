# Tasks

## 1. 数据契约与版本库

- [ ] 1.1 定义 Skill、代码项、不可变版本、共享授权、验证结果和执行记录的数据结构及迁移脚本；验证建表可重复执行且旧会话/知识库表不变。
- [ ] 1.2 实现 Skill 结构 schema 与代码入口契约校验，包含字符串 ID、参数类型、步骤类型和固定代码引用；验证非法字段、缺失版本及越界参数被拒绝。
- [ ] 1.3 实现 Skill 保存即启用、停用、版本和共享 API；验证仅所有者可编辑，停用项不可进入可用目录，受共享者只读。
- [ ] 1.4 实现代码项创建、在线编辑、发布版本、共享及版本定位 API；验证旧版本内容不可变、新版本不自动改写 Skill 引用。
- [ ] 1.5 记录上述 API 与存储迁移说明；核对文档中的权限和版本示例能由接口测试复现。

## 2. 隔离代码执行与样例验证

- [ ] 2.1 建立独立 Python/Java 执行组件、每用户工作目录及资源限额配置；验证两个用户的目录互不可见，路径遍历和符号链接越界被拒绝。
- [ ] 2.2 实现 Python 入口与 JSON 输入输出，并运行成功、异常、超时和过大输出样例；验证聊天服务在任务超时时仍可响应。
- [ ] 2.3 实现 Java 入口、版本编译及缓存，并运行成功、编译失败、异常和超时样例；验证同一版本可重复调用且缓存不改变结果。
- [ ] 2.4 实现代码版本可选样例验证与诊断记录；绑定不依赖验证状态，失败版本保留错误且不得被表述为已成功运行。
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
- [ ] 4.3 编写 Agent 的 Skill 撰写规范与结构化创建工具；验证成功任务可生成含适用条件、参数、步骤、失败处理和样例的 Skill，保存成功即启用。
- [ ] 4.4 支持 Agent 保存即发布代码、可选运行样例、绑定有权限的固定版本；未验证或失败状态如实显示，诊断可见。
- [ ] 4.5 记录提示词版本与行为样例；用提示注入、误匹配、版本缺失和普通对话回退用例核对固定规则。

## 5. 前端卡片库与编辑流程

- [ ] 5.1 在“新聊天”下新增“Skill 库”“代码库”入口及右侧卡片列表；验证名称、简介、空状态和返回聊天均正确展示。
- [ ] 5.2 实现 Skill 详情、字段编辑、版本、启停和共享界面；验证创建与修改后默认启用、共享用户没有编辑入口且后端仍拒绝越权请求。
- [ ] 5.3 实现 Python/Java 代码创建或修改时保存即发布、样例验证状态、版本与引用跳转界面；验证代码位置 `id@version#entrypoint` 可追溯，保存新版不改变旧 Skill 展示。
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

### Verification / Result（2026-10-17，个人空间写文件手测失败）

- 负责人要求 Agent 创建可写入“个人空间”的 Skill，Agent 却把个人空间误判为仅有 Skill 库/代码库，并创建了不写文件的文本整理草稿；已登记 IssueLog。
- 静态定位：Agent 当前仅注册 `get_skill`、`create_skill`、`create_code`，未提供个人工作目录的文件读写工具；任务 2.1 的每用户工作目录、3.4 的 Skill 步骤执行仍未实现。现有 `skill_system` 文案强调两类库是 Cloud 记录，模型据此推断出错误的存储边界。
- 建议先落实每用户隔离的个人文件空间及有权限的读写工具，让 Agent 和 Skill 都能调用；创建 Skill 时校验所引用工具确实可执行，再以写入后读回、跨用户拒绝、路径越界拒绝验证。按 AGENTS.md 第 4 节，等待负责人确认后进入下一轮修复与测试。

### 多空间内部工具实施计划（负责人已确认）

- [x] 测试先行建立统一内部工具注册表，使模型声明与实际处理函数同源；保留三个已有工具且不在用户卡片展示。
- [x] 测试先行实现 Cloud 用户隔离的持久文件写入/读回，覆盖跨用户、绝对路径、遍历、符号链接及超限内容；配置生产持久卷。Windows 符号链接测试受环境限制跳过，代码仍拒绝符号链接。
- [x] 测试先行实现知识库文档工具，仅在当前用户对目标空间有编辑权时写入，并验证文档字段及缓存失效。
- [x] 将 Agent 网关与 Cloud 内部接口接通；Skill 草稿拒绝引用未注册的工具，更新提示词以准确区分四种目标。
- [x] 执行定向测试、构建、OpenSpec 校验与 2–3 项变异检验，按 AGENTS.md 第 8 节核查契约和风险，更新 IssueLog。
- [ ] 使用根目录脚本先停后启本地服务，列出负责人页面手测步骤；结果返回前不标记人工验收完成。

### Verification / Result（2026-10-17，多空间工具首轮定向测试）

- Cloud 定向测试 11 个：1 失败、1 跳过；Agent 定向测试退出码 0。失败点是 `create` 模式重复写已有文件时，`FileAlreadyExistsException` 被包装为通用 `IllegalStateException`，没有返回明确的文件已存在错误。符号链接测试在当前 Windows 环境跳过。
- 已登记 IssueLog。拟将文件已存在异常映射为明确的参数冲突，再补齐接口调用验证并重跑测试；按 AGENTS.md 第 4 节，先等负责人确认修复方案。本轮未完成构建、变异检验、OpenSpec 校验或服务启动，不声称功能已验证。

### Verification / Result（2026-10-17，负责人确认后的修复测试）

- 已将重复创建已有文件转换为明确的 `IllegalArgumentException`；补充 Cloud 内部接口与 Agent HTTP 网关测试。Agent 定向测试退出码 0。
- Cloud 定向测试 13 个：0 断言失败、2 个测试准备错误、1 跳过。新增接口测试使用 Mockito 模拟 `final` 的 `AgentWikiDocumentWriter`，当前配置不支持，因此尚未执行接口断言；原文件测试已通过。
- 已登记 IssueLog。建议改为构造真实 Writer、仅模拟其依赖服务，再重跑定向测试。按 AGENTS.md 第 4 节等待负责人确认下一轮修复测试。未完成构建、变异检验、OpenSpec 校验或服务启动。

### Verification / Result（2026-10-17，本机真实服务验证）

- 负责人确认使用本机真实服务。修正接口测试的 `final` 类模拟问题后，Cloud 和 Agent 定向测试均退出码 0；Cloud 文件测试 3 个中 1 个符号链接场景因 Windows 环境能力跳过，其余通过。OpenSpec 严格校验通过。项目脚本先停后启，两模块打包、8123/8124/3000 启动通过。
- 变异检验：将个人文件上限从 1 MiB 错改为 2 MiB，超限测试变红；将 `shell` 错加到 Skill 工具白名单，未注册工具测试变红。两处恢复后定向测试重新通过。
- 本机真实 HTTP：个人文件写入成功并由服务读回验证；跨用户读取返回 40000。真实开发账号在个人知识空间创建文档后，公开详情读取匹配；不存在的用户 ID 写入返回 40000；测试文档经业务接口逻辑删除，文件测试产物已清理。
- Agent 端到端调用确实通过 `write_file` 创建了指定文件，Cloud 读回内容匹配；然而这次流式响应检查既未见 `event: done`，也未见 `event: error`。可能是 PowerShell SSE 响应读取方式或服务端完成帧问题，现有证据不足以区分。已登记 IssueLog；下一轮先保留并检查原始响应，再决定是否修代码。按 AGENTS.md 第 4 节等待负责人确认，本轮不声称对话链路通过。
- 知识文档的异步 RAG 索引记录了 DashScope embedding 连接失败；文档写入、读取、清理均成功，但不能宣称该测试文档完成了检索索引。已另记 IssueLog。
- 契约变化：新增内部文件与知识文档端点、`write_file`/`read_file`/`write_wiki_document` 工具、`AGENT_WORKSPACE_ROOT` 和部署持久卷；既有三工具名称不变。关键假设：Cloud 接收的 `userId` 来自已认证会话（若不成立可越权）、内部密钥仅供 Cloud/Agent 使用（泄漏可伪造用户）、生产卷持久挂载（缺失则重建丢文件）、知识空间编辑权检查在保存前执行（缺失则越权）。最可能问题依次为 Agent 流式完成帧异常、模型未按提示使用读回工具、RAG 异步索引受外部网络影响；分别通过保留原始 SSE、核对文件及工具结果、检查索引日志提前发现。Review 三问：内部 `userId` 如何绑定登录态？写入与读回之间的文件替换如何处理？部署回滚时持久卷如何保留？

### Verification / Result（2026-10-17，原始 SSE 诊断准备）

- 负责人确认继续测试。按项目脚本重启服务，准备使用 `curl.exe -N` 分别保存 Agent 直连与 Cloud 代理的原始 SSE。
- 诊断脚本在读取登录响应时失败：PowerShell 默认编码误解 UTF-8 中文昵称，使 `ConvertFrom-Json` 拒绝有效 JSON；独立 UTF-8 解析确认登录响应有效。测试停在发出 SSE 请求之前，因此上一轮的完成帧问题仍未定位。
- 已登记 IssueLog，临时登录响应与 cookie 已清理。建议下一轮显式按 UTF-8 读取，再执行两层原始帧比较；按 AGENTS.md 第 4 节等待负责人确认，不把本轮记为 SSE 测试通过或失败。

### Verification / Result（2026-10-17，原始 SSE 定位完成）

- 负责人确认后，诊断脚本显式按 UTF-8 读取登录响应。项目脚本先停后启，两模块打包和三个服务启动退出码 0。
- `curl.exe -N` 保存原始流：Agent 直连 HTTP 200，3453 字节，包含多个 `delta` 和终止 `done`，文件内容匹配；Cloud 代理 HTTP 200，2940 字节，包含 `meta`、多个 `delta` 和终止 `done`，文件内容匹配。两份测试文件已清理。
- 复核原先 PowerShell 判定方式：`Invoke-WebRequest.Content` 在 `text/event-stream` 响应中是 `System.Byte[]`，直接 `.Contains("event: done")` 为 false，显式按 UTF-8 解码后为 true，且没有 `error`。因此上轮“Agent 缺少完成帧”是测试脚本误判，不是服务端丢帧。IssueLog 对应记录已更新为已修复。
- 异步 RAG embedding 连接失败属于另一个尚未解决的问题，不影响本次文件写入和 SSE 完成帧判断；不据此宣称知识文档已可检索。负责人页面手测仍待完成。

### Verification / Result（2026-10-17，当前分支本机收口）

- Cloud 定向测试 17 项无失败、1 项符号链接测试因 Windows 环境跳过；Agent 定向测试 27 项无失败。OpenSpec 严格校验与 `git diff --check` 通过。
- 变异检验：将个人文件上限错改为 2 MiB，超限测试变红；将未注册的 `shell` 错加到 Skill 工具白名单，白名单测试变红。两处源码均恢复，Cloud 定向测试重新通过。
- 负责人确认只用本机既有开发环境；Docker Compose 配置校验因终端未提供必填 `AGENT_INTERNAL_KEY` 停在插值阶段，此项记入 IssueLog 并标为本轮范围外，不作为本机验证结论。
- 依项目脚本先执行 `stop-dev.ps1`，再执行 `start-dev.ps1 -NoTunnel -NoPause`；两模块打包及 8123/8124/3000 服务启动退出码 0。负责人页面手测与上传确认尚待返回。
- 契约变化：增加内部个人文件读写与知识文档写入端点、三个 Agent 工具、`AGENT_WORKSPACE_ROOT` 与持久卷；无新增面向浏览器的公开 REST。隐式假设：Cloud 转发的用户 ID 来自已登录会话（不成立会越权）、内部密钥仅供服务间使用（泄漏可伪造用户）、部署卷持久化（缺失会丢文件）、知识文档保存前检查编辑权（缺失会越权）、缓存失效成功（失败会短时显示旧数据）。
- 上线风险按可能性：Agent 缺少知识空间发现能力，会要求用户提供 ID（本轮已知限制，纳入后续独立文档模块）；生产卷配置遗漏会在容器重建后丢文件；异步 embedding 网络失败会使新文档暂不可检索。分别通过页面手测、部署卷检查和索引日志发现。Review 重点三问：`userId` 怎样绑定登录态？写入读回间文件被外部进程替换如何处理？缓存清理或异步索引失败时，用户会看到什么状态？

### Verification / Result（2026-10-17，负责人页面反馈）

- 负责人确认 Skill 与代码可创建。代码“仍需手动发布”的具体操作阶段待澄清，不能据此把 `UNVERIFIED` 当作未发布；现有 `createCode` 已在同一事务写入代码项与版本 1。
- 负责人反馈 Agent 声称 Wiki 写入成功，但 Wiki 列表未显示该文档。此项手测失败已登记 IssueLog；负责人明确决定暂缓，在后续独立文档任务中复现与修复，本轮不继续 Wiki 修复测试。
- 负责人进一步决定：代码无论创建还是修改均保存即发布，Skill 可直接绑定当前用户有权访问的固定版本和入口；删除“样例验证通过后才能绑定”门槛。`UNVERIFIED` / `FAILED` 保留真实状态，不能声称已运行成功。此决定覆盖上文历史记录中旧的绑定限制；独立代码执行器仍是未完成任务。

### Verification / Result（2026-10-17，代码保存即发布和直接绑定）

- 现有 `createCode` 已在一次事务中保存代码项与已发布 v1；改动集中于移除 Skill 绑定 SQL 的 `PASSED` 过滤、Agent 工具显式返回 `published=true`、提示词区分发布/验证、页面把创建和修改统一表述为保存即发布，并在新建后直接显示 v1。
- TDD 红灯：旧 SQL 拒绝绑定 `UNVERIFIED` v1；旧工具结果没有 `published=true`。实现后 Cloud 的 `AgentLibraryStoreIT,AgentSkillDefinitionValidatorTest,AgentLibraryControllerTest,AgentLibraryInternalControllerTest` 与 Agent 的 `AgentToolRegistryTest,AgentControllerTest,AgentLoopTest` 均退出码 0；前端 `npm run type-check`、`npm run build`、OpenSpec 严格校验及 `git diff --check` 均通过。
- 变异检验：重加 `v.validationState = 'PASSED'` 后 `AgentLibraryStoreIT` 变红；把工具结果 `published` 错改为 `false` 后 `AgentToolRegistryTest` 变红。两处恢复后的定向测试重新通过。
- 契约检查：Skill 可绑定有权限的固定代码版本及入口，仍拒绝无权/不存在的版本与错误入口；验证状态保持 `UNVERIFIED` / `FAILED` 原值。Agent 工具结果新增布尔 `published` 字段。独立代码执行器尚未实现，绑定与启用不能被表述为实际运行成功。
- 隐式假设：`createCode` 与 `publishCode` 的事务会同时提交代码项和版本（不成立会出现卡片有条目却无可绑版本）；`latestVersion` 与版本表一致（不成立会打开错误版本）；用户共享在绑定时可见（不成立会误拒绝或越权）；前端字符串 ID 不被数字化（不成立会因雪花 ID 精度导致找不到卡片）。最可能问题是模型仍沿用旧措辞、前端用户误解待验证、尚无执行器却误报运行成功；分别用 Agent 聊天回复、代码卡片状态和实际执行结果核对。Review 三问：为什么允许失败版本绑定？谁保证当前用户可访问代码？没有执行器时怎样避免宣称 Skill 已运行？以上均按负责人新决定和当前实现边界处理。
- 已按根目录脚本先 `stop-dev.ps1` 再 `start-dev.ps1 -NoTunnel -NoPause`，两模块打包及 8123/8124/3000 启动退出码 0。负责人页面手测待完成；Wiki 列表异常按负责人决定留待独立任务。

### 2026-10-18：Skill 创建与修改保存即启用

- [x] 修改 `AgentLibraryStoreIT`：创建后 `ACTIVE/v1` 可用；修改后新版本立即可用、旧版本仍可读取；修改已停用项后重新启用；无效定义不改变既有启用版本。先运行定向测试确认红灯。
- [x] 修改 `AgentLibraryStore.createSkill/reviseSkill`：版本写入与启用状态更新在同一事务提交，保留手动停用与选用旧版本能力。
- [x] 修改 `AgentToolRegistryTest` 和 Agent 工具、提示词：`create_skill` 成功返回 `ACTIVE/v1`，不再宣称草稿；不得把启用当作代码已运行。
- [x] 修改 `AgentLibraryPanel.vue`：保存按钮、成功提示与卡片状态反映立即启用，创建后直接打开 v1，修改后更新当前启用版本。
- [x] 对齐 OpenSpec 提案、设计和规格；执行 Cloud/Agent 定向测试、前端类型检查和构建、OpenSpec 严格校验及 diff 检查。按 AGENTS.md 第 8 节检查签名、隐式假设及 2–3 个关键变异。
- [x] 按根目录脚本先停后启本地服务，列出页面人工验收项目；负责人回复“OK.提交并上传吧”，确认本轮页面验收并授权上传。

回滚：回退本轮应用改动后，新建 Skill 恢复为 DRAFT、修改仅增加版本；已保存的不可变版本仍保留，已有 ACTIVE 项维持数据库中的启用版本，必要时所有者可手动停用。

### Verification / Result（2026-10-18，Skill 保存即启用）

- TDD 红灯：创建后仍为 DRAFT 的旧实现使 Cloud 3 项新预期断言失败；Agent `create_skill` 的旧工具结果仍为 DRAFT，使对应测试失败。改动后 Cloud 定向测试 15 项、Agent 定向测试 21 项均 0 失败/错误；前端 `npm run type-check`、`npm run build`、OpenSpec 严格校验及 `git diff --check` 退出码均为 0。构建有既有大 chunk 警告，未影响构建完成。
- 变异检验：把创建 SQL 的 ACTIVE 错改为 DRAFT，创建测试变红；把修改 SQL 的 `activeVersion` 错改为上一版，修改测试变红。两处恢复后完整定向测试重新通过。
- 契约变化：`create_skill` 工具成功结果从 `DRAFT` 改为 `ACTIVE/version=1`；浏览器 REST 请求和返回类型未变。数据库现有 DRAFT 记录继续可见、可由所有者启用；新写入由 Store 显式设 ACTIVE，无需数据迁移。
- 隐式假设及失效现象：Skill 保存均经过 Store（若绕过，表默认值仍为 DRAFT）；版本 INSERT 和条目状态 UPDATE 处于同一事务（若拆开，卡片可能指向缺失版本）；`latestVersion` 与版本表一致（若失配，页面打开错误版本）；共享查询在 ACTIVE 时展示授权用户（若不成立，共享用户看不到新版本）。上线最可能问题依次为模型仍称“草稿”、用户误把启用当作代码已运行、直接写表的旧客户端创建 DRAFT；可用聊天回复、代码验证状态、Skill 卡片状态分别发现。Review 三问：为何保存已停用项会重新启用？无效定义会否改变当前版本？启用是否代表代码可运行？答案分别来自本轮负责人决定、校验先于事务及测试断言、独立执行器仍未完成。
- 按项目脚本先 `stop-dev.ps1` 再 `start-dev.ps1 -NoTunnel -NoPause`，Cloud/Agent 两模块打包成功，8123/8124/3000 均启动。负责人页面手测待反馈；本轮未提交或上传。

### Verification / Result（2026-10-18，负责人确认上传）

- 负责人回复“OK.提交并上传吧”，本轮 Skill 默认启用页面验收完成并授权提交推送；之前暂缓的 Wiki 列表问题仍单独处理。
- 上传前复核：Cloud 库、个人文件和 Wiki 写入相关定向测试，Agent 工具及网关相关定向测试，前端构建、OpenSpec 严格校验、`git diff --check` 均退出码 0；前端仅有大 chunk 提示。两项 Skill 关键变异检验结果见上节。

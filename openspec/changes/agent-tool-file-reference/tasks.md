# Tasks

## 1. 工具结果契约

- [x] 1.1 保留 Cloud 工具调用返回的业务错误码，并区分写入结果未知；运行 Agent 网关测试验证既有成功结果兼容、40000/40101/40400 原码、写入超时未知及无自动重试。
- [x] 1.2 工具执行前按声明校验参数；运行 Agent 工具测试验证缺失字段、额外字段、类型和枚举错误均拒绝执行，既有有效工具调用通过。
- [x] 1.3 增加无敏感正文的工具调用状态和耗时；运行 Agent 和 Cloud 流测试验证成功、失败、部分成功及历史重放，不泄露内部 ID 和正文。
- [x] 1.4 对同轮多次 Wiki 操作分别保留结果并通过 SQL 获取当前文档归属，兼容历史记录；运行独立多文档、多文件夹、部分成功、未知结果、根目录、重命名移动、失权和会话重载验证，保留每个结果且不得混淆。
- [x] 1.5 只读排查 Agent 工具调用、消息历史与前端结果中单组状态覆盖或关联错配风险；核对代码和测试并逐项记录已证实问题、推测及处置状态，不将未复现问题表述为故障。

#### 1.5 补充只读排查

- 实例 `audit_1_5_retry` 核对当前 Agent、Cloud、前端及既有测试：多目录文档错配在 1.1 第 7 轮报告中标为验证修复，测试使用真实 SQL 与受控 SSE，未验证真实模型或完整跨进程链路；当前前端单个 interaction 赋值对应服务端聚合结果，静态信息不足以认定多文档再次覆盖。AUDIT-002 的部分成功历史文字缺控制器逐字断言，保持待处理；AUDIT-003 的后续异常导致已确认结果未传递已在受控环境复现，用户明确暂缓，未修复。原始只读报告 `docs/issue-reports/agent-tool-file-reference/issue-related-readonly-audit.md` 为早期状态，应结合后续轮次和问题表阅读；本项待独立复核后勾选。
- `tester_1_5_round2` 对 A1 判定 PASS：独立核对现状及历史标注，47 个代码文件前后哈希一致；仅只读，未重跑历史测试或浏览器。IssueLog 第 283 行标题仍用确定性表述，但该行说明已有“静态风险，未端到端复现”的限制，后续应调整标题以免误读。

### 1.2 工具参数校验固定验收

- A1：所有已注册工具执行前按照其向模型声明的参数 schema 检查必填、类型、枚举及 `additionalProperties: false`，任一错误时返回可理解的 40000 参数错误且不调用实际工具或网关；对缺失、额外字段、错误类型、非法枚举和嵌套字段分别断言。`cloud_agent` 下运行 `mvn -q "-Dtest=AgentToolSchemaValidationTest,AgentToolRegistryTest,SkillDraftAuthoringTest,AgentLoopTest,AgentWikiCreationToolsTest,AgentWikiPartialFailureTest,AgentControllerWikiEventTest" test`，退出码 0。
- A2：schema 有意允许任意内容的对象字段仍接受合法自定义属性，整数、布尔值严格区分；原有合法 Skill、代码、文件、Wiki 工具、临时别名和选定空间绑定正常执行，验证命令同 A1 且关键断言通过。未知工具、无效 JSON 和执行异常不泄露内部信息，不修改当前用户权限规则。
- A3：参数声明和实际验证来自同一注册表契约；新工具添加后自动受到同样校验，不另行维护一份可能漂移的字段清单。用声明驱动的参数化测试及模拟网关调用计数验证，禁止新增依赖或修改对外公开 REST、数据库和 schema。

### 1.3 工具调用状态及耗时固定验收

- A1：Agent 成功完成的一轮中，按调用顺序提供工具的安全显示名称、成功/失败/阻止/未知状态和非负耗时；Wiki 部分成功与未知保持真实含义，既有结果和权限投影不回退。`cloud_agent` 下运行 `mvn -q "-Dtest=AgentToolTraceTest,AgentLoopTest,AgentControllerWikiEventTest,AgentWikiPartialFailureTest" test`；`cloud` 下运行 `mvn -q "-Dtest=AgentToolTraceLocalIT,AgentWikiMultiResultLocalIT,AgentWikiUnknownProjectionTest,AgentPreviewProxyWikiEventTest,AgentWikiChoiceLocalIT" test`；均退出 0。
- A2：Agent 执行期间或已完成回放的展示均不得包含工具参数、文件正文、密钥、内部用户/空间/文件夹/文档 ID 或原始错误文本；真实 SQL 记录与本人历史重载正确，跨用户拒绝；已授权但后续失权时不输出失权名称；前端 `npm run build` 退出 0，真实数据库测试事务回滚。
- A3：本任务不实现 AUDIT-003 中断后恢复；没有完成事件时不伪造完成或保留未证实的结果。工具调用状态失败/未知与回答生成失败分别表达，不把未知标为失败；维持原工具结果和历史 Wiki 卡片兼容。验证命令同 A1，额外断言完整回放及既有 Wiki 测试通过。

#### 1.2 第 1 轮独立验收

- 独立实例 `tester_1_2_round1` 判定总体 FAIL：固定测试 36 项通过，但受控 AgentLoop 探针发现根参数 `[]` 抛异常而非返回 40000（A1 FAIL）；非法 JSON 在有效对象后附加尾随字符时仍触发一次假写入（清单外重大问题）。A2/A3 仍证据不足，需补端到端及声明驱动的调用计数。冻结 20 文件的测试前后 SHA256 一致。
- 只读复核提示未复现的额外契约风险：声明 integer 对数值 1.0 的处理以及 get_skill 版本超出 Java int 范围后截断；须用受控测试核对，保留问题性质与证据，不将静态分析写为已复现故障。修复后新建独立测试实例复验，原验收不降低。

#### 1.2 第 2 轮独立验收

- 实例 `tester_1_2_round2`：A1/A3 和完整输入、整数补充检查 PASS；A2 FAIL，真实 Agent 注册表接收 Skill `code_version: 1.0` 后将其送至 Cloud 校验器，该校验器因 `isInt()` 拒绝声明允许的值。
- 另有清单外交付阻断：Skill `min: 1.00000000000000001, max: 1` 在发送网关前被二次解析并舍入；受控真实校验器接受改变后的边界。假网关无 HTTP/数据库写入，未运行基线，不能断言舍入是新增回归。20 文件前后哈希一致；第 3 轮修复须保留 A1/A2/A3 不变并补充这两项行为验证。

#### 1.2 第 3 轮独立验收

- 实例 `tester_1_2_round3` 对 A1/A2/A3 及 B1/B2 判定 PASS；Agent/Cloud 指定 Maven 命令均退出 0，跨模块受控探针验证严格 JSON、整数归一、高精度边界和自由对象；24 文件测试前后 SHA256 一致。只读复核无新增已证实阻断。原问题状态及证据见 `docs/issue-reports/agent-tool-file-reference/issue-p1-t1-2-round3.md`；真实 HTTP 与数据库未在探针中执行。

#### 1.3 第 1 轮独立验收

- 实例 `tester_1_3_round1` 对 A1/A2/A3 判定 PASS，指定 Agent、Cloud 测试和前端构建通过；真实数据库用例事务回滚，26 文件测试前后 SHA256 一致。真实模型及浏览器交互未测试，详见 `docs/issue-reports/agent-tool-file-reference/issue-p1-t1-3-round1.md`。只读复核未发现已证实的新阻断；Wiki 写入后正文抑制为既有静态风险 `ATFR-AUDIT-004`，尚未动态复现，待评估。

## 2. 个人文件存储

- [x] 2.1 抽象个人文件存储并实现 50 MiB 有界写入、同文件协调、版本冲突及不损坏原文件的失败处理；运行 Cloud 存储测试验证边界、隔离、路径、并发和回滚。
- [x] 2.2 实现版本绑定的 UTF-8 有界分段读及有界续传搜索；运行 Cloud 测试验证中文边界、超长行、完整还原、文件变化和搜索续传。
- [x] 2.3 接通 Cloud 内部接口与 Agent 文件工具，保持小文件调用兼容且大文件返回分段指引；运行 Agent/Cloud 网关与集成测试验证限额、错误码和授权。
- [x] 2.4 增加分段上传/写入的必要外部接口和部署限制配置；测试 50 MiB 存取、越限拒绝、断线清理与现有文件保留，记录资源峰值。

### 2.1 个人文件安全写入固定验收

- A1：以 UTF-8 字节计算文件大小，create、overwrite、append 支持不超过 50 MiB，超限拒绝且已有文件字节不变；流式写入不将整个文件载入内存。`cloud` 下运行 `mvn -q "-Dtest=AgentPersonalFileStoreTest,AgentResourceInternalControllerTest" test`，退出 0；测试核对边界、中英文持久字节、三种写入方式和超限后原文件内容。
- A2：保持现有用户隔离及相对路径约束，拒绝越界和符号链接逃逸；写入中途异常或提交失败不得破坏原文件且临时文件清理；验证命令同 A1，退出 0，测试实际目录与文件。
- A3：同一用户同一文件并发写协调并支持基于版本的冲突检查，两个基于同一版本的竞争写至多一个成功，其他文件不被全局锁串行化；无版本的旧调用保持兼容。验证命令同 A1，退出 0，测试并发结果和最终字节。范围限定单进程本地持久卷，不承诺多实例协调；不更改公开 REST、数据库、依赖或资源缓存。

#### 2.1 第 1 轮独立验收

- 实例 `tester_2_1_round1`：A1、A3 PASS；A2 FAIL。Windows 目录联接指向用户目录外时，受控探针调用写入成功并在联接目标生成文件，违反路径隔离；需保持原固定验收进入第 2 轮。指定 Maven 命令退出 0，符号链接测试因权限跳过；独立探针验证跨 JVM 版本稳定、ABA 冲突和 16 MiB 堆流式创建 50 MiB。29 文件测试前后哈希一致。临时探针目录的删除被自动审批拒绝，仍保留于系统临时目录。

#### 2.1 第 2 轮独立验收

- 实例 `tester_2_1_round2`：A1、A3 和目录联接修复 B1 PASS；A2 因 Windows 创建普通符号链接无权限而证据不足，整体暂不勾选。独立探针确认用户目录及子目录 junction 均拒绝写入并无越界文件；原 Maven 命令退出 0，符号链接用例跳过；29 文件测试前后哈希一致。普通符号链接启动独立 Linux 补证。临时探针清理被自动审批拒绝，目录仍保留。
- 独立实例 `tester_2_1_symlink_supplement` 使用隔离 Linux 容器运行冻结源码的探针，验证真实符号链接在用户根、子目录、最终文件的读写均拒绝逃逸，普通路径正常；29 文件快照前后不变。结合 Windows 实际 junction 测试，2.1 固定验收通过；Windows 普通符号链接本机仍未实测。记录见 `docs/issue-reports/agent-tool-file-reference/issue-p2-t2-1-round2.md`。

### 2.2 分段读取和搜索固定验收

- A1：存储内部按 UTF-8 有效字符边界返回有限大小的文本段和续传位置，顺序拼接能还原中英文、跨段多字节字符及超长行；50 MiB 文件可按段读取且不需整文件入堆。`cloud` 下运行 `mvn -q "-Dtest=AgentPersonalFileStoreTest,AgentPersonalFileSegmentTest,AgentResourceInternalControllerTest" test`，退出 0；测试实际文件、完整字节还原和固定分段上限。
- A2：每段与用户、路径和文件版本绑定，非法位置或跨用户访问被拒绝；文件在两次读取间被修改时拒绝继续而不混入不同版本；旧小文件 read 行为不退化。验证命令同 A1，退出 0，测试冲突、非法位置、隔离与既有小文件。
- A3：提供有界、可续传的文件内搜索，匹配跨读取边界及中文，限制单次扫描范围和返回数量，连续搜索结果不遗漏或重复；超长行不导致整行加载；版本变化拒绝续传。验证命令同 A1，退出 0，测试多个结果、空结果、边界及续传。仅实现内部存储接口，不修改公开 REST、数据库或依赖；单进程协调，不实现完整限流和缓存。

#### 2.2 第 1 轮独立验收

- 实例 `tester_2_2_round1` 对 A1/A2/A3 判定 PASS；指定 Maven 测试退出 0（Windows 符号链接 1 项因权限跳过），独立探针以 24 MiB 堆分 51 段还原 50 MiB，字节哈希一致；30 文件测试前后哈希一致。每次操作均流式读取全文计算版本摘要，51 段约 2.49 GiB 摘要 I/O，登记 `ATFR-P2-T2.2-R1-IO` 暂缓；不能将候选搜索范围上限表述为总 I/O 上限。详见 `docs/issue-reports/agent-tool-file-reference/issue-p2-t2-2-round1.md`。

### 2.3 Agent 与个人文件内部接口固定验收

- A1：Agent 经 Cloud 内部接口以当前用户和内部密钥获取有界段及搜索结果，支持首次请求和续传，跨用户或错误密钥拒绝，版本变化返回可识别的业务错误；实际 HTTP 网关和控制器测试核对响应及错误码。`cloud` 下运行 `mvn -q "-Dtest=AgentResourceInternalControllerTest,AgentPersonalFileStoreTest,AgentPersonalFileSegmentTest" test`，`cloud_agent` 下运行 `mvn -q "-Dtest=AgentLibraryHttpGatewayTest,AgentToolRegistryTest,AgentToolSchemaValidationTest,AgentLoopTest" test`，均退出 0。
- A2：旧 `read_file` 小文件返回兼容正文；大文件明确指引使用分段工具且不返回全文，新增分段及搜索工具声明与执行参数一致并受统一 schema 校验；工具输出上限与 Agent 默认累计 16000 字符限制协调，典型中英文分段及续传不因单次响应过大而中断。验证命令同 A1，退出 0，测试真实注册表、网关和有界结果。
- A3：保留现有个人文件写入方式、路径限制和业务错误码；不泄露服务密钥、内部磁盘路径及其他用户内容；保留 Wiki、Skill、代码工具行为。验证命令同 A1，退出 0；新增内部接口仅供 Agent，未新增公开浏览器接口或数据库及依赖。

#### 2.3 第 1 轮独立验收

- 实例 `tester_2_3_round1_retry` 对 A1/A2 判定 PASS，A3 在 Windows 普通符号链接权限缺失时证据不足；指定 Agent 和 Cloud 命令退出 0，33 文件前后哈希一致。独立 `tester_2_3_symlink_supplement` 对同快照在 Linux 以真实符号链接补证写入、读段及搜索拒绝越界，结合 Windows junction 检查完成 A3；Windows 普通符号链接仍未实测。内部密钥错误实测拒绝读取，但 HTTP 200、业务码 50000，已记录 `ATFR-P2-T2.3-R1-AUTH` 待评估；仅静态确认基线相同，不认定本轮新增。详见 `docs/issue-reports/agent-tool-file-reference/issue-p2-t2-3-round1.md`。

### 2.4 个人文件分段上传固定验收

- A1：已登录用户可通过有界分段上传创建、覆盖或追加 UTF-8 文本文件，成功提交后总大小至多 50 MiB，其他用户不可读取或操纵该上传；用户可取消上传。`cloud` 下运行 `mvn -q "-Dtest=AgentPersonalFileUploadTest,AgentPersonalFileStoreTest,AgentPersonalFileSegmentTest" test`，退出 0；测试完整 50 MiB 文件的提交和持久字节、三种模式、登录与跨用户隔离。
- A2：上传过程中拒绝超限、不连续或重复的分段，不完整、断线、无效 UTF-8、版本冲突或提交失败均不修改原文件；取消及过期上传清理临时内容；文件路径和真实磁盘路径不泄露。验证命令同 A1，退出 0，测试原文件哈希、上传资源清理和对应拒绝。
- A3：写入和上传分段保持内存有界，配置应用或反向代理的请求大小至少容纳单段而非整个文件；部署配置与回滚方式有文档，保留已有个人文件和旧调用。验证命令同 A1，退出 0，核对配置及完整真实字节。总体并发压测与 2G 资源峰值按照负责人决定暂缓，仍须验证有界处理。

#### 2.4 第 1 轮独立验收

- 实例 `tester_2_4_round1` 对 A3 判定 PASS，A1/A2 因真实 HTTP 登录、隔离、断流和若干失败场景原文件哈希缺少证据判定证据不足；指定 Maven 命令退出 0（23 项中 1 项 Windows 符号链接跳过），37 文件测试前后哈希一致。现有 8123 服务对新接口返回 404，未停用或重启该服务；另派独立补证。未发现已证实的固定验收失败或清单外重大回归。
- 补证实例 `tester_2_4_supplement` 未执行行为验证：其隔离临时副本包含项目资源目录，尝试清理该副本时被自动审批策略拒绝，按任务卡停止，37 文件结束快照一致。副本 `C:/Users/origin/AppData/Local/Temp/upload-supplement-387cdc81f0934d109e35234b7589e69a` 仍保留，是否含敏感配置未知；任务维持未完成。
- 后续 `upload_supplement_retry` 在受控 HTTP 环境以身份替身验证 50 MiB、跨用户拒绝、断流与无效文件等失败时哈希不变；`upload_auth_probe` 使用真实会话读取方法及数据源替身检查身份和错误响应。只读复核 `upload_evidence_review` 确认现有源码与探针哈希一致，但运行时编译产物与冻结源码的对应和全过程快照证据不足，不能据此宣称本任务独立 PASS；真实登录入口未测，多个拒绝响应为业务码 50000。继续保留未完成状态。
- `tester_2_4_current` 对当前快照 A1/A2/A3 判定 PASS：47 个工作区文件及 555 个编译输入前后哈希一致，指定 Maven 测试通过；隔离真实 HTTP、生产会话方法和模拟用户数据源验证 50 MiB、跨用户、退出、断线、无效 UTF-8 与路径脱敏。未验证真实登录入口，Windows 普通符号链接测试跳过，总体并发及 2G 资源峰值按决定暂缓；详见 `docs/issue-reports/agent-tool-file-reference/issue-p2-t2-4-current.md`。

## 3. 引用与页面

- [x] 3.1 生成绑定用户、消息、版本与位置的可信个人文件引用并持久化；运行 Cloud 测试验证重载、伪造标记、跨用户和文件变化。
- [x] 3.2 复用会话右侧预览实现个人文件分段加载及引用定位；运行前端测试和构建验证多轮引用、翻页、过期及 RAG/Wiki 原行为。
- [ ] 3.3 运行真实服务集成验证：两用户隔离、中文大文件存取、会话刷新引用、文件修改提示、并发限流及 2G 资源峰值；记录命令和观察结果。

3.3 的总体并发限流和 2G 峰值按负责人决定暂缓。`tester_3_3_functional` 补查真实数据库事务测试 3 项通过、文件组件 17 项中 16 项通过且 1 项符号链接跳过；这些均非真实 Cloud→Agent 跨进程或真实页面联测。隔离测试数据库与完整独立服务的外部依赖、配置及持久化边界尚未建立，未启动新服务或容器；跨进程及真实页面证据不足，保持任务未勾选。原始日志位于系统临时目录 `agent-tool-file-reference-3-3-functional-probe-cloud.log` 和 `agent-tool-file-reference-3-3-functional-files.log`。

### 3.1 个人文件引用固定验收

- A1：Agent 使用个人文件读取或搜索后，仅由服务器已验证的真实文件、版本及片段位置生成结构化引用；引用绑定当前用户和具体会话消息，完成的消息保存并重载后仍可解析对应文件及位置。`cloud` 下运行 `mvn -q "-Dtest=AgentPersonalFileReferenceTest,AgentPersonalFileReferenceLocalIT,AgentWikiMultiResultLocalIT" test`，`cloud_agent` 下运行 `mvn -q "-Dtest=AgentToolRegistryTest,AgentLoopTest,AgentControllerWikiEventTest" test`，均退出 0；测试真实数据库事务保存、查询及回滚，覆盖同轮多文件、多段和历史重载。
- A2：用户仅可访问自己消息绑定的真实引用；伪造的模型标记、跨用户、跨消息或篡改位置不得创建有效引用，不泄露文件内容或内部磁盘路径；文件变化、删除或失权后明确拒绝或提示过期。验证命令同 A1，退出 0，逐项断言真实授权和位置绑定。
- A3：不改变既有 Wiki/RAG 引用、历史消息和错误处理；仅完成服务端生成、持久化和安全解析，页面展示留给 3.2；不引入数据库迁移或新依赖。验证命令同 A1，退出 0，覆盖既有引用兼容及消息历史。

#### 3.1 第 1 轮独立验收

- 实例 `tester_3_1_round1` 对 A1/A3 判定 PASS；A2 因保存的本人引用在失权后的行为未实际断言而证据不足。指定 Maven 命令 Cloud 15 项、Agent 27 项均通过；真实数据库事务测试覆盖多文件、多位置、跨用户/消息、篡改、修改和删除并回滚，41 文件快照前后无变化。实际跨进程及浏览器链路未测；独立补证进行中。
- 实例 `tester_3_1_revocation` 使用生产会话登出方法及 Servlet 会话补证：登出后原引用解析被登录检查拒绝；用户查询和消息存储为替身，探针封装的 HTTP 响应不代表生产结果。项目未发现个人文件单独共享撤权机制，该测试不证明文件级撤权；见 `docs/issue-reports/agent-tool-file-reference/issue-p3-t3-1-round1.md`，A2 保持证据不足，未勾选。
- `tester_3_1_current` 在当前 47 文件快照上对 A1/A2/A3 判定 PASS：指定 Cloud、Agent 测试及真实数据库事务回滚通过；当前源码会话探针验证登出后拒绝，数据库用户及消息为替身，探针 HTTP 状态为模拟；同消息 RAG 与个人引用共存由真实服务逻辑和 mapper 替身验证。个人文件当前无独立共享撤权功能，完整跨进程及真实模型未测；详见 `docs/issue-reports/agent-tool-file-reference/issue-p3-t3-1-current.md`。

### 3.2 页面引用与分段预览固定验收

- A1：已完成会话中新产生和历史重载的个人文件引用以可点击形式显示文件名及位置；点击后在现有右侧预览打开对应位置，前后翻段读取并保持 UTF-8 内容正确，不一次加载 50 MiB。`cloud_front` 下运行 `npm run build` 及项目现有前端测试；`cloud` 下运行 `mvn -q "-Dtest=AgentPersonalFileReferenceLocalIT,AgentPersonalFilePreviewTest,AgentWikiMultiResultLocalIT" test`，均退出 0；受控浏览器或组件交互测试验证引用点击、定位、翻页和多轮引用。
- A2：每次预览根据当前用户、会话、消息和引用重新鉴权并绑定文件版本；跨用户、伪造、删除、权限失效或文件变化时不得显示内容并给出清楚提示；不泄露服务器绝对路径或私有令牌。验证命令同 A1，退出 0，覆盖未授权、过期和失败后页面状态。
- A3：保持现有 RAG/Wiki 引用、文档预览及历史消息行为；新增必要公开读取接口仅限引用预览，单段响应有明确上限，不增加数据库迁移或新依赖。验证命令同 A1，退出 0，含既有预览回归与构建。

#### 3.2 第 1 轮独立验收

- `tester_3_2_round1`：前端构建、98 项测试及指定 Cloud 14 项均通过；受控浏览器验证历史及模拟新消息引用、定位、翻段、过期和 RAG/Wiki。`tester_3_2_supplement` 验证 24 MiB 堆下 50 MiB 文件的存储层定位和分段；`tester_3_2_http` 以认证和消息存储替身核对真实控制器错误响应不泄露正文及私有信息。各自前后 43 文件一致；真实认证失效和完整端到端链路证据不足，任务不勾选。详见 `docs/issue-reports/agent-tool-file-reference/issue-p3-t3-2-round1.md`。
- `tester_3_2_current` 对当前 47 文件快照 A1/A2/A3 判定受控范围 PASS：前端 98 项、构建及指定 Cloud 测试通过，受控浏览器使用模拟 API 验证引用、翻段和既有 RAG/Wiki；真实 50 MiB 文件在 24 MiB 堆下完成存储定位；真实 HTTP、生产会话及异常处理器验证退出、篡改和版本变化，用户/消息数据源为替身。真实登录入口、真实 RAG 和完整跨进程链路未测；详见 `docs/issue-reports/agent-tool-file-reference/issue-p3-t3-2-current.md`。

## 4. 组织、运维与验收

负责人 2026-10-30 决定：工具分类与通用边界（4.1，属于用户清单第 1 项）继续；完整资源控制/缓存扩展（4.2）、整体收尾验收与上传（4.3–4.4）暂缓，不代表前面功能无需单项验证。

- [x] 4.1 增加工​​具分类和通用执行边界，拆分 Wiki 流程中的业务逻辑并保持程序级安全约束；运行 Agent Wiki 回归测试验证选择、部分成功及 ID 不泄露。
- [ ] 4.2 提供有界并发配置、可替换缓存边界及运维说明；运行限流和缓存命中鉴权测试并记录默认参数与回滚方法。
- [ ] 4.3 完成各阶段独立验收、必要的变异检查和 `openspec validate agent-tool-file-reference --strict`；记录证据、问题与修复状态。
- [ ] 4.4 负责人手测确认后同步规格、归档和按项目脚本上传；验证持久文件保留和回滚限制。

### 4.1 工具分类与通用边界固定验收

- A1：每个已注册工具在同一注册契约声明业务领域和只读/写入属性，新增工具缺少分类不可注册或执行；分类用于通用执行及未知写入保护，不改变既有模型可见工具声明和业务权限。`cloud_agent` 下运行 `mvn -q "-Dtest=AgentToolRegistryTest,AgentToolSchemaValidationTest,AgentLoopTest,AgentWikiCreationToolsTest,AgentWikiPartialFailureTest,AgentControllerWikiEventTest" test`，退出 0；测试逐个注册工具和未知结果后的只读、写入处理。
- A2：将 Wiki 专属别名及安全处理从通用循环中拆到清晰内部边界，保留原选择、别名授权、部分成功及内部 ID 隐藏行为，不能依赖 Skill 文本执行安全约束。验证命令同 A1，退出 0，覆盖原多次操作、选择和别名回归。
- A3：无数据库迁移、新依赖或公开 REST 行为变更；未注册工具和错误参数拒绝，调用追踪及个人文件工具兼容。验证命令同 A1，退出 0，逐项断言而非只检查构建。

#### 4.1 第 1 轮独立验收

- `tester_4_1_round1`：A1 PASS，A2/A3 因补充回归失败证据不足；指定 44 项通过。补充命令 `mvn -q "-Dtest=AgentToolTraceTest,AgentControllerTest,AgentWikiChoiceLoopTest,AgentWikiDestinationToolTest,AgentWikiWorkflowLoopTest" test` 中控制器 Skill 创建和 Wiki 工作流两项失败；是否本轮引入或既有测试不兼容尚未确认，正只读复核。45 文件前后哈希一致，任务保持未勾选。
- `tester_4_1_round2` 对 A1/A2/A3 和额外 B1 判定 PASS，指定和补充命令共 56 项通过；47 文件测试前后哈希一致。复测核对两份测试夹具更新保留身份、工具顺序和结果断言，未取消参数或别名安全检查；未运行开发前基线，不断言首轮失败的历史来源。详见 `docs/issue-reports/agent-tool-file-reference/issue-p4-t4-1-round2.md`。

## 证据与回滚

负责人 2026-10-30 明确不开发 AUDIT-003 的异常恢复机制，转为暂缓且不作为本轮的交付阻断；不宣称问题修复。1.1 和 1.4 的第 7 轮独立验收及安全复核已通过，见下方证据；全部改动仍未提交。AUDIT-002 证据不足，后续仅在相关已授权任务中核对。

每项开发交接与独立测试记录测试实例、基线、冻结快照及校验值、命令、预期与实际；未独立 PASS 不勾选。失败或阻塞依项目规则登记 IssueLog。回滚停用新增接口并恢复旧代码，保留个人文件持久卷与会话；旧实现不能读取超过原有上限的文件，须向用户说明。

### 1.1 第 1 轮独立验收

- 测试实例 `tester_1_1_round1`，基线 `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`，五个被测文件测试前后 SHA256 一致。
- A1：指定测试通过，三个业务码覆盖固定消息；任意错误消息的敏感信息处理证据有限。
- A2：FAIL。受控 HTTP 服务收到首个写请求后超时，模型读取 unknown 后再次请求写入，服务实测接收两次；证据 `C:/Users/origin/AppData/Local/Temp/agent-tool-file-reference-retry-probe.log` 和 `RetryProbe.java`。
- A3：证据不足。指定投影/代理测试通过，但仅验证实时流与完成回调，没有验证实际数据库历史重载。任务保持未完成，修复并重测时原 A1/A2 与追加 A3 均不降低。

### 1.1 第 2 轮独立验收与复核

- 测试实例 `tester_1_1_round2` 对 A1/A2/A3 判定 PASS，七文件快照测试前后一致；数据库实际保存并重新读取未知状态，事务回滚。只读复核后追加的受控探针发现清单外阻断：请求已提交但响应损坏或断开时，Agent 返回 `saved:false` 且同轮再次请求写入，服务各收到两次 POST（Issue `ATFR-P1-T1.1-R2-001`）。
- 同轮先成功创建文档后再次写入 unknown 时，实时流与持久化回调同时包含先前成功卡和未知提示，缺少将两个结果明确关联到各操作的证据（Issue `ATFR-P1-T1.1-R2-002`）。原固定验收结论不变，但因清单外重大风险暂不勾选或上传；证据见 `docs/issue-reports/agent-tool-file-reference/`。进入修复第 3 轮并使用全新独立测试实例。

### 1.1 第 3 轮独立验收与复核

- 测试实例 `tester_1_1_round3` 对 A1/A2/A3 与响应断连、连续 Wiki 操作的补充检查判定 PASS，七文件快照测试前后一致，数据库测试事务回滚。
- 只读复核后新增受控探针复现清单外交付阻断：文档 A 成功、文件夹 B 成功、之后写入未知，结果文字错误地把文档 A 归到文件夹 B；同文件夹内多个成功文档只显示最后一个。`ATFR-P1-T1.1-R3-001`，IssueLog 第 281 行及问题目录有原始证据。任务不得勾选或上传；已达三轮上限，停止并上报负责人。
- 另有未复现的静态风险：文件写入后校验抛参数异常可能被错误映射为确定未保存；仍需独立验证，不作为已证实故障。

### 1.1 第 4 轮负责人授权与固定验收

负责人明确授权对任务 1.1 增加一轮，用于逐项操作记录及 SQL 查询文档归属；第 1–3 轮证据与 A1/A2/A3、B1/B2 原验收继续有效，不降低。以第 3 轮未提交快照为修复起点，保留所有既有工作。第 4 轮额外固定验收：

- C1：同轮跨两个文件夹创建两份文档、同一文件夹创建两份文档、先成功再未知及先创建两个文件夹再写入前者时，每个确认成功均独立显示并可打开，关联不串位；前端构建通过。验证 `cloud` 下 `mvn -q "-Dtest=AgentWikiMultiResultLocalIT,AgentWikiUnknownProjectionTest,AgentPreviewProxyWikiEventTest" test` 和 `cloud_front` 下 `npm run build`，退出 0，测试核对实时、数据库重载及页面对应结构。
- C2：通过实际 SQL 根据文档 ID 查询当前文件夹关系，覆盖根目录、文件夹改名、文档移动、文件夹或文档删除、失权及另一用户访问；不存在或无权限不得泄露名称和正文；原先成功结果未知不得按名称猜测。验证 `cloud` 下 `mvn -q "-Dtest=AgentWikiMultiResultLocalIT" test`，退出 0，测试事务自动回滚。
- C3：历史成功或部分成功的实际回答不能替换为“请选择写入空间”，已有选择流程与旧单结果记录保持兼容，重载和预览按现权限核验且无原始 ID；验证同 C1 的 Cloud 命令及 `cloud_front` 构建。
- A1/A2：`cloud_agent` 下 `mvn -q "-Dtest=AgentLibraryHttpGatewayTest,AgentLoopTest,AgentToolRegistryTest,AgentWikiCreationToolsTest,AgentWikiPartialFailureTest" test` 退出 0；A3/B1/B2 原断言不变，`cloud` 下 `mvn -q "-Dtest=AgentWikiUnknownProjectionTest,AgentPreviewProxyWikiEventTest,AgentWikiChoiceLocalIT" test` 退出 0。测试实际调用链，不能仅凭 mock 替代 SQL 与历史回读。

#### 第 4 轮验证与补证

- 新独立测试实例 `tester_1_1_round4` 指定命令与构建通过，但最初 C1 文档与文件夹逐条对应证据不足；旧单测试使用动态 ID 后仍检查固定值 `456`，该处断言针对性减损。
- 独立补证实例 `tester_1_1_round4_supplement` 使用真实 MySQL 事务验证跨目录、同目录各两文档的实时及历史标题、实际目录名、令牌和正文逐项一致，动态 ID 未泄露；事务回滚并核对测试账户已清理。未执行浏览器交互，完整报告在 `docs/issue-reports/agent-tool-file-reference/issue-p1-t1-1-round4-supplement.md`。
- 补证发现清单外交付阻断：`ATFR-P1-T1.1-R4-001` 仅创建文件夹却展示文档尚未保存；`ATFR-P1-T1.1-R4-002` 多次失败的公开结果仅给计数，缺少逐操作明细。已记录 IssueLog 第 285–286 行；冻结的 15 个文件测试前后哈希一致。负责人仅授权增加一轮，暂停进一步修改、勾选与上传，等待负责人决定。

### 1.1 第 5–7 轮负责人授权与固定验收

负责人授权继续开发最多三轮。保留全部原 A1/A2/A3、B1/B2、C1/C2/C3 验收，修复 R4-001、R4-002；此前的真实 SQL、当前授权、历史兼容和旧结果安全不得回退。

- D1：仅执行一次成功的文件夹创建、不尝试文档创建时，实时输出和历史重载均仅报告文件夹创建成功，不得显示“文档未保存”或虚构文档操作；文件夹确实成功、后续文档写入确定失败时仍准确显示部分成功。`cloud` 下运行 `mvn -q "-Dtest=AgentWikiMultiResultLocalIT,AgentWikiUnknownProjectionTest,AgentPreviewProxyWikiEventTest,AgentWikiChoiceLocalIT" test`，退出 0，含真实数据库与流式、历史场景断言。
- D2：同轮多次不同的失败、阻止或未知操作按原顺序逐项呈现安全的操作类型和准确状态，可与成功结果共存；实时、历史重载一致，不暴露敏感内容及内部 ID，不将未知称失败，不建议盲目重试。验证命令同 D1，退出 0；`cloud_front` 下 `npm run build` 退出 0。
- D3：在原有多文档真实 SQL 测试中对每个文档的实际标题、所属文件夹、token 和预览正文逐项断言，覆盖不同文件夹及同文件夹；旧安全断言使用实际动态 ID 而非旧固定值。验证命令同 D1，退出 0。
- D4：只读查询 Wiki 位置成功或失败后，如果本轮无结构化选择和写入结果，Agent 的最终文本 SHALL 正常交付并保存；当写入或选择需要安全投影时，继续阻止未审核的内部 ID 泄露。`cloud_agent` 下运行 `mvn -q "-Dtest=AgentLibraryHttpGatewayTest,AgentLoopTest,AgentToolRegistryTest,AgentWikiCreationToolsTest,AgentWikiPartialFailureTest" test`，`cloud` 下运行 `mvn -q "-Dtest=AgentWikiMultiResultLocalIT,AgentWikiUnknownProjectionTest,AgentPreviewProxyWikiEventTest,AgentWikiChoiceLocalIT" test`，均退出 0；测试覆盖可写位置查询正常、40101 错误、最终用户可见内容与会话重载，以及现有写入安全投影。
- D5：已授权并启用的旧自定义 Skill，若其结构化 Wiki 工具参数包含固定合法 ID，模型上下文使用本轮不透明别名，工具执行还原并由 Cloud 按当前用户重新鉴权；对伪造、失权或跨轮别名拒绝，禁止以 Skill 作者权限代替当前用户。Skill 其他内容和版本不被修改；内置 Skill、分页和已选空间兼容。`cloud_agent` 下运行 `mvn -q "-Dtest=AgentLoopTest,AgentControllerTest,AgentControllerWikiEventTest,AgentWikiPartialFailureTest,AgentLibraryHttpGatewayTest" test`；`cloud` 下运行 `mvn -q "-Dtest=AgentWikiMultiResultLocalIT,AgentWikiUnknownProjectionTest,AgentPreviewProxyWikiEventTest,AgentWikiChoiceLocalIT" test`，均退出 0；真实 MySQL 权限与持久化回归、模型上下文及工具执行映射均需实际断言。用户手动提供的任意自然语言和数据不属于系统生成 Wiki 工具结果，不能声称能从任意文本中识别和隐藏所有可能的 ID。

#### 第 5 轮独立验收

- 独立测试实例 `tester_1_1_round5` 对 D1/D2/D3、A1/A2/A3、B1/B2、C2/C3 判定 PASS；C1 实时 SSE 未逐卡核对标题、文件夹及 token 对应关系，证据不足。指定 Cloud、Agent 测试和前端构建退出 0，冻结 15 文件测试前后哈希一致。
- 受控探针复现已登记的 `ATFR-AUDIT-001`：只读查询返回 40101 后模型最终说明被丢弃，用户收到空答复；单列为交付阻断。用户已授权最多三轮，进入第 6 轮，原固定验收及问题事实不降低。

#### 第 6 轮独立验收与补证

- 新独立测试实例 `tester_1_1_round6` 对原 A1–A3、B1/B2、C1–C3、D1–D4、分页与别名绑定判定 PASS，指定命令和构建通过、18 文件哈希一致；自定义 Skill 输入边界证据不足。
- 独立探针 `tester_1_1_skill_probe` 复现已校验旧 Skill 的 `write_wiki_document` 步骤含固定 `spaceId` 时被新别名规则拒绝，假工具未执行；输入 ID 为模拟用户提供，未证明 Wiki 工具结果泄漏。记 `ATFR-P1-T1.1-R6-001` 待处理及 `ATFR-P1-T1.1-R6-S1` 证据不足，IssueLog 第 288–289 行。进入用户授权的第 7 轮；原验收不降低。证据见 `docs/issue-reports/agent-tool-file-reference/issue-p1-t1-1-round6-skill.md`。

#### 第 7 轮独立验收与后续补证

- 新实例 `tester_1_1_round7` 对 A1–A3、B1/B2、C1–C3、D1–D5 判定 PASS，18 文件测试前后哈希一致；只读安全复核未发现已证实新增阻断。真实模型、浏览器和完整 Agent→Cloud 跨进程链未测试。详见 `docs/issue-reports/agent-tool-file-reference/issue-p1-t1-1-round7-acceptance.md`。
- 随后补证实例 `tester_1_1_audit_supplement` 对 AUDIT-003 受控复现：假写工具返回成功后模型异常、超时、轮数或工具结果超限，真实 AgentController 只输出 error，不输出成功 Wiki 事件或 done；AgentPreviewProxy 测试回调 success=false、答案与交互为空。Cloud 控制器进入失败分支为代码事实，未联测真实数据库及实际 Wiki 写入。为交付阻断；AUDIT-002 实际控制器与数据库路径仍证据不足。负责人授权的三轮已用完，暂停修改、勾选与上传并上报。

### 相邻流程只读排查（早期历史记录，状态见上方 1.5 补充排查）

- 早期 `ATFR-AUDIT-001` 受控探针复现只查询可写 Wiki 位置后模型最终文本未发送；`ATFR-AUDIT-002` 是非空交互可能导致历史文字被替换为“请选择写入空间”的静态风险；`ATFR-AUDIT-003` 是成功写入后续步骤异常可能导致事件未传递的早期静态风险。该次排查时后两项均未整链路复现，三项当时待处理；后续结果分别见上方第 5–7 轮和 1.5 补充排查，不以本段早期状态代替当前状态。早期证据见 `docs/issue-reports/agent-tool-file-reference/issue-related-readonly-audit.md`。

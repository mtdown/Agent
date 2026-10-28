# Tasks

## 1. 空间权限与可写目录查询

- [x] 1.1 在 `WikiSpaceService` / `WikiSpaceServiceImpl` 加入带 `isWrite` 的统一检查，并保留读路径行为；用公开普通用户、个人空间所有者、团队 admin/editor/viewer、平台管理员和已删除空间的定向测试验证。
- [x] 1.2 在 `WikiFolderService` / `WikiFolderServiceImpl` 加入带 `isWrite` 的文件夹检查，验证跨空间、逻辑删除文件夹和团队 viewer 的失败路径，验证读目录仍可访问。
- [x] 1.3 将现有文档、文件夹、附件、导入和回收站写入口接入写权限检查，从目标记录而非请求字段确定所属空间；用页面接口/Service 测试验证公开用户可写、团队 viewer 可读不可写、跨空间 ID 不能越权、移动同时检查两端。
- [ ] 1.4 实现 Cloud 的轻量可写目录查询和内网接口，按当前用户返回字符串空间 ID 及可分页的活动文件夹；用目录测试验证个人默认候选、公开空间、可编辑团队、隐藏团队 viewer/他人个人空间、已删除文件夹和分页边界。合并前审查发现平台管理员无法列出未加入但可写的团队空间；负责人决定本次 PR 暂不修改，遗留项记录于 `IssueLog.xlsx` 第 263 行，后续需先明确管理员对他人个人空间的权限边界。
- [x] 1.5 在 `AgentLibraryGateway`、HTTP gateway 和 `AgentToolRegistry` 注册 `list_writable_wiki_locations`，用绑定/HTTP 假服务测试验证工具声明、当前用户绑定、参数校验及结果不包含文档正文。
- [x] 1.6 完成第一阶段的定向自动化验证和权限矩阵审查，汇报改动文件、通过/失败项及风险，交负责人确认第一步后再进入第二步；确认结果见文末 2026-10-28 收尾记录。

## 2. 创建文件夹和文档

### 2026-10-26 本轮进度

- 在原任务分支上快进到已合并的 `origin/main`。已写入 Agent 专用文件夹创建的 Cloud 入口与事务命名代码，并开始文档创建入口；尚未接入 Agent 侧工具，也未完成并发、失败回滚和缓存/RAG 验证，因此 2.1–2.5 保持未完成。
- TDD 红灯：文件夹接口缺失时返回 404；实现后本机文件夹命名与重名测试通过。文档接口缺失时返回 404；实现后接口返回 200，但测试通过 `MockMvc.getContentAsString()` 读取 UTF-8 JSON 时中文标题被错误解码，断言失败。已记入 `IssueLog.xlsx` 第 264 行。
- 负责人已授权修复编码问题。三处 MockMvc 响应读取改为显式 UTF-8，并增加数据库中文标题与正文原值断言；本机文档创建定向测试 1 项通过，确认本次是测试解码错误，IssueLog.xlsx 第 264 行已更新为“已修复”。第二阶段自动化总验、服务启动和页面手测尚未进行。
- Cloud 的创建服务和 Agent 的两个新工具及 HTTP gateway 已接通。旧 `write_wiki_document` 保留名称，改为单次事务内先建文件夹再建文档，返回真实字符串 ID；旧根目录写入类及其过时 Mockito 测试已移除。独立工具分两次提交，第二次失败时保留第一次创建的文件夹 ID。
- 并发测试发现 MySQL `REPEATABLE READ` 在加锁前建立旧快照，导致锁后同名计数仍为 0；三个创建事务改为 `READ COMMITTED`。测试夹具的个人空间唯一键与 MyBatis 一级缓存问题、超长 emoji 截断问题均已修复并记入 `IssueLog.xlsx` 第 265–267 行。
- 本机 Cloud 定向测试 `AgentWikiCreationLocalIT,AgentResourceInternalControllerTest` 共 9 项通过；Agent 定向测试 `AgentWikiCreationToolsTest,AgentWikiPartialFailureTest,AgentLibraryHttpGatewayTest` 共 8 项通过；未运行 Mockito 测试。Cloud 和 Agent 的 `mvn -q -DskipTests package`、`openspec validate add-agent-wiki-write --strict` 通过。故意破坏重名顺延和文件夹归属检查后，对应测试各变红；恢复后 Cloud 9 项重新通过。
- 提交前隐式假设核对：Spring 事务隔离级别确实作用于创建入口（并发测试验证）；`DocumentWikiServiceImpl.save` 发布 `DOC_CREATED` 且 RAG listener 在提交后处理（事件断言验证发布，实际异步建索引需在人工环境继续观察）；MyBatis 逻辑删除不会把活动文件夹错误过滤（拒绝路径测试验证）；缓存回调只在成功提交后执行（真实 Redis 列表键测试验证）。同名查重只序列化 Agent 写入口，页面并发写入仍沿用既有规则；Redis 失联发生在 DB 提交后时可能产生“已保存但工具报错”，需在后续可靠缓存任务中处理。
- 上线风险按可能性排序：①旧用户 Skill 可能仍按旧 `id` 字段读取 `write_wiki_document` 结果，触发后表现为找不到保存 ID，可用旧 Skill 回归发现；②页面写入与 Agent 写入并发时缺少数据库唯一约束，可能留下同名项，可用混合并发场景发现；③Redis 提交后清理失败可能使工具报错或短时缓存陈旧，可用故障注入发现。Review 三问：为何只对 Agent 路径加锁且不用唯一约束？旧返回结构的消费者是否都能处理 `folderId`/`documentId`？Redis 清理失败时如何避免“数据库已提交、用户以为失败”？

- [x] 2.1 实现 Agent 专用文件夹创建代码与内网接口：按 `Asia/Shanghai` 日期生成任务文件夹名、限制 128 字符、在同级活动文件夹重名时顺延；用重复、超长、空白和并发创建测试验证实际名称及 ID。
- [x] 2.2 实现 Agent 专用文档创建代码与内网接口：要求活动文件夹与空间匹配、重验写权限、规范标题并处理同级重名；用跨空间、删除文件夹、权限撤销、同名和保存失败测试验证无错误写入。
- [x] 2.3 将新建文档接入现有摘要、Markdown 元数据、缓存失效及 RAG 索引事件路径；用服务测试验证返回的字符串文档/文件夹 ID、缓存清理和索引通知。
- [x] 2.4 在 Agent gateway/注册器分别注册 `create_wiki_folder` 和 `create_wiki_document`，并将旧 `write_wiki_document` 作为兼容入口导向同一服务逻辑；用工具测试验证三个名字的参数/结果契约以及旧入口不再写空间根目录。
- [x] 2.5 验证先建文件夹后文档失败的部分成功结果，确保内部保留已有文件夹 ID 供后续处理、对用户只报告文件夹名称与位置且不声称保存成功；兼容入口行为见 `design.md`，自动化与负责人验收结论见下文及收尾记录。

## 3. 内置流程 Skill 与 Agent 循环

### 2026-10-26 本轮开发与暂停点

- 已加入固定版本内置 Wiki Skill，并在 Cloud 本机测试验证不同用户的目录可见、详情可读、保留 ID 不可修改；Agent HTTP 网关已能读取该保留 ID。Skill 校验器已接受 Wiki 查询、目标规划及两个创建工具。
- Agent 侧已加入 `plan_wiki_destination`，未指定位置返回等待选择的候选；唯一团队名称可解析目标；循环默认上限改为 6。脚本化模型测试验证六轮工具链，Wiki 工具轮次的未核验模型文字不会直接输出，内部 SSE 已能携带选项和工具活动。
- Cloud 已增量准备 `agent_message.interactionJson` / `choiceConsumed`，开始实现服务端一次性选项。`AgentWikiChoiceLocalIT` 曾因 `@Transactional` 所在的 `AgentWikiChoiceService` 声明为 `final` 而无法启动；负责人确认后移除 `final`，本机 `local` profile 定向测试 3 项通过。已记录于 `IssueLog.xlsx` 第 269 行。未做变异检验；当前结构化选择与页面接入均未完成。
- 本轮继续接入历史消息的安全投影、Agent 内部 Wiki SSE 的 Cloud 转换、点击 token 后的续写入口、选定空间的工具侧约束和前端按钮。Cloud 历史/投影测试及 Agent 工具测试通过，前端 `npm run type-check` 通过。新增结果预览 token 后，本机 `AgentWikiChoiceLocalIT,AgentPreviewProxyWikiEventTest` 共 8 项中 1 项失败：成功写入状态被误判为待选状态，详见 `IssueLog.xlsx` 第 271 行。按项目规则暂停下一轮修复和测试，等待负责人确认；变异检验与完整自动化验证尚未执行。
- 负责人授权修复后，按交互类型区分待选项和写入结果；Cloud 本机定向测试 8 项通过，`IssueLog.xlsx` 第 271 行已关闭。随后接通通过不透明 token 打开文档的权限重验接口与页面按钮；Cloud 与 Agent 定向测试通过。前端 `npm run type-check` 发现 `AgentWikiChoice | AgentWikiResult` 联合类型未在选择函数中收窄，TS2339 共 3 处；记入 `IssueLog.xlsx` 第 272 行并暂停下一轮修复测试。页面人工验收与变异检验尚未执行。
- 2026-10-27 负责人要求给出验收结果后，选择函数先用 `kind === 'destination_choice'` 收窄类型，`IssueLog.xlsx` 第 272 行已关闭。Cloud 19 项、Agent 11 项定向测试通过；前端 `npm run type-check`、`npm run build`，Cloud/Agent `mvn -q -DskipTests package`，以及 OpenSpec 严格校验均通过。把前端类型判断和 Cloud 一次性消费判断分别故意反转后，对应检查变红；恢复后分别重新通过。页面手测仍待负责人执行。

- [x] 3.1 在 Cloud 增加保留 ID 的内置、固定版本文档写入 Skill，使所有登录用户的 Skill 目录可发现、`get_skill` 可读取，且用户不能覆盖；用目录/详情/权限测试验证不依赖个人 Skill 分享表。
- [x] 3.2 将新工具加入 Skill 步骤校验和 Agent 提示，明确未指定位置时等待结构化选项点击、团队唯一匹配、歧义时停止、缺少内容时先询问、按顺序创建及结果核对；既有 Skill 定义校验与脚本化工具轨迹测试结果见下文。
- [x] 3.3 将默认循环上限由 4 调整为 6，既有脚本化测试覆盖唯一目标的 `get_skill → 查询目录 → 创建文件夹 → 创建文档 → 最终回答`、待选状态与续写轮次；负责人于 2026-10-28 明确取消与变更前 Agent 基线的耗时/token/工具结果长度对比，不再补测。
- [x] 3.4 已汇报第三阶段的文件位置、测试结果和工具调用轨迹；负责人于 2026-10-28 确认本阶段完成，结论见收尾记录。

## 4. 结构化空间选择与写入结果

- [x] 4.1 为 Agent 对话定义结构化目标选择和写入结果契约。选择项只含展示标签与一次性不透明 token；服务端在现有对话消息的结构化状态中保存 token 与真实空间 ID 的关联、用户/会话/原轮次和 30 分钟过期时间。既有测试覆盖重载恢复与较新轮次失效；负责人于 2026-10-28 确认完成。
- [x] 4.2 实现选择提交入口：原子消费 token，校验会话归属、时效与当前写权限，并恢复原始写入请求。既有测试覆盖一次性消费、跨用户及较新轮次失效；负责人于 2026-10-28 明确免除其余伪造、跨会话、过期和撤权场景的追加测试，不将免测项记为测试通过。
- [x] 4.3 统一个人/公开/团队空间的展示名称；团队保留真实名称，文件夹和文档保留真实名称与路径。存量 `个人区-<userId>` 仅作为内部存储值，页面选项、写入结果、错误和历史回放不显示其后缀或原始 Wiki ID；未新增空间名称映射表。代码与既有断言已核对，负责人于 2026-10-28 确认完成。
- [x] 4.4 调整 Wiki 写入轮次的 SSE 与历史存储：工具结果及未经核验的模型文字不得直接显示，选项和成功/部分失败结果由已验证的结构化数据生成；成功结果提供可打开文档的操作，部分失败只显示已创建文件夹的名称与位置。既有自动化证据见下文；负责人于 2026-10-28 确认页面验收成功。
- [x] 4.5 前端在 Agent 对话内展示可点击选项，未指定空间时推荐「个人空间」但不自动点击；明确且唯一的团队目标直接执行，多个候选等待点击。负责人于 2026-10-28 确认页面验收成功；未提供逐项手测记录。
- [x] 4.6 汇报结构化交互的接口字段、页面代码位置、自动化验证与人工手测清单，交负责人确认。负责人于 2026-10-27 反馈“验收通过，提交到远程”；未提供逐项测试记录，因此仅记录总体人工验收结论。

## 5. 跨模块验证与交付

### 当前验证记录（2026-10-22，第一阶段）

- 本机 `AgentWikiWriteLocalIT` 使用 `localhost:3307/Cloud` 的真实 MySQL 与 Spring Bean，3 项通过；事务回滚测试数据。Agent HTTP 环回测试 4 项通过。本轮没有运行 Mockito 测试；仓库已有的 Mockito 测试不作为本轮验证证据。
- 公开写入、团队 viewer 拒写、目录分页各做一次故意改错，3 次测试均按预期变红；恢复代码后定向测试重新通过。
- `openspec validate add-agent-wiki-write --strict` 通过。
- 两模块首次 `mvn -q -DskipTests package` 在 Spring Boot repackage 重命名 JAR 时失败；本机 8123/8124 服务进程正在使用相应 JAR。已记入 `IssueLog.xlsx` 第 262 行。负责人确认后使用 `stop-dev.ps1` 停服，两模块重新打包通过，再以 `start-dev.ps1 -NoTunnel -NoPause` 启动 8123/8124/3000。第一阶段人工确认和后续步骤仍未完成。
- 合并前审查发现管理员目录枚举与按 ID 权限不一致：未加入的团队空间对平台管理员可写，但不在 Agent 可写目录内；他人个人空间也存在按 ID 可访问但不在目录内的差异。已记入 `IssueLog.xlsx` 第 263 行。负责人决定本次 PR 不修改；后续任务明确权限边界后再处理。

- [x] 5.1 运行 Cloud、Agent 和前端定向测试、各自构建及 `openspec validate add-agent-wiki-write --strict`；按 `AGENTS.md` §8 列出隐式假设、生产风险和三个 review 质问，并对 2–3 个关键权限/命名/选择判断做变异检验，记录可复现结果。

### 2026-10-27 自动化验收与风险核对

- Cloud：`AgentWikiChoiceLocalIT,AgentPreviewProxyWikiEventTest,BuiltInWikiWriteSkillLocalIT,AgentWikiCreationLocalIT` 共 19 项通过。Agent：`AgentWikiCreationToolsTest,AgentWikiPartialFailureTest,AgentWikiChoiceLoopTest,AgentWikiDestinationToolTest,AgentWikiWorkflowLoopTest,AgentControllerWikiEventTest` 共 11 项通过。前端类型检查和构建、Cloud 与 Agent 打包、OpenSpec 严格校验、`git diff --check` 均通过。本轮没有用 Mockito 测试作为证据。
- 变异检验 1：把前端 `choice?.kind !== 'destination_choice'` 反转为 `===`，`vue-tsc` 因 `consumed`、`expiresAt` 不存在而失败；恢复后类型检查通过。变异检验 2：把 Cloud 的 `choiceConsumed != 0` 反转为 `== 0`，`AgentWikiChoiceLocalIT` 出现 2 个错误；恢复后 7 项通过。
- 对外契约变化：Agent 对话新增结构化 `interaction`、选择提交与文档预览 token 入口；消息表新增 `interactionJson`、`choiceConsumed`；旧 `write_wiki_document` 改为创建任务文件夹后写文档，返回字段变化。前端 Wiki ID 保持字符串，用户可见投影只提供标签和不透明 token。
- 隐式假设及失效表现：数据库已应用新增消息列，否则选择状态读写报 SQL 错；`@Transactional` 代理生效，否则消费与读取可能不在预期事务中；选项 token 随机且服务端留存，泄露或可预测会产生错误选择；每次点击重新校验写权限，缺失会让撤权用户仍能写；Redis 清理和 RAG 事件在提交后可用，故障时可能短暂见到旧目录或延迟检索；前端把 19 位 ID 当字符串，转成 Number 会打开错误文档；默认 6 轮足够 Skill/目录/规划/创建链路，模型额外调用工具时可能触顶。非空假设：目录候选必须至少有一项，缺失时返回明确失败而不能创建空文件夹。
- 按可能性排序的生产风险：①模型未按 Skill 顺序或调用超过 6 轮，表现为中途停止，可从 Agent 工具轨迹和轮次日志发现；②旧自定义 Skill 依赖 `write_wiki_document` 旧结果字段，表现为写入成功但后续引用失败，可用存量 Skill 回归发现；③Redis/异步 RAG 故障使刚写入的文档短时不可见，可用停 Redis 或延迟事件的本机测试发现；④页面与 Agent 同名写入并发，可能出现重复名称，可用混合并发测试发现。
- Mentor review 三问：为什么一次性选择需绑定用户、会话、原轮次并在点击时重验权限？为什么旧 `write_wiki_document` 契约变化没有为存量自定义 Skill 提供字段兼容层？为什么 DB 提交后缓存/RAG 失败仍可能让用户认为写入失败？
- 页面验收未完成，负责人反馈前不得标记 5.3 或交付完成；平台管理员目录枚举差异仍按第 263 行暂缓。
- [x] 5.2 正式测试失败时已按规则暂停、记录 `IssueLog.xlsx` 第 271/272 行，并在负责人授权后修复；最终自动化通过，变异检验结果见上。
- [x] 5.3 自动化验证通过后，依次使用当前分支的 `stop-dev.ps1` 和 `start-dev.ps1` 启动服务，交付前后端地址、账号/数据前提及人工测试清单。负责人于 2026-10-27 反馈总体验收通过；未提供逐项测试记录。
- [x] 5.4 最终改动、验证证据及遗留风险已在 PR #44 汇报，回滚方式见 `proposal.md`；负责人已合并 PR #44 到远程 `main`。本次归档收尾另行上传，不由 AI 合并到 `main`。

### 2026-10-27 提交前审查暂停点

- 负责人已确认页面总体验收并要求推送远程分支。提交前只读审查另发现两个此前验收未覆盖的场景，已记入 `IssueLog.xlsx` 第 274–275 行：文件夹成功但文档失败时历史缺少结构化部分成功结果；兼容工具 `write_wiki_document` 成功时结果缺少真实文件夹名称。
- 这两项与本 change 的结构化结果及历史回放要求冲突。依 `AGENTS.md` 第 4 节先汇报原因和拟修复方案，等待负责人确认下一轮修复与测试；确认前不提交或推送。此前的页面验收结论仍按负责人原话保留。

### 2026-10-27 审查问题修复与新增手测

- 负责人回复“开始修改”后，在本分支修复第 274–275 行。先新增三个失败测试，分别证明部分成功缺少 `interactionJson`、SSE 未发送结构化结果、兼容工具缺少真实文件夹名；随后由 Cloud 生成 `wiki_partial_result` 的安全投影并保存在对话历史，前端显示空间和文件夹名且不出现文档打开按钮。文档创建结果统一返回实际文件夹名，兼容工具的结果及历史回放因此包含重名顺延后的名称。
- Cloud 定向测试 `AgentWikiChoiceLocalIT,AgentPreviewProxyWikiEventTest,BuiltInWikiWriteSkillLocalIT,AgentWikiCreationLocalIT` 共 22 项通过；Agent 相关 6 类测试共 11 项通过；前端构建通过。变异检验：反转部分成功生成条件，相关测试变红；移除文档结果的文件夹名，兼容工具测试变红；恢复后全部重新通过。`IssueLog.xlsx` 第 274–275 行已标记修复，页面新增场景仍待负责人手测。
- 新增手测：使用会造成文档写入失败的条件，确认对话即时结果和重载历史均显示“文件夹已创建、文档未保存”，无打开文档按钮和内部 ID；让存量 Skill 调用 `write_wiki_document` 并制造同名文件夹，确认成功结果与重载历史都显示顺延后的真实文件夹名。两项手测结论返回前，相关 2.5 / 4.4 验收任务保持未完成。
- 修复后复核：Cloud、Agent 的 `mvn -q -DskipTests package`，前端 `npm run build`，OpenSpec 严格校验和 `git diff --check` 通过；用项目脚本停服再启动，8123/8124/3000 均就绪。对外变化为文档工具结果增加 `folderName`，Agent 消息增加 `wiki_partial_result`；不改表结构。假设 `requireFolderAccess` 返回的活动文件夹名非空，否则结果无法提供路径；假设 Agent 正常输出 `done`，否则已提交的文件夹仍可能没有完成消息；假设网关不删减新 `folderName` 字段，否则兼容工具的投影会拒绝不完整结果。对应提前发现手段为真实 MySQL 同名测试、SSE/历史回放测试和 Agent 工具轨迹。
- 本轮风险按可能性排序：①旧对话中已经保存为纯文字的部分失败消息不会自动补成结构化结果，可从存量历史抽查发现；②模型或网络在文件夹创建后未产生 `done` 时，仍可能留下空文件夹而无完成消息，可用中断流测试发现；③有外部消费者严格解析旧文档工具 JSON 时，新增 `folderName` 字段可能需要兼容检查，可搜调用方或做契约测试。Mentor review 三问：为何在 `interactionJson()` 取值时才生成部分成功状态？为什么历史投影绝不输出其中的真实 `folderId`？兼容工具返回的是数据库实际名称还是由标题猜测？

### 2026-10-28 收尾确认与归档边界

- 负责人确认 1.6、2.5、3.4、4.4、4.5 已完成且验收成功；此前 2026-10-27 的总体验收结论保持原样，不补造逐项手测记录。3.2、4.1、4.3 的实现与既有测试断言经只读核对后，负责人确认完成。
- 负责人取消 3.3 中与变更前 Agent 基线的耗时、token、工具结果长度对比，并要求 3.3、4.2 不再追加测试。既有自动化测试与变异检验证据见上文；免测部分不作为“测试通过”项。
- 1.4 的平台管理员可写目录枚举差异仍按 `IssueLog.xlsx` 第 263 行暂缓，本次归档保留该未完成项；后续明确管理员对他人个人空间的权限边界后，以独立任务处理，不在本次收尾中改变权限逻辑。
- PR #44（`https://github.com/mtdown/Agent/pull/44`）已于 2026-10-28 合并到远程 `main`。本次只更新 OpenSpec 记录、同步主规格并归档；不修改实现代码。回滚时撤销本变更的工具、Skill、结构化交互与写权限入口变动，保留已有 Wiki 数据，详见 `proposal.md`。
- 归档前验证：`openspec validate add-agent-wiki-write --strict` 通过；`openspec validate --specs` 为 30 项通过、0 项失败；五份 delta spec 的全部 17 条需求与主规格逐条一致；`git diff --check` 通过。`IssueLog.xlsx` 第 276 行记录的文档末尾空行问题已修复。本次仅改文档，未新增功能测试或变异检验；功能验证依据为上述历史记录。

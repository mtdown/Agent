# Tasks

## 1. Wiki 问答 Skill

- [x] 1.1 增加可读取的内置 Wiki 问答 Skill，并验证其仅使用已有 RAG 证据、保留普通对话及现有 Skill；按 A1–A3 独立验收。

## 2. 网页导入接线

- [x] 2.1 接入授权的 Wiki 网页导入工具及真实结果投影，按负责人确认安全重试缓存清理；按 B1–B5 独立验收。负责人接受 #7 的证据不足并允许交付，测试结论未改判。
- [x] 2.2 按负责人授权核实并修复基线会话持久化映射冲突，保持既有断言、初始化升级与会话行为；按 D1–D2 独立验收。负责人接受 #6 的证据不足并允许交付，测试结论未改判。

## 3. 导入 Skill 与提示词

- [x] 3.1 接入内置网页导入 Skill、整理系统提示词，并验证完整流程和既有能力回归；按 C1–C3 独立验收。负责人接受 #10、#13、#14 的证据不足并允许交付，原 C1 失败记录及测试结论未改判。
- [x] 3.1a 增加服务端可信导入草案、持久化与单次确认执行，封住旧内部直通路径；执行负责人批准调整后的 C1 和其余原验收，并验证 E1–E3。负责人接受完整联合测试证据不足并允许交付。
- [x] 3.1b 接入前端导入确认卡片、取消及历史状态，核对原有空间选择兼容并与服务端联合验证 E1–E3。负责人接受 #14 的真实浏览器及联合验证证据不足并允许交付。
- [x] 3.2 核对各轮独立证据并同步主规格、严格校验；负责人批准证据不足例外交付，原始结论和未决问题见 `docs/issue-reports/agent-wiki-workflows/delivery-exceptions.md`，归档后验证。

## 固定 acceptance

所有命令在仓库根目录运行，PATH 前置 IntelliJ IDEA 2024.1 内置 Maven bin；使用本地 mock，不访问付费外网、不修改用户数据。开发、测试在每轮之前记录当前分支与基线。测试时禁止开发者写入被测文件。

A1. Cloud 内置 Skill：无需用户自建，当前登录用户可在目录中发现并按固定版本读取 Wiki 问答 Skill；该 Skill 使用已有 RAG 证据、区分无证据并要求来源引用，不包含不存在的检索工具或代码执行步骤。内置能力在用户 Skill 超出目录上限时仍可发现。
    verify：`mvn -f cloud/pom.xml "-Dtest=AgentWikiAnswerSkillTest" test`
    预期：退出码 0，断言目录、读取、版本、用户数据隔离及定义内容。

A2. Agent/RAG 集成：以可控模型和测试上下文核对问答指令包含当前 RAG 证据和 Skill 读取；空检索维持现有短路，不能声称模型被调用；用户普通对话和其他已有 Skill 不被强制替换。
    verify：`mvn -f cloud_agent/pom.xml "-Dtest=AgentWikiAnswerLoopTest" test`；`mvn -f cloud/pom.xml "-Dtest=AgentPreviewControllerTest" test`
    预期：两个命令退出码均为 0，断言证据、无结果和普通对话分支。

A3. 回归：既有 Wiki 写入 Skill、目录权限与问答不因新增内置 Skill 丢失；检查实现无数据库、依赖、公开 HTTP 接口变化。
    verify：`mvn -f cloud/pom.xml "-Dtest=AgentSkillCatalogTest,AgentPreviewControllerTest" test`；`mvn -f cloud_agent/pom.xml "-Dtest=AgentLoopTest,AgentToolRegistryTest" test`
    预期：均退出 0，测试覆盖原内置 Skill 和一般工具；diff 符合范围。

B1. Agent 导入契约：受控工具接收精确用户选定空间、可选已有目录和网页 URL，复用现有导入服务并保留每项标题、来源、结果及文档引用；长正文不经 Fetch 截断；无用户保存意图时不得写入。
    verify：`mvn -f cloud/pom.xml "-Dtest=AgentUrlImportTest" test`；`mvn -f cloud_agent/pom.xml "-Dtest=AgentUrlImportToolTest" test`
    预期：退出码 0；断言真实调用链、用户、目标、超过 5000 字正文与来源、正常不写分支。

B2. 权限与目标：缺少或伪造内部密钥、不可写空间、跨空间/已删除目录、选择后失权不得创建；根目录 null 与合法目录行为正确；工具不接收模型自选 userId，ID 不泄露到用户文本。
    verify：`mvn -f cloud/pom.xml "-Dtest=AgentUrlImportTest" test`；`mvn -f cloud_agent/pom.xml "-Dtest=AgentUrlImportToolTest" test`
    预期：退出码 0；各拒绝路径断言无写入，正常路径绑定真实用户与选定目标。

B3. 结果和安全：混合结果逐项可识别；不可确认写入不得报告明确失败或成功、不得自动重试；URL 安全和限制复用现有服务；导入结果支持现有 Wiki 安全投影，既有写入结果不回归。
    verify：`mvn -f cloud/pom.xml "-Dtest=AgentUrlImportTest,AgentPreviewProxyWikiEventTest,WikiBatchImportServiceImplTest" test`；`mvn -f cloud_agent/pom.xml "-Dtest=AgentUrlImportToolTest,AgentWikiChoiceLoopTest" test`
    预期：退出码 0；断言逐项状态、请求数、私有地址拒绝、内部 ID 不泄漏及原有事件。

B4. 回归与范围：不新增依赖、数据库迁移或公开 HTTP API；全模块测试通过。
    verify：`mvn -f cloud/pom.xml test`；`mvn -f cloud_agent/pom.xml test`
    预期：均退出码 0；测试无失败或错误，diff 符合范围。

B5. 缓存异常：文档成功保存后缓存清理首次失败时只重试清理一次；重试成功时保存次数仍为 1；重试失败时仍只保存一次、报告已保存及缓存刷新失败并保留脱敏诊断；不能确认持久化的结果保持未知而不重试保存。
    verify：`mvn -f cloud/pom.xml "-Dtest=AgentUrlImportTest,WikiBatchImportServiceImplTest" test`
    预期：退出码 0；断言保存次数、缓存调用次数、可确认结果及异常记录。

B6. 负责人确认内部方案：Agent 导入内部逐项区分成功、明确失败、未知和未执行；未知时停止剩余项和本轮后续写入，HTTP 中断不假设写入失败；仅确认提交后清缓存；目标每项写前重新校验且选择后失权、目录失效时拒绝。公开导入接口字段和原 SUCCESS/FAILED 语义不变。
    verify：`mvn -f cloud/pom.xml "-Dtest=AgentUrlImportTest,WikiBatchImportServiceImplTest" test`；`mvn -f cloud_agent/pom.xml "-Dtest=AgentUrlImportToolTest" test`
    预期：均退出码 0；模拟提交/网络异常、权限变化及批次混合结果，断言写入次数、未执行状态与公开接口兼容；若现有并发锁无法覆盖权限变更路径则停止并上报。

B7. 负责人确认扩大共享路径：用真实 MySQL 和隔离测试数据验证导入与撤权/退出、目标目录及祖先删除、空间删除的可控并发；根据实际提交顺序检查当前权限、目录与文档状态，不得出现撤权后继续创建或删除后留下孤立文档；验证死锁/失败不重复保存、不虚报成功。检查相关权限变更、目录移动和删除使用协调锁与当前读，且公开接口签名不变。
    verify：`mvn -f cloud/pom.xml "-Dtest=AgentUrlImportConcurrencyLocalIT" test`
    预期：退出码 0；测试使用经验证隔离的本地 MySQL 测试数据，屏障控制竞争并直接读回持久化状态，执行后清理全部测试数据；无法安全隔离或未执行则 BLOCKED，不得以 mock 代替。

D1. 保留原 `AgentConversationPersistenceMappingTest` 断言，恢复映射约束并保留数据库初始化、旧消息字段补齐与会话数据使用行为；不新增迁移、公开接口或删除数据。
    verify：`mvn -f cloud/pom.xml "-Dtest=AgentConversationPersistenceMappingTest,AgentConversationStoreTest" test`
    预期：退出码 0，测试实际覆盖原映射断言和既有会话行为。

D2. 现有会话与 Wiki 交互回归：初始化及历史消息投影、权限归属、写入后会话重开等既有断言通过；与 B4 全量回归一起核对。
    verify：`mvn -f cloud/pom.xml "-Dtest=AgentConversationStoreTest,AgentPreviewControllerTest,AgentPreviewProxyWikiEventTest" test`；`mvn -f cloud/pom.xml test`
    预期：均退出码 0；不弱化或删除现有断言，报告所有跳过项及其原因。

C1. 内置链接导入 Skill 按目标确认、权限、真实工具导入和逐项结果顺序执行；用户只要求摘要时应避免导入，模型误触发确认卡时未经用户明确确认不得实际写入。负责人于 2026-11-01 明确批准该验收变更；原要求为“用户只要求摘要不得调用写工具”，第 2 轮对该原要求的 FAIL 及问题 #10 保留作为历史记录，不改判。
    verify：`mvn -f cloud/pom.xml "-Dtest=AgentUrlImportSkillTest" test`；`mvn -f cloud_agent/pom.xml "-Dtest=AgentWikiWorkflowsLoopTest" test`
    预期：退出码 0；可控模型断言工具选择、位置、结果与无写分支，并模拟模型误调用导入而未确认的零写入。

C2. 系统规则简洁保留用户目标和时间约束、可信边界与真实状态；不承诺运行不存在的代码工具。精简后普通聊天、原 Wiki 写入、Skill/代码库和联网搜索仍可用。
    verify：`mvn -f cloud_agent/pom.xml "-Dtest=AgentWikiWorkflowsLoopTest,AgentLoopTest,AgentToolRegistryTest,DeepSeekWebResearchTest" test`
    预期：退出码 0；断言对话、工具、目标、拒绝不可信指令和失败/未知信息边界；不以模型自由生成结果保证安全。

C3. 最终 Cloud 和 Agent 单元回归，核对 OpenSpec、权限和变更范围；不测试真实外网。
    verify：`mvn -f cloud/pom.xml test`；`mvn -f cloud_agent/pom.xml test`；`openspec validate agent-wiki-workflows --strict`
    预期：全部退出 0，测试无失败或错误；严格校验通过。

E1. 导入确认：待确认卡片准确展示空间、目录及全部链接；未确认、取消或过期不写入，只有登录用户明确确认后按服务端保存的草案执行；内部密钥或模型参数不可绕过。
    verify：本机受控 Agent、Cloud 和前端交互测试，使用模拟网页和隔离测试数据，核对确认前零写入、确认后调用及卡片状态。
    预期：实际写入前必须完成可信用户确认，摘要请求不产生写入，逐项结果真实可见。

E2. 确认绑定与并发：验证跨用户、跨会话、参数篡改、过期及取消均拒绝；重复或并发确认至多执行一次；确认后失权、目录失效或空间/目录路径改名与移动导致确认快照失效时不写入；在共享锁下检查当前状态。
    verify：本机隔离数据库并发测试和服务端授权测试，直接核对导入次数与保存状态。
    预期：无越权、无重复写入，既有权限/目录并发固定验收不降低。

E3. 状态与恢复：确认后部分成功、缓存清理失败、提交未知、断线及刷新时状态如实呈现；不能把确认、领取或未知误报为成功，也不得自动重试不确定写入。
    verify：本机受控组件与交互测试，模拟混合结果、提交未知及刷新恢复；无真实外网。
    预期：显示已知状态与限制，未能恢复的结果标记未知；原有会话和 Wiki 写入行为不回归。

## 验证记录及回滚

开发前基线 bbc1819f7d44a0968638b193ff5509fec5786046：Cloud `AgentSkillCatalogTest,AgentPreviewControllerTest` 5 项通过；Agent `AgentLoopTest,AgentToolRegistryTest` 28 项通过。两个模块按现有 Maven 测试路径运行，不启停开发服务、不修改用户数据。1.1 第 1 轮 A1–A3 独立验收 PASS，证据 `docs/issue-reports/agent-wiki-workflows/test-1-1-round1.md`。负责人确认仅重试缓存清理，重试失败仍报告已保存及缓存未刷新，保留异常信息；对问题 #1 确认采用内部逐项状态及事务边界，公开接口保持兼容；对问题 #2 确认扩大共享并发路径范围并用真实 MySQL 验证。每阶段记录独立报告、快照摘要、失败编号及回滚；问题当前状态仅在本机 SQLite。回滚相应应用代码与内置 Skill 到任务前版本；保留已有 Wiki 内容、索引与用户 Skill，不进行数据库迁移。

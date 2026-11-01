# 开发记录：Wiki 问答 Skill，第 1 轮

- Change：`agent-wiki-workflows`；阶段 1；原子任务 1.1。
- 分支：`feature/agent-wiki-workflows开发`；开发前基线：`bbc1819f7d44a0968638b193ff5509fec5786046`。
- 开发身份：仅实现内置 Wiki 问答 Skill，未修改 OpenSpec、数据库、依赖、公开 HTTP API、RAG 权限或检索流程。未启停服务或操作用户 Wiki 数据。

## 修改事实

1. 新增 `cloud/src/main/java/com/et/cloud/controller/AgentBuiltInWikiAnswerSkill.java` 和 `cloud/src/main/resources/agent-wiki-answer-skill.json`。固定 ID 为 `builtin:wiki-answer`，版本 1；定义仅含响应步骤，要求使用当前轮证据、引用与不足说明。
2. 修改 `cloud/src/main/java/com/et/cloud/controller/AgentLibraryStore.java`，在原有内置写入 Skill 后、用户条目前插入问答 Skill，接入固定版本读取；用户 Skill 读取和查询沿用现有用户参数及权限逻辑。
3. 修改 `cloud_agent/src/main/java/com/et/agent/AgentLibraryHttpGateway.java`，允许新内置 ID；身份由既有工具绑定传入。
4. 新增 `cloud/src/test/java/com/et/cloud/controller/AgentWikiAnswerSkillTest.java`、`cloud_agent/src/test/java/com/et/agent/AgentWikiAnswerLoopTest.java`；修改 `cloud/src/test/java/com/et/cloud/controller/AgentPreviewControllerTest.java`，增加空检索不调用 Agent 的断言。

## 过程与自检

先核对任务卡、规则、OpenSpec 工件以及现有 Skill 校验器、目录、RAG 空结果短路；确认纯响应步骤可以表达该 Skill。随后实现 Cloud 内置定义及目录接线和 Agent 标识校验，编写模拟用户目录、可控模型和 HTTP 网关测试。自检期间发现 Python 写回造成三个已跟踪文件行尾变化，已恢复原有 LF 行尾并通过 `git diff --check`；功能内容没有因此更改。

在仓库根目录将 `C:/Program Files/JetBrains/IntelliJ IDEA 2024.1/plugins/maven/lib/maven3/bin` 前置 PATH，按以下实际顺序执行：

| 命令 | 结果 |
| --- | --- |
| `mvn -f cloud/pom.xml "-Dtest=AgentWikiAnswerSkillTest" test` | 退出码 0；1 项通过 |
| `mvn -f cloud_agent/pom.xml "-Dtest=AgentWikiAnswerLoopTest" test` | 退出码 0；当时 2 项通过 |
| `mvn -f cloud/pom.xml "-Dtest=AgentPreviewControllerTest" test` | 退出码 0；4 项通过 |
| `mvn -f cloud/pom.xml "-Dtest=AgentSkillCatalogTest,AgentPreviewControllerTest" test` | 退出码 0；5 项通过 |
| `mvn -f cloud_agent/pom.xml "-Dtest=AgentLoopTest,AgentToolRegistryTest" test` | 退出码 0；28 项通过 |
| 补充网关用户绑定和其他 Skill 选择测试后，再运行 `mvn -f cloud_agent/pom.xml "-Dtest=AgentWikiAnswerLoopTest" test` | 退出码 0；4 项通过 |
| `git diff --check` | 退出码 0；无输出 |

Cloud 与 Agent 同模块测试按顺序执行。测试均为开发自检，不能替代独立验收；测试使用模拟用户数据、受控模型和本地模拟 HTTP 服务，没有请求真实外网。

## 限制与回滚

模型能否在真实对话中每次选择适用 Skill 不由上述受控模型测试保证；空检索仍由 Cloud 现有逻辑短路，不调用模型。没有新增统一操作记录、网页导入或代码执行能力。

回滚本任务：还原上述三个已修改的代码及测试文件，移除上述四个新增文件；保留其他任务文件、用户 Wiki 与用户 Skill 数据，不需数据库回滚。本记录本身可按项目文档保留策略处理。

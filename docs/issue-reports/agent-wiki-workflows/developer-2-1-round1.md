# 阶段 2.1 第 1 轮开发过程与修改事实

记录时间：2026-10-31。Change `agent-wiki-workflows`，任务分支 `feature/agent-wiki-workflows开发`，任务基线 `bbc1819f7d44a0968638b193ff5509fec5786046`。本记录是开发自检，不是独立验收。当前状态 **PARTIAL**：Cloud 全量 B4 有基线失败 #3；其他定向及并发自检结果如下，未经过独立测试。

## 修改文件

下列均为本任务新增或修改，路径相对项目根目录：

- 修改 `cloud/src/main/java/com/et/cloud/controller/AgentResourceInternalController.java`：增加内部密钥保护的 URL 导入入口，使用可信用户参数。
- 修改 `cloud/src/main/java/com/et/cloud/controller/AgentWikiChoiceProjection.java`、`cloud/src/main/java/com/et/cloud/controller/AgentWikiChoiceService.java`：导入逐项状态及来源、安全显示、已确认结果和缓存警告投影。
- 修改 `cloud/src/main/java/com/et/cloud/mapper/UserMapper.java`、`cloud/src/main/java/com/et/cloud/mapper/WikiSpaceMapper.java`：当前用户锁定读取和空间逻辑删除 SQL。
- 修改 `cloud/src/main/java/com/et/cloud/service/impl/WikiBatchImportServiceImpl.java`：抽取共享抓取和文档构造；公开结果保持现有字段及 SUCCESS/FAILED 两态，缓存清理失败时保留已确认的成功结果和警告。
- 修改 `cloud/src/main/java/com/et/cloud/service/impl/WikiFolderServiceImpl.java`、`cloud/src/main/java/com/et/cloud/service/impl/WikiSpaceServiceImpl.java`：相关创建、移动、删除、团队成员权限变更和退出路径加入空间协调锁与复查；空间删除显式更新删除标记。
- 新增 `cloud/src/main/java/com/et/cloud/service/impl/AgentUrlImportService.java`、`cloud/src/main/java/com/et/cloud/service/impl/WikiMutationLock.java`：Agent 独立逐项状态、受控事务、提交后缓存清理和协调锁。
- 修改 `cloud/src/test/java/com/et/cloud/service/impl/WikiBatchImportServiceImplTest.java`；新增 `cloud/src/test/java/com/et/cloud/service/impl/AgentUrlImportTest.java`、`cloud/src/test/java/com/et/cloud/service/impl/AgentUrlImportConcurrencyLocalIT.java`。
- 修改 `cloud_agent/src/main/java/com/et/agent/AgentLibraryGateway.java`、`AgentLibraryHttpGateway.java`、`AgentLoop.java`、`AgentToolRegistry.java`、`AgentWikiBoundary.java`：工具注册、用户绑定、目标别名、未知后禁止后续写入、保留事件。
- 修改 `cloud_agent/src/test/java/com/et/agent/AgentToolRegistryTest.java`；新增 `cloud_agent/src/test/java/com/et/agent/AgentUrlImportToolTest.java`。

工作区另含阶段 1 已验收的 `AgentLibraryStore.java`、`AgentPreviewControllerTest.java`、`AgentBuiltInWikiAnswerSkill.java`、`agent-wiki-answer-skill.json`、`AgentWikiAnswerSkillTest.java`、`AgentWikiAnswerLoopTest.java`，以及该阶段修改过且本阶段继续修改的 `AgentLibraryHttpGateway.java`；它们不得在本阶段回滚时误删。OpenSpec 和已有报告由总管维护，本任务未改 OpenSpec。

## 过程与设计事实

先发现原公开结果只有 SUCCESS/FAILED、无法表达未知，登记 #1；负责人确认内部四态方案。再发现原空间锁未覆盖撤权及目录变更，登记 #2；负责人确认扩大共享锁路径。当前锁次序为先锁空间行，再锁当前用户行并清除 MyBatis 会话缓存；相关事务使用 READ_COMMITTED，持锁后重查空间、成员和目录；不对可能已写入的保存自动重试。Agent 每项经 REQUIRES_NEW 事务提交，提交不确定视为未知并停止余项和本轮后续写入；缓存仅在确认提交后清理，最多额外重试一次。

B7 初始测试发现现有空间删除调用 `updateById` 未更新逻辑删除字段，隔离库读回文档删除但空间未删除；日志 SQL 缺少 isDelete 更新。登记 #4，并在授权的空间删除路径改为显式 SQL；随后真实 MySQL 测试重跑通过。真实 MySQL 测试还模拟保存时的数据库死锁异常：异常由测试替身注入，**没有制造真实数据库死锁**；回执不能把它描述为真实死锁复现。

初次容器测试因 Windows Java ProcessBuilder 模板引号丢失失败；后发现 `mysqladmin ping` 可在认证失败时返回成功，导致初始化尚未完成即加载 schema。诊断和修复改为标签 JSON 检查及带认证 TCP 查询等待就绪。首次故障遗留的本任务临时容器已逐一核对名称、标签、ID 并连同专属匿名卷删除。之后测试内使用随机容器名、随机回环端口、独立 MySQL 8.0.45 和独立 schema，按标签、名称、ID 验证后删除容器及匿名卷；最终按本任务标签查询无容器残留。B7 没有使用现有数据库、真实用户或外网，网页获取和缓存/RAG 副作用由测试替身控制。

## 自检记录

以下均使用仓库根目录和指定 IntelliJ Maven PATH。目标命令顺序执行；日志在 `tmp/`，可能含调试数据，不直接作为公开报告。

| 命令 | 实际结果 |
| --- | --- |
| `mvn -f cloud/pom.xml "-Dtest=AgentUrlImportTest" test` | 退出 0，11 项通过（`tmp/wiki-final5-cloud-1.log`） |
| `mvn -f cloud/pom.xml "-Dtest=AgentUrlImportTest,AgentPreviewProxyWikiEventTest,WikiBatchImportServiceImplTest" test` | 退出 0，36 项通过（`tmp/wiki-final4-cloud-target.log`） |
| `mvn -f cloud/pom.xml "-Dtest=AgentUrlImportTest,WikiBatchImportServiceImplTest" test` | 退出 0，33 项通过（`tmp/wiki-final5-cloud-2.log`） |
| `mvn -f cloud/pom.xml "-Dtest=AgentUrlImportConcurrencyLocalIT" test` | 退出 0，15 项通过（`tmp/wiki-final3-cloud-4.log`）；此后仅在另一测试类补充三个定向测试，B7 生产文件与并发测试文件未再修改 |
| `mvn -f cloud_agent/pom.xml "-Dtest=AgentUrlImportToolTest" test` | 退出 0，6 项通过（`tmp/wiki-final4-agent-1.log`） |
| `mvn -f cloud_agent/pom.xml "-Dtest=AgentUrlImportToolTest,AgentWikiChoiceLoopTest" test` | 退出 0，8 项通过（`tmp/wiki-final4-agent-2.log`） |
| `mvn -f cloud_agent/pom.xml test` | 退出 0，92 项通过（`tmp/wiki-final4-agent-full.log`） |
| `mvn -f cloud/pom.xml test` | 退出 1，381 项、1 失败、0 错误、1 跳过；唯一失败为 #3（`tmp/wiki-final5-cloud-full.log`） |
| `git diff --check` | 退出 0，无空白错误；其后仅增加本记录 |

#3 为 `AgentConversationPersistenceMappingTest` 第 31 行：测试要求 `AgentConversationStore` 不含 JdbcTemplate，但实际含有。通过 `git archive` 将基线 cloud 目录导出到 tmp（非 Git worktree、不切换分支），运行该单元测试，同样退出 1；证据见 `baseline-cloud-mapping-failure.md`。不能因此将 B4 改称通过；修复和放行由总管／负责人裁决。

## 全量测试隔离遗漏

**重要：** Cloud 全量命令中既有 `CloudApplicationTests` 激活 local 配置并启动真实 Spring 上下文，连接了本机 3307 已有 Cloud 库；我在执行前没有识别这一点，违反任务要求的测试数据隔离。日志显示启动器只读查询了管理员数量与公开空间，均已存在，未见启动器初始化插入；这不证明整个运行不存在所有副作用。没有证据表明发生业务写入，但不能断言绝无写入。已及时告知总管，并在问题库登记 #5（待处理）；后续 Cloud 全量测试必须先隔离数据源和启动副作用。原始日志保留在 `tmp/wiki-final3-cloud-full.log` 和 `tmp/wiki-final5-cloud-full.log`，包含现有空间的内部标识及其他调试信息，不应公开分享；本记录仅保留脱敏事实。前述 B7 的 MySQL 测试本身仍为独立容器。

## 问题状态及未完成

本机问题库中 #1、#2、#3、#4、#5 当前均为“待处理”；本次新增 #5，未修改其他问题状态。#1、#2、#4 已有本轮实现和针对性自检证据，但尚未更新待验证或经过独立验收；#3 基线失败仍待处理。B4 尚未通过，阶段 3 未实施，未同步规格、归档、上传或合并。控模测试只能证明给定调用序列，不保证自由模型绝不会错误调用工具；HTTP 中断后的已执行部分无法完全重建。后续应先核对快照、问题状态和测试隔离，再由全新测试实例独立验收。

## 回滚

回滚应以阶段 2 前的总管快照为界，恢复本记录“修改文件”所列阶段 2 代码和测试，移除阶段 2 新增四个文件，保留阶段 1 文件、OpenSpec、已有 Wiki 数据和用户 Skill。`AgentLibraryHttpGateway.java` 有阶段 1 与阶段 2 的叠加修改，不可整体恢复到基线而丢失阶段 1。无数据库迁移；不要删除现有 Wiki 数据或清理用户容器。

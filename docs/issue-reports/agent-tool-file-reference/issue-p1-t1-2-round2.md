# 1.2 第2轮独立验收问题记录

实例 `/root/tester_1_2_round2`；2026-10-30；分支 `feature/agent-tool-file-reference开发`，HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`。总体 FAIL。测试前后 20 文件逐项 SHA256 与 `%TEMP%/agent-tool-file-reference-1.2-round2-sha256.txt` 全部匹配。完整 diff SHA256 `CB69A033AA7E56AB38C79B216757B886BD3F3B6DEE9840993A79E7B7EF0C2F8F`；开发记录 SHA256 `4437837AA251B2964D463FA1682241AE4FA72067CBA0A675305FE2EE74D1E95E`。未运行基线，不能断言舍入为新增回归。

## ATFR-P1-T1.2-R2-001：结构化 Skill 的 1.0 版本声明与 Cloud 校验不一致（A2 FAIL，待处理）

前提：`create_skill` 的步骤 `code_version` 声明为 integer。输入合法定义，code 步骤版本为 `1.0`；其他字段合法；resolver 固定返回存在。预期数学整数 1.0 按已声明契约可供后续 Skill 校验处理。实际 Agent 校验接受，假网关捕获到的 payload 仍为 `1.0`，调用真实 Cloud 校验器首次返回 `A fixed positive code version is required`。影响：按声明可接受的 Skill 定义在后续校验被拒绝。位置：`cloud_agent/src/main/java/com/et/agent/AgentToolRegistry.java:165`、`cloud_agent/src/main/java/com/et/agent/SkillDraftAuthoring.java:56`、`cloud/src/main/java/com/et/cloud/controller/AgentSkillDefinitionValidator.java:79`。

## ATFR-P1-T1.2-R2-002：高精度参数边界转发时舍入（清单外交付阻断，待处理）

输入合法定义，number 参数 `min=1.00000000000000001`、`max=1`。预期转发保留输入的数值，避免改变边界。实际假网关捕获 payload 中 `min=1.0`、`max=1`，真实 Cloud 校验器接受被改变的值。影响：调用方提供的边界及其大小关系发生变化。位置：`cloud_agent/src/main/java/com/et/agent/SkillDraftAuthoring.java:16`、`:73-88`。未运行基线，不能断言为新增回归。

## 证据及复现

[探针源码](evidence-p1-t1-2-round2-probe.java)、[原始脱敏输出](evidence-p1-t1-2-round2-probe-output.txt)、[输入与观察对照](issue-p1-t1-2-round2.html)。在仓库根目录，从 `cloud_agent/target/surefire-reports/TEST-com.et.agent.AgentToolSchemaValidationTest.xml` 的 `java.class.path` 取类路径，`javac -encoding UTF-8 -cp <classpath> -d <临时目录> cloud/src/main/java/com/et/cloud/controller/AgentSkillDefinitionValidator.java docs/issue-reports/agent-tool-file-reference/evidence-p1-t1-2-round2-probe.java`，再运行 `java -cp <临时目录>;<classpath> ProbeTask12Round2`。假网关只打印数据并直接调用真实 Cloud 校验器，resolver 固定返回 true；无网络、数据库或真实写入。假网关为继续观察返回假 ID，输出中的 ACTIVE 是假返回值，不能证明真实 HTTP 或持久化成功。

固定 Maven 命令退出 0，41 测试无失败、错误或跳过；完整验收逐项结果另见测试报告。未启停服务、未变异代码；探针 class 文件已删除。

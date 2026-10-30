# 1.2 第3轮独立验收

实例 `/root/tester_1_2_round3`；2026-10-30；分支 `feature/agent-tool-file-reference开发`，HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`。总体 PASS：A1、A2、A3、B1、B2 均 PASS。未运行基线，未作变异检验。

## 问题复核

- R1-001：已验证修复。根数组、null 等错误经 AgentLoop 返回 40000，假网关无交互。
- R1-002：已验证修复。合法 JSON 后接文字、额外 JSON、控制字符均返回 40000 且假网关无交互；尾随空白的合法输入成功。
- R1-S1：已验证。合法文件工具及未知工具、无效 JSON、执行异常的安全断言通过；原条目记录的是证据不足，并非缺陷。
- R1-S2：已验证。必填及属性类型、枚举和额外字段的声明驱动断言通过，代码审阅确认声明与校验共用注册契约；原条目记录的是证据不足，并非缺陷。
- R2-001：已验证修复。真实 Agent 注册表将 1.0 和 1e0 传给真实 Cloud Validator 及 AgentLibraryStore 版本解析器时为整数 1；1.1、2147483648、1.00000000000000001 在假网关前拒绝。
- R2-002：已验证修复。min=1.00000000000000001、max=1 被真实 Cloud Validator 拒绝；合法高精度上下界、嵌套自由对象、1e-20 和大整数与输入数值相等。

## 命令和证据

在 `cloud_agent` 执行 `mvn -q "-Dtest=AgentToolSchemaValidationTest,AgentToolRegistryTest,SkillDraftAuthoringTest,AgentLoopTest,AgentWikiCreationToolsTest,AgentWikiPartialFailureTest,AgentControllerWikiEventTest" test`；在 `cloud` 执行 `mvn -q "-Dtest=AgentSkillDefinitionValidatorTest" test`。两者退出码均为 0；对应 Surefire 报告位于各模块 `target/surefire-reports`，其中 schema 测试10项、Cloud Validator 测试8项均无失败、错误或跳过。

[跨模块探针源码](evidence-p1-t1-2-round3-probe.java) SHA256 `099509093EB887998B8DAB2D77F2654A9A3952FC335952E4C2FD7BD9FD1303C8`；[输出](evidence-p1-t1-2-round3-probe-output.txt) SHA256 `B295F94FF04BF8812CD03B971286345E1EF04ECA328B68BAEDBE6B32F3F9610C`。以两个模块的 classes、Agent 类路径和 Cloud Surefire XML 的 java.class.path 组成类路径，在临时目录用 `javac -encoding UTF-8 -cp <classpath> -d <临时目录> <源码>` 编译，并用 `java -cp <临时目录>;<classpath> AgentSkillStoreProbe` 运行，退出0。网关为内存代理，真实版本解析器使用模拟 JdbcTemplate；未测试真实 HTTP 或数据库，也不证明持久化结果。

测试前后逐项 Get-FileHash -Algorithm SHA256 对照 `%TEMP%/agent-tool-file-reference-1.2-round3-sha256.txt`，24/24 匹配，分支与 HEAD 一致。diff SHA256 `D7DA4BC9097D48F11F957BE7EEFE9DE87941D348B800D5177EE21B2CDAF81A3A`；开发记录 SHA256 `FB3652BA35BDCC4B2ACF063D78D456743DD4D3C6BDE1306ACDB7F5B5B7052B2C`。未启停服务、写真实数据库或修改被测文件。负责人暂缓 AUDIT-003，不计本轮阻断。
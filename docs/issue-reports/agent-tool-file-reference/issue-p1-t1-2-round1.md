# 1.2 第1轮独立验收记录

实例：/root/tester_1_2_round1；2026-10-30；分支 feature/agent-tool-file-reference开发；HEAD 7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6。总体 FAIL。测试前后冻结清单20项 SHA256 一致；diff 和开发记录哈希与任务卡一致。未运行基线，未启停服务。

## ATFR-P1-T1.2-R1-001：根类型错误未返回 40000（A1，待处理）

前提：注册表声明 write_file 根参数为 object。探针向 AgentLoop 传入 []，网关为内存代理。预期：40000 参数错误，网关零调用。实际：抛出 IllegalArgumentException: Invalid tool arguments，网关零调用，未生成 40000 工具结果；AgentController 通用异常路径发送 Agent request failed。见 AgentLoop.java:165-175、193-199、AgentController.java:89-90。影响：调用方无法获得固定验收要求的参数错误。

## ATFR-P1-T1.2-R1-002：无效 JSON 尾随内容仍调用写入网关（重大清单外，待处理）

输入：`{"path":"x","content":"x","mode":"create"} trailing`。预期：无效 JSON 不应触发写入调用。实际：loop 完成并收到假结果 `{ "saved": true }`，假网关调用一次。AgentLoop.java:171 及 AgentToolRegistry.java:235 使用 readTree。假网关完全在内存中运行，没有真实写入或网络访问。

## ATFR-P1-T1.2-R1-S1：A2 证据不足

自定义对象、整数与布尔值区分以及 Skill、代码、Wiki、别名、目标绑定有部分断言；完整合法文件工具及未知工具、无效 JSON、执行异常不泄露内部信息的端到端断言不足。记录为证据不足，并非测试失败。

## ATFR-P1-T1.2-R1-S2：A3 证据不足

注册表参数用于声明与通用校验，空对象必填检查遍历声明；类型、枚举、额外字段及新增工具自动校验缺少完整声明驱动参数化断言，对外 REST、数据库和 schema 未全面审计。记录为证据不足，并非测试失败。

## 运行及证据

在 cloud_agent 执行 `mvn -q "-Dtest=AgentToolSchemaValidationTest,AgentToolRegistryTest,SkillDraftAuthoringTest,AgentLoopTest,AgentWikiCreationToolsTest,AgentWikiPartialFailureTest,AgentControllerWikiEventTest" test`，退出0；Surefire 共36项，失败0、错误0、跳过0。报告在 cloud_agent/target/surefire-reports。

受控探针：[源码](evidence-p1-t1-2-round1-probe.java)、[输出](evidence-p1-t1-2-round1-probe-output.txt)、[输入与观察对照](issue-p1-t1-2-round1.html)。从 cloud_agent 的 Surefire XML 的 java.class.path 取类路径，用 `javac -encoding UTF-8 -cp <classpath> -d <临时目录> <探针源码>` 编译，再用 `java -cp <临时目录>;<classpath> ProbeTask12` 运行。class 已删除。仅模拟网关计数，不访问服务或真实数据。

冻结快照：%TEMP%/agent-tool-file-reference-1.2-round1-sha256.txt；diff SHA256 78EEB241ED7C2DAA75599FD82C1C7E521C87F96BBE7C7D7C74516BB2532DAF0E；开发记录 SHA256 39D4A9096229876CA75D0AE86D06CA6C607615EC0ACFA9452FA7AD3D9C5C4C9C。变异检验未做；未授权修改被测代码。共享问题记录依交接顺序追加。

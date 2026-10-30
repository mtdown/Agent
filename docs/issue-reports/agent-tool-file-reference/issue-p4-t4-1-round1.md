# 4.1 第 1 轮独立测试报告

Change `agent-tool-file-reference`，阶段 4／任务 4.1／第 1 轮；实例 `/root/tester_4_1_round1`；2026-10-30。总体 **FAIL**：指定测试通过，但补充回归两项失败，是否本轮引入尚未判定，交总管仲裁。

- A1 **PASS**：`cloud_agent` 下执行 `mvn -q "-Dtest=AgentToolRegistryTest,AgentToolSchemaValidationTest,AgentLoopTest,AgentWikiCreationToolsTest,AgentWikiPartialFailureTest,AgentControllerWikiEventTest" test`，退出 0，44 项无失败、错误或跳过。注册分类逐项断言，未知写入结果后阻止其他写入并允许读取，未知工具及注入拒绝。
- A2 **证据不足**：上述命令中的别名授权、选择、部分成功和内部 ID 隐藏断言通过；Wiki 处理位于 `AgentWikiBoundary`。补充完整 Wiki 流程测试失败，不能确认完整流程通过。
- A3 **证据不足**：上述命令的未注册工具及参数拒绝断言通过，文件引用证据隔离断言通过；调用追踪补充测试通过，控制器补充测试失败。差异审查未见本轮数据库迁移、依赖或公开 REST 改动；未执行完整个人文件端到端集成测试。

## 附加回归失败，待处理

`cloud_agent` 下执行 `mvn -q "-Dtest=AgentToolTraceTest,AgentControllerTest,AgentWikiChoiceLoopTest,AgentWikiDestinationToolTest,AgentWikiWorkflowLoopTest" test`，退出 1；12 项中 2 项失败。未运行开发前基线，不能确定是否本轮引入，也不能仅凭测试失败判断产品行为缺陷。测试输入与断言对照见 [HTML](issue-p4-t4-1-round1.html)。

- **ATFR-P4-T4.1-R1-001**：`AgentControllerTest.usesAuthenticatedUserContextToCreateCodeAndSkillDraft`，Surefire 报 `AgentControllerTest.java:106`，`expected: <true> but was: <false>`。测试向 `create_skill` 提供仅有名称的 definition，预期继续完成保存并断言结果，实际断言失败。证据：`cloud_agent/target/surefire-reports/com.et.agent.AgentControllerTest.txt`。测试源码第 75–107 行。
- **ATFR-P4-T4.1-R1-002**：`AgentWikiWorkflowLoopTest.defaultBudgetAllowsSkillDirectoryFolderDocumentAndFinalAnswer`，Surefire 报 `AgentWikiWorkflowLoopTest.java:52`，`expected: <true> but was: <false>`。测试向 Wiki 写入传入原始 ID，预期完成文档结果断言，实际断言失败。证据：`cloud_agent/target/surefire-reports/com.et.agent.AgentWikiWorkflowLoopTest.txt`。测试源码第 23–57 行。

## 快照与限制

分支 `feature/agent-tool-file-reference开发`，HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`。`%TEMP%/agent-tool-file-reference-4.1-round1-sha256.txt` SHA256 `287bbe0c23493487db634c53a1fcaca31682c5f7c4713a956015f957dbbcc29e`；测试前后逐项 Get-FileHash，45/45 均匹配，状态范围一致。完整 diff SHA256 `9d02536f5e7e74affd4aea0637b109a75f1958e8109e4cef5bf42c21867231a9`；开发记录 `development-p4-t4-1-round1.md` SHA256 `ec5099a3d83ebe597b56dde44b2c06bc65f17456709a5fa491bfb24626802cec`，其自检与本轮固定命令结果一致；先失败后修复过程未独立复现。对照 3.2 差异检查调整测试，保留原断言并新增注入拒绝和未知结果断言。

未运行基线或变异检验；未启停服务、写真实数据或修改被测代码。Maven `target` 测试产物保留。共享问题表按交接追加；两项状态待处理。

# 4.1 第 2 轮开发过程记录

- 分支 feature/agent-tool-file-reference开发，HEAD 7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6。第 1 轮冻结清单 45 个文件经 Get-FileHash SHA256 核对均一致；两份本轮修改的测试不在该清单内。
- 补充命令复现 12 项、2 失败。临时在 Skill 原成功断言前检查最后工具消息 code 40000；在 Wiki 原成功断言前检查最后工具消息 Invalid or expired Wiki location reference。两条诊断断言均通过后原断言仍失败，证实失败发生于参数校验及别名验证，而非推测。随后移除临时诊断断言。
- Skill 输入原 definition 只有 name，与 SkillDraftAuthoring.toolParameters 的必填字段不符。补全 schema 所需字段，保留用户 7、代码及 Skill ID、最终流结果断言；针对性测试退出 0。
- Wiki 写入原先直接使用真实 ID，违反当前轮次别名协议。改为分别从规划及创建文件夹结果解析别名，断言不是原始 ID，并在工具处理端断言还原后的空间及文件夹 ID；保留调用顺序、文档结果、输出与预算断言。针对性测试退出 0。
- 未执行开发前基线，不能判定失败是否 4.1 引入；本轮没有更改生产代码、安全检查、公开契约、tasks 或 IssueLog。
- 首次尝试并行运行两组 Maven，因共享 target 无法充分排除互相干扰，随后按顺序重新运行：原固定命令 `mvn -q "-Dtest=AgentToolRegistryTest,AgentToolSchemaValidationTest,AgentLoopTest,AgentWikiCreationToolsTest,AgentWikiPartialFailureTest,AgentControllerWikiEventTest" test` 退出 0；补充命令 `mvn -q "-Dtest=AgentToolTraceTest,AgentControllerTest,AgentWikiChoiceLoopTest,AgentWikiDestinationToolTest,AgentWikiWorkflowLoopTest" test` 退出 0。开发自检不代替独立测试。
- 回滚本轮仅恢复 AgentControllerTest.java 与 AgentWikiWorkflowLoopTest.java 的本轮前内容；两文件修改前均与 HEAD 一致。不得覆盖其他既有工作区改动。

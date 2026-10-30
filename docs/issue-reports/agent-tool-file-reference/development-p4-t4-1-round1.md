# 4.1 第 1 轮开发过程记录

- 分支 feature/agent-tool-file-reference开发，起始 HEAD 7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6；工作区已有未提交改动，按任务卡保留。
- 确认未注册 lookup 测试注入与 A3 冲突后向总管报告；总管确认将通用测试改用已注册工具，保留断言，新增未注册注入拒绝断言。
- 新增未注册注入测试先失败（期望 IllegalArgumentException，实际执行至 turn limit），实现拒绝后通过。
- 新增结构化 unknown 写入结果测试先失败（后续写入未阻止），实现分类驱动阻止后通过；读操作仍执行。
- 首次测试编写脚本因工作目录错误未创建文件，后续以 cloud_agent 相对路径重试。中间完整回归发现另一个未注册 lookup 测试，按同一裁决改为已注册名称，保留异常脱敏断言。
- 最终 cloud_agent 下执行 `mvn -q "-Dtest=AgentToolRegistryTest,AgentToolSchemaValidationTest,AgentLoopTest,AgentWikiCreationToolsTest,AgentWikiPartialFailureTest,AgentControllerWikiEventTest" test`，退出 0，44 tests、0 failures、0 errors、0 skipped。此结果为开发自检，非独立验收。
- 本轮未保留修改前文件快照；相对于 HEAD 的 Git diff 混合既有上游改动。总管需用已有交接快照核对本轮差异。
- 未改数据库、依赖、公开 REST 或 tasks 勾选；未提交或上传。回滚需基于本轮前快照恢复六个本轮文件，避免覆盖上游未提交改动。

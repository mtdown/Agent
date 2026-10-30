# 1.3 第1轮独立验收

实例 `/root/tester_1_3_round1`，2026-10-30；分支 `feature/agent-tool-file-reference开发`，HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`。总体 PASS：A1、A2、A3 均 PASS，范围限于受控上游、本机数据库和构建验证。未运行基线，未做变异检验。

## 验收与证据

- A1 PASS：`cloud_agent` 下运行 `mvn -q "-Dtest=AgentToolTraceTest,AgentLoopTest,AgentControllerWikiEventTest,AgentWikiPartialFailureTest" test`，退出 0，25 项、无跳过；断言工具状态、顺序及非负耗时。
- A2 PASS：`cloud` 下运行 `mvn -q "-Dtest=AgentToolTraceLocalIT,AgentWikiMultiResultLocalIT,AgentWikiUnknownProjectionTest,AgentPreviewProxyWikiEventTest,AgentWikiChoiceLocalIT" test`，退出 0，32 项、无跳过；断言安全实时输出、SQL 历史、跨用户拒绝及失权后名称隐藏。`cloud_front` 下 `npm run build` 退出 0。额外临时反射探针确认安全字段、顺序、unknown、过滤非法耗时及状态；探针通过后删除。
- A3 PASS：上述测试断言中断无 done 或已完成轨迹，未知保持 unknown，完整回放及既有 Wiki 测试通过。AUDIT-003 的中断恢复不在本任务范围，负责人已决定暂缓，未修复、不作为本轮阻断。

真实 local MySQL 测试使用事务和受控上游；Cloud 复跑日志出现 23 条事务回滚记录。真实模型、真实浏览器未测；浏览器展示结论限于构建、模板和受控代理断言。未启停用户服务，未写真实用户数据。原始日志 `%TEMP%/agent-tool-file-reference-1.3-round1-independent-cloud.log`，SHA256 `038F6F82663906FED95C4A9FA4B2FFE688378A97E1F1E44C8E84993AF2590630`；含测试夹具与 SQL 输出，按内部证据处理。各模块 Surefire XML 位于 `target/surefire-reports`。

测试前后逐项 `Get-FileHash -Algorithm SHA256` 核对 `%TEMP%/agent-tool-file-reference-1.3-round1-sha256.txt`，26/26 匹配，分支和 HEAD 一致。完整 diff SHA256 `55C9BB822CBE6FDDB196228ADD6C8605449870B6FB30218C7B7B8A5054D1D9A0`；开发事实记录 SHA256 `A4C8A01096DCE516427D648594A3A702A2304FDB20D7AB08D7B083FBBC6A0D7C`。仅保留允许的构建产物与原始日志。

## 只读审查提出的待评估风险

ATFR-AUDIT-004：`AgentLoop.java` 中，启用 Wiki 文本保护时，Wiki 写事件存在后，最终模型正文的缓冲片段不再发送；用户可见 Wiki 结果由固定投影摘要提供。此项为只读静态审查，未运行针对性探针，未证明此轮新增，也未动态复现影响；状态为待评估，不作为本轮固定验收 FAIL。参见[代码与观察对照](issue-p1-t1-3-round1-audit.html)。

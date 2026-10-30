# 第4轮补证观察：文件夹结果及多次失败明细

- 测试实例 `/root/tester_1_1_round4_supplement`；2026-10-29；分支 `feature/agent-tool-file-reference开发`，HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`。两项待处理，列为清单外事实供总管仲裁，不推断需求。
- 被测 15 文件 SHA256 测试前后逐项一致；diff SHA256 `3DB25EA4E6C1A8FE7F65687166CC658C1A4A97C6D34A9A66F3EC58B4134C1E36`。开发记录 SHA256 `B70EAD1741946C3F3F4EFB4AA13E413FA998BBB2EE02DC234DD4C10A67F265D7`。

## ATFR-P1-T1.1-R4-001：仅创建文件夹时提示文档未保存

- 输入：在真实 MySQL 测试事务中新建用户、空间和文件夹；向实际 `AgentWikiChoiceProjection` 仅送入一次成功的 `create_wiki_folder` 事件，不发送文档操作。
- 实际：`displayInteraction` 的 kind 为 `wiki_partial_result`，`displayAnswer` 说“已在个人空间创建文件夹「FolderAlpha 2026-10-29」，文档尚未保存。” 页面模板对该 kind 显示“文档未保存”。该提示将无文档操作的场景描述为文档尚未保存；影响用户理解实际操作。
- 比较依据：输入未包括文档保存请求；此处不判断产品需求是否要求该文案。位置：`cloud/src/main/java/com/et/cloud/controller/AgentWikiChoiceProjection.java` 的 `state`，`AgentWikiChoiceService.java:402`，`cloud_front/src/pages/aiAssistant/AiAssistantPage.vue:150`。

## ATFR-P1-T1.1-R4-002：多次失败的公开结果不包含逐操作明细

- 输入：向实际投影依次送入 `create_wiki_document` 的 `{saved:false}`、`write_wiki_document` 的 `{status:blocked}`。
- 实际：内部 `operations` 包含 `create_wiki_document/failed` 和 `write_wiki_document/blocked`；公开结果为 `kind=wiki_write_results, results=[], unknown=0, failed=2`，答复重复“另有写入未成功。”两次，未给出各工具名或状态。影响：用户无法仅凭公开结果区分这两次操作。
- 比较依据：内部事件包含两条不同操作及状态；此处不推断对外显示明细是否为产品需求。位置：`AgentWikiChoiceProjection.java` 的 `project` 和 `state`，`AgentWikiChoiceService.java:353-354,405-407`。

## 验证证据与限制

- [探针源码](evidence-p1-t1-1-round4-supplement-probe.java)；编译后从 `cloud` 执行 `mvn -q "-Dtest=Round4SupplementProbeTest" surefire:test`，1 项通过。探针调用真实服务和 SQL；两项观察是直接投影调用，未经过真实 SSE、浏览器或页面实际操作。
- 同一探针对跨目录和同目录各两文档，在实时投影及数据库重载逐一断言标题、创建返回的实际目录名、非空 token 和 token 对应正文；还断言动态文档 ID 不出现在公开结构中。事务结束后 `@AfterTransaction` SQL 查询确认测试账户数为 0。测试数据库主键序列可能递增。
- 首跑断言输入文件夹名称，实际创建服务追加日期，导致探针断言失败；回滚后改为断言创建结果返回的真实名称，重跑通过。首跑及通过日志分别保存在系统临时目录 `agent-round4-supplement-probe-first.log`、`agent-round4-supplement-probe.log`；日志可能包含环境信息，未复制进报告。
- 指定的 `mvn -q "-Dtest=AgentWikiMultiResultLocalIT,AgentWikiUnknownProjectionTest,AgentPreviewProxyWikiEventTest" test` 退出 0（15 项），`cloud_front` 的 `npm run build` 退出 0。未运行基线或变异测试，未操作浏览器。
- `AgentWikiChoiceLocalIT.java:141` 使用真实动态 ID 后仍断言旧虚构 `456` 不出现；这一行本身不验证当前 ID。同文件第 232 行及多结果测试另有动态 ID 断言。

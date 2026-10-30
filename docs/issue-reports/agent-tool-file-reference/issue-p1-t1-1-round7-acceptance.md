# 第 7 轮独立验收及问题复核

- Change／阶段／任务／轮次：agent-tool-file-reference / 1 / 1.1 / 7；测试实例 tester_1_1_round7；2026-10-29。
- 分支 feature/agent-tool-file-reference开发；HEAD 7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6。
- 测试前后 18 项文件逐项 SHA256 均匹配 `C:/Users/origin/AppData/Local/Temp/agent-tool-file-reference-1.1-round7-sha256.txt`，数量 18，差异 0。测试期间受测文件未变。
- 完整 diff SHA256：FC856D5775C45297154F42C7BA38A22297E919D05C3D8F9DAECD26239DE65A1E；开发记录 SHA256：0253EC705A940270499ED406880B030682445DB6F7E684E5E47BAC24D7FE5090，均与任务卡一致。记录已读取；以下为独立断言。
- 范围内固定项 D5、A1、A2、A3、B1/B2、C1、C2、C3、D1、D2、D3 的指定命令退出 0。以下对旧问题独立判断，保留历史事实。

| 问题 | 第 7 轮状态 | 实际证据及限制 |
|---|---|---|
| ATFR-P1-T1.1-R1-001 | 已验证修复 | AgentLibraryHttpGatewayTest 的超时及重复写入测试断言写入 unknown、无 saved，服务只收到一次写请求，后续写入被阻止。 |
| ATFR-P1-T1.1-R2-001 | 已验证修复 | 受控 HTTP 服务已收到 POST 后断连、损坏响应、非 200 和缺数据均断言 unknown 且阻止再写；明确业务拒绝不标 unknown。见 AgentLibraryHttpGatewayTest.uncertainResponsesAfterWriteBlockModelRetry、explicitBusinessRejectionIsNotUnknown。 |
| ATFR-P1-T1.1-R2-002 | 已验证修复 | AgentWikiUnknownProjectionTest.confirmedThenUnknownStreamShowsSeparatedOutcomesAndSafeCard 和 AgentWikiMultiResultLocalIT.controllerSsePersistsMultipleCardsAndUnknownWithoutOverwritingAnswer 分别断言实时及数据库历史保留成功卡、另一次结果未知并隐藏 ID。 |
| ATFR-P1-T1.1-R3-001 | 已验证修复 | AgentWikiMultiResultLocalIT.controllerSsePersistsMultipleCardsAndUnknownWithoutOverwritingAnswer 使用真实 SQL 数据，两个目录、三份文档逐项断言实时和历史标题、文件夹、token 与预览正文。 |
| ATFR-P1-T1.1-R4-001 | 已验证修复 | AgentWikiMultiResultLocalIT.folderOnlyAndFailuresStreamAndReloadInCallOrder 覆盖仅创建文件夹的实时和历史，不声称文档未保存。未操作浏览器。 |
| ATFR-P1-T1.1-R4-002 | 已验证修复 | 同一测试断言 failed、blocked、unknown 按序显示，历史保留状态，不泄露敏感文本、不建议重试。 |
| ATFR-P1-T1.1-R5-C1 | 已验证修复 | 上述三文档实时 SSE 卡逐项断言标题、目录、token、预览正文，并与数据库回读逐项匹配。 |
| ATFR-P1-T1.1-R6-001 | 已验证修复 | AgentLoopTest.fixedWikiSkillLocationsBecomeTurnAliasesWithoutChangingOtherSteps 断言对象及字符串 definition 的固定位置产生别名、工具收到原 ID；AgentWikiMultiResultLocalIT.sharedFixedLocationSkillKeepsVersionAndChecksCurrentUserWikiAccess 独立断言真实 SQL 用户授权、撤权及原定义、版本。未运行真实模型或完整跨进程链。 |
| ATFR-AUDIT-001 | 已验证修复 | AgentWikiMultiResultLocalIT.wikiReadOnlyFinalAnswerIsStreamedAndSavedForSuccessAndError 断言只读成功和错误的最终答案均流出并保存。 |
| ATFR-AUDIT-002 | 待处理 | 成功和未知结果的历史未被选择提示覆盖；部分成功的历史交互已断言，但控制器路径中部分成功历史答案未作逐字断言，保留原状态。 |
| ATFR-AUDIT-003 | 待处理 | 未对已成功写入后后续步骤抛异常执行针对性验证，不关闭。 |
| ATFR-P1-T1.1-R6-S1 | 证据不足 | 结构化固定 Wiki ID 的本轮别名、伪造／跨轮拒绝、已选空间冲突、当前用户授权和撤权已有断言；未验证任意用户自然语言或数据中所有可能的 ID，也不作此保证。保留原状态。 |

## 执行命令

`cloud_agent`：

```text
mvn -q "-Dtest=AgentLoopTest,AgentControllerTest,AgentControllerWikiEventTest,AgentWikiPartialFailureTest,AgentLibraryHttpGatewayTest" test
mvn -q "-Dtest=AgentLibraryHttpGatewayTest,AgentLoopTest,AgentToolRegistryTest,AgentWikiCreationToolsTest,AgentWikiPartialFailureTest" test
```

`cloud`：

```text
mvn -q "-Dtest=AgentWikiMultiResultLocalIT,AgentWikiUnknownProjectionTest,AgentPreviewProxyWikiEventTest,AgentWikiChoiceLocalIT" test
mvn -q "-Dtest=AgentWikiMultiResultLocalIT,AgentWikiUnknownProjectionTest,AgentPreviewProxyWikiEventTest" test
mvn -q "-Dtest=AgentWikiMultiResultLocalIT" test
```

`cloud_front`：`npm run build`。均退出 0；目标类无跳过。真实 SQL 测试事务回滚，临时 HTTP 服务停止；未启停用户服务。未测试真实模型、浏览器或完整 Agent 到 Cloud 跨进程链。未做变异检验。系统临时目录保留 cloud C1/C2 测试日志 `atfr-r7-independent-cloud-c1.log`、`atfr-r7-independent-cloud-c2.log`。
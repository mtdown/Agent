# 3.1 第 1 轮开发事实

基线 7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6；原有未提交改动保留。
本轮在上列业务文件增加文件证据签名、内部事件、消息 statsJson 私有引用、历史脱敏和授权解析；未改前端、迁移、依赖、任务勾选或 IssueLog。
Cloud 与 Agent 指定 Maven 命令均退出 0；Cloud 附加 AgentResourceInternalControllerTest 退出 0。日志见下方哈希清单。数据库测试使用随机测试用户、@Transactional 回滚；文件使用临时目录。
首批红测：Cloud 分段引用和搜索引用、旧读引用分别因证据缺失失败；AgentLoop 因证据进入模型历史失败；随后定向测试通过。一次 Python 命令因 PowerShell 引号报语法错误，未修改目标；一次 Maven 因工作目录无 pom 失败，随后更正。一次并行运行同一模块的两个 Maven 命令，之后单独重新运行固定 Cloud 命令并退出 0。
真实跨进程 Agent→Cloud→数据库全链路未测试；数据库测试以真实 Cloud 文件工具结果和受控 Agent SSE 替身贯通 Cloud 存储，Agent 侧使用受控工具结果验证循环。独立验收仍须检查。
权限：会话所有权、assistant 消息及随机令牌校验；每次解析以当前用户目录重新校验文件版本。引用版本失效及文件删除拒绝。无新增持久缓存；读取同一批搜索结果时仅在本次事件内复用版本计算。
回滚：恢复本轮文件并移除新增类和测试；保留数据库与个人文件卷，历史 statsJson 的额外字段由旧版本忽略。大文件对旧版仍有读取限制。

## 本轮文件与日志 SHA-256

- `cloud/src/main/java/com/et/cloud/controller/AgentConversationStore.java`: `9e975e16796e28df248bd6153b23e6a325cdc4b8b37e560822fe2963326a0295`
- `cloud/src/main/java/com/et/cloud/controller/AgentPreviewController.java`: `ccc72a5ae123db24f8a52802acdc095f8fff8607354075578e02c99324dad36f`
- `cloud/src/main/java/com/et/cloud/controller/AgentPreviewProxy.java`: `a373f7641363ae92a2b7c74338299e64add658ee3bd06a6d1fe5a70fd33b105f`
- `cloud/src/main/java/com/et/cloud/controller/AgentResourceInternalController.java`: `ff83f7885a843d55df3c48e5fe360e1626fdc6234225704886a9be0747d6324b`
- `cloud/src/main/java/com/et/cloud/controller/AgentPersonalFileEvidence.java`: `10fe54c2f8c2b42e978947019d33e4154fbef50b290ed225c4c0db445f0198f4`
- `cloud/src/main/java/com/et/cloud/controller/AgentPersonalFileReferences.java`: `e454094fd1ca312a580c7e54e63c2243809206eb098e28effb0ec8878c2b7722`
- `cloud/src/test/java/com/et/cloud/controller/AgentPersonalFileReferenceTest.java`: `fa261ccb1bc2fca08ad211201dcb9cf808ab7fb49f5984c5d2edd9c6c2e5ee75`
- `cloud/src/test/java/com/et/cloud/controller/AgentPersonalFileReferenceLocalIT.java`: `618cced17e8b33b4148dc1899d1a7d9dc5fc712e7789273a6f5ddd52bb984935`
- `cloud_agent/src/main/java/com/et/agent/AgentController.java`: `f0a594e707b3193f0ddbfcb46ec631dacec22070b4957a5fe9011b8d7b99ee6f`
- `cloud_agent/src/main/java/com/et/agent/AgentLoop.java`: `dbabef32193081edf23e2b0eedb63ba2d5e8e102aafbef0702ac90e76567f5b2`
- `cloud_agent/src/test/java/com/et/agent/AgentLoopTest.java`: `b67dd91616d685faf8af5c2b7b56ea99534a2c3ced1c2b2d89d96a3e7941ba2d`
- `tmp/agent-file-ref-3-1-cloud.log`: `218a7e1ed4804f8d734f55db4157a57a9f19dac0ed0bba5d11f741450ead408e`
- `tmp/agent-file-ref-3-1-agent.log`: `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `tmp/agent-file-ref-3-1-cloud-compat.log`: `76df48068ad220160f3b52b90926fb94db7cb7d34b1a36cc0c98fc896c252166`

## 当前完整工作区状态

```
 M IssueLog.xlsx
 M cloud/src/main/java/com/et/cloud/controller/AgentConversationStore.java
 M cloud/src/main/java/com/et/cloud/controller/AgentPersonalFileStore.java
 M cloud/src/main/java/com/et/cloud/controller/AgentPreviewController.java
 M cloud/src/main/java/com/et/cloud/controller/AgentPreviewProxy.java
 M cloud/src/main/java/com/et/cloud/controller/AgentResourceInternalController.java
 M cloud/src/main/java/com/et/cloud/controller/AgentSkillDefinitionValidator.java
 M cloud/src/main/java/com/et/cloud/controller/AgentWikiChoiceProjection.java
 M cloud/src/main/java/com/et/cloud/controller/AgentWikiChoiceService.java
 M cloud/src/main/java/com/et/cloud/mapper/AgentMessageMapper.java
 M cloud/src/test/java/com/et/cloud/controller/AgentPersonalFileStoreTest.java
 M cloud/src/test/java/com/et/cloud/controller/AgentResourceInternalControllerTest.java
 M cloud/src/test/java/com/et/cloud/controller/AgentSkillDefinitionValidatorTest.java
 M cloud/src/test/java/com/et/cloud/controller/AgentWikiChoiceLocalIT.java
 M cloud_agent/src/main/java/com/et/agent/AgentController.java
 M cloud_agent/src/main/java/com/et/agent/AgentLibraryGateway.java
 M cloud_agent/src/main/java/com/et/agent/AgentLibraryHttpGateway.java
 M cloud_agent/src/main/java/com/et/agent/AgentLoop.java
 M cloud_agent/src/main/java/com/et/agent/AgentToolRegistry.java
 M cloud_agent/src/main/java/com/et/agent/SkillDraftAuthoring.java
 M cloud_agent/src/test/java/com/et/agent/AgentControllerWikiEventTest.java
 M cloud_agent/src/test/java/com/et/agent/AgentLibraryHttpGatewayTest.java
 M cloud_agent/src/test/java/com/et/agent/AgentLoopTest.java
 M cloud_agent/src/test/java/com/et/agent/AgentWikiPartialFailureTest.java
 M cloud_agent/src/test/java/com/et/agent/SkillDraftAuthoringTest.java
 M cloud_front/src/api/ragController.ts
 M cloud_front/src/pages/aiAssistant/AiAssistantPage.vue
 M deploy/README.md
?? cloud/src/main/java/com/et/cloud/controller/AgentPersonalFileEvidence.java
?? cloud/src/main/java/com/et/cloud/controller/AgentPersonalFileReferences.java
?? cloud/src/main/java/com/et/cloud/controller/AgentPersonalFileUpload.java
?? cloud/src/main/java/com/et/cloud/controller/AgentPersonalFileUploadController.java
?? cloud/src/main/java/com/et/cloud/controller/PersonalFileStorage.java
?? cloud/src/test/java/com/et/cloud/controller/AgentPersonalFileReferenceLocalIT.java
?? cloud/src/test/java/com/et/cloud/controller/AgentPersonalFileReferenceTest.java
?? cloud/src/test/java/com/et/cloud/controller/AgentPersonalFileSegmentTest.java
?? cloud/src/test/java/com/et/cloud/controller/AgentPersonalFileUploadTest.java
?? cloud/src/test/java/com/et/cloud/controller/AgentToolTraceLocalIT.java
?? cloud/src/test/java/com/et/cloud/controller/AgentWikiMultiResultLocalIT.java
?? cloud/src/test/java/com/et/cloud/controller/AgentWikiUnknownProjectionTest.java
?? cloud_agent/src/test/java/com/et/agent/AgentToolSchemaValidationTest.java
?? cloud_agent/src/test/java/com/et/agent/AgentToolTraceTest.java
?? docs/issue-reports/agent-tool-file-reference/
?? openspec/changes/agent-tool-file-reference/

```

## 已跟踪累计差异摘要

```
 IssueLog.xlsx                                      | Bin 84673 -> 90936 bytes
 .../cloud/controller/AgentConversationStore.java   |  48 +++-
 .../cloud/controller/AgentPersonalFileStore.java   | 309 +++++++++++++++++++--
 .../cloud/controller/AgentPreviewController.java   |  29 +-
 .../com/et/cloud/controller/AgentPreviewProxy.java |  34 ++-
 .../AgentResourceInternalController.java           |  63 ++++-
 .../controller/AgentSkillDefinitionValidator.java  |   3 +-
 .../controller/AgentWikiChoiceProjection.java      | 142 +++++-----
 .../cloud/controller/AgentWikiChoiceService.java   | 292 ++++++++++++++++++-
 .../com/et/cloud/mapper/AgentMessageMapper.java    |   4 +-
 .../controller/AgentPersonalFileStoreTest.java     | 216 +++++++++++---
 .../AgentResourceInternalControllerTest.java       |  39 +++
 .../AgentSkillDefinitionValidatorTest.java         |  16 ++
 .../cloud/controller/AgentWikiChoiceLocalIT.java   |  51 +++-
 .../main/java/com/et/agent/AgentController.java    |   8 +-
 .../java/com/et/agent/AgentLibraryGateway.java     |   2 +
 .../java/com/et/agent/AgentLibraryHttpGateway.java |  71 ++++-
 .../src/main/java/com/et/agent/AgentLoop.java      | 249 +++++++++++++++--
 .../main/java/com/et/agent/AgentToolRegistry.java  |  74 ++++-
 .../java/com/et/agent/SkillDraftAuthoring.java     |  15 +-
 .../com/et/agent/AgentControllerWikiEventTest.java |  27 +-
 .../com/et/agent/AgentLibraryHttpGatewayTest.java  | 209 ++++++++++++++
 .../src/test/java/com/et/agent/AgentLoopTest.java  | 213 +++++++++++++-
 .../com/et/agent/AgentWikiPartialFailureTest.java  |  12 +-
 .../java/com/et/agent/SkillDraftAuthoringTest.java |  12 +
 cloud_front/src/api/ragController.ts               |  21 +-
 .../src/pages/aiAssistant/AiAssistantPage.vue      |  22 +-
 deploy/README.md                                   |   8 +
 28 files changed, 1945 insertions(+), 244 deletions(-)

```

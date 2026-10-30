# 2.3 第1轮独立验收及符号链接补证

Change ID `agent-tool-file-reference`；阶段 2／任务 2.3／第 1 轮；独立实例 `/root/tester_2_3_round1_retry`；2026-10-30 10:33–10:35 +08:00。分支 `feature/agent-tool-file-reference开发`，HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`。原验收 A1、A2 PASS，A3 因 Windows 普通符号链接未执行为证据不足；后由独立实例 `/root/tester_2_3_symlink_supplement` 对相同快照补证 Linux 真实符号链接 PASS。Windows 原生普通符号链接仍未实测，Linux 补证不等同 Windows 原生验证。是否接受跨平台补证由总管判定。

## 指定命令与结果

```powershell
# cloud
mvn -q "-Dtest=AgentResourceInternalControllerTest,AgentPersonalFileStoreTest,AgentPersonalFileSegmentTest" test
# cloud_agent
mvn -q "-Dtest=AgentLibraryHttpGatewayTest,AgentToolRegistryTest,AgentToolSchemaValidationTest,AgentLoopTest" test
```

两命令退出 0；Cloud 共 20 项，失败 0、错误 0、跳过 1（`refusesExistingSymlinkAsParent`，环境不支持创建符号链接）；Agent 共 48 项，失败、错误、跳过均为 0。Surefire 报告在各模块 `target/surefire-reports`，后续运行可能覆盖。

- A1 PASS：控制器测试验证首次请求、篡改签名、跨用户、搜索词绑定、重建后的搜索续传和版本变化。补充使用真实控制器及全局异常处理的 MockMvc 请求，跨路径游标 HTTP 200／业务码 40000；错误密钥 HTTP 200／业务码 50000／data=null，拒绝访问。版本变化业务码 40000，消息 `Personal file version conflict`。网关测试验证用户与内部密钥、请求路径和参数。
- A2 PASS：小文件返回正文，大文件返回使用分段工具的提示且不返回全文；测试真实工具注册表和网关、统一 schema 校验及默认累计 16000 字符预算内的两次中英文分段调用。
- A3 原结论证据不足：测试验证用户隔离、写入、路径越界和 Windows junction，覆盖部分 Wiki、Skill、代码工具；普通 Windows 符号链接测试跳过。未发现新增 Maven 依赖或数据库。Linux 额外补证见下。

## 独立 Linux 符号链接补证

补证实例 `/root/tester_2_3_symlink_supplement` 在同一 33 文件快照上，使用真实源码由 javac 17 编译，在 Java 21 禁网络容器只读运行。其报告：用户根目录、子目录及最终文件指向外部的真实符号链接均拒绝 read、segment、search 和相关 write；外部哨兵不变、外部新文件未创建，普通路径读写、分段、搜索正常。补证探针源码存于 `%TEMP%/atfr-supplement-899fa951f7454a5d97462ce86c5a346e/SymlinkProbe.java`；本实例已只读核对探针断言，运行结论来源为补证实例，未自行重跑。该临时目录清理被自动审批拒绝，留有源码、stub 和 classes，未绕过。

## 长期风险 ATFR-P2-T2.3-R1-AUTH

错误内部密钥被拒绝，但经真实 MockMvc 控制器及 `GlobalExceptionHandler` 的请求实际返回 HTTP 200、业务码 50000，而非能区分认证失败的状态。`requireUser` 抛出 401 `ResponseStatusException`，被全局 `RuntimeException` 处理。固定 A1 只要求拒绝且未指定错误密钥状态码，故不判固定项 FAIL。HEAD 仅静态核对两处相同逻辑，未运行基线，不能认定新回归。状态待评估；见 [代码对照](issue-p2-t2-3-round1-auth.html)。

## 快照与事实记录

测试前后逐个对照 `%TEMP%/agent-tool-file-reference-2.3-round1-sha256.txt` 的 33 文件，均 0 不匹配；HEAD 不变。清单 SHA256 `07450ae4a75135fd5310e3124978ea89ec7101a8456c789a3dbccd0b3f4b8a6f`；累计完整 diff `98418b97c20b11ef619b17515b434d5c049c24342164894a239b707f965030b2`；开发事实 `e4057731bbd0522eafcd1a9f9a8a279be57c9ffbce3627a5931f73a3a174b262`；开发七文件清单 `2096736ec2bc75a129ee87b6501ae7f7e5b60725055711cec95de7d09f86fe59`；相对 HEAD 补丁 `35cc33529d5f4bcc9a9f320f49148c54ee0cbcc4c09f5522609630a7ad6c4b63`。五项测试前哈希匹配，七文件清单与 33 文件清单对应；开发记录所述七文件差异包含以前未提交内容，不视为全部由本轮产生。

<details><summary>33 文件快照清单</summary>

```text12be54444c446dd97751ebd6a2c56be875e8854bbc7f6c02f485a397d34fe082  cloud/src/main/java/com/et/cloud/controller/AgentConversationStore.java
b8f6292068b8fe82c99841c4cdca66464774b3b5cc1d23dbe5fe7c1309f27529  cloud/src/main/java/com/et/cloud/controller/AgentPersonalFileStore.java
62bdb14fdbf40ffc3b8ab2657f0c5121d53c4a3ad2021c54f2fa64dbed288c42  cloud/src/main/java/com/et/cloud/controller/AgentPreviewController.java
0d6aa8eaa2a99a4d7de7b76a7233450eec3687c32261be1ae490667db83b1966  cloud/src/main/java/com/et/cloud/controller/AgentPreviewProxy.java
86e21111b93400593a04407627d1d3726bb7d127989824073f7513417b192ffb  cloud/src/main/java/com/et/cloud/controller/AgentResourceInternalController.java
319cb1627fc22d76c2758180e2c3d24a222365ff349d314576ac98c87602abb7  cloud/src/main/java/com/et/cloud/controller/AgentSkillDefinitionValidator.java
ebfaff6d0eced89636ac35dc115b1ae41eeb743238e1c46f6427a26d82b0c496  cloud/src/main/java/com/et/cloud/controller/AgentWikiChoiceProjection.java
5fbb61033971de692a6ef5c9193a2634de4cfbef2c96ecfcdd354dbaa313280c  cloud/src/main/java/com/et/cloud/controller/AgentWikiChoiceService.java
d99b133f1d60872d46f34ad6adff0a649346ffc138f5c5689c6b4535499b118a  cloud/src/main/java/com/et/cloud/controller/PersonalFileStorage.java
19cfd80763a1e1ade948be2d957ab959adbab11622391ea102b50fcfe94f4e64  cloud/src/main/java/com/et/cloud/mapper/AgentMessageMapper.java
cfc008547d91e18dfe4a8fff9770f463c26582d2bd6fb20d27381a3bd8e58b25  cloud/src/test/java/com/et/cloud/controller/AgentPersonalFileSegmentTest.java
adc2d4df2171027a464536ff8710451adc04faa47d6964f5aab70586a7aa5ccc  cloud/src/test/java/com/et/cloud/controller/AgentPersonalFileStoreTest.java
5409edbe4544654a311547d8800c4e52fd1daa5312773db8f13f52c31d3db383  cloud/src/test/java/com/et/cloud/controller/AgentResourceInternalControllerTest.java
395826fb649cd8c079d44fe50423b5f7494b4ad67485c65fbcc15ae9f3b799d6  cloud/src/test/java/com/et/cloud/controller/AgentSkillDefinitionValidatorTest.java
4da4969b20d754b0c71a996f4922f68ca7229fa537ea5967f6e9f4460dba4c7e  cloud/src/test/java/com/et/cloud/controller/AgentToolTraceLocalIT.java
2e1f83ddefcc54a3b07a18f3cecee0fcbba1e492f11eca266cda0b89a5132e4f  cloud/src/test/java/com/et/cloud/controller/AgentWikiChoiceLocalIT.java
bf2cf0cea4c4ea555c5f43adfc3c629812f50363f7ea2392d76cb68d0a82b89b  cloud/src/test/java/com/et/cloud/controller/AgentWikiMultiResultLocalIT.java
ec20c8952e21d099120c7020077d3feefe2b1559c124f88656212a5171ffe712  cloud/src/test/java/com/et/cloud/controller/AgentWikiUnknownProjectionTest.java
8ecd844dd10c566ebca70b1dfc9c16c8db504e0b59c8f00aa9ce28a0f6334669  cloud_agent/src/main/java/com/et/agent/AgentController.java
ff70fc52b6a93a0b659a64f3685bb2dec19f6c5e0259751defe3edf8f2106b47  cloud_agent/src/main/java/com/et/agent/AgentLibraryGateway.java
30eedebf919cb22260734bdb4db47049f6f32250d6a3bb5b78fc6ccbbd592d76  cloud_agent/src/main/java/com/et/agent/AgentLibraryHttpGateway.java
2d12bd01d77b423c3d994527ba451124e0063de6dbcd849133f178d90bea1941  cloud_agent/src/main/java/com/et/agent/AgentLoop.java
af74c187199fa083f66af1b58126e2522d75d26055d6d3630ee5808664aedbd3  cloud_agent/src/main/java/com/et/agent/AgentToolRegistry.java
c0241d8711f8d2ac1ec5c384aaef9576747b3c1c3e5a00cb9bda2e02204847e6  cloud_agent/src/main/java/com/et/agent/SkillDraftAuthoring.java
3f1913b4d5509c27ae76a82db5c06269d4c65b738533e4237ba57959b2a42264  cloud_agent/src/test/java/com/et/agent/AgentControllerWikiEventTest.java
6e1abb49667e21d88a2f3d6d220472abd293730e0584ed1242d328d648d409bf  cloud_agent/src/test/java/com/et/agent/AgentLibraryHttpGatewayTest.java
f55dad2262b39aae22d825a132bf6e9f5bf3923a8a50861d066387eb28d7de2e  cloud_agent/src/test/java/com/et/agent/AgentLoopTest.java
b4a636ac00f0713b6b07c176ce9ca402174b901881fb1bc441e490cbc9badf6c  cloud_agent/src/test/java/com/et/agent/AgentToolSchemaValidationTest.java
3c5bf96c5865a24a577835340713f85846fc59a477c10d5ae3d7ebf50c3f1a88  cloud_agent/src/test/java/com/et/agent/AgentToolTraceTest.java
1c9c4c1d9d6f621e35e531c70653bb670dc291700dc1c30fc44932e513ea30ec  cloud_agent/src/test/java/com/et/agent/AgentWikiPartialFailureTest.java
47e29eba5c75c294630b8e8a2feabc71509363bd4358f98c91d279f5484dabb1  cloud_agent/src/test/java/com/et/agent/SkillDraftAuthoringTest.java
3ab3685395b47979cb2788510ba668fcca6d89755e92ae2c7fdaece93902656d  cloud_front/src/api/ragController.ts
a5443e01bbd29114dbccee4c14d828193c8bdd412434eb277cdc4c03af25685c  cloud_front/src/pages/aiAssistant/AiAssistantPage.vue
```
</details>

未运行基线或变异检验；未使用真实用户数据或启动持久服务。一次性 MockMvc 探针、编译类及依赖路径清单已删除；Maven 构建产物保留。清单外交付阻断问题未发现；普通 Windows 符号链接未验证。问题表与本报告按后续授权串行追加。
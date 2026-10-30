# 2.2 第1轮独立验收

Change ID：`agent-tool-file-reference`；阶段 2／任务 2.2／第 1 轮；测试实例 `/root/tester_2_2_round1`；2026-10-30 10:12–10:14 +08:00。分支 `feature/agent-tool-file-reference开发`，HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`。固定验收 A1、A2、A3 均 PASS；临时产物清理被自动审批拒绝。未运行基线或变异检验，未启停服务，未使用真实用户数据。

## 执行与结果

在 `cloud` 运行完整命令：

```powershell
mvn -q "-Dtest=AgentPersonalFileStoreTest,AgentPersonalFileSegmentTest,AgentResourceInternalControllerTest" test
```

退出码 0。Surefire：SegmentTest 5 项、StoreTest 12 项、ControllerTest 1 项，失败和错误均为 0；StoreTest 的 `refusesExistingSymlinkAsParent` 因 Windows 符号链接权限不足跳过。报告在 `cloud/target/surefire-reports/`，构建产物可能被后续运行覆盖。该跳过不视为已实测 Windows 符号链接。

- A1 PASS：SegmentTest 对混合中英文和表情分段后比对 UTF-8 字节；50 MiB 文件每段最大 1 MiB 并逐段与原块逐字节对照。独立低堆探针创建真实 50 MiB 文件，其中中文字符跨 1 MiB 边界；逐段流式计算 SHA-256，与从原文件流式计算的 SHA-256 一致。JVM 最大堆 25,165,824 字节，输出 `bytes=52428800 pages=51 SHA256=match`。
- A2 PASS：SegmentTest 验证跨用户、越限、修改后的旧游标和原有小文件读取；独立探针验证跨用户、跨路径、反射构造负位置均拒绝，修改后搜索续传拒绝。原有 StoreTest 和控制器测试覆盖原有读取和用户隔离。
- A3 PASS：SegmentTest 验证边界中文、多结果、空结果、重叠结果、超长行、多页续传和变更拒绝；独立探针 `scanBytes=1,maxResults=1` 返回中文偏移 `[1, 10]`，经 18 页结束且无重复或遗漏；输出 `isolation,path,invalid cursor,search positions=[1, 10], scanPages=18, version conflict: PASS`。

探针源码见 [evidence-p2-t2-2-round1-probe.java](evidence-p2-t2-2-round1-probe.java)。实际首次探针因测试文件自身生成无效 UTF-8 抛 `MalformedInputException`；更正测试数据后以同一源码路径重编译并获得上述通过输出，不将首次结果归因于实现。最终执行命令（`$dir` 为 `%TEMP%/agent-2-2-independent-probe`）：

```powershell
[xml]$xml=Get-Content -Raw 'target/surefire-reports/TEST-com.et.cloud.controller.AgentPersonalFileSegmentTest.xml'
$cp=($xml.testsuite.properties.property | Where-Object {$_.name -eq 'java.class.path'}).value
javac -encoding UTF-8 -cp $cp -d $dir (Join-Path $dir 'Probe.java')
java -Xmx24m -cp "$dir;$cp" com.et.cloud.controller.Probe (Join-Path $dir 'data')
```

探针的原始通过输出未单独保存文件，上述输出由执行结果记录；源码及临时编译产物仍可核验。测试未注入并发外部进程写入，契约为单进程协调。

## 长期跟踪风险 ATFR-P2-T2.2-R1-IO

`AgentPersonalFileStore.digest` 每次流式读取整个文件；`segment`、`search` 在分段或候选匹配扫描前均调用它。`scanBytes` 限制候选位置，匹配读取还允许最多 1023 字节前瞻；总 I/O 另外包含全文件摘要读取。以本次 50 MiB、51 次分段为例，摘要逻辑对应约 2.49 GiB 的文件读取，另有分段读取；这是按代码和调用次数计算的逻辑读取量，并非操作系统物理磁盘 I/O 实测。长文件频繁续传可能产生高 I/O 和延迟，未测吞吐或并发性能。负责人已暂缓完整资源优化；该风险不作为固定验收 FAIL。

## 快照和开发记录

测试前后分别按下列方式逐文件核对 30 文件，均 0 不匹配；`git status --short`、`git branch --show-current`、`git rev-parse HEAD` 的范围和结果前后一致。测试后写本报告和问题表属于后续授权记录，不属于被测源码快照。

```powershell
$lines=Get-Content -Encoding UTF8 "$env:TEMP/agent-tool-file-reference-2.2-round1-sha256.txt"
$bad=@(); foreach($line in $lines){if($line -match '^([0-9a-f]{64})  (.+)$'){if((Get-FileHash -Algorithm SHA256 -LiteralPath $Matches[2]).Hash -ne $Matches[1]){$bad += $Matches[2]}}}
$lines.Count; $bad; git status --short; git branch --show-current; git rev-parse HEAD
```

清单 SHA256 `d6a4043ba827e282bed2a622eb618226e48988a3f83c7e228c24f07d5a9f4e91`；开发事实 `e8b5b2a42a07ceec4c5d1c2c4814a4c2da929486f70d617719f48152c2f86b73`；开发文件清单 `4f2e7edbd62bb109b3db69fc2b48f654f6697246cba75b60d7c436ca03a2c2ae`；累计 diff `caa644dd4ee0cd706020199d7dc5ae60aef0e833f752d577ffc3c555537b6cab`；开发相对 HEAD diff `7d059d84d3cb1a8d6283ad1040eeacad61122ff4a5edaa35a61a4191dc582dbf`。以上五项测试前经 `Get-FileHash -Algorithm SHA256` 校验匹配。开发清单三个文件的哈希与 30 文件清单匹配；独立核查固定命令、摘要 I/O 和测试断言，未将开发自检当作验收。

<details><summary>30 文件逐项 SHA-256 清单</summary>

```text
12be54444c446dd97751ebd6a2c56be875e8854bbc7f6c02f485a397d34fe082  cloud/src/main/java/com/et/cloud/controller/AgentConversationStore.java
c8ad1518cf7daf654df9d13d9a0e3a12faa0898947b5828724efa30006a59a0d  cloud/src/main/java/com/et/cloud/controller/AgentPersonalFileStore.java
62bdb14fdbf40ffc3b8ab2657f0c5121d53c4a3ad2021c54f2fa64dbed288c42  cloud/src/main/java/com/et/cloud/controller/AgentPreviewController.java
0d6aa8eaa2a99a4d7de7b76a7233450eec3687c32261be1ae490667db83b1966  cloud/src/main/java/com/et/cloud/controller/AgentPreviewProxy.java
319cb1627fc22d76c2758180e2c3d24a222365ff349d314576ac98c87602abb7  cloud/src/main/java/com/et/cloud/controller/AgentSkillDefinitionValidator.java
ebfaff6d0eced89636ac35dc115b1ae41eeb743238e1c46f6427a26d82b0c496  cloud/src/main/java/com/et/cloud/controller/AgentWikiChoiceProjection.java
5fbb61033971de692a6ef5c9193a2634de4cfbef2c96ecfcdd354dbaa313280c  cloud/src/main/java/com/et/cloud/controller/AgentWikiChoiceService.java
d99b133f1d60872d46f34ad6adff0a649346ffc138f5c5689c6b4535499b118a  cloud/src/main/java/com/et/cloud/controller/PersonalFileStorage.java
19cfd80763a1e1ade948be2d957ab959adbab11622391ea102b50fcfe94f4e64  cloud/src/main/java/com/et/cloud/mapper/AgentMessageMapper.java
cfc008547d91e18dfe4a8fff9770f463c26582d2bd6fb20d27381a3bd8e58b25  cloud/src/test/java/com/et/cloud/controller/AgentPersonalFileSegmentTest.java
adc2d4df2171027a464536ff8710451adc04faa47d6964f5aab70586a7aa5ccc  cloud/src/test/java/com/et/cloud/controller/AgentPersonalFileStoreTest.java
395826fb649cd8c079d44fe50423b5f7494b4ad67485c65fbcc15ae9f3b799d6  cloud/src/test/java/com/et/cloud/controller/AgentSkillDefinitionValidatorTest.java
4da4969b20d754b0c71a996f4922f68ca7229fa537ea5967f6e9f4460dba4c7e  cloud/src/test/java/com/et/cloud/controller/AgentToolTraceLocalIT.java
2e1f83ddefcc54a3b07a18f3cecee0fcbba1e492f11eca266cda0b89a5132e4f  cloud/src/test/java/com/et/cloud/controller/AgentWikiChoiceLocalIT.java
bf2cf0cea4c4ea555c5f43adfc3c629812f50363f7ea2392d76cb68d0a82b89b  cloud/src/test/java/com/et/cloud/controller/AgentWikiMultiResultLocalIT.java
ec20c8952e21d099120c7020077d3feefe2b1559c124f88656212a5171ffe712  cloud/src/test/java/com/et/cloud/controller/AgentWikiUnknownProjectionTest.java
8ecd844dd10c566ebca70b1dfc9c16c8db504e0b59c8f00aa9ce28a0f6334669  cloud_agent/src/main/java/com/et/agent/AgentController.java
17869c941ca3e8452d625d5977087de564cfdb5eff1e27506d831de3a15e0a22  cloud_agent/src/main/java/com/et/agent/AgentLibraryHttpGateway.java
2d12bd01d77b423c3d994527ba451124e0063de6dbcd849133f178d90bea1941  cloud_agent/src/main/java/com/et/agent/AgentLoop.java
a21d9153b4e1e9eb060cfbcaff62a8f214e5130c4f829775d0bdff29c40664f8  cloud_agent/src/main/java/com/et/agent/AgentToolRegistry.java
c0241d8711f8d2ac1ec5c384aaef9576747b3c1c3e5a00cb9bda2e02204847e6  cloud_agent/src/main/java/com/et/agent/SkillDraftAuthoring.java
3f1913b4d5509c27ae76a82db5c06269d4c65b738533e4237ba57959b2a42264  cloud_agent/src/test/java/com/et/agent/AgentControllerWikiEventTest.java
d4cad430c233718388022bf417bbf6a6ee24867d969083f88f6a20048aa7d329  cloud_agent/src/test/java/com/et/agent/AgentLibraryHttpGatewayTest.java
f55dad2262b39aae22d825a132bf6e9f5bf3923a8a50861d066387eb28d7de2e  cloud_agent/src/test/java/com/et/agent/AgentLoopTest.java
b4a636ac00f0713b6b07c176ce9ca402174b901881fb1bc441e490cbc9badf6c  cloud_agent/src/test/java/com/et/agent/AgentToolSchemaValidationTest.java
3c5bf96c5865a24a577835340713f85846fc59a477c10d5ae3d7ebf50c3f1a88  cloud_agent/src/test/java/com/et/agent/AgentToolTraceTest.java
1c9c4c1d9d6f621e35e531c70653bb670dc291700dc1c30fc44932e513ea30ec  cloud_agent/src/test/java/com/et/agent/AgentWikiPartialFailureTest.java
47e29eba5c75c294630b8e8a2feabc71509363bd4358f98c91d279f5484dabb1  cloud_agent/src/test/java/com/et/agent/SkillDraftAuthoringTest.java
3ab3685395b47979cb2788510ba668fcca6d89755e92ae2c7fdaece93902656d  cloud_front/src/api/ragController.ts
a5443e01bbd29114dbccee4c14d828193c8bdd412434eb277cdc4c03af25685c  cloud_front/src/pages/aiAssistant/AiAssistantPage.vue
```
</details>

固定验收缺陷及清单外交付阻断：未发现。风格意见：无。未运行基线，不判断历史回归；未进行变异检验。未改业务源码或真实用户文件。测试时共享 IssueLog 未写入；本报告交接后按授权记录风险。

临时目录清理：尝试 `Remove-Item -LiteralPath $dir -Recurse -Force` 被自动审批以策略阻止拒绝；遵照任务卡停止，未绕过。`%TEMP%/agent-2-2-independent-probe` 留有探针、class 和约 50 MiB 测试文件。Maven 构建产物保留。

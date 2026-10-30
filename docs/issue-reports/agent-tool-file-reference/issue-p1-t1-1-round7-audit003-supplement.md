# ATFR-AUDIT-003 第7轮后补证

Change `agent-tool-file-reference` / 1.1；测试实例 `/root/tester_1_1_audit_supplement`；2026-10-29；分支 `feature/agent-tool-file-reference开发`，HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`。补证不构成固定验收或全套 PASS。状态：暂缓（2026-10-30 负责人决定）。

## 输入、预期与结果

受控模型第一轮列出可写位置，第二轮调用真实 `AgentToolRegistry` 中 `write_wiki_document`；假网关计数并返回合法成功 JSON（测试用位置、文件夹、文档标识分别为 11、21、31），不访问真实 Wiki。预期：之后发生异常时，已成功的操作事实安全地交给用户并可在历史中保留。实际如下；各行假写接口调用均为 1 次。

| 后续情形 | 模型调用次数 | AgentController 实际 SSE |
|---|---:|---|
| 模型异常 | 3 | `event: error`，`{"message":"Agent request failed"}` |
| 模型超时 | 3 | 同上 |
| 轮数上限 | 3 | 同上 |
| 工具结果总量上限 | 2 | 同上 |

均无 `wiki_tool_event`、`done` 或已成功操作说明。结果超限发生在假写返回后、模型下一次调用前。模型与网关均为假实现；不声称真实数据库写入或真实 Wiki 操作。

在独立代理探针中，以本地临时 HTTP 服务返回上述实际错误帧给真实 `AgentPreviewProxy`；实际 SSE 为同一通用错误，完成回调为 `success=false`、`answer=""`、`stats=null`、`interaction=null`。此代理探针未与 AgentController 进程直接串联；输入是前述实际输出。未联测真实 Cloud Controller、SQL 或真实历史；依据 Cloud 控制器源码，无成功回调时调用 `store.fail`，这是代码推导，非持久化实测。AUDIT-002 仍为证据不足。

## 方法与命令

系统临时目录中创建受控 `Probe.java`、`ProxyProbe.java`，从本地构建包提取依赖。Java 17；对当前源码即时编译 AgentLoop、AgentController、AgentToolRegistry、AgentLibraryHttpGateway 和 AgentPreviewProxy，其余依赖来自本地构建包；不启动或停止用户已有服务。代理探针启动一次仅本机临时 HTTP 服务并在 finally 中停止。

```powershell
jar xf cloud_agent/target/cloud-agent-0.0.1-SNAPSHOT.jar BOOT-INF/lib BOOT-INF/classes
javac -encoding UTF-8 -cp "$p/BOOT-INF/classes;$p/BOOT-INF/lib/*" -d "$p/BOOT-INF/classes" cloud_agent/src/main/java/com/et/agent/AgentLoop.java cloud_agent/src/main/java/com/et/agent/AgentController.java cloud_agent/src/main/java/com/et/agent/AgentToolRegistry.java cloud_agent/src/main/java/com/et/agent/AgentLibraryHttpGateway.java
javac -encoding UTF-8 -cp "$p/BOOT-INF/classes;$p/BOOT-INF/lib/*" "$p/Probe.java"
java -cp "$p;$p/BOOT-INF/classes;$p/BOOT-INF/lib/*" Probe
jar xf cloud/target/cloud-0.0.1-SNAPSHOT.jar BOOT-INF/lib BOOT-INF/classes
javac -encoding UTF-8 -cp "$q/BOOT-INF/classes;$q/BOOT-INF/lib/*" -d "$q/BOOT-INF/classes" cloud/src/main/java/com/et/cloud/controller/AgentPreviewProxy.java
javac -encoding UTF-8 -cp "$q/BOOT-INF/classes;$q/BOOT-INF/lib/*" "$q/ProxyProbe.java"
java -cp "$q;$q/BOOT-INF/classes;$q/BOOT-INF/lib/*" ProxyProbe
```

其中 `$p` 和 `$q` 分别为临时探针及其 cloud 子目录；jar 提取在各自目录中执行，命令中的 jar、javac、java 使用 Java 17 绝对路径。上述编译和运行退出码均为 0。代理结果：

```text
completed=false answer= stats=null interaction=null
SSE=event: error\ndata: {"message":"Agent request failed"}\n\n
```

## 代码路径与快照

`AgentLoop.java:137-163` 在模型和检查后可能抛异常；`:196-202` 在加入事件前检查结果上限；`:355-357` 检查超时。`AgentController.java:69-80` 等 loop 正常返回才发送内部 Wiki 事件，`:86-88` 异常发送通用错误。`AgentPreviewProxy.java:89-113,143-150` 仅 done 设置成功，最终回调交出答案和交互；`AgentPreviewController.java:213-218` 非成功调用 `store.fail`。另见[代码与结果对照](issue-p1-t1-1-round7-audit003-supplement.html)。

测试前后指定 18 文件 SHA-256 清单逐项匹配；完整 diff SHA-256 `FC856D5775C45297154F42C7BA38A22297E919D05C3D8F9DAECD26239DE65A1E`；开发记录 SHA-256 `0253EC705A940270499ED406880B030682445DB6F7E684E5E47BAC24D7FE5090`。未写数据库，无数据需回滚。临时探针清理命令被自动策略拒绝，未绕过，目录仍在；本次补充报告未重试清理。


## 2026-10-30 负责人决定

负责人决定不继续开发异常中断后成功结果恢复机制，将 ATFR-AUDIT-003 暂缓。该决定不表示问题已修复；本报告的受控观察及其限制仍然有效。用户可重新发起任务，届时重新评估并安排处理。

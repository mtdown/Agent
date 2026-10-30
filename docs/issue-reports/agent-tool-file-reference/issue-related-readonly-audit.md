# Agent 工具调用关联审查（非正式验收）

Change ID：agent-tool-file-reference。执行日期：2026-10-29。性质：只读审查与临时受控探针，不是固定验收或新的测试轮次；未启动服务、未调用真实模型或数据库。状态均为待处理。

## ATFR-AUDIT-001：只读 Wiki 查询后最终答复丢失（受控探针复现）

前提：注册 `plan_wiki_destination`，模型调用 `list_writable_wiki_locations`，之后模型返回普通最终文本且未请求选择。

步骤：使用已构建的依赖，在系统临时目录使用 `javac` 编译当前 `AgentLoop.java` 和 [探针](evidence-related-readonly-probe.java)，通过 `java` 运行。工具与模型均为假实现。未测试浏览器或服务端集成。

```text
javac -encoding UTF-8 -cp 'C:/Users/origin/IdeaProjects/Agent/cloud_agent/target/classes;BOOT-INF/lib/*' -d . C:/Users/origin/IdeaProjects/Agent/cloud_agent/src/main/java/com/et/agent/AgentLoop.java Probe.java
java -cp '.;C:/Users/origin/IdeaProjects/Agent/cloud_agent/target/classes;BOOT-INF/lib/*' Probe
modelFinalText=Found locations; emittedText=[]; wikiEvents=1; choice=null
```

预期：最终内容应输出。实际：最终内容未发出；工具事件非空且没有待选择项。`AgentLoop.java:157-160` 只在事件列表为空时发送最终文本，而 `:198-201` 把只读查询也计入事件。`AgentWikiChoiceProjection.java:38-41,115-126` 不生成该只读事件的替代文字；后者为静态推导，未在该探针中执行。影响：相关会话可能没有最终答复。

## ATFR-AUDIT-002：历史消息文案可能被覆盖（静态风险，未端到端复现）

`AgentPreviewController.java:213-217` 对所有非空 interaction 保存固定文案“请选择写入空间”，未区分目标选择、成功文档和部分成功；`AgentConversationStore.java:143-157` 存入 answer；`AgentMessageMapper.java:18-22` 读取已完成文本供后续对话使用。触发条件为成功文档或部分成功交互非空并正常完成。潜在影响：实时和历史文案不一致，下一轮收到的上下文错误。未运行控制器、数据库、浏览器全链路验证。

## ATFR-AUDIT-003：写入后异常使已成功结果无法投影（静态风险，未端到端复现）

`AgentLoop.java:174-201` 执行工具后可能因结果总量上限抛错；`AgentController.java:70-91` 等 loop 正常返回后才发送事件，异常只发送通用错误；`AgentPreviewController.java:213-218` 非成功时标记失败。若成功写入后后续步骤抛异常，潜在影响是用户和历史看不到已完成的操作，可能影响再次操作判断。未实测真实写入或持久化。

## 快照和环境限制

探针采用 Windows Java 17，依赖取自本地构建包及 target/classes；仅被测 AgentLoop.java 由源码即时编译，其余依赖未重新编译。源码 SHA-256：AgentLoop.java `C972FCDD4C03C5179A97522ED5DC6333CF5C084E0E9BF772D34EC69F702255CD`；AgentPreviewController.java `3056E2F502113AC23266F8AF0CCE679E03C2C0A6CBCB241DD4CF11F12FA64584`；AgentPreviewProxy.java `EE7F1CEC36334F05AFCCF7D8C1EC592C303B0E2C9B5FF870EDE1A98AA3F646EF`。未冻结完整测试快照，不能用于正式验收。

探针临时目录 `C:\Users\origin\AppData\Local\Temp\agent-audit-56cdff89f6bd4c8e854d12e5af06932f`。尝试删除时工具策略拒绝，目录仍在；未修改业务代码或服务状态。

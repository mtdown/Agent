# 3.1 第 1 轮当前快照独立补证

Change `agent-tool-file-reference`，阶段 3／任务 3.1／第 1 轮；实例 `/root/tester_3_1_current`，2026-10-30 16:00–16:05（UTC+08）。**A1、A2、A3 PASS；总体 PASS**，适用范围及替身边界见下。无确认的固定验收缺陷或清单外重大回归。项目未见文件级共享撤权模型；登录会话失权补证不证明未来文件级撤权机制，原问题记录仍保留此边界。

## 环境、快照与开发事实

分支 `feature/agent-tool-file-reference开发`，HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`。固定文件清单 `%TEMP%/agent-tool-file-reference-current-sha256.txt` SHA256 `65f2d54c09b2eb3cd72985c988066d1ee8b87505407e46a900b3d0472860488f`，47 文件逐项前后核对全部一致；末次补证后再次核对全部一致。累计 diff `%TEMP%/agent-tool-file-reference-current-diff.txt` SHA256 `ed586f692a3b1c072bdc76bbc845df945e59ed50670ee3d889ee389fd4a1ad1a`。开发事实 [dev-p3-t3-1-round1.md](dev-p3-t3-1-round1.md) SHA256 `e54fa712b66d519bbce17f036dd87212324be10ada2cdda930fc742f66211f5e`；该记录描述的事务、受控 SSE 替身和无完整跨进程测试与本次检查一致；所列旧文件哈希代表开发时快照，并非当前 47 文件清单。未运行基线或变异检验。

## 固定验收

```powershell
# cloud
mvn -q "-Dtest=AgentPersonalFileReferenceTest,AgentPersonalFileReferenceLocalIT,AgentWikiMultiResultLocalIT" test
# cloud_agent
mvn -q "-Dtest=AgentToolRegistryTest,AgentLoopTest,AgentControllerWikiEventTest" test
```

两命令均退出 0。Cloud 16 项、Agent 30 项，失败、错误、跳过均为 0。日志分别为 `%TEMP%/agent-ref-current-cloud-independent.log`（SHA256 `26b2461e0f57a8d9d55513dd73a5c86955829ca36d9f61d7b67c4e86da168b9d`）与 `%TEMP%/agent-ref-current-agent-independent.log`（空日志，SHA256 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`）；统计以本次 surefire 报告为据，可能被后续测试覆盖。

- **A1 PASS：**真实数据库事务中，真实文件搜索和读取产生两文件三片段引用；完成消息保存和历史查询后按消息解析路径与字节位置。真实文件证据经过受控 Agent SSE 替身进入 Cloud 控制器并持久化；Agent 循环测试覆盖工具证据处理。日志记录数据库事务回滚。
- **A2 PASS：**测试断言跨用户、跨消息、随机令牌、伪造证据、篡改签名位置及游标均拒绝；文件修改或删除后拒绝，流与历史不泄露私有内容。登录会话失权补证如下；本项目无个人文件级共享撤权时，失权语义为会话撤销而非文件级授权撤销。
- **A3 PASS：**指定测试覆盖 Wiki 结果、错误和历史；工作区未见本轮依赖或迁移修改。RAG 同消息共存与旧历史补证如下；未验证浏览器页面。

## 会话及 RAG 补证

在 `%TEMP%/agent-ref-current-probe-independent` 以当前源码编译 `RevocationProbe.java` 与六个相关源码类，`javac`、`java` 均退出 0，输入哈希前后相同。真实 `UserServiceImpl`、真实控制器、真实 Servlet 会话及随机端口 Tomcat 下，登录及引用解析成功、登出后同 cookie 请求抛 `BusinessException`；探针将异常模拟映射为 HTTP 401，**不是项目全局异常处理器的真实响应**。用户查询和消息存储使用替身；控制器先鉴权的代码路径意味着该次拒绝不进入解析，但探针无调用计数断言。日志 `run.log` SHA256 `6edb2cb85f3957e51966d7f917ded4df3419a685d2581cb26703fe0c08e1bae7`；源码 SHA256 `3566ceb17d89d01337bd6c2f8f82f274e2eb1ef2f80f3c5fda2df4d0dfbac12c`。

同目录 `RagCoexistProbe.java` 用当前源码编译并运行，均退出 0；真实 `AgentConversationStore.complete` 将非空 RAG `citationsJson` 与个人文件引用放入同一 assistant 消息，历史投影后引用字符串逐字一致、个人文件引用可解析、私有 stats 被移除且原有统计保留；旧消息的引用保持不变。mapper 持久化为内存替身，个人文件为隔离临时文件；此探针不证明真实 SQL 或真实 RAG 检索。源码 SHA256 `26cb5d91cfbdb1ab119e7e5abd9f3779026810137841b2809f8ed02dccc52318`；日志 `rag-run.log` SHA256 `6f9b07139b1428d8a38fb91597edc94829ba3f715de0add11c934d6db32d8822`，编译输入 `rag-inputs-before.txt` 和 `rag-inputs-after.txt` 相同。

真实模型与完整跨进程 Agent→Cloud→数据库链路未测；上述替身不能视为这些链路的证据。探针、输入哈希和日志按任务卡保留；探针 Tomcat 已停止，测试数据库事务回滚，未停止现有服务。IssueLog `ATFR-P3-T3.1-R1-S1` 保留文件级撤权尚无验证模型的准确状态；本报告只记录当前范围补证。

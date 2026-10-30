# ATFR-P1-T1.1-R2-001：写请求响应断开或损坏被报告为失败并允许再次写入

- Change／阶段／任务／轮次：agent-tool-file-reference / 1 / 1.1 / 2；测试实例 tester_1_1_round2。
- 状态：待处理。分支 feature/agent-tool-file-reference开发，HEAD 7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6。
- 输入及操作：本机受控服务读取 POST 正文后分别关闭连接，或返回 HTTP 200 与非法 JSON；模型在收到工具结果后再次调用相同 write_file。输入 path=a.txt、content=text、mode=create。
- 预期：服务已收到写请求但无法确认结果时，应报告结果未知，避免同轮再次写入。
- 实际：两种模式下服务各收到两次 POST，工具两次均返回 {"saved":false,"error":"Cloud library request failed"}。服务仅计数，未实际持久化。
- 影响：已提交写入可能被误称失败，后续模型写入可能造成重复或其他副作用。
- 代码位置：cloud_agent/src/main/java/com/et/agent/AgentLibraryHttpGateway.java, cloud_agent/src/main/java/com/et/agent/AgentLoop.java。
- 证据：[探针](evidence-p1-t1-1-round2-transport-probe.java)、[原始输出](evidence-p1-t1-1-round2-transport-output.txt)、[代码对照](issue-p1-t1-1-round2-transport.html)。
- 未运行基线；不判断是否为既有问题。受控假服务已停止。

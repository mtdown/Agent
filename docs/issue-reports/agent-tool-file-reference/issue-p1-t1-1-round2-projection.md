# ATFR-P1-T1.1-R2-002：成功文档与后续未知结果同时进入用户可见投影

- Change／阶段／任务／轮次：agent-tool-file-reference / 1 / 1.1 / 2；测试实例 tester_1_1_round2。
- 状态：待处理。分支 feature/agent-tool-file-reference开发，HEAD 7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6。
- 输入及操作：受控 SSE 先发送 create_wiki_document 成功（First），再发送 write_wiki_document status=unknown，最后 done。
- 预期：页面应能明确区分先前成功文档和后续未知写入。
- 实际：流中先输出“写入结果未知，请先核查目标位置”，随后输出 wiki_write_result，documentTitle 为 First；完成回调同时包含未知文本和 wiki_write_result 交互。探针未运行浏览器，也未持久化数据库。
- 影响：同一消息同时呈现已成功文档卡及未知提示；实际展示的含义需结合前端和上下文判定。
- 代码位置：cloud/src/main/java/com/et/cloud/controller/AgentWikiChoiceProjection.java, cloud/src/main/java/com/et/cloud/controller/AgentPreviewProxy.java。
- 证据：[探针](evidence-p1-t1-1-round2-projection-probe.java)、[原始输出](evidence-p1-t1-1-round2-projection-output.txt)、[代码对照](issue-p1-t1-1-round2-projection.html)。
- 未运行基线；不判断是否为既有问题。受控假服务已停止。

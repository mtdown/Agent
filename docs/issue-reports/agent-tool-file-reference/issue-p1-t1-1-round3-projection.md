# 第3轮清单外问题：多个 Wiki 结果投影

- ID：ATFR-P1-T1.1-R3-001；状态：待处理；结论：交付阻断。
- 环境：feature/agent-tool-file-reference开发，HEAD 7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6。实际 `AgentWikiChoiceProjection`，Mockito 仅提供空间标签；不访问数据库或服务。
- 精确事件输入及顺序见 [探针](evidence-p1-t1-1-round3-projection-probe.java)，实际输出见 [输出](evidence-p1-t1-1-round3-projection-output.txt)，令牌已脱敏。
- 场景一：成功文档 Document A 属于 Folder A；随后 Folder B 成功，再收到另一写入 unknown。预期成功文档的位置与其确认结果一致，且与后续未知结果区分；实际答复将 Document A 归入 Folder B，成功卡仍为 Folder A。影响：用户收到彼此矛盾的位置，可能在错误位置核查。
- 场景二：同一文件夹依次收到 Document A 和 Document B 的成功结果。实际答复和卡仅保留 Document B；Document A 不再出现在投影中。预期至少不能把已确认的不同成功结果误认为只有后一项；此处不推断应如何呈现多卡。影响：用户看不到此前确认成功的结果。
- 相关代码：`cloud/src/main/java/com/et/cloud/controller/AgentWikiChoiceProjection.java` 的 `project` 与 `displayAnswer`。
- 限制：探针直接调用投影，不声称已执行浏览器、SSE 或数据库历史；其显示结果通过该类公开方法取得。

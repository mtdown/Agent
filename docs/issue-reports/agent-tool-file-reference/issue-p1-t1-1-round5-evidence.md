# 第 5 轮：受控读错误探针及 C1 验收证据

Change `agent-tool-file-reference`，阶段 1，任务 1.1；测试实例 `/root/tester_1_1_round5`；2026-10-29。分支 `feature/agent-tool-file-reference开发`，HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`。本记录只补充事实，不修改固定验收。

## ATFR-AUDIT-001：读错误后模型回答丢失（第 5 轮受控复现）

前提：AgentLoop 注册 `plan_wiki_destination`；假模型先调用 `list_writable_wiki_locations`，假工具返回 `{"code":40101,"message":"request rejected"}`；模型下一轮输出 `Read failed. Please check access.`，不请求选择。预期：错误说明能发出。实际：工具错误已进入模型历史，但输出为空，事件数为 1、choice 为 null。受控探针不调用真实模型、数据库或 HTTP 服务，不证明浏览器端到端行为。

- [探针输入](evidence-p1-t1-1-round5-read-error-probe.java)；[原始输出](evidence-p1-t1-1-round5-read-error-output.txt)。
- 复现：复制输入至临时目录 `AtfrReadErrorProbe.java`，以 `cloud_agent/target/classes` 及本机 Maven 测试依赖为 classpath，执行 `javac -encoding UTF-8 -cp <classpath> -d <temp> <temp>/AtfrReadErrorProbe.java`，再执行 `java -cp <temp>;<classpath> AtfrReadErrorProbe`，均退出 0。
- 观察依据：`AgentLoop.java:133-158,198-201` 缓冲模型文字并把读取事件计入列表；`AgentWikiChoiceProjection.java:38-40,98-100` 对该事件没有替代输出。后者是代码核对，不是端到端执行证据。

## ATFR-P1-T1.1-R5-C1：实时卡片关联证据不足

预期：跨文件夹及同文件夹多文档场景中，实时、历史及页面结构逐项核对文档标题、所属文件夹与可打开 token 的关联。实际：`AgentWikiMultiResultLocalIT.java:107-118` 对 SSE 断言两个标题存在、无真实 ID；对数据库历史中的两张卡逐项断言标题、文件夹、token 对应的预览正文。`historyUsesCurrentSqlLocationsAndNeverLeaksAfterDeletionOrLoss` 对同文件夹的历史卡和预览逐项断言。没有逐卡断言实时 SSE 中标题、文件夹和 token 的对应关系，也未执行浏览器。Cloud 命令、单独 SQL 命令和前端构建退出 0；这是一项**证据不足**，不是断言失败，也不声称产品行为错误。

四类 Cloud 原始日志 `C:/Users/origin/AppData/Local/Temp/atfr-independent-r5-cloud.log`；单独 SQL 日志 `C:/Users/origin/AppData/Local/Temp/atfr-independent-r5-sql.log`；构建日志 `C:/Users/origin/AppData/Local/Temp/atfr-independent-r5-front.log`。测试前后冻结的 15 文件 SHA256 均与 `C:/Users/origin/AppData/Local/Temp/agent-tool-file-reference-1.1-round5-sha256.txt` 一致。测试数据库事务已回滚；假服务关闭。
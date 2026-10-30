# 第 6 轮补证：自定义 Skill 与 Wiki 别名

Change `agent-tool-file-reference`，阶段 1，任务 1.1；独立补证实例 `/root/tester_1_1_skill_probe`；2026-10-29，分支 `feature/agent-tool-file-reference开发`。此记录不更改固定验收。

## ATFR-P1-T1.1-R6-001：自定义 Skill 固定数字参数被拒绝

前提和输入：受控假 `get_skill` 返回模拟已授权自定义 Skill，定义中 `steps[0]` 为 `write_wiki_document`，其 `args.spaceId` 为用户自定义的数字字符串 `123456789`，其余参数为示例正文。定义经现有 `AgentSkillDefinitionValidator` 校验接受。模拟模型从工具结果读取该步骤，并原样发起工具调用。

预期：依照定义提供的合法固定参数能够进入写工具；实际：AgentLoop 先要求请求内有效 Wiki 别名，返回 `{"saved":false,"error":"Invalid or expired Wiki location reference"}`，假写工具执行 0 次。模型 history 的 get_skill 结果包含原始数字。影响仅限此受控场景：含原始数字 spaceId 的自定义 Skill 按定义执行时不兼容；未验证真实历史 Skill、用户授权、数据库持久化或端到端路径，不能推断所有旧 Skill 都受影响。

## ATFR-P1-T1.1-R6-S1：Skill 输入安全检查证据不足

本次只验证了用户自定义 Skill 数据在模型历史中可见及随后别名验证的行为。数字不是服务端 Wiki 查询结果，不能据此认定服务端 Wiki 结果泄漏；本次未验证真实授权、持久化、内建 Skill 或其他输入路径，故安全检查证据不足，不作泛化安全结论。该条是证据不足，不是断言失败。

## 证据和复现范围

[受控探针完整命令、源码、输入及输出](evidence-p1-t1-1-round6-skill.txt)，原始文件与本副本 SHA-256 均为 `3539829171D3331DE7D4F6AF5520D8EBA2B4E0ABBB12DF9EFF1DD37108F29C72`；两个 Java 程序编译和运行退出码均为 0。探针使用现有 `cloud_agent/target/classes`、`cloud/target/classes` 及本地 Jackson 2.15.3；假工具不访问数据库、网络，也无真实写入。

测试前后核对 `C:/Users/origin/AppData/Local/Temp/agent-tool-file-reference-1.1-round6-sha256.txt` 所列 18 文件，均匹配；探针源和 class 的临时目录已删除，原始证据文本保留。代码依据：`cloud_agent/src/main/java/com/et/agent/AgentLoop.java` 的 Wiki 工具判断、`WikiAliases.decodeArguments` 和 history 写入；`cloud/src/main/java/com/et/cloud/controller/AgentSkillDefinitionValidator.java` 的 tool step 验证。详见[输入与观察对照](issue-p1-t1-1-round6-skill.html)。

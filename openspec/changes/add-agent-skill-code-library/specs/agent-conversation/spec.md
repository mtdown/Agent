# Spec Delta

## ADDED Requirements

### Requirement: Agent 操作本人历史会话
Agent SHALL 能以当前登录用户身份读取、搜索、重命名及删除该用户自己的历史会话；删除前 SHALL 请求用户确认；Agent SHALL NOT 改写或删除已保存的单条原始问答，也不得读取其他用户会话。

#### Scenario: 搜索本人历史
- **WHEN** 用户要求 Agent 查找自己以前的对话
- **THEN** Agent 只能检索并读取当前用户自己的会话与消息

#### Scenario: 删除本人会话
- **WHEN** 用户要求 Agent 删除本人历史会话并确认该操作
- **THEN** 系统按现有所有者边界删除该会话

#### Scenario: 未确认删除或越权请求
- **WHEN** Agent 尚未取得删除确认，或请求目标属于其他用户
- **THEN** 系统拒绝删除且目标会话不变

#### Scenario: 原始问答不可编辑
- **WHEN** Agent 尝试改写或删除历史会话中的单条已保存问答
- **THEN** 系统拒绝该操作，原始内容保持不变

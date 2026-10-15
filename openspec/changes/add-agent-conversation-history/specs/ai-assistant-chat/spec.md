# Spec Delta

## ADDED Requirements

### Requirement: Agent 逐轮选择 RAG 范围
Agent 页面 SHALL 允许用户逐轮关闭检索、使用全部授权空间或指定一个以上可见空间；每次检索 SHALL 以请求时的实际空间权限为准。

#### Scenario: 关闭 RAG
- **WHEN** 用户关闭本轮 RAG 后提问
- **THEN** 系统不检索知识库，回答不展示文档引用

#### Scenario: 指定空间
- **WHEN** 用户选择一个或多个可见空间后提问
- **THEN** 系统仅在这些空间与当前授权空间的交集内检索

#### Scenario: 空选择与越权选择
- **WHEN** 用户提交空的指定空间集合或只提交无权访问的空间
- **THEN** 系统不得退化为检索全部授权空间

### Requirement: Agent 历史引用
Agent SHALL 将每条回答的引用关联到该条消息；点击引用 SHALL 再次检查当前文档权限。

#### Scenario: 历史引用正确归属
- **WHEN** 用户打开含多轮 RAG 回答的历史会话
- **THEN** 每轮显示其自身检索得到的引用，点击某轮标记不会跳到另一轮的引用

#### Scenario: 空检索结果
- **WHEN** 开启 RAG 但指定范围内无支持证据
- **THEN** 系统不给出伪造引用，并明确告知未找到依据

#### Scenario: 引用来源失权
- **WHEN** 用户打开旧会话中来自已失权空间的引用
- **THEN** 文档入口拒绝打开，旧会话文本仍可由会话所有者查看


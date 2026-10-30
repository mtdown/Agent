# Spec Delta

## ADDED Requirements

### Requirement: Agent personal file citations persist with their message
Agent SHALL 将真实的个人文件引用关联到对应会话消息，并提供与现有引用一致的点击预览体验，不信任模型自行生成的引用目标。

#### Scenario: Reload a personal file citation
- **WHEN** 用户刷新并重开包含个人文件引用的历史会话
- **THEN** 引用仍属于原消息并能在当前授权且文件未变化时打开对应片段

#### Scenario: Model invents a citation
- **WHEN** 模型输出没有匹配服务端引用数据的标记
- **THEN** 页面不将该标记变为可打开文件的引用

# Spec Delta

## ADDED Requirements

### Requirement: Agent 库入口与卡片视图
AI 助手页面 SHALL 在左侧“新聊天”下显示“Skill 库”和“代码库”入口；选择任一入口时，右侧主区域 SHALL 以卡片展示当前用户可访问条目的名称和简要能力描述，并提供查看详情、版本及本人条目的编辑入口。

#### Scenario: 浏览 Skill 库
- **WHEN** 登录用户点击左侧“Skill 库”
- **THEN** 右侧展示可访问 Skill 卡片，点击卡片可查看结构化详情与版本

#### Scenario: 浏览代码库
- **WHEN** 登录用户点击左侧“代码库”
- **THEN** 右侧展示可访问代码卡片，点击卡片可查看源代码、入口、版本及使用它的 Skill

#### Scenario: 返回聊天
- **WHEN** 用户从任一库返回某历史会话或新聊天
- **THEN** 原有聊天与流式回答界面继续正常工作

# Proposal

## Why

现有网页端 Agent 只有固定提示词与少量内置工具，无法把成功任务沉淀为可查找、可编辑、可复用的 Skill，也没有供 Skill 引用的在线代码库。需要在保持用户资源权限和现有会话能力的前提下，为 Agent 提供自主撰写 Skill、版本化代码和受控执行的完整闭环。

## What Changes

- 在 AI 助手左侧“新聊天”下增加“Skill 库”和“代码库”，右侧以名称和能力简介卡片展示条目，支持详情、编辑、版本和共享管理。
- 增加 `skill_system` 目录提示词：只预载当前用户可用且已启用 Skill 的简要总览；匹配后按需读取完整结构化 Skill。Agent 完成任务后可提议或自动沉淀 Skill 草稿，用户确认启用后才可执行。
- 增加在线 Python/Java 代码项的创建、编辑、发布、版本和样例验证。Agent 可自行创建并发布代码，验证可用后将固定代码版本绑定到 Skill 草稿；更新代码不自动升级既有 Skill。
- 为代码执行提供用户独立工作空间以及有权限的知识库、本人历史会话、Skill 与代码访问能力；保持原始历史消息不可改写。
- 增加运行限额、权限复核、失败定位及版本追溯，避免代码任务拖慢现有聊天服务。

## Goals / Non-Goals

**Goals:** 网页端 Agent 能创建、选择和复用 Skill；用户能在线维护 Skill 与代码；代码来源及版本明确；执行访问遵守当前用户权限；草稿不会自动运行。

**Non-Goals:** 将 Skill 混入 RAG 文档空间；让普通用户访问宿主机任意路径或其他用户的历史会话；自动修改已保存的原始问答；将新发布代码静默替换到已启用 Skill。

## Acceptance Criteria

- 用户能在两个卡片库查看名称和简介，编辑本人条目，查看版本，并主动共享；非所有者无法修改。
- `skill_system` 只暴露当前用户可用的已启用目录项；未审核草稿不能被自动选择或执行。
- Agent 可从完成的任务生成结构化 Skill 草稿；关联的 Python/Java 代码必须先通过样例验证，再绑定确定版本。
- Skill 执行按当前用户权限读取知识库、本人的历史会话、Skill 和代码；用户工作空间彼此隔离，失权后再次执行会被拒绝。
- 代码超时、资源不足或步骤失败有可定位的错误；现有 Agent 对话与 RAG 引用能力保持可用。

## Capabilities

### New Capabilities

- `agent-skill-library`: Skill 草稿、启用、结构化格式、版本、共享和按需选择。
- `agent-code-library`: 在线 Python/Java 代码项、版本发布、样例验证和固定引用。
- `agent-skill-execution`: 用户隔离的执行空间、受权限约束的资源操作与运行控制。

### Modified Capabilities

- `ai-assistant-chat`: 在现有 Agent 页面增加两个库的导航与卡片视图，并接入 Skill 选择。
- `agent-conversation`: Agent 代表当前用户读取、搜索、重命名和删除本人历史会话，保持原始消息不可改写。

## Impact

涉及 `cloud_front` 的 AI 助手页面、`cloud_agent` 的系统提示词与工具循环、`cloud` 的权限与持久化接口，以及新的数据库表、代码执行组件和部署资源预算。现有知识库与会话表继续作为各自数据来源。回滚时关闭 Skill 选择及代码执行入口并保留新库版本数据，原有 Agent 对话仍可用。

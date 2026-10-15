# Proposal

## Why

Agent 预览目前每次只发送当前问题，刷新后丢失对话，也无法在 Agent 模式选择知识库。用户需要能继续自己的多轮对话，并明确控制每次回答是否使用已授权的 RAG 空间。

## What Changes

- 为 Agent 增加按用户隔离、可重新打开的持久化会话与消息历史；管理员也不能通过会话接口查看其他用户的内容。
- 对每次 Agent 提问提供关闭 RAG、全部授权空间或手动指定空间三种范围；服务端始终重新校验空间权限。
- 将最近的已完成对话作为 Agent 上下文，并把每条回答对应的引用与生成指标持久化，在历史页面正确恢复。
- Agent 对话采用左侧历史列表、右侧问答的布局；首问命名，支持本人重命名与删除，并修复现存乱码标题。
- 调整两级流式接口的异步超时，使生成时间超过 30 秒时仍可返回结果。
- 延用现有检索、流式输出与文档权限检查；本次不实现只读知识库 Skill，也不把对话自动写入知识库。

## Capabilities

### New Capabilities

- `agent-conversation`: 用户私有的 Agent 会话生命周期、消息持久化、多轮上下文和流式恢复语义。

### Modified Capabilities

- `ai-assistant-chat`: Agent 模式支持逐轮 RAG 范围选择与按消息展示引用；经典 RAG 模式保持现有行为。

## Impact

涉及 `cloud` 的 Agent API、MySQL 表和权限/检索编排，`cloud_agent` 的结构化多轮输入，`cloud_front` 的 Agent 会话列表及对话页面，以及数据库迁移脚本和自动化测试。

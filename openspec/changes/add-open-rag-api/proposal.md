# Proposal: add-open-rag-api

## Why

change 1/2 已交付 RAG 检索管道与 AI 助手面板，但能力只限应用内使用。求职主线项目（本地 Qwen Agent）需要把这个 WIKI 当作**云端知识库**调用——本地 Agent 没有登录态，无法走 sa-token 会话。开放一组 API-Key 认证的只读 RAG 接口，串起"微调 Agent + 云端知识库"的完整故事。

## What Changes

- 新表 `rag_api_key`：用户级 API Key（SHA-256 哈希存储，明文仅创建时展示一次）
- Key 管理接口（应用内，登录态）：创建 / 列表 / 删除
- 开放只读检索接口 `POST /api/open/rag/search`：请求头 `X-API-Key` 认证，复用 RagSearchService，**权限硬过滤天然继承**（key 属主只能检索自己可见空间）
- 开放 SSE 问答接口 `POST /api/open/rag/ask`：同 key 认证，复用 RagAskService（本地 Agent 可直接拿带文号引用的成段回答）
- 前端：AI 助手页新增「开放 API」抽屉——Key 列表、创建（明文一次性展示+复制）、删除、curl 调用示例

## Capabilities

- **New**: `open-rag-api` —— API Key 管理与开放只读 RAG 接口（检索 + SSE 问答）

## Non-goals

- Key 启用/停用开关（删除即吊销，够用；状态位后续按需加）
- 限流/配额（演示规模不做；设计留一句扩展点）
- Key 过期时间（长期 key，删除即吊销）
- 多 Key 并发数上限（不限个数，一人多 key 允许）
- MCP Server 封装（远期阶段 4）

## Acceptance Criteria

- 用户在 AI 助手页「开放 API」抽屉创建 Key，明文只出现一次，可一键复制；列表显示名称/前缀/创建时间，可删除
- 用有效 Key 调 `POST /api/open/rag/search`：返回与登录态调用 `/rag/search` 相同结构的结果；非授权空间（key 属主不可见）零泄露
- 用有效 Key 调 `POST /api/open/rag/ask`（SSE）：事件流与应用内问答一致（meta/reason/delta/done/error）
- 无 Key / 错 Key / 已删除 Key → 统一 40101 无权限（BaseResponse），不泄露"Key 是否存在"
- 删除 Key 后立即失效（下次请求 40101）

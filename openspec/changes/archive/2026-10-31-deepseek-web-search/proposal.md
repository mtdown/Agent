# Proposal

## Why

Agent 缺少实时网页资料，无法可靠回答需要最新信息或阅读用户指定网页的问题。

## What Changes

- 使用 DeepSeek Search 搜索网页并按需使用 Fetch 抓取正文，由 Agent 工具循环选择调用。
- 对外部内容、来源、失败、调用次数及配置设置边界；回答区分摘要和已获取正文。
- 保持现有会话、知识库检索和流式协议兼容。

## Capabilities

### New Capabilities
- `agent-web-research`: Agent 网页搜索、按需抓取与来源使用。

### Modified Capabilities

## Impact

影响 cloud_agent 的工具注册、运行时配置、模型提示和测试；访问 DeepSeek 的付费只读 API。不新增依赖、数据库迁移或公开 HTTP API。

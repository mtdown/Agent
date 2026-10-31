# Agent 网页搜索与抓取

服务端通过环境变量 `DEEPSEEK_WEB_API_KEY` 配置独立的 DeepSeek 网页 API 密钥。该密钥不复用模型密钥，也不传给前端；未设置或设置为空时网页工具停用，已有工具保持可用。变更环境变量后重启 cloud_agent。

Agent 根据问题搜索并按需抓取网页；搜索词或 URL 会发送给 DeepSeek，网页内容会进入模型上下文。每轮 Search 和 Fetch 各最多三次上游请求（包括重试）。这两个接口可能按次产生费用，请以 DeepSeek 的实际计费政策为准。

停用时删除 `DEEPSEEK_WEB_API_KEY` 或设为空并重启服务。移除本功能代码可完全回滚，无数据库迁移。

# agent-web-research Specification

## Purpose

让 Agent 在用户问题需要实时资料或指定网页时，安全地搜索和读取网页，并清晰说明回答所依据的来源及其限制。

## Requirements

### Requirement: 有界网页搜索与抓取
系统 SHALL 在配置 DeepSeek 网页 API 密钥时提供 Search 和 Fetch 工具，供 Agent 自动按需调用；未配置时不得影响其他 Agent 功能。

#### Scenario: 搜索与读取
- **WHEN** Agent 需要搜索网页并读取某个结果的详情
- **THEN** 系统分别调用 Search 和 Fetch，保留实际标题、链接、摘要或 Markdown 正文供回答使用

#### Scenario: 超出预算
- **WHEN** 本轮 Search 或 Fetch 的上游 HTTP 尝试分别达到三次
- **THEN** 对应工具不再请求上游，返回可识别的预算耗尽结果

#### Scenario: 未配置密钥
- **WHEN** 未配置网页 API 密钥
- **THEN** Agent 不提供联网工具且既有工具仍可使用

### Requirement: 来源与内容可信边界
系统 SHALL 将搜索结果和网页正文视为不可信资料，提示 Agent 使用实际来源链接，并区分摘要与正文。

#### Scenario: 正文不可用
- **WHEN** 网页抓取失败且搜索摘要可用
- **THEN** Agent 的工具上下文保留摘要和抓取失败状态，提示其说明仅基于摘要且不猜测无法核实的细节

#### Scenario: 外部指令
- **WHEN** 网页内容包含要求改变 Agent 规则或泄露信息的文字
- **THEN** 系统提示模型把该文字作为资料而非指令，且密钥不包含在工具输出中

### Requirement: 参数和故障处理
系统 SHALL 校验搜索词及抓取 URL，限制资源消耗并区分无结果、配置错误、余额不足、限流及上游故障。

#### Scenario: 搜索无结果
- **WHEN** Search 返回空 results
- **THEN** 系统返回无结果而不伪造来源

#### Scenario: 拒绝无效 URL
- **WHEN** Fetch URL 不是带主机且不含用户凭据的 HTTP 或 HTTPS 地址，或超过 8192 字符
- **THEN** 系统拒绝调用上游

#### Scenario: 请求失败
- **WHEN** 上游返回 401、402 或其他不可重试 4xx
- **THEN** 系统不重试并返回可识别的错误类别，不暴露密钥

#### Scenario: 临时故障
- **WHEN** 上游返回 408、429 或 5xx
- **THEN** 系统最多重试一次且每次上游尝试均计入本轮预算

## Purpose

定义 RAG embedding 的提供方配置、密钥注入、失败可观测性与存量向量全量重建行为，保证问答检索链路不依赖本地模型服务即可工作。

## ADDED Requirements

### Requirement: embedding 默认使用云端 DashScope 模型

系统默认 SHALL 通过 DashScope compatible-mode 端点调用 `qwen3.7-text-embedding-flash` 生成向量，不再默认连接本地 Ollama。仍 SHALL 保留通过环境变量覆盖 base-url / model / api-key 的能力（例如切回本地端点）。

#### Scenario: 默认配置启动

- **WHEN** 开发服务以默认配置启动且已注入 embedding API key
- **THEN** 文档索引与查询向量化均调用 DashScope compatible-mode 的 `qwen3.7-text-embedding-flash`，无需本地 Ollama 运行

#### Scenario: 显式覆盖为本地端点

- **WHEN** 启动时通过环境变量将 embedding base-url 指向本地 OpenAI 兼容端点
- **THEN** 系统使用该端点且不发送 Authorization 头，行为与切换前一致

### Requirement: embedding 密钥仅经环境变量注入，不入仓库

API key SHALL 存放在被 Git 忽略的本地文件（根目录 `.env.dev`），开发启动脚本 MUST 在启动后端进程前将其读取并注入为进程环境变量；仓库中提交的配置文件 MUST NOT 包含任何明文密钥。

#### Scenario: 启动脚本注入密钥

- **WHEN** `.env.dev` 存在且包含 `RAG_EMBEDDING_API_KEY`，负责人运行开发启动脚本
- **THEN** 后端进程环境中存在该变量，embedding 视为已配置，索引与检索正常工作

#### Scenario: 密钥文件不存在时的行为

- **WHEN** `.env.dev` 不存在或未配置该变量
- **THEN** 启动脚本不报错正常继续，后端 embedding 视为未配置，文档保存不受影响（沿用既有降级）

### Requirement: embedding 连接类失败必须给出可读错误

当 embedding 请求因网络/连接异常失败且底层异常 message 为空时，系统 SHALL 在抛出的错误信息中包含异常类名与请求目标端点，MUST NOT 输出 `embedding 请求失败: null` 这类无法定位原因的信息。

#### Scenario: 本地端点未启动

- **WHEN** embedding base-url 指向未监听的本地端口并发起向量化请求
- **THEN** 错误信息包含异常类型（如 ConnectException）与目标 URL，能直接看出"连不上哪个端点"

### Requirement: 管理端支持强制全量重嵌入

管理端重建接口 SHALL 支持强制模式：`force=true` 时跳过"文档已有当前版本 ACTIVE 向量"的增量判断，对全部在库 Markdown 文档重新切块并重新生成向量；默认（`force` 缺省或 false）SHALL 保持既有幂等增量行为。接口 MUST 仅限管理员角色调用。

#### Scenario: 换模型后强制重建

- **WHEN** embedding 模型切换后管理员以 `force=true` 调用重建接口
- **THEN** 全部 216 篇文档重新生成向量（旧行置 INVALID、插入新行），完成后检索使用新模型的向量打分

#### Scenario: 默认调用保持增量幂等

- **WHEN** 管理员不带 force 参数调用重建接口
- **THEN** 已有当前版本 ACTIVE 向量的文档被跳过，仅补齐缺失或版本落后的文档

#### Scenario: 非管理员调用被拒绝

- **WHEN** 非管理员账号调用重建接口（任意参数）
- **THEN** 请求被权限校验拒绝

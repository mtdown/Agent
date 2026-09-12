# 设计：switch-embedding-dashscope

## Context

见 proposal.md。现状约束：`wiki_chunk` 表存量 2061 行向量为 Ollama `qwen3-embedding:4b`（2560 维），换模型后维度不同，余弦相似度对维度不一致的向量记 0 分（不抛错）；`InMemoryCosineVectorStore` 按空间缓存、`onChunksChanged` 失效重载。评测体系（99 题考卷 + `run_eval.py`）与离线向量缓存（`qwen3.7-text-embedding-flash` 全量 2061 chunk）均已就绪，DashScope compatible-mode + 该模型已被离线评测验证可用。`start-dev.ps1` 以 `Start-Process` 拉起后端 jar（profile=local），当前不注入任何 RAG 环境变量。

## Goals / Non-Goals

**Goals:**

- 默认配置即云端 embedding，无本地依赖；密钥不进仓库、不改启动命令的使用方式。
- 换模型后能一条命令完成存量全量重嵌入，且保留原增量幂等语义。
- 每轮问答的时间线（开始/结束、每步调用、各步耗时）对前端可见。
- 产出可与旧基线对比的 http 模式召回指标。

**Non-Goals:**

- 不做 embedding 多提供方路由/降级切换（单模型单一配置）。
- 不持久化问答耗时（只在 SSE 载荷与日志中呈现，不建表）。
- 不改评测集与指标口径。

## Decisions

### D1 配置默认值整体切云端（yml 与代码默认一致）

`application.yml` 与 `RagProperties.Embedding` 的默认 base-url 都改为 `https://dashscope.aliyuncs.com/compatible-mode/v1`、默认 model 改为 `qwen3.7-text-embedding-flash`。两处保持一致，避免 yml 段缺失时回退出"云端模型 + 本地端点"的错配组合。`isConfigured` 逻辑不变：云端要求 api-key 非空；显式覆盖 base-url 为 localhost 时仍免 key。
*替代方案*：只改 yml 不改代码默认——留错配风险，弃。

### D2 密钥走根目录 `.env.dev`，由 `start-dev.ps1` 注入

格式为每行 `KEY=VALUE`（`#` 注释、忽略空行），脚本在启动后端前逐行解析并 `$env:KEY = VALUE`。`Start-Process` 子进程自动继承。文件加入 `.gitignore`。首版写入 `RAG_EMBEDDING_API_KEY`；`RAG_LLM_API_KEY` 等其他 RAG 变量天然支持同通道注入（存在即注入，不存在跳过）。脚本对 `.env.dev` 缺失零感知（提示一行即可）。
*替代方案*：密钥写 `application-local.yml`——会进仓库，泄漏风险，弃；用 Spring 的 `spring.config.import` .env 插件——为单个 key 引入新依赖，弃。

### D3 报错信息兜底：类名 + 目标端点

`RagEmbeddingClient` 捕获异常时，message 为空则拼 `e.getClass().getSimpleName()`，并附带请求目标 URL（host 即可），形如 `embedding 请求失败: ConnectException (http://localhost:11434/v1/embeddings)`。HTTP 非 200 与返回数量不符的既有报错保持不变。

### D4 rebuild 加 `force` 查询参数，语义为"跳过 isUpToDate"

`POST /admin/rag/rebuild?force=true`。接口签名 `rebuildAll(boolean force)`，impl 内 `if (!force && isUpToDate(doc)) skip`。选查询参数而非请求体：保持既有 POST 无体调用兼容，curl 一行可测。重建期间新旧维度向量共存 → 检索短暂记 0 分抖动，完成后自愈（每篇文档完成后 `onChunksChanged` 逐空间失效缓存）。量级：216 篇 / 2061 chunk / batch=10 → 约 207 次嵌入调用，DashScope 限频（个人级常见 3000 RPM）远够。
*替代方案*：先 SQL 全量置 INVALID 再普通 rebuild——两步操作无事务保护，中途失败留全库空索引，弃。

### D5 计时埋点：检索阶段 timings 挂在 `RagSearchResult`，问答阶段在 `RagAskServiceImpl` 汇总

- `RagSearchResult` 新增 `timings`（`SearchTimings`：permissionMs / docCountMs / docNumberMs / embedMs / vectorMs / totalMs），由 `RagSearchServiceImpl` 在各阶段埋点填充。检索服务是独立入口（`/rag/search` 也用它），挂返回值让两个消费方都受益。
- `RagAskServiceImpl.runAsk` 记录 `startedAt`（epoch ms）→ 调 search（得 retrievalMs 与子项）→ 构造 meta/prompt（promptMs）→ LLM（首 token、thinking、answer，既有逻辑）→ done。
- `DonePayload` 增量字段：`startedAt`、`finishedAt`、`totalMs`、`retrievalMs`、`promptMs`、`firstTokenMs`、`steps`（检索子项复用 SearchTimings 字段）。既有 `thinkingMs`/`answerMs`/`usage` 原样保留，旧字段语义不变。
- 零命中短路路径同样发 done（生成段为 0）。

### D6 前端：统计行保留 + 可展开"调用明细"面板

`AiAssistantPage.vue` 消息对象增 `trace` 字段存 done 的时间线；模板在现有统计行旁加一个切换按钮，展开后按时间线渲染各步骤（label + ms，开始/结束时间用 `dayjs`/原生 `toLocaleTimeString` 格式化 epoch ms）。默认收起，不影响消息流布局。

### D7 验证与对比口径

顺序：`smoke_test.py`（eval/.env 已配同模型同端点）→ 单测 → `start-dev.ps1` 起服务 → admin 登录拿 session → `POST /api/admin/rag/rebuild?force=true` → `run_eval.py --probe` → `run_eval.py`（http 模式）。对比对象固定为 2026-09-11 http 基线（Ollama + 文号混合层），offline 数字（无混合层）仅作参考不并列比较——沿用 README"两种 retriever 分数不可混合平均"的既有口径。

## Risks / Trade-offs

- [重建期间检索质量短暂抖动（新旧维度混存记 0 分）] → 演示/开发环境可接受；重建是分钟级操作，完成后自愈；如需避免，可在业务低峰执行。
- [`.env.dev` 忘记配置导致 embedding 未配置] → 与 Ollama 未启动同样是"未配置降级"，文档保存不受影响；启动脚本输出一行提示当前注入了哪些 RAG 变量（只列名字不列值）。
- [API key 曾出现在对话/工单中] → 建议负责人后续在 DashScope 控制台轮换；本次仅写入 gitignore 文件，不入库。
- [新模型召回可能低于旧模型] → 本 change 目标是量化对比而非保证提升；若明显回退，回滚方案见下。

## Migration Plan

1. 合并部署后首次启动前：创建 `.env.dev` 并写入 `RAG_EMBEDDING_API_KEY`。
2. 启动服务 → `POST /api/admin/rag/rebuild?force=true` 全量重嵌入 → 跑 http 评测存档新基线。
3. 回滚：环境变量覆盖回 Ollama（`RAG_EMBEDDING_BASE_URL=http://localhost:11434/v1`、`RAG_EMBEDDING_MODEL=qwen3-embedding:4b`）→ 再 force 重建一次即恢复旧向量；代码回滚走正常分支回退。

## Open Questions

（无——模型可用性、量级、密钥通道均已在探索阶段确认。）

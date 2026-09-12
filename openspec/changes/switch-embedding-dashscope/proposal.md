# 提案：switch-embedding-dashscope

## Why

本地 Ollama 未启动时 RAG embedding 直接失败，且报错为不可读的 `embedding 请求失败: null`（Windows 下 ConnectException 的 message 为空），AI 助手问答链路不可用。需要把 embedding 切换到云端 DashScope 的 `qwen3.7-text-embedding-flash`（该模型已在离线评测中验证可用），摆脱对本地服务的依赖；同时对存量文档全量重嵌入、用既有评测集量化召回变化，并给 AI 助手补上分步耗时明细以支撑后续性能优化。

## What Changes

- 后端 embedding 默认配置从本地 Ollama（`http://localhost:11434/v1` + `qwen3-embedding:4b`）切换为云端 DashScope compatible-mode（`https://dashscope.aliyuncs.com/compatible-mode/v1` + `qwen3.7-text-embedding-flash`）；Ollama 不再是默认，但仍可通过环境变量覆盖 base-url 使用。
- 新增密钥注入通道：根目录 `.env.dev`（gitignore）存放 `RAG_EMBEDDING_API_KEY` 等密钥，`start-dev.ps1` 启动后端前读入注入进程环境。
- 修复 embedding 连接类报错信息：message 为空时带上异常类名与目标端点，让"连不上"一眼可辨。
- `POST /admin/rag/rebuild` 新增 `force` 参数：`force=true` 时跳过 `isUpToDate` 检查，对全部存量文档强制重嵌入（换模型后向量维度变化，必须全量重建）。
- AI 助手问答链路新增分步计时：墙钟开始/结束时间、权限过滤、文号精确匹配、查询向量化、向量检索、prompt 构造、LLM 首 token/思考/回答各阶段耗时，经 SSE done 事件下发；前端在回答下方增加可展开的"调用明细"时间线面板。
- 按既有评测流程（`smoke_test.py` → force 重建 → `run_eval.py` http 模式）产出新模型召回指标，与 Ollama 基线（docRecall@6=0.880 / MRR=0.4342）对比。

### 非目标

- 不改切块策略、检索参数（topK）与文号混合检索逻辑。
- 不承诺新模型召回一定优于旧模型；本 change 只负责切换与量化对比。
- 不改造 LLM 生成链路（模型、prompt、SSE 协议的既有事件语义保持兼容，只新增字段）。

## Capabilities

### New Capabilities

- `rag-embedding`: RAG embedding 的云端默认配置、密钥环境变量注入、可读的失败报错、以及管理端 force 全量重嵌入语义。
- `ai-assistant-trace`: AI 助手问答的分步耗时追踪——SSE done 载荷携带时间线字段，前端渲染调用明细面板。

### Modified Capabilities

（无——RAG/AI 助手既有能力尚未同步进 `openspec/specs/`，本 change 新建对应规格。）

## Impact

- 后端：`cloud/src/main/resources/application.yml`、`RagProperties`、`RagEmbeddingClient`、`WikiRagIndexService(+Impl)`、`WikiRagAdminController`、`RagSearchService(+Impl)`、`RagSearchResult`、`RagAskServiceImpl` 及对应单测。
- 前端：`cloud_front/src/pages/aiAssistant/AiAssistantPage.vue`（耗时面板展示）。
- 脚本与配置：`start-dev.ps1`（注入 `.env.dev`）、`.gitignore`（忽略 `.env.dev`）、`.env.dev`（新建，仅本地，不入库）。
- 数据：`wiki_chunk` 表全量重嵌入（旧行置 INVALID、插入新向量行；行数与逻辑块数不变，物理行 id 变化——评测 gold 使用逻辑坐标不受影响）。
- 运行依赖：开发机不再需要运行 Ollama；需要 MySQL(3307)、Redis(6379) 与可访问 DashScope 的网络。

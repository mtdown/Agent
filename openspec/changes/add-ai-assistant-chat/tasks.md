# add-ai-assistant-chat Tasks

## 1. 后端 LLM 接入

- [x] 1.1 RagProperties 增加 llm 配置段（base-url/api-key/model/timeout/max-tokens，key 走环境变量 RAG_LLM_API_KEY 默认空）；application.yml 同步 — 2026-09-10
- [x] 1.2 实现 RagLlmClient：OpenAI 兼容流式 chat/completions（JDK HttpClient ofLines 逐行解析 SSE，content/reasoning_content 分流回调，[DONE] 结束，超时控制）— 2026-09-10
- [x] 1.3 RagLlmClient 单测 4 个：交错 reasoning/content 解析、坏行跳过、非 200 报错、key 未配置 — 2026-09-10 全绿

## 2. 问答服务与 SSE 接口

- [x] 2.1 RagAskRequest DTO（query/spaceIds/topK）+ RagAskService/Impl：检索（复用 RagSearchService 权限硬过滤）→ 0 命中短路拒答 → 拼 prompt（资料带编号 + 防伪造文号系统提示）→ 流式生成；EventSink 抽象使编排可纯单测 — 2026-09-10
- [x] 2.2 WikiRagController 增 `POST /rag/ask` SseEmitter 端点（登录校验前置抛 BaseResponse、SSE 协议 meta/reason/delta/done/error、120s 超时、异常兜底）— 2026-09-10
- [x] 2.3 RagAskService 单测 5 个：空 query/未登录前置拒绝、0 命中不调 LLM 短路拒答、meta 先于正文且 citations 编号与 prompt 一致、LLM 错误转 error 事件、检索异常转 error 不外抛 — 2026-09-10 全绿

## 3. 前端面板

- [x] 3.1 路由 `/aiAssistant` + GlobalHeader 菜单项「AI 助手」（loginOnly，RobotOutlined）— 2026-09-10
- [x] 3.2 `api/ragController.ts`：ragAskUsingStream（fetch + ReadableStream，SSE 帧 event/data 解析）+ ragSearchUsingPost + 本地类型 — 2026-09-10
- [x] 3.3 AiAssistantPage.vue：范围多选（可见空间 + 全部授权空间快捷项 + 授权文档数 tag）、消息流气泡、流式增量渲染、深度思考折叠块、示例问题 chips — 2026-09-10
- [x] 3.4 引用渲染：`[n]` 转义后替换为可点击角标（事件委托跳文档详情）；引用区（标题+文号 tag，元数据来源）点击跳 `/documentWiki/{docId}` — 2026-09-10

## 4. 验证与收尾

- [x] 4.1 后端全量 `mvn test` **170/170**（新增 9：LLM 客户端 4 + 问答服务 5）；前端 `vue-tsc --build` 通过 — 2026-09-10
- [x] 4.2 真实冒烟：服务重启（新代码 + RAG_LLM_API_KEY 用户级环境变量已设，需新终端生效）后走 SSE 全链路（文号题/语义题/范围过滤/语料外拒答）
  - 2026-09-10 首轮：文号题 ✅（24号精确命中排前）、范围过滤 ✅（0 文档短路拒答）、语料外拒答 ✅（偶发 DeepSeek Connection reset，重试即过）；**语义题 ❌** finishReason=length —— 思考型模型 reasoning_content 与正文共用 max-tokens=2048 预算，复杂题思考阶段即耗尽，正文 0 字。
  - 修复（负责人已同意）：max-tokens 2048→8192（application.yml + RagProperties 默认值）；前端 onDone 检查 finishReason=length 显示截断提示。后端 compile + 前端 vue-tsc 通过。
  - 2026-09-10 复测：语义题 ✅（低空经济，思考 3285 事件 + 正文 1017 事件 + done(stop)，结构化分点回答带 [1] 角标，引用命中渝府发〔2026〕7号/渝府办发〔2025〕64号等相关文档）。四场景全过。
- [x] 4.3 汇报并等负责人页面手测（手测项：导航入口、范围多选、流式打字、引用跳转、拒答）
  - 2026-09-10 手测反馈 4 项：①思考时间过长 ②跳转改为双栏+引用高亮 ③缺思考耗时/token 统计 ④缺停止生成。
  - 修复（负责人已确认方案）：
    - ①「深度思考」开关：RagProperties 双模型（model=思考型 / fast-model=deepseek-chat 默认），请求 deepThinking 字段切换；新增 RagLlmClientTest.deepThinkingFlagSelectsModel。
    - ②双栏引用预览：点击角标/引用右侧 doc-panel 动画展开（flex-basis 过渡），复用 DocumentWikiContentViewer，chunkText 匹配块级元素黄色高亮 + 滚动定位，不再跳路由。
    - ③统计：done 事件带 thinkingMs/answerMs/usage（DeepSeek 流式末帧 usage 解析），前端消息底部统计行（思考/回答耗时 + tokens ↑↓ + 模式）。
    - ④停止：前端停止按钮 AbortController.abort()；后端 emitter onCompletion/onTimeout → future.cancel(true) 中断 LLM 阻塞流。
  - 验证：后端 171/171 全过（+1 模型选择测试）、前端 vue-tsc 通过。
  - 2026-09-10 手测复验：其余全过；右栏文档不能滚动 —— a-spin 内部 .ant-spin-nested-loading/.ant-spin-container 未透传高度导致 flex 链断裂，补深层样式修复。vue-tsc 通过。
- [ ] 4.4 负责人确认后上传任务分支

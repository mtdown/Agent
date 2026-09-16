# Design: add-ai-assistant-chat

## D1. 总链路

```
[AiAssistantPage.vue]
  范围多选(可见空间) → POST /rag/ask (fetch + ReadableStream SSE)
        |
[RagAskService]
  1. ragSearchService.search(user, req)   ← change 1 的权限硬过滤 + 文号精确层
  2. 无命中 → 直接流式回"未找到依据"，不调 LLM
  3. 拼 prompt(资料带编号) → RagLlmClient.streamChat(回调)
  4. SseEmitter 依次推送 meta(引用元数据) → reason(思考) → delta(正文) → done
```

## D2. SSE 事件协议（前端渲染契约）

| event | data（JSON） | 说明 |
|---|---|---|
| `meta` | `{effectiveSpaceIds, authorizedDocCount, citations:[{index, docId, docTitle, docNumber, chunkIndex, chunkHeading, chunkText}]}` | 首帧；citations 的 index 与 prompt 资料编号一致，answer 的 `[n]` 据此渲染 |
| `reason` | `{content}` | 思考增量（deepseek 思考型模型 reasoning_content），前端折叠展示 |
| `delta` | `{content}` | 回答正文增量 |
| `done` | `{finishReason, thinkingMs, answerMs, usage:{promptTokens, completionTokens}\|null}` | 结束；thinkingMs=LLM 开始到首个正文 delta（思考耗时），answerMs=正文流式耗时，usage 来自 DeepSeek 流式末帧 |
| `error` | `{message}` | 任一环节失败；LLM 未配置/检索空结果也走可读 error 或直接 delta 文案 |

- SseEmitter 超时 120s（思考型模型首 token 慢）
- 前端用 `fetch` + `ReadableStream`（axios 不支持流式读），`credentials: 'include'` 携带登录态
- **可中断**：ask() 持有 CompletableFuture，emitter onCompletion/onTimeout 回调 `cancel(true)` → HttpClient 阻塞线程收到 interrupt 抛 InterruptedException → 流终止。前端"停止"按钮 abort fetch 即触发该链路。

## D2.1 双模型（深度思考开关）

- `rag.llm.model`（思考型 deepseek-v4-flash-vision-exp）/ `rag.llm.fast-model`（deepseek-chat，默认）
- 请求体 `deepThinking: true` → 思考型；缺省/false → 快速模型（`RagProperties.Llm.resolveModel`）
- 前端 scope-bar a-switch「深度思考」默认关闭，统计行标注当次使用的模式，便于对比

## D2.2 引用预览双栏布局

- 点击角标/引用条目不再跳转路由，改为右侧 `doc-panel` 展开（flex-basis 0→46% + opacity 过渡，页面 max-width 960→1400 同步过渡）
- 文档内容经 `getDocumentWikiVisByIdUsingGet` 加载，复用 `DocumentWikiContentViewer` 渲染
- 高亮：chunkText 剥离 front-matter 后取首个 ≥12 字正文行，在渲染后 DOM 的块级元素 textContent 中匹配（前 24 字），命中加 `.chunk-highlight`（黄色底）并 `scrollIntoView(center)`；未命中则仅打开文档

## D3. Prompt 设计（防伪造文号的核心）

```
system: 你是政策问答助手。只能依据提供的资料回答：
1) 资料不足以回答时，明确说明"根据现有资料未能找到相关依据"，不得编造
2) 引用资料时在句末标注角标[n]，n 为资料编号，可多条如[1][3]
3) 不得自行编造文号；文号只能来自资料标注
user:
问题：{query}

资料：
[1] 《{docTitle}》（{docNumber 或 "无文号"}）
{chunkText}
---
[2] ...
```

- 角标渲染在前端：`[n]` 正则替换为可点击 `<sup>`，点击滚动到引用区/跳详情页；**LLM 输出的文号一律不直接信任**（引用区文号来自 meta.citations 元数据）
- topK 沿用检索默认 6；检索 0 命中时短路返回固定文案（省 LLM 调用）

## D4. LLM 客户端（RagLlmClient）

- 与 RagEmbeddingClient 同模式：JDK HttpClient 零新依赖，OpenAI 兼容 `/chat/completions`（stream=true）
- `rag.llm` 配置段：base-url（默认 https://api.deepseek.com）、api-key（env `RAG_LLM_API_KEY`）、model（默认 deepseek-v4-flash-vision-exp）、timeout-seconds、max-tokens
- 流式解析：`BodyHandlers.ofLines()` 逐行读，`data: {...}` 前缀解析，delta 里 `content` 与 `reasoning_content` 分别回调；`[DONE]` 结束
- 复用 `RagProperties` 增加 LLM 内部类；isConfigured() 判 api-key 非空（云端专用，无本地模式——本地 LLM 留扩展点不实现）

## D5. 前端结构

| 文件 | 改动 |
|---|---|
| `GlobalHeader.vue` | originItems 增 `{key:'/aiAssistant', label:'AI 助手', loginOnly}`（RobotOutlined） |
| `router/index.ts` | 增 `/aiAssistant` → `pages/aiAssistant/AiAssistantPage.vue` |
| `api/ragController.ts` | 新建：`askRagUsingStream`（fetch 流式）+ 类型定义 |
| `AiAssistantPage.vue` | 新建：范围多选（listVisibleSpaceUsingGet）+ 消息流 + 引用区 + [n] 渲染 |

- 引用跳转：`router.push('/documentWiki/'+docId)`（详情页复用现有路由）；`chunkIndex` 作 query 透传，详情页本轮不实现锚点定位（扩展点，见 Non-goals 边界——跳到文档即满足验收"可点击跳原文"）
- 思考过程（reason 事件）渲染为灰色折叠块"深度思考"，默认收起

## D6. 失败降级

| 场景 | 行为 |
|---|---|
| LLM key 未配置 | meta 推送后 error 事件提示"LLM 未配置（RAG_LLM_API_KEY）"，HTTP 仍 200（SSE 已建立） |
| embedding 失败 | 同上，error 提示 embedding 不可用 |
| 检索 0 命中 | 不调 LLM，delta 直接推"根据现有资料未能找到相关依据"，done 结束 |
| LLM 中途断流 | error 事件 + emitter.complete |
| 未登录 / 空参数 | 标准 BaseResponse 40100/40000（进入 SSE 前拦截） |

## D7. 风险

- 思考型模型首 token 延迟数秒 → 前端"思考中"动画兜底体验；SseEmitter 120s 超时
- DeepSeek 流式 chunk 的 reasoning_content 与 content 交错 → 客户端按字段分发互不干扰
- 长答案 token 上限 → max-tokens 配置默认 2048

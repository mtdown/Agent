# Proposal: add-ai-assistant-chat

## Why

change 1（add-wiki-rag-pipeline，已合并 main）完成了 RAG 索引管道与权限硬过滤检索，但能力只暴露为裸接口——用户在产品内看不到任何 AI 问答入口。验收演示要求"问政策 → 给出依据文号的回答 → 讲清检索范围按权限过滤"，必须有一个用户可见的对话界面承接。

## What Changes

- 顶部导航新增「AI 助手」入口（登录可见），新增 `/aiAssistant` 对话面板页
- 面板支持**检索范围多选**：checkbox 勾选用户可见空间 + "全部授权空间"快捷项，显示"本次检索 N 篇授权文档"
- 后端新增 SSE 流式问答接口 `POST /rag/ask`：复用 RagSearchService（权限硬过滤 + 文号精确匹配层）→ 拼 prompt → LLM 流式生成
- LLM 接入：OpenAI 兼容客户端（默认 DeepSeek `deepseek-v4-flash-vision-exp`，api-key 走环境变量 `RAG_LLM_API_KEY`）
- 引用机制：LLM 只输出 `[1][2]` 角标，**文号/标题/跳转由 chunk 元数据渲染**（防 LLM 伪造文号）；点击引用跳文档详情页
- 模型为思考型（reasoning_content 先行流式输出），前端展示"深度思考"折叠块

## Capabilities

- **New**: `ai-assistant-chat` —— AI 助手对话面板（导航入口、范围选择、流式问答、引用渲染与跳转）

## Non-goals

- 对话历史持久化（刷新即丢，v2 再议）
- 多轮对话上下文（单轮问答）
- rerank / 混合检索调优（文号精确匹配层已在 change 1 落地）
- 开放 API / API Key 机制（change 3 范围）

## Acceptance Criteria

- 演示脚本成立：问"渝府办发〔2026〕24号说了什么" → 流式回答带文号引用，点击引用跳到《重庆市加快场景培育和开放推动新场景大规模应用行动方案》详情页
- 勾选/取消空间即改变检索范围，面板显示有效空间数与授权文档数；无权限空间不出现在可选项
- 语料外问题明确回答"未能找到依据"，不编造文号
- LLM key 未配置时接口返回明确错误提示，前端显示可读信息，不影响其他功能

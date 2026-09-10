# add-wiki-rag-pipeline Proposal

## Why

云策库的第一步（Wiki 知识库）已上线，团队空间「政策文档」已沉淀 216 篇重庆政务语料。第二步 RAG 检索增强需要一个地基：让每篇 Markdown 文档变成"可按语义检索的切片"，并为后续 AI 问答（change 2）与开放 API（change 3）提供统一的、带权限过滤的检索服务。现在文档只能靠关键词搜索，无法支撑"问政策 → 给出依据文号的回答"这一目标。

## What Changes

- 新增 `wiki_chunk` 表：存储文档切片、文号（docNumber 正则提取）、标题路径、contentVersion、embedding 向量、ACTIVE/INVALID 状态
- 新增 Markdown 结构化切片器：按标题层级优先切分，超长块二分、过短块合并、相邻块 overlap
- 新增 OpenAI 兼容 Embedding 客户端（默认 DashScope，base-url 可配置）
- 新增 `VectorStore` 检索抽象：本期实现 MySQL 存向量 + 内存余弦相似度检索，预留 Elasticsearch 混合检索扩展点
- 新增索引挂钩：重写 `DocumentWikiServiceImpl` 的 save/updateById/logicalDelete/restore/permanentDelete，事务提交后（AFTER_COMMIT）异步建索引/失效索引，覆盖全部 5 个文档保存入口，上传接口零改动
- 新增存量回填接口（管理端手动触发，按 contentHash 幂等）
- 新增每日对账任务：自动失效"文档已删/在回收站但仍 ACTIVE"的孤儿 chunk
- 新增权限过滤的检索 Service（spaceId ∈ 用户可见空间集合 ∩ 指定范围），供 change 2/3 复用
- 新增切片查询接口（按文档列出其 chunk 及状态），供 change 2 的详情页切片预览面板使用

## Capabilities

### New Capabilities

- `wiki-rag-pipeline`: Wiki 文档的切片、向量化、索引生命周期管理与按权限过滤的语义检索
- `wiki-rag-admin`: 存量回填、对账兜底等管理端 RAG 运维能力

### Modified Capabilities

（无——本期不改既有 spec 的需求行为；文档保存/删除/回收站的对外行为不变，仅在内部挂索引事件）

## Impact

- **后端 `cloud/`**：新增 wiki_chunk 实体/Mapper/Service、切片器、Embedding 客户端、VectorStore 接口及 MySQL 实现、索引事件监听、回填与对账接口、检索 Service；修改 `DocumentWikiServiceImpl`（事件发布）
- **数据库**：新建 `wiki_chunk` 表（含 embedding BLOB 列与 spaceId/docId/contentVersion 索引）；不修改既有表结构（contentHash/contentVersion 预留字段直接启用）
- **配置**：`application.yml` 新增 LLM/embedding base-url、api-key、model、top-K 等配置项（api-key 走环境变量）
- **前端 `cloud_front/`**：无改动（切片预览 UI 属 change 2）
- **风险**：embedding 调用依赖外部 API（key 未配置时索引降级跳过并记录，不阻塞文档保存）；初次回填 216 篇 × 切片数的 embedding 调用量需控制并发

## Non-goals

- 不做 AI 对话面板、SSE 流式问答、引用渲染跳转（change 2 `add-ai-assistant-chat`）
- 不做开放 API / API Key（change 3 `add-open-rag-api`）
- 不做 BM25/ES 混合检索、rerank（仅留 VectorStore 扩展点）
- 不做 PDF/docx 解析（语料仅 MD）
- 不做图片/附件（wiki_attachment）的向量化
- 不做多轮对话记忆与对话历史入库

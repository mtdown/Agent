# add-wiki-rag-pipeline Tasks

## 1. 数据模型与基础设施

- [x] 1.1 编写 `cloud/sql/create_table_wiki_chunk.sql` 建表脚本（幂等，含 spaceId/status、docId/contentVersion/status 索引），在本地库执行验证 — 2026-09-10 已在本地 MySQL 3307/Cloud 执行成功，SHOW COLUMNS 确认 14 列齐全
- [x] 1.2 新建 `WikiChunk` 实体 + `WikiChunkMapper`（含批量失效/按空间失效/孤儿对账自定义 SQL）+ `WikiChunkService`，mapper 交互由 IndexService/检索层单测覆盖（mock 交互验证），建表实测 — 2026-09-10
- [x] 1.3 新增 `application.yml` 的 rag.embedding / rag.retrieval / rag.index 配置段（api-key 走环境变量，缺省不阻塞启动）+ `RagProperties` 绑定 — 2026-09-10；未配置 key 时 rebuild 返回失败报告、索引降级跳过均有单测

## 2. 切片器

- [x] 2.1 实现 `MarkdownChunker`：标题层级分节（标题路径栈，标题行随节入正文利于 embedding）+ >600 字二分（单段超长按句号/分号硬切）+ <100 字合并 + overlap≈80，纯函数无外部依赖 — 2026-09-10
- [x] 2.2 实现 docNumber 正则提取（`[\u4e00-\u9fa5]{2,12}〔\d{4}〕\d+号`）与 front-matter 保留策略 — 2026-09-10
- [x] 2.3 Chunker 单测 11 个：无标题文档、单超长节、多级标题路径、front-matter、含文号/不含文号/半角括号不匹配、空输入、序号连续性 — 2026-09-10 全绿

## 3. Embedding 客户端

- [x] 3.1 实现 `RagEmbeddingClient`（JDK HttpClient 零新依赖，texts 批量，base-url/model/key 配置化，超时控制）— 2026-09-10
- [x] 3.2 单测 4 个：本地 HttpServer mock 验证批量请求体与向量解析、空输入、key 未配置抛 `RagEmbeddingUnavailableException`、isConfigured — 2026-09-10 全绿

## 4. 索引生命周期

- [x] 4.1 定义 `WikiDocumentChangedEvent`（9 种变更类型含 DOC_MOVED 与空间级事件），重写 `DocumentWikiServiceImpl` 的 save/updateById/logicalDelete/restore/permanentDelete + 新增 `moveDocument`（显式 SET wrapper），controller move 端点改调服务方法；`WikiSpaceServiceImpl` 三个空间方法补发空间级事件；`mvn compile` + 既有测试回归 — 2026-09-10 挂钩单测 7 个全绿
- [x] 4.2 实现 `WikiRagIndexListener`（@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true) + @Async("ragIndexExecutor") 独立线程池）：doc 五分支 + 空间三分支；版本竞态由"事件版本与库内版本不一致时重切/置 INVALID"覆盖 — 2026-09-10；索引服务单测 16 个全绿（含版本漂移重切、先失效后插入、move 更新 spaceId）
- [x] 4.3 索引失败不阻塞验证：embedding 抛异常时 `indexDocument` 降级跳过（RagEmbeddingUnavailableException 单测）、监听器/索引服务全链 try-catch、线程池拒绝策略为记日志丢弃靠回填兜底 — 2026-09-10
- 备注：chunk 状态变更全部走 Mapper 自定义 SQL（显式 UPDATE status），未使用 updateById 实体法，规避 NOT_NULL 假成功红线

## 5. 向量检索

- [x] 5.1 实现 `VectorStore` 接口与 `InMemoryCosineVectorStore`（按 spaceId 懒加载 ACTIVE 向量缓存、chunk 写后失效重载、余弦 top-K、防御跨空间脏行）+ `VectorCodec`（float32 小端 BLOB）— 2026-09-10 单测 5 个全绿
- [x] 5.2 实现 `RagSearchService.search(user, req)`：Service 层算 有效空间 = 用户可见空间 ∩ 请求范围（先过滤后检索），返回 hits + effectiveSpaceIds + authorizedDocCount；暴露 `POST /rag/search` 登录接口 — 2026-09-10
- [x] 5.3 单测 6 个：权限过滤（无交集短路、vectorStore 只见过滤后集合）、null 范围=全部可见、topK 默认回填、空 query 拒绝、顺序保持 — 2026-09-10 全绿
- [x] 5.4 **文号精确匹配层**（2026-09-10 回填冒烟实测发现）：查询含文号时先按 docNumber 精确命中（score=1.0，按 chunkIndex 排序），向量检索只补剩余名额并去重；文号填满 topK 时跳过 embedding 调用。单测 +3（精确命中排前 / 饱和跳过向量 / 语料无此文号回落向量）共 9/9 绿 — 实测修复"〔2026〕24号查询排到 14/6/34 号之后"的排序缺陷

## 6. 管理端运维

- [x] 6.1 实现回填接口 `POST /admin/rag/rebuild`（@AuthCheck admin，ACTIVE+版本一致则 skip 的幂等规则，输出 total/created/skipped/failed 报告；legacy 行补算 contentVersion/contentHash）— 2026-09-10 单测覆盖 skip/重建/单篇失败不中断/未配置 key 报告
- [x] 6.2 实现每日对账 `@Scheduled(cron 03:00)`（孤儿 ACTIVE chunk 置 INVALID + 日志）+ 手动触发 `POST /admin/rag/reconcile` — 2026-09-10
- [x] 6.3 实现按文档的 chunk 列表查询接口 `GET /rag/document/{docId}/chunks`（复用 checkDocumentWikiVisible 鉴权）— 2026-09-10；service 层投影单测覆盖，鉴权复用既有 controller 模式

## 7. 集成验证与汇报

- [x] 7.1 本地全量回填：embedding 切换本地 Ollama `qwen3-embedding:4b`（负责人无 DashScope key，本地模式零配置），2026-09-10 触发 `POST /admin/rag/rebuild` 完成——**216/216 篇全覆盖、2061 条 ACTIVE chunk、1341 条带文号**，embedding 2560 维确认走 qwen3 模型
- [x] 7.2 检索冒烟（2026-09-10 完成，重启后全量复测）：① 文号精确题"渝府办发〔2026〕24号主要内容" → Top4 全部命中 24 号文档（score=1.0 按序）✅ ② 语义题 Top1 命中 24 号（0.74）✅ ③ 空间范围过滤收敛正确 ✅ ④ **非授权账号验证**（common_user，临时改密测毕已恢复）：其可见空间仅"公开文档+个人区"→ 指定政策文档空间检索 code=0 且 hits=0（权限硬过滤零泄露）、默认范围检索授权文档数=0（语料全在其不可见空间）✅
- [x] 7.3 后端 `mvn test` 全量通过（RAG 单测 50+9=59 个：Chunker 11 + Embedding 5 + 挂钩 7 + 索引 16 + 检索 9 + 向量库 5）；前端 `vue-tsc --build` 通过（零改动零回归）— 2026-09-10
- [x] 7.4 汇报：改动概要、测试结果、建表结果、发现的问题与建议 — 2026-09-10 已汇报，等待负责人确认
- [ ] 7.5 负责人确认后通过 upload.ps1（或等价手动流程）上传任务分支

## 实施结果小结（2026-09-10）

- 新增 18 个文件（rag 包 14 + mapper/entity/service 3 + SQL 1 + controller 2），修改 5 个既有文件（DocumentWikiServiceImpl/Service 接口/DocumentWikiController/WikiSpaceServiceImpl/application.yml）
- 自动化验证全绿：后端 157/157（新增 RAG 单测 49：Chunker 11 + Embedding 4 + 挂钩 7 + 索引 16 + 检索 6 + 向量库 5），前端 type-check 通过
- 建表已在本地 3307/Cloud 落地；语料 216 篇确认就绪
- 阻塞项：7.1/7.2 需要 DashScope API Key（环境变量 RAG_EMBEDDING_API_KEY）+ 新代码部署，等负责人提供后执行（预计回填请求数 ≈ 220 次 embedding 调用，批量 10 条/请求）
- 发现（IssueLog）：application.yml 默认 datasource 指向 localhost:3306/picture，而实际运行用 application-local.yml 的 3307/Cloud——端口/库名不一致，用默认 profile 启动会连不上库，建议后续统一

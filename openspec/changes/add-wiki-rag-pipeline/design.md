# add-wiki-rag-pipeline Design

## Context

团队空间「政策文档」已有 216 篇重庆政务 MD 语料（四栏目），`document_wiki` 表已预留 contentHash / contentVersion / visibility / metadataJson 字段。文档保存入口共 5 个（单个导入、URL 导入、手动创建 ×2、编辑 ×2、批量文件、批量 URL），全部汇到 `DocumentWikiService.save/updateById`，删除走自有方法 `logicalDelete/restore/permanentDelete`。完整技术方案见桌面《云策库AI问答模块RAG技术方案.md》。

## Goals / Non-Goals

Goals:
- 一处挂钩覆盖全部文档写入入口，未来新增入口自动被覆盖
- 检索服务内置权限过滤，供 change 2（对话面板）与 change 3（开放 API）直接复用
- 索引故障绝不阻塞文档增删改

Non-Goals（设计级）:
- 不引入任何新的外部服务（ES/Redis Stack/Milvus），16G 开发机零额外负担
- 不做向量维度变更的在线迁移（embedding 换模型 → 走回填接口全量重建）
- 不做附件（wiki_attachment）向量化

## Decisions

### D1. 索引挂钩：Service 层收口 + Spring 事件 + AFTER_COMMIT 异步

重写 `DocumentWikiServiceImpl.save/updateById/logicalDelete/restore/permanentDelete`，成功后发布 `WikiDocumentChangedEvent`（携带 docId、变更类型、contentVersion），由 `WikiRagIndexListener` 以 `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` 消费。

- 为什么不在 Controller/上传接口挂钩：5 个入口会漏，将来加入口还会再漏
- 为什么 AFTER_COMMIT：避免"索引建好了、文档事务回滚了"的孤儿数据
- 为什么异步：embedding 是外部 HTTP 调用（百 ms 级），同步会拖慢上传接口
- 线程池：独立小线程池（如 core=2, max=4, queue 有界），与业务线程池隔离；队列满/拒绝时记录日志留给回填兜底

### D2. 数据模型：单表 `wiki_chunk`，向量存 BLOB（float32 数组）

```
wiki_chunk
  id (ASSIGN_ID 雪花)
  docId            -> document_wiki.id（无外键约束，逻辑关联）
  spaceId          冗余，权限过滤与批量失效的主口
  contentVersion   对应文档版本，失效判断
  chunkIndex       文档内序号，跳转定位用
  chunkHeading     标题路径
  chunkText        切片原文
  docTitle         冗余，检索结果免联表
  docNumber        文号（正则提取，可空）
  embedding        BLOB（float[] 小端序列化；DashScope text-embedding-v3 为 1024 维 ≈ 4KB/chunk）
  status           ACTIVE / INVALID
  isDelete/createTime/updateTime
索引：(spaceId, status)、(docId, contentVersion, status)
```

- 为什么不用 JSON 存向量：BLOB 省一半以上空间，反序列化快；JSON 仅在调试时人工查看才方便
- 为什么 docTitle/docNumber 冗余：检索高频路径免 JOIN；更新频度极低（文档标题编辑时同步刷新该 doc 的 chunk 冗余字段）

### D3. 切片：标题层级优先，长度兜底

1. 按行扫描 markdown，遇 `#{1,6}` 标题分节，记录标题路径栈
2. 单节 > 600 字 → 按空行分段二分；单节 < 100 字 → 并入相邻节
3. 相邻 chunk overlap ≈ 80 字（从上一块尾部回捞整段）
4. YAML front-matter（语料 content.md 开头的 title/fileNum 元数据块）随首块入库——文号正则可命中，利于检索
- 替代方案：语义模型切片（质量高但引入额外模型调用与不可控延迟）→ 否决，演示规模收益不抵成本

### D4. 检索：VectorStore 抽象 + MySQL 内存余弦实现

```
interface VectorStore { search(float[] query, Set<Long> spaceIds, int topK): List<ChunkHit> }
```
- 本期实现 `InMemoryCosineVectorStore`：启动/回填后按 (spaceId, status=ACTIVE) 加载 chunk 向量到内存（216 篇 × 约 8-15 chunk ≈ 2000-3000 条 × 4KB ≈ 12MB，可接受），查询时逐条余弦
- 权限过滤在 Service 层先算 `有效 spaceId 集合 = 用户可见空间 ∩ 请求范围`，再传给 VectorStore —— **过滤不进入相似度计算，先过滤后检索**
- 为什么不做 MySQL 向量 SQL 函数：5.7/8.0 无原生向量类型，SQL 内算余弦性能差且难移植；内存方案在万级 chunk 内毫秒返回
- ES 扩展点：change 未来如需 BM25+KNN，新实现 `EsVectorStore` 替换 Bean 即可，业务层零改动

### D5. Embedding 客户端：OpenAI 兼容，配置化

```
rag:
  embedding:
    # 默认本地 Ollama（OpenAI 兼容端点，无需 api-key）；云端切换：环境变量覆盖三项即可
    base-url: ${RAG_EMBEDDING_BASE_URL:http://localhost:11434/v1}
    api-key:  ${RAG_EMBEDDING_API_KEY:}
    model:    ${RAG_EMBEDDING_MODEL:qwen3-embedding:4b}
  retrieval:
    top-k: 6
  index:
    batch-embed-size: 10   # 批量向量化，一次请求带多个文本
```
- key 未配置/调用失败：索引流程记日志跳过，文档保存不受影响（spec 已锁场景）；回填接口报告失败明细
- 批量 embedding：DashScope/Ollama 均支持 texts 数组，回填 216 篇时把请求数从 ~2000 降到 ~220
- **配置判定（2026-09-10 更新）**：`isConfigured()` = api-key 非空 **或** base-url 指向 localhost/127.0.0.1（本地
  Ollama 无需 key）；本地模式不发送 Authorization 头
- **模型选型（2026-09-10 实测定稿）**：本地默认 `qwen3-embedding:4b`（2560 维，中文强）。实测对比
  nomic-embed-text（768 维，英文为主）：无关 chunk 得分 0.40 vs 0.58、区分度 0.34 vs 0.18——中文政策语料
  必须用中文优化的模型。换 embedding 模型 = 向量空间不同 → 必须清空 wiki_chunk 全量回填（D2 已声明不做在线迁移）

### D6. 生命周期联动的具体落点

| 事件 | 监听动作 |
|---|---|
| CREATED | 切片 → 批量 embedding → insert chunk (ACTIVE) |
| UPDATED | `UPDATE wiki_chunk SET status=INVALID WHERE docId=? AND contentVersion<新版本`（含标题冗余字段刷新 ACTIVE 行）→ 新切片入库 |
| LOGICAL_DELETED | docId 全部置 INVALID |
| RESTORED | 版本未变 → 置回 ACTIVE；变了 → 重切 |
| PERMANENT_DELETED | `DELETE FROM wiki_chunk WHERE docId=?` |
| SPACE_DELETED | `UPDATE wiki_chunk SET status=INVALID WHERE spaceId=?`（由空间删除服务发事件，或对账兜底覆盖） |

**注意红线**：MyBatis-Plus `updateById` 默认 NOT_NULL 策略跳过 null 字段——置 INVALID 等更新一律用 `LambdaUpdateWrapper` 显式 `.set()`，不踩"假成功"坑（见 MEMORY 2026-09-09 记录）。

### D7. 回填与对账

- 回填 `POST /admin/rag/rebuild`（@AuthCheck admin）：分页扫描 isDelete=0 且 contentFormat=markdown 的文档 → 有 ACTIVE chunk 且 contentHash 一致 → skip；否则重建。输出 {total, created, skipped, failed[]}
- 对账：`@Scheduled(cron 每日 03:00)`，SQL 反查 ACTIVE chunk 中 docId 不在有效文档集合的行 → 置 INVALID，记日志
- 内存向量缓存失效策略：chunk 表任何写操作后按 spaceId 维度重载该空间的 ACTIVE 向量（粗粒度，实现简单；文档量级内可接受）

### D8. 检索服务签名（change 2/3 复用契约）

```
RagSearchResult search(User user, RagSearchRequest req)
  req: { query, spaceIds(可空=全部可见), topK }
  返回: { hits: [{chunkText, docId, docTitle, docNumber, chunkHeading, chunkIndex, score}],
          effectiveSpaceIds, authorizedDocCount }
```
`authorizedDocCount` 来自 chunk 表按有效空间数 DISTINCT docId，支撑前端"本次检索范围：X 空间 / N 篇"。

## Risks / Trade-offs

- **内存向量随语料线性增长**：万级 chunk（≈40MB）后需换 ES/磁盘索引——已在 D4 留扩展点，Non-goal 明示
- **embedding 供应商限流**：回填 200+ 请求集中在数分钟内，DashScope 有 QPS 限制——批量化（D5）+ 失败可重跑（D7 幂等）双保险
- **语料 front-matter 进切片**：首块含 YAML 元数据文本，对检索是噪声也是信号（文号可命中）——保留，观察评测结果再定
- **并发编辑竞态**：同一文档连续两次编辑，第二次事件可能先完成索引 → 以 contentVersion 比较，监听器只索引"事件版本 ≥ 库内最大版本"的 chunk，旧版本事件的产物直接置 INVALID

## Migration Plan

- 新建 `wiki_chunk` 表（SQL 脚本进 `cloud/sql/`，幂等 CREATE TABLE IF NOT EXISTS）
- 不修改任何既有表；contentHash 由服务端在保存时计算填充（既有行 contentHash 为空 → 回填时补算）
- 上线顺序：部署 → 跑回填 → 校验报告 created+skipped = 文档总数
- 回滚：删除 wiki_chunk 表 + 回滚代码即可，文档数据零影响

## Open Questions

- 回填接口是否要给非 admin 的空间管理员开放（本期定 admin-only，change 3 再议）
- 对账任务与回填接口的调度入口是否暴露到管理面板 UI（属 change 2/3 范围）

# Design: KnowledgeItem 详细设计

## 1. 数据模型

### 1.1 KnowledgeItem（条目主表）

```sql
CREATE TABLE knowledge_item (
  id            BIGINT       NOT NULL PRIMARY KEY,             -- 雪花 id
  space_id      BIGINT       NOT NULL,                         -- 所属 wiki space
  folder_id     BIGINT       NULL,                             -- 所属文件夹（null = 空间根）
  user_id       BIGINT       NOT NULL,                         -- 创建者
  type          VARCHAR(16)  NOT NULL,                         -- PICTURE / DOCUMENT / MIXED
  title         VARCHAR(255) NOT NULL,
  summary       VARCHAR(512) NULL,                             -- 列表摘要（从 block 自动生成，可覆盖）
  cover_pic_id  BIGINT       NULL,                             -- 列表缩略图
  tags          VARCHAR(512) NULL,                             -- JSON 数组
  view_count    BIGINT       DEFAULT 0,

  -- ===== 以下为「知识库连接 / RAG」预留字段，当前全部留空，不影响现有功能 =====
  -- 组 1 内容结构化
  content_format VARCHAR(16)  DEFAULT 'plain',                 -- plain / markdown / html / blocks
  content_hash   VARCHAR(64)  NULL,                            -- 内容指纹，避免无谓重新向量化
  content_version INT         DEFAULT 1,                       -- 内容版本，每次编辑 +1

  -- 组 2 来源溯源
  source_type   VARCHAR(16)  DEFAULT 'NATIVE',                 -- NATIVE / UPLOAD / IMPORT / URL / API / ADAPT
  source_url    VARCHAR(1024) NULL,                            -- 外部来源地址
  source_ref    VARCHAR(128) NULL,                             -- 外部系统文档 id（或旧表迁移 id）

  -- 组 3 文件附件
  file_url      VARCHAR(1024) NULL,                            -- 原始文件对象存储地址
  file_type     VARCHAR(64)  NULL,                             -- MIME 类型
  file_size     BIGINT       NULL,                             -- 字节数
  file_hash     VARCHAR(64)  NULL,                             -- 文件指纹，防重复解析

  -- 组 4 RAG 向量索引（核心预留）
  index_status    VARCHAR(16) DEFAULT 'PENDING',               -- PENDING/INDEXING/SUCCESS/FAILED/SKIPPED
  index_time      DATETIME    NULL,                            -- 最近一次成功索引时间
  index_version   INT         DEFAULT 0,                       -- 已索引的内容版本（与 content_version 对比判断过期）
  index_error     VARCHAR(512) NULL,                           -- 最近失败原因
  embedding_model VARCHAR(64) NULL,                            -- embedding 模型标识（换模型需重新索引）
  embedding_dim   INT         NULL,                            -- 向量维度
  chunk_count     INT         DEFAULT 0,                       -- 切片数量

  -- 组 5 外部知识库对接
  external_kb_id  VARCHAR(128) NULL,                           -- 外部知识库 id
  external_doc_id VARCHAR(128) NULL,                           -- 外部知识库中的文档 id
  sync_status     VARCHAR(16) DEFAULT 'NONE',                  -- NONE/SYNCING/SYNCED/FAILED
  sync_time       DATETIME    NULL,

  -- 组 6 可见性与兜底扩展
  visibility      VARCHAR(16) DEFAULT 'SPACE',                 -- PRIVATE / SPACE / PUBLIC（RAG 检索必须按此过滤）
  metadata_json   VARCHAR(2048) NULL,                          -- 万能兜底，避免频繁 ALTER TABLE
  -- ===== 预留字段结束 =====

  edit_time     DATETIME     NOT NULL,
  create_time   DATETIME     NOT NULL,
  update_time   DATETIME     NOT NULL,
  is_delete     TINYINT      DEFAULT 0,
  INDEX idx_space_folder (space_id, folder_id, is_delete, edit_time),
  INDEX idx_user (user_id, is_delete),
  INDEX idx_source_ref (source_type, source_ref),
  INDEX idx_index_status (index_status, index_version)
);
```

> 预留字段的完整设计理由见同目录 `rag-extension-fields.md`。核心结论：**现在加可空字段成本几乎为零，将来接知识库只写业务代码、不改表结构。**

`type` 由后台根据 block 组成自动派生（纯图 = PICTURE；纯文本 = DOCUMENT；混合 = MIXED），保存时回写。

### 1.2 KnowledgeBlock（有序内容块表）

```sql
CREATE TABLE knowledge_block (
  id            BIGINT       NOT NULL PRIMARY KEY,
  item_id       BIGINT       NOT NULL,
  block_type    VARCHAR(16)  NOT NULL,                         -- TEXT / HEADING / IMAGE / CODE / TABLE / QUOTE / LIST / DIVIDER / FILE
  sort_order    INT NOT NULL,                                  -- 块在条目内的顺序
  content       LONGTEXT     NULL,                             -- 文本 / 代码 / 引用正文
  ref_picture_id BIGINT       NULL,                            -- 当 block_type=IMAGE 时引用 picture.id
  ref_file_url  VARCHAR(512) NULL,                             -- 当 block_type=FILE 时存放对象存储 key
  meta_json     VARCHAR(2048) NULL,                            -- caption / language / align / listStyle

  -- 知识库连接预留（block 本身即可作为一个切片）
  content_hash  VARCHAR(64)  NULL,                             -- 块内容指纹，实现「只重新向量化变动的块」
  index_version INT          DEFAULT 0,                        -- 已索引的块版本
  token_count   INT          NULL,                             -- 估算 token 数，用于切片与计费

  edit_time     DATETIME     NOT NULL,
  create_time   DATETIME     NOT NULL,
  update_time   DATETIME     NOT NULL,
  is_delete     TINYINT      DEFAULT 0,
  INDEX idx_item (item_id, is_delete, sort_order)
);
```

**为什么 block 也要 `content_hash`**：一篇长文改了一个段落，不该把整篇重新向量化。按 block 粒度比对 hash，只重新处理变动的块——这是 RAG 增量索引的标准做法，能显著降低 embedding 调用成本。

设计要点：**每条 block 是一行**，而不是把整个正文塞进 `content` LONGTEXT。这样图片块也能复用同一张表的权限、缓存和事务，**也避免了 `longtext` 大字段的读放大**——这是面试经常追问的点。

### 1.3 Picture 降级为"图片资源"

`picture` 表保持不变，**仅在以下两点做收口**：

- `PictureController` 上加一个 `POST /picture/promote`：把一张图提升为 `KnowledgeItem`（默认 type=PICTURE，自动生成 1 个 IMAGE block）。
- 新增 `Picture.getOwnedItemId()` 反向索引能力（不强约束，写到 `KnowledgeBlock.ref_picture_id` 即可追溯）。

## 2. 后端模块

```
com.et.cloud
  ├─ model.entity.KnowledgeItem
  ├─ model.entity.KnowledgeBlock
  ├─ model.vis.KnowledgeItemVis        # 详情返回（items + blocks 一次拿）
  ├─ model.enums.KnowledgeType         # PICTURE / DOCUMENT / MIXED
  ├─ model.enums.BlockType             # TEXT / HEADING / IMAGE / ...
  ├─ dto.knowledgeItem.KnowledgeItemAddRequest
  ├─ dto.knowledgeItem.KnowledgeItemEditRequest
  ├─ dto.knowledgeItem.KnowledgeItemQueryRequest
  ├─ dto.knowledgeItem.BlockUpsertRequest
  ├─ mapper.KnowledgeItemMapper
  ├─ mapper.KnowledgeBlockMapper
  ├─ service.KnowledgeItemService
  ├─ service.KnowledgeBlockService
  ├─ service.adapter.DocumentWikiAdapter       # 老 DocumentWiki -> KnowledgeItem 适配
  ├─ service.impl.KnowledgeItemServiceImpl
  ├─ service.impl.KnowledgeBlockServiceImpl
  ├─ service.impl.adapter.DocumentWikiAdapterImpl
  ├─ controller.KnowledgeItemController
  └─ manager.cache.KnowledgeCacheManager        # 条目粒度缓存，淘汰老 wikiCacheManager
```

### 2.1 关键设计：BlockRenderer（策略模式）

```java
public interface BlockRenderStrategy {
    BlockType   supportType();
    String      render(KnowledgeBlock block, RenderContext ctx);  // 返回 Markdown / JSON AST
}
```

实现：`TextBlockRenderer`、`HeadingBlockRenderer`、`ImageBlockRenderer`、`CodeBlockRenderer`、`TableBlockRenderer`、`QuoteBlockRenderer`、`ListBlockRenderer`、`FileBlockRenderer`。

前端**不需要实现策略**——后端把所有 block 渲染成统一 AST，前端只渲染 AST，是面试时显式强调"前后端渲染边界"的点。

### 2.2 事务与一致性

- `KnowledgeItem` 与 `KnowledgeBlock` 在同一个 `Transactional` 方法里写入（`add` / `edit`），`KnowledgeItem.type` 由 block 集合回算后再回填。
- 删除走"软删除"：先标记 `KnowledgeItem.is_delete=1`，再批量标记关联 block。

### 2.3 缓存策略

- Key 形如：`agentWiki:item:vis:{spaceId}:{itemId}`。
- 失效：`save/edit/delete block` 时按 item 维度失效（不直接 DEL key，用 Redis `DEL` + 抖动 5 分钟 TTL，规避雪崩）。
- 这条比"图片/文档各自一套缓存"更收敛，也是面试里"为什么不要给每条 block 单独做缓存"的回答依据。

## 3. 前端模块

```
cloud_front/src
  ├─ pages/knowledge
  │   ├─ KnowledgeListPage.vue          # 统一列表，按 type 过滤
  │   ├─ KnowledgeDetailPage.vue        # 三栏：左目录 / 中正文 / 右操作
  │   ├─ KnowledgeEditPage.vue          # 块编辑器
  │   └─ components/
  │       ├─ KnowledgeBlockRenderer.vue  # 递归渲染每种 block
  │       ├─ BlockImage.vue
  │       ├─ BlockCode.vue
  │       ├─ BlockTable.vue
  │       └─ BlockToolbar.vue           # 浮动 "+" 插入按钮
  ├─ components/KnowledgeCard.vue       # 列表卡片
  └─ router/index.ts                    # /knowledge/* 三条路由
```

排版修复（直接对应你截图里"列表、按钮、Tab 挤在一起"）：

1. **列表卡片化**：缩略图 + 标题 + 摘要 + 标签 + 时间，卡片高度统一，告别"文本表格式"。
2. **详情三栏**：左 240px 目录（自动从 HEADING block 抽取）；中 720px 正文 BlockRenderer；右 240px 操作 + 作者 + 版本。
3. **顶栏统一**：所有知识库页面共用 `KnowledgeHeader.vue`，标题/状态/编辑按钮全在这。

## 4. 面试可讲亮点（直接当 talking points）

| 维度 | 讲法 |
| ----|------|
| **领域建模** | 抽象 `KnowledgeItem` + `KnowledgeBlock`，把"图片 / 文档"统一为"有序内容块"，证明能识别业务本质 |
| **设计模式** | `BlockRenderStrategy` 走策略模式，新增 block 类型只需加一对实现类，符合 OCP |
| **存储取舍** | block 表 vs 大字段：可读、可改、可统计、可分页，避免 `longtext` 热点 |
| **事务一致性** | item + blocks 同事务提交，`type` 字段由 block 回算后回填（衍生字段一致性） |
| **缓存设计** | 条目粒度失效 + TTL 抖动，规避雪崩；并解释为何不对 block 单独做缓存 |
| **兼容演进** | 用 Adapter 模式把老 DocumentWiki 平滑过渡，不破坏数据 |
| **RBAC 复用** | 沿用 WikiSpace 已有权限，不发明新模型，体现工程纪律 |
| **可视化** | 块编辑器是面试时跑一遍 demo 的最佳载体，比纯 CRUD 更亮眼 |

## 5. 落地切片（建议分 3 期交付）

| 期 | 内容 | 估时 |
|---|------|------|
| Stage 1 | 建表 + entity + 单条新增/详情（仅 TEXT block） + 旧 DocumentWiki 适配读 | 3 天 |
| Stage 2 | 全 block 类型 + 前端块编辑器 + 列表卡片化 + 详情三栏 | 4 天 |
| Stage 3 | Picture 提升为条目 + 搜索 + 缓存粒度化 + OpenAPI 重新生成 + IssueLog 联测 | 2 天 |

## 6. 主要风险与对策

- **风险**：旧 `DocumentWiki` 的 `longtext content` 转 block 时会丢换行 / 表格样式。
  **对策**：先用启发式按 `\n\n` / `\n` 切块，标 `TEXT`，未来允许手动"转 Block 编辑"。
- **风险**：图片块 + 富文本混排，前端编辑器选型决定 UX 上限。
  **对策**：先用自研 BlockRenderer（轻量、可控），不引第三方编辑器，避免在面试时被问到"为什么用 EditorX"。
- **风险**：条目粒度缓存对"批量移动文件夹"场景不友好。
  **对策**：移动走"原子化"——DEL 旧条目 key + 旧空间列表 key + 新空间列表 key 三件套，**不**刷整库。
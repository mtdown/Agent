# 面向"知识库连接"的字段预留设计

> 目标：在现在建 `knowledge_item` / `knowledge_block` 表时，把将来做**云端知识库 + RAG 检索对接**需要的字段一次性留好。
> 原则：**现在只留空壳（nullable + 默认值，零业务侵入），将来填值即可，不再改表结构。**

---

## 一、为什么要现在就留字段

改表是有代价的：线上 `ALTER TABLE` 会锁表、要刷历史数据、要发版协调。而知识库对接（RAG/向量检索）所需的字段**高度可预测**——业界做法基本收敛。现在多留十几个可空字段，成本几乎为零；将来再补，代价是迁移脚本 + 全量回填。

**核心判断**：这些字段现在"用不上"，但将来"一定用得上"。

---

## 二、字段分组设计

### 组 1：内容结构化字段（Content）

| 字段 | 类型 | 默认 | 用途 |
|---|---|---|---|
| `content_format` | `VARCHAR(16)` | `'plain'` | 内容格式：`plain` / `markdown` / `html` / `blocks`。**这是从纯文本升级到富文本/Markdown 的开关** |
| `content_hash` | `VARCHAR(64)` | NULL | 内容指纹（MD5/SHA）。**RAG 省钱关键**：判断内容是否真变了，没变就不重新向量化 |
| `content_version` | `INT` | 1 | 内容版本号，每次编辑 +1 |

**为什么必须有 `content_hash`**：embedding 调用是要花钱花时间的。文档被打开点了一下"保存"但内容没变，不该触发重新向量化。用 hash 对比即可拦截。这是 RAG 工程里最常见的优化点，也是面试里能拉开差距的细节。

### 组 2：来源溯源字段（Source）

| 字段 | 类型 | 默认 | 用途 |
|---|---|---|---|
| `source_type` | `VARCHAR(16)` | `'NATIVE'` | `NATIVE`（站内新建）/ `UPLOAD`（文件上传）/ `IMPORT`（批量导入）/ `URL`（网页抓取）/ `API`（外部系统推送） |
| `source_url` | `VARCHAR(1024)` | NULL | 原始来源地址（URL 抓取、外部系统链接） |
| `source_ref` | `VARCHAR(128)` | NULL | 外部系统的文档 ID，用于回写/双向同步 |

**为什么重要**：知识库的答案必须能溯源。用户问"这条从哪来的"，没有溯源字段就只能答"不知道"。企业场景这是硬需求。

### 组 3：文件/附件字段（File）

> 当前项目**没有文件表**——`FileController` 只有两个 test 接口，文件传完丢 COS，路径不落库。要做云端知识库，文件必须先有"身份"。

| 字段 | 类型 | 默认 | 用途 |
|---|---|---|---|
| `file_url` | `VARCHAR(1024)` | NULL | 原始文件在对象存储的地址（PDF/Word/Markdown 原件） |
| `file_type` | `VARCHAR(64)` | NULL | MIME 类型（`application/pdf` 等） |
| `file_size` | `BIGINT` | NULL | 文件字节数 |
| `file_hash` | `VARCHAR(64)` | NULL | 文件指纹，避免同一文件重复解析入库 |

### 组 4：RAG / 向量索引字段（核心预留）

| 字段 | 类型 | 默认 | 用途 |
|---|---|---|---|
| `index_status` | `VARCHAR(16)` | `'PENDING'` | `PENDING` / `INDEXING` / `SUCCESS` / `FAILED` / `SKIPPED` |
| `index_time` | `DATETIME` | NULL | 最近一次**成功**索引时间 |
| `index_version` | `INT` | 0 | **已索引的内容版本**。与 `content_version` 对比即可判断"索引是否过期" |
| `index_error` | `VARCHAR(512)` | NULL | 最近一次失败原因，便于重试与排查 |
| `embedding_model` | `VARCHAR(64)` | NULL | embedding 模型标识（如 `text-embedding-v3`） |
| `embedding_dim` | `INT` | NULL | 向量维度（1536 / 1024 等） |
| `chunk_count` | `INT` | 0 | 切片数量 |

**三个关键设计点（面试可讲）**：

1. **为什么用 `index_version` 而不是只记 `index_time`**
   时间是"点"，版本能和内容版本**对齐**。判断索引是否过期：`index_status='SUCCESS' AND index_version = content_version`。用时间判断会遇到时钟回拨、批量重索引等边界问题。

2. **为什么必须存 `embedding_model`**
   **换 embedding 模型 = 换向量空间**。旧向量和新向量不在同一空间，混在一起检索结果全错。存了模型名，就能识别"这条是用老模型索引的，需要重新索引"。这是 RAG 系统迁移时最常踩的坑。

3. **为什么 `index_status` 要有 `SKIPPED`**
   空文档、纯图片无文字、内容过短——这些不该进索引，但也不是错误。单独一个状态避免"失败告警误报"。

### 组 5：外部知识库对接字段（External KB）

> 如果你将来接第三方知识库平台（Dify / Coze / 企业微信 / 内部 AI 平台），需要"我这边的文档"与"他那边的文档"建立映射。

| 字段 | 类型 | 默认 | 用途 |
|---|---|---|---|
| `external_kb_id` | `VARCHAR(128)` | NULL | 外部知识库 ID |
| `external_doc_id` | `VARCHAR(128)` | NULL | 外部知识库中的文档 ID |
| `sync_status` | `VARCHAR(16)` | `'NONE'` | `NONE` / `SYNCING` / `SYNCED` / `FAILED` |
| `sync_time` | `DATETIME` | NULL | 最近同步时间 |

### 组 6：可见性与兜底扩展

| 字段 | 类型 | 默认 | 用途 |
|---|---|---|---|
| `visibility` | `VARCHAR(16)` | `'SPACE'` | `PRIVATE` / `SPACE` / `PUBLIC`。**RAG 检索时必须按可见性过滤，否则会泄密** |
| `metadata_json` | `VARCHAR(2048)` | NULL | **万能兜底字段**。将来多出来的元数据先塞这里，避免频繁 `ALTER TABLE` |

**`visibility` 是安全红线**：向量检索是"语义相似就召回"，不看权限。如果检索时不带可见性过滤，用户 A 能通过提问拿到用户 B 的私有文档内容。这是企业知识库最严重的安全事故，必须提前在模型层留好。

---

## 三、落地建议：现在就用，还是以后加

**建议：现在就在建 `knowledge_item` 表时一次性加好。**

理由：
1. 新表还没上线，加字段零成本
2. 这些字段全部 `nullable` + 有默认值，**不影响任何现有功能**
3. 将来接知识库时，只需写业务代码填值，不用改表、不用刷历史数据

**不建议**：等以后再加。那时表已上线有数据，`ALTER TABLE` + 回填 + 发版协调，成本翻十倍。

---

## 四、给 `document_wiki` 老表的建议

老表**不要大改**，只补最关键的三个字段即可（成本最低，收益最大）：

```sql
ALTER TABLE document_wiki
    ADD COLUMN content_format VARCHAR(16) DEFAULT 'plain' COMMENT 'plain/markdown/html/blocks',
    ADD COLUMN content_hash   VARCHAR(64) NULL COMMENT 'content fingerprint',
    ADD COLUMN content_version INT DEFAULT 1 COMMENT 'content version',
    ADD COLUMN visibility     VARCHAR(16) DEFAULT 'SPACE' COMMENT 'PRIVATE/SPACE/PUBLIC',
    ADD COLUMN metadata_json  VARCHAR(2048) NULL COMMENT 'extensible metadata';
```

老表的 RAG 字段**等真正做知识库时再迁移到 `knowledge_item`**，避免在旧模型上重复投入。

---

## 五、演进路线

| 阶段 | 动作 | 字段使用情况 |
|---|---|---|
| 现在 | 建 `knowledge_item` 表，加全部预留字段 | 全部留空（有默认值） |
| 近期 | 富文本/Markdown 编辑，`content_format` 填 `markdown` | 启用组 1 |
| 中期 | 文件上传解析（PDF/Word），启用文件表 | 启用组 3 |
| 知识库对接 | 切片 + embedding + 向量库 | 启用组 4（核心） |
| 可选 | 对接第三方知识库平台 | 启用组 5 |

---

## 六、一句话总结

**现在留字段 = 买一份"将来不改表"的保险。** 成本是十几个可空列，收益是将来接知识库时只写业务代码、不碰数据结构、不刷历史数据。

-- wiki_chunk: RAG 切片表（add-wiki-rag-pipeline）
-- embedding 存 float32 小端序列化 BLOB（DashScope text-embedding-v3 = 1024 维 ≈ 4KB/chunk）
CREATE TABLE IF NOT EXISTS wiki_chunk (
    id bigint NOT NULL COMMENT 'id (ASSIGN_ID)',
    docId bigint NOT NULL COMMENT 'document_wiki.id',
    spaceId bigint NOT NULL COMMENT 'wiki_space.id (冗余, 权限过滤主口)',
    contentVersion int NOT NULL DEFAULT 1 COMMENT '对应文档 contentVersion',
    chunkIndex int NOT NULL DEFAULT 0 COMMENT '文档内切片序号',
    chunkHeading varchar(512) NULL COMMENT '所属标题路径',
    chunkText mediumtext NOT NULL COMMENT '切片原文',
    docTitle varchar(256) NULL COMMENT '文档标题(冗余, 检索免联表)',
    docNumber varchar(64) NULL COMMENT '文号, 正则提取, 可空',
    embedding longblob NULL COMMENT 'float32 little-endian 序列化向量',
    status varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / INVALID',
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    isDelete tinyint NOT NULL DEFAULT 0 COMMENT 'soft delete flag',
    PRIMARY KEY (id),
    KEY idx_space_status (spaceId, status),
    KEY idx_doc_version_status (docId, contentVersion, status)
) COMMENT 'wiki rag chunk';

-- rag_api_key: 开放 RAG API 的用户级 API Key（add-open-rag-api）
-- keyHash 存 SHA-256 hex（明文不落库，仅创建响应返回一次）；删除即吊销（软删）
CREATE TABLE IF NOT EXISTS rag_api_key (
    id bigint NOT NULL COMMENT 'id (ASSIGN_ID)',
    userId bigint NOT NULL COMMENT '属主 user.id',
    keyName varchar(64) NOT NULL COMMENT '用途名, 如"本地 Agent"',
    keyHash char(64) NOT NULL COMMENT 'SHA-256(明文key) hex, 认证唯一入口',
    keyPrefix varchar(16) NOT NULL COMMENT '明文前8位, 列表展示',
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    isDelete tinyint NOT NULL DEFAULT 0 COMMENT 'soft delete flag (=1 即吊销)',
    PRIMARY KEY (id),
    KEY idx_user (userId, isDelete),
    KEY idx_key_hash (keyHash)
) COMMENT 'rag open api key';

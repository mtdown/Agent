CREATE TABLE IF NOT EXISTS agent_wiki_operation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_version SMALLINT NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    space_id BIGINT NOT NULL,
    folder_id BIGINT NOT NULL,
    document_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_wiki_user_operation (user_id, operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

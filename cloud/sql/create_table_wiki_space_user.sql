CREATE TABLE IF NOT EXISTS wiki_space_user (
    id bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
    spaceId bigint NOT NULL COMMENT 'space id',
    userId bigint NOT NULL COMMENT 'user id',
    spaceRole varchar(32) NOT NULL DEFAULT 'editor' COMMENT 'space role',
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    isDelete tinyint NOT NULL DEFAULT 0 COMMENT 'soft delete flag',
    PRIMARY KEY (id),
    KEY idx_space_user (spaceId, userId),
    KEY idx_user_delete (userId, isDelete)
) COMMENT 'wiki team member';

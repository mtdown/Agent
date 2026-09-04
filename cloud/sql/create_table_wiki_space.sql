CREATE TABLE IF NOT EXISTS wiki_space (
    id bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
    type tinyint NOT NULL COMMENT '0-personal, 1-team, 2-public',
    name varchar(128) NOT NULL COMMENT 'space name',
    ownerUserId bigint NULL COMMENT 'owner user id',
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    isDelete tinyint NOT NULL DEFAULT 0 COMMENT 'soft delete flag',
    personalOwnerKey bigint GENERATED ALWAYS AS (CASE WHEN type = 0 THEN ownerUserId ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_personal_owner (personalOwnerKey),
    KEY idx_type_delete (type, isDelete),
    KEY idx_owner (ownerUserId)
) COMMENT 'wiki space';

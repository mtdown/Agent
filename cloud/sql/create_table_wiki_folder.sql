CREATE TABLE IF NOT EXISTS wiki_folder (
    id bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
    spaceId bigint NOT NULL COMMENT 'space id',
    parentId bigint NULL COMMENT 'parent folder id',
    name varchar(128) NOT NULL COMMENT 'folder name',
    deleteTime datetime NULL COMMENT 'logical delete time',
    deleteBy bigint NULL COMMENT 'logical delete user',
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    editTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'edit time',
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    isDelete tinyint NOT NULL DEFAULT 0 COMMENT 'soft delete flag',
    PRIMARY KEY (id),
    KEY idx_space_delete (spaceId, isDelete),
    KEY idx_parent (parentId)
) COMMENT 'wiki folder';

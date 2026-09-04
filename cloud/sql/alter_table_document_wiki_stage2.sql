ALTER TABLE document_wiki
    ADD COLUMN spaceId bigint NULL COMMENT 'wiki space id',
    ADD COLUMN folderId bigint NULL COMMENT 'wiki folder id',
    ADD COLUMN deleteTime datetime NULL COMMENT 'logical delete time',
    ADD COLUMN deleteBy bigint NULL COMMENT 'logical delete user',
    ADD KEY idx_space_delete (spaceId, isDelete),
    ADD KEY idx_folder (folderId),
    ADD KEY idx_delete_time (deleteTime),
    ADD KEY idx_delete_by (deleteBy);

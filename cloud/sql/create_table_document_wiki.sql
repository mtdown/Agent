-- Wiki document table for Stage 1 DocumentWiki MVP.
create table if not exists document_wiki
(
    id         bigint auto_increment comment 'id' primary key,
    title      varchar(128)                       not null comment 'document title',
    content    longtext                           not null comment 'document content',
    summary    varchar(512)                       null comment 'document summary',
    category   varchar(64)                        null comment 'category',
    tags       varchar(512)                       null comment 'tags JSON array',
    userId     bigint                             not null comment 'creator user id',
    viewCount  bigint   default 0                 not null comment 'view count',
    createTime datetime default CURRENT_TIMESTAMP not null comment 'create time',
    editTime   datetime default CURRENT_TIMESTAMP not null comment 'edit time',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment 'update time',
    isDelete   tinyint  default 0                 not null comment 'is deleted',
    index idx_title (title),
    index idx_category (category),
    index idx_tags (tags),
    index idx_userId (userId),
    index idx_editTime (editTime)
) comment 'wiki document' collate = utf8mb4_unicode_ci;

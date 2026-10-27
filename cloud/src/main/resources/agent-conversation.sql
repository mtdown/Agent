CREATE TABLE IF NOT EXISTS agent_conversation (
    id bigint NOT NULL,
    ownerUserId bigint NOT NULL,
    title varchar(160) NOT NULL DEFAULT 'New chat',
    busy tinyint NOT NULL DEFAULT 0,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete tinyint NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_owner_time (ownerUserId, isDelete, updateTime)
) COMMENT 'private agent conversations';

CREATE TABLE IF NOT EXISTS agent_message (
    id bigint NOT NULL,
    conversationId bigint NOT NULL,
    turnId bigint NOT NULL,
    role varchar(16) NOT NULL,
    content longtext NOT NULL,
    status varchar(16) NOT NULL,
    ragMode varchar(16) NULL,
    spaceIdsJson text NULL,
    citationsJson mediumtext NULL,
    statsJson text NULL,
    interactionJson mediumtext NULL,
    choiceConsumed tinyint NOT NULL DEFAULT 0,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conversation_order (conversationId, id),
    KEY idx_turn (turnId)
) COMMENT 'private agent conversation messages';

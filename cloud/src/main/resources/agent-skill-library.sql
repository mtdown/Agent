CREATE TABLE IF NOT EXISTS agent_skill_item (
    id bigint NOT NULL,
    ownerUserId bigint NOT NULL,
    name varchar(160) NOT NULL,
    summary varchar(500) NOT NULL,
    state varchar(16) NOT NULL DEFAULT 'DRAFT',
    latestVersion int NOT NULL DEFAULT 0,
    activeVersion int NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete tinyint NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_skill_owner (ownerUserId, isDelete, updateTime),
    KEY idx_skill_state (state, isDelete)
) COMMENT 'user owned agent skill entries';

CREATE TABLE IF NOT EXISTS agent_skill_version (
    skillId bigint NOT NULL,
    version int NOT NULL,
    definitionJson longtext NOT NULL,
    createdBy bigint NOT NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (skillId, version)
) COMMENT 'immutable structured skill versions';

CREATE TABLE IF NOT EXISTS agent_skill_share (
    skillId bigint NOT NULL,
    targetUserId bigint NOT NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (skillId, targetUserId),
    KEY idx_skill_share_target (targetUserId, skillId)
) COMMENT 'explicit skill read and use grants';

CREATE TABLE IF NOT EXISTS agent_code_item (
    id bigint NOT NULL,
    ownerUserId bigint NOT NULL,
    name varchar(160) NOT NULL,
    summary varchar(500) NOT NULL,
    latestVersion int NOT NULL DEFAULT 0,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete tinyint NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_code_owner (ownerUserId, isDelete, updateTime)
) COMMENT 'user owned code entries';

CREATE TABLE IF NOT EXISTS agent_code_version (
    codeId bigint NOT NULL,
    version int NOT NULL,
    language varchar(16) NOT NULL,
    entrypoint varchar(160) NOT NULL,
    sourceCode longtext NOT NULL,
    inputSchemaJson mediumtext NOT NULL,
    outputSchemaJson mediumtext NOT NULL,
    validationState varchar(16) NOT NULL DEFAULT 'UNVERIFIED',
    createdBy bigint NOT NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (codeId, version)
) COMMENT 'immutable published Python and Java source versions';

CREATE TABLE IF NOT EXISTS agent_code_share (
    codeId bigint NOT NULL,
    targetUserId bigint NOT NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (codeId, targetUserId),
    KEY idx_code_share_target (targetUserId, codeId)
) COMMENT 'explicit code read and use grants';

CREATE TABLE IF NOT EXISTS agent_code_validation (
    id bigint NOT NULL,
    codeId bigint NOT NULL,
    codeVersion int NOT NULL,
    sampleName varchar(160) NOT NULL,
    inputJson mediumtext NOT NULL,
    expectedJson mediumtext NOT NULL,
    actualJson mediumtext NULL,
    status varchar(16) NOT NULL,
    diagnostics text NULL,
    durationMs bigint NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_code_validation_version (codeId, codeVersion, createTime)
) COMMENT 'published code sample validation results';

CREATE TABLE IF NOT EXISTS agent_skill_execution (
    id bigint NOT NULL,
    ownerUserId bigint NOT NULL,
    skillId bigint NOT NULL,
    skillVersion int NOT NULL,
    status varchar(16) NOT NULL,
    errorCode varchar(64) NULL,
    errorDetail text NULL,
    startTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finishTime datetime NULL,
    PRIMARY KEY (id),
    KEY idx_skill_execution_owner (ownerUserId, startTime),
    KEY idx_skill_execution_skill (skillId, skillVersion)
) COMMENT 'agent skill invocation audit';

CREATE TABLE IF NOT EXISTS agent_skill_step_execution (
    id bigint NOT NULL,
    executionId bigint NOT NULL,
    stepIndex int NOT NULL,
    codeId bigint NULL,
    codeVersion int NULL,
    status varchar(16) NOT NULL,
    durationMs bigint NULL,
    errorCode varchar(64) NULL,
    errorDetail text NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_skill_step_execution (executionId, stepIndex)
) COMMENT 'agent skill step audit';

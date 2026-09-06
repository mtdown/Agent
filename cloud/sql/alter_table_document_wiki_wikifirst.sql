-- Wiki-First refactor: document_wiki content format + source tracing + RAG reserved fields.
-- All reserved columns are nullable or defaulted; no business logic depends on them yet.
ALTER TABLE document_wiki
    ADD COLUMN contentFormat varchar(16) DEFAULT 'plain' COMMENT 'plain/markdown (RAG group 1)',
    ADD COLUMN sourceType varchar(16) DEFAULT 'NATIVE' COMMENT 'NATIVE/UPLOAD/IMPORT/URL (RAG group 2)',
    ADD COLUMN sourceUrl varchar(1024) NULL COMMENT 'original source url (RAG group 2)',
    ADD COLUMN contentHash varchar(64) NULL COMMENT 'content fingerprint (RAG group 1)',
    ADD COLUMN contentVersion int DEFAULT 1 COMMENT 'content version, +1 per edit (RAG group 1)',
    ADD COLUMN visibility varchar(16) DEFAULT 'SPACE' COMMENT 'PRIVATE/SPACE/PUBLIC (RAG group 6)',
    ADD COLUMN metadataJson varchar(2048) NULL COMMENT 'extensible metadata (RAG group 6)';

ALTER TABLE document_wiki
    ADD FULLTEXT INDEX idx_document_wiki_title_content_fulltext (title, content) WITH PARSER ngram;

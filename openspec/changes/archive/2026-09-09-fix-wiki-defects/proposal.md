# Change: Fix Wiki Defects (Post Wiki-First Review)

## Why

The Wiki-First refactor (`wiki-first-refactor`) is functionally complete: phases 1-5 shipped the attachment channel, Markdown content, the `md-editor-v3` editor with paste-to-upload, the 969-line monolith split, and wiki-first navigation. Backend tests (21) and the frontend build both pass.

A line-by-line source review performed after that work found **12 defects that tests and the build do not cover**. They fall into three groups:

1. **Correctness gaps in the new attachment path.** `WikiAttachmentServiceImpl` writes to COS and only then saves the database row; a failed save leaves an unreferenced object in COS, and the class has no `@Transactional` even though `WikiFolderServiceImpl` and `WikiSpaceServiceImpl` do. The `documentId` parameter is written without verifying it belongs to the acting space.
2. **A product-level capability gap.** Wiki search runs `LIKE '%keyword%'` against `content`, which cannot use an index, does not segment Chinese text, and ranks nothing. A knowledge base that cannot find its own content is not a knowledge base.
3. **Known liabilities left over from earlier stages.** Sharding config is fully commented out, `mvn test` depends on an external Redis, `README.md` still states permission checks are incomplete, and the main bundle is 2.5 MB.

A fourth group is a consistency blemish: the picture module's N+1 was already fixed in an earlier stage (recorded in `README.md`), but the wiki document list still queries the author once per row.

## What Changes

Six sequential phases, each independently verifiable, executed one at a time:

1. **Attachment correctness** — compensate the COS object when the row save fails, validate `documentId` ownership, tag the operation transactional.
2. **Full-text search** — replace `LIKE` with a MySQL `FULLTEXT` + `ngram` index, keep the existing space-visibility filter, add relevance ordering.
3. **Document list N+1** — batch-load authors with a single `listByIds` and map them back, matching the picture module's existing approach.
4. **Project hygiene** — decide sharding (enable or honestly retire), make `mvn test` runnable without an external Redis, and correct the stale permission note in `README.md`.
5. **Upload hardening** — require edit rights rather than mere visibility, sniff real file content instead of trusting the suffix, normalize suffix casing in the object path.
6. **Polish** — clear stale detail caches missing `contentFormat`, and code-split the editor bundle.

### Out of Scope

- Changing the storage layout of existing `wiki_attachment` rows (no backfill; new uploads follow the new path).
- Migrating search to Elasticsearch. MySQL `FULLTEXT` is sufficient at this data scale; the decision is recorded rather than implemented.
- Enabling ShardingSphere for picture tables. Phase 4 explicitly allows retiring the code instead of turning it on.
- Rewriting the picture module. It is demoted to an auxiliary capability; only the wiki paths are in scope.

## Capabilities

### New Capabilities

- `wiki-search`: full-text search over document title and content using a MySQL `FULLTEXT` index with `ngram` parsing, restricted to the caller's visible spaces and ordered by relevance.

### Modified Capabilities

- `wiki-attachment`: image upload becomes atomic with respect to COS (orphan compensation), validates that `documentId` belongs to the acting space, requires edit-level space access, and verifies real file content.
- `wiki-document-list`: paginated document queries load their authors in one batch instead of one query per row.
- `project-hygiene`: backend tests run without an external Redis; the sharding code is either enabled or retired; `README.md` stops claiming that permission checks are incomplete.

## Impact

- **Database**: new `FULLTEXT` index on `document_wiki` (`title`, `content`) with the `ngram` parser; no column changes; no migration of existing rows.
- **Backend**: `WikiAttachmentServiceImpl` gains compensating delete, ownership validation, content sniffing, and a transaction boundary; `DocumentWikiServiceImpl` query path changes for both search and list; test configuration changes so the suite runs without external Redis.
- **Frontend**: no functional change in phases 1-5; phase 6 code-splits the editor route and is verified by build output size.
- **Relationship to other changes**: `wiki-first-refactor` is the source of the defects fixed here and stays as the delivery record. `add-wiki-space-folder-model` is unaffected except where `WikiSpaceService` visibility helpers are reused.

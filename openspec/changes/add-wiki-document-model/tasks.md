# Tasks: DocumentWiki MVP

## 0. Pre-Implementation Review

- [x] Review original picture list/detail/create/edit/delete operation flow.
- [x] Review existing user login and permission helper usage.
- [x] Review existing MySQL table style and MyBatis-Plus entity style.
- [x] Review existing Redis usage and cache invalidation style.
- [x] Confirm Stage 1 does not use personal/team spaces, document upload, RAG chunks, URL crawling, or object storage for document text.

## 1. Database

- [x] Add `cloud/sql/create_table_document_wiki.sql`.
- [x] Define `document_wiki` table with title, content, summary, category, tags, userId, viewCount, createTime, editTime, updateTime, and isDelete.
- [x] Keep document text in MySQL `longtext`.
- [x] Do not create version, chunk, space, or upload-file tables in this change.

## 2. Backend Domain Files

- [x] Add `DocumentWiki` entity.
- [x] Add `DocumentWikiMapper`.
- [x] Add `DocumentWikiService`.
- [x] Add `DocumentWikiServiceImpl`.
- [x] Add `DocumentWikiVis`.
- [x] Add DTOs under `dto/documentWiki`:
  - [x] `DocumentWikiAddRequest`
  - [x] `DocumentWikiEditRequest`
  - [x] `DocumentWikiQueryRequest`

## 3. Backend Behavior

- [x] Implement create document API.
- [x] Implement page/list query API.
- [x] Implement detail query API.
- [x] Implement online edit/save API.
- [x] Implement soft delete API.
- [x] Validate document title/content.
- [x] Reuse current login requirement and ownership checks.
- [x] Convert entity data to `DocumentWikiVis` for frontend responses.

## 4. Redis Reuse

- [x] Reuse the existing Redis connection/configuration.
- [x] Add Redis read cache for document detail.
- [x] Add Redis read cache for document list queries where suitable.
- [x] Clear related cache after create.
- [x] Clear related cache after edit.
- [x] Clear related cache after delete.
- [x] Ensure Redis is not the permanent source of saved document content.

## 5. Frontend Pages

- [x] Add `DocumentWikiEditor.vue`.
- [x] Add document list page.
- [x] Add document detail page.
- [x] Add document create page.
- [x] Add document edit page.
- [x] Add routes for document list/detail/create/edit.
- [x] Add navigation entry for wiki documents.
- [x] Regenerate or update frontend API calls after backend OpenAPI is available.

## 6. Verification

- [x] Run OpenSpec validation: `openspec validate add-wiki-document-model --strict`.
- [x] Run backend build/test command available in this project.
  - `mvn -Dtest=DocumentWikiServiceImplTest test` passed.
  - `mvn -DskipTests package` passed.
  - `mvn test` is blocked by the existing missing `ALIYUN_AI_API_KEY` environment variable in `CloudApplicationTests`.
- [x] Run frontend install/build command available in this project.
  - `npm run build-only` passed.
  - `npm run build` is blocked by existing TypeScript strictness issues outside the new `documentWiki` files.
- [ ] Manually verify document create, list, detail, edit, and delete flow.
  - Requires applying `cloud/sql/create_table_document_wiki.sql` to MySQL and running the app with Redis available.
- [ ] Manually verify the original picture list/detail flow still works.
  - Requires running the app against the existing environment.
- [x] Check that Stage 1 did not accidentally add version/chunk/RAG/space behavior.

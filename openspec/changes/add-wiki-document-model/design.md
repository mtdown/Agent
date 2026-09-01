## Context

See `proposal.md` for motivation. The existing backend is a Spring Boot monolith with MyBatis-Plus entities, DTO request objects, `Vis` response objects, controller/service/mapper layering, logical delete through `isDelete`, and unified `BaseResponse` results. The existing domain already has `User`, `Space`, `SpaceUser`, and `Picture`.

The important constraint for this change is that `Picture` remains a valid model. It is not replaced by `Document`; it becomes a reusable Wiki asset model for covers, embedded images, attachments, and space materials.

## Goals / Non-Goals

**Goals:**

- Add document-centered backend data structures without breaking picture behavior.
- Keep document ownership and space ownership consistent with the existing picture model.
- Provide basic document CRUD behavior for later frontend Wiki pages.
- Record document versions at create/edit time so the later editor has history support.
- Add document chunks as a simple persistence foundation for later RAG.
- Follow existing project style: entity + DTO + Vis + Mapper + Service + Controller.

**Non-Goals:**

- No document file upload or text parsing in this change.
- No PDF, DOCX, Markdown, or TXT parser implementation in this change.
- No frontend Wiki page implementation in this change.
- No RAG, embedding generation, vector database, URL crawling, or Agent orchestration.
- No rewrite of the existing picture, space, or team member permission modules.

## Decisions

### Decision 1: Add `Document` beside `Picture`

`Document` is introduced as the Wiki content model. `Picture` remains the image asset model.

Alternative considered: rename `Picture` to a generic file model and store documents and images in one table. This was rejected because the existing picture upload, thumbnail, review, and image metadata behavior is already specialized and useful for Wiki image assets.

### Decision 2: Reuse `spaceId` semantics

Documents use the same space ownership pattern as pictures:

- `spaceId == null` means public Wiki document.
- `spaceId != null` means the document belongs to a personal Wiki or team Wiki space.

This preserves the existing mental model and prepares the document APIs for stage 4 permission checks.

### Decision 3: Store current document content on `document`

The `document` table stores the current editable content and metadata. The `document_version` table stores immutable snapshots.

Alternative considered: store all content only in `document_version` and compute current content from the latest version. This was rejected for MVP because it makes list/detail queries and updates more complex than the current project needs.

### Decision 4: Save a version when documents are created or edited

Create records version `1`. Edit records the next version number.

The implementation should use a transaction when saving the document and version together. This prevents a document update from succeeding while its version record fails.

### Decision 5: Add `document_chunk` now, keep chunk generation simple later

Stage 1 only adds storage and basic query behavior for chunks. Stage 2 will decide how to generate chunks from parsed text. Stage 6 will decide embedding generation and retrieval behavior.

This keeps database shape stable before RAG without pulling AI concerns into the first backend model change.

### Decision 6: Use numeric status fields for compatibility

`reviewStatus`, `parseStatus`, and `embeddingStatus` use integers, matching the existing `Picture.reviewStatus` style.

Initial recommended status meanings:

- `reviewStatus`: `0` reviewing, `1` pass, `2` reject.
- `parseStatus`: `0` pending, `1` processing, `2` success, `3` failed.
- `embeddingStatus`: `0` pending, `1` processing, `2` success, `3` failed.

### Decision 7: Keep stage 1 permissions minimal

Stage 1 should require login for mutating document operations and support public/space query structure. Full Wiki document permissions are stage 4.

This lets the backend document model land before the permission map changes from picture-focused keys to document-focused keys.

## Risks / Trade-offs

- [Risk] `Document` name can conflict with `org.jsoup.nodes.Document` in existing URL image batch code. -> Mitigation: use fully qualified imports carefully and avoid editing Jsoup-heavy code in this change.
- [Risk] `document.content` can become large. -> Mitigation: use `longtext` in MySQL and avoid loading content in future list summaries unless needed.
- [Risk] Version creation can become inconsistent with document edits. -> Mitigation: save document and version in one transaction.
- [Risk] Stage 1 APIs may be accessible before stage 4 permission refinement. -> Mitigation: require login for create/edit/delete and document that space-level document permissions are stage 4.
- [Risk] Chunks exist before parser/RAG behavior. -> Mitigation: keep chunk APIs/internal service minimal and treat chunks as persistence preparation.

## Migration Plan

1. Add `cloud/sql/create_table_document.sql` for the three new tables.
2. Apply the SQL to the local `picture` database before manually testing endpoints.
3. Deploy backend code with new document classes and endpoints.
4. Existing users, spaces, pictures, and space members require no data migration.
5. Rollback can remove the new document endpoints from the running service; existing picture behavior is unaffected because existing tables and code paths are not modified.

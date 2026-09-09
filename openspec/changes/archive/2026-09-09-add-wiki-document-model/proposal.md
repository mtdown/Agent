# Change: Add DocumentWiki MVP

## Why

The original cloud gallery project is being reshaped into an enterprise wiki product. Stage 1 must stay small and verifiable: users should be able to create, view, edit, query, and delete wiki documents in the existing application before later work adds team spaces, upload parsing, RAG, URL crawling, or agent collaboration.

The image model remains in the product direction because wiki documents often embed images, but Stage 1 does not change image storage or image workflows.

## What Changes

- Add a new `DocumentWiki` document domain beside the existing picture domain.
- Add basic CRUD for wiki documents.
- Add an online document editor UI for creating and editing document content.
- Persist saved document content in MySQL.
- Reuse the existing Redis configuration for document read cache and cache invalidation.
- Reuse the existing login/user model for document ownership.
- Add document list/detail/create/edit/delete pages to the frontend navigation.

## Scope

### In Scope

- Backend `DocumentWiki` entity, mapper, service, DTO/VO, and controller.
- MySQL table `document_wiki`.
- Redis cache for document detail/list reads, with invalidation after create/edit/delete.
- Frontend pages and component needed for the visible online editing workflow.
- API generation/update for frontend document requests.
- Verification that the original picture flow still builds after the new module is added.

### Out of Scope

- Personal wiki space and team wiki space.
- Document version history.
- Document chunking for RAG.
- Enterprise document RAG.
- URL crawler/importer.
- File upload and multi-format document parsing.
- Object storage upload for document files.
- Image embedding changes beyond keeping the existing picture model available for later use.

## Main Files To Add Or Update

### Backend

- `cloud/sql/create_table_document_wiki.sql`
- `cloud/src/main/java/com/et/cloud/model/entity/DocumentWiki.java`
- `cloud/src/main/java/com/et/cloud/dto/documentWiki/DocumentWikiAddRequest.java`
- `cloud/src/main/java/com/et/cloud/dto/documentWiki/DocumentWikiEditRequest.java`
- `cloud/src/main/java/com/et/cloud/dto/documentWiki/DocumentWikiQueryRequest.java`
- `cloud/src/main/java/com/et/cloud/model/vis/DocumentWikiVis.java`
- `cloud/src/main/java/com/et/cloud/mapper/DocumentWikiMapper.java`
- `cloud/src/main/java/com/et/cloud/service/DocumentWikiService.java`
- `cloud/src/main/java/com/et/cloud/service/impl/DocumentWikiServiceImpl.java`
- `cloud/src/main/java/com/et/cloud/controller/DocumentWikiController.java`

### Frontend

- `cloud_front/src/components/DocumentWikiEditor.vue`
- `cloud_front/src/pages/documentWiki/DocumentWikiListPage.vue`
- `cloud_front/src/pages/documentWiki/DocumentWikiDetailPage.vue`
- `cloud_front/src/pages/documentWiki/AddDocumentWikiPage.vue`
- `cloud_front/src/pages/documentWiki/EditDocumentWikiPage.vue`
- `cloud_front/src/router/index.ts`
- frontend API files regenerated from backend OpenAPI when the backend is running

## Operation Flow To Preserve And Replace

### Original Flow To Review First

- Picture list and detail browsing.
- Picture create/upload management.
- Picture edit/delete.
- Existing user login and permission checks.
- Existing Redis/MySQL configuration.

### Stage 1 Replacement Flow

- User opens the wiki document list.
- User creates a document with title and content.
- User views the saved document detail.
- User edits the document online and saves changes.
- User deletes the document.
- MySQL stores the saved content permanently.
- Redis caches read results and is cleared when saved content changes.

## Acceptance Criteria

- A logged-in user can create a `DocumentWiki` document.
- A logged-in user can view document list and detail.
- A logged-in user can edit document title/content from an online editor page.
- A logged-in user can delete a document.
- Deleted documents do not appear in normal list/detail results.
- Saved document content is stored in MySQL.
- Redis is used only as cache, not as the source of truth.
- Stage 1 does not require object storage for document content.
- Stage 1 does not expose personal/team wiki space behavior.

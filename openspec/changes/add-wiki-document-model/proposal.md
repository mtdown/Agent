## Why

The current project is centered on image assets, which makes the product look like a common cloud gallery project. AgentWiki needs a document-centered Wiki domain model so the existing user, space, permission, COS, and image asset capabilities can evolve into an enterprise knowledge collaboration platform.

This change creates the backend foundation for Wiki documents while preserving the existing picture model as reusable Wiki image assets.

## What Changes

- Add a `Document` domain model as the new primary Wiki content object.
- Add `DocumentVersion` to preserve document edit history for later Wiki editing workflows.
- Add `DocumentChunk` to prepare parsed document content for future RAG indexing.
- Add document CRUD APIs for create, edit, delete, detail, and paginated list.
- Add document query, add, edit, update, and version view request/response objects following the existing DTO/Vis style.
- Keep the existing `Picture` model, picture APIs, COS image upload, thumbnail, and `spaceId` behavior.
- Reuse the existing `Space` model so documents can belong to public Wiki, personal Wiki, or team Wiki contexts.
- Do not implement document upload parsing, frontend Wiki pages, RAG, URL crawling, or Agent orchestration in this change.

## Capabilities

### New Capabilities

- `wiki-document-model`: Defines the backend Wiki document, version, and chunk model and the basic document CRUD behavior needed before upload parsing and Wiki editing are implemented.

### Modified Capabilities

- None.

## Impact

- Adds SQL schema file for `document`, `document_version`, and `document_chunk`.
- Adds backend entity, DTO, Vis, Mapper, Service, and Controller classes under `cloud/src/main/java/com/et/cloud`.
- Adds document endpoints under `/document`.
- Adds tests for document validation, query wrapper behavior, and controller/service behavior where practical.
- Does not remove or rewrite `PictureController`, `PictureService`, `Picture`, `Space`, or `SpaceUser`.

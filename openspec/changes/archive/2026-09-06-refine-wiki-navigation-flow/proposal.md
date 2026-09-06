## Why

The first Wiki layout pass established the top-navigation and three-column visual direction, but the current page still duplicates navigation through an in-page secondary tab row and offers a separate document detail route. The next refinement should make the top navigation authoritative and keep document reading inside the `/documentWiki` workspace.

## What Changes

- Remove `文档管理` from the top navigation bar.
- Remove the in-page secondary navigation tabs from the Wiki document main page: `文档`, `回收站`, and `文档空间管理`.
- Make `WIKI文档`, `回收站`, and `文档空间管理` top navigation entries open their corresponding interfaces directly.
- Keep document list reading inside `http://127.0.0.1:3000/documentWiki`.
- Make document list actions that open/read a document update the center column in the three-column Wiki workspace instead of navigating to `/documentWiki/:id`.
- Preserve edit, move, and delete actions for documents.
- Preserve role-based access: `文档空间管理` remains administrator-only.
- Keep existing backend APIs, database tables, and document permission rules unchanged.

## Capabilities

### New Capabilities

- `wiki-navigation-flow`: Covers the refined Wiki navigation behavior where top navigation owns page-level switching and document reading remains inside the main Wiki workspace.

### Modified Capabilities

- None.

## Impact

- Frontend navigation: `cloud_front/src/components/GlobalHeader.vue`.
- Wiki document workspace: `cloud_front/src/pages/documentWiki/DocumentWikiListPage.vue`.
- Wiki document list actions: `cloud_front/src/pages/documentWiki/components/WikiDocumentList.vue`.
- Router mappings may need adjustment if dedicated top-level paths are introduced for recycle bin or document space management.
- Existing `/documentWiki/:id` route may remain for backward compatibility, but it should no longer be the primary document-reading path from the Wiki list.

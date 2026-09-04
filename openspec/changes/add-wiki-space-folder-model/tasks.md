# Tasks: Wiki Spaces, Folders, and Recycle Bin (Stage 2)

## 0. Pre-Implementation Review

- [x] 0.1 Review Stage 1 `document_wiki` controller/service/entity and the existing `getLoginUser`/`isAdmin` helpers; verify expected files are present
- [x] 0.2 Review picture module `Space`/`SpaceUser` membership style as a reference, confirming wiki tables stay independent (design Decision 1)
- [x] 0.3 Confirm the four delta specs (wiki-space, wiki-folder, wiki-document, wiki-recycle-bin) map 1:1 to the four planned feature areas

## 1. Database

- [x] 1.1 Add `cloud/sql/create_table_wiki_space.sql` with type/name/ownerUserId/isDelete and indexes; verify DDL executes on the local MySQL
- [x] 1.2 Add `cloud/sql/create_table_wiki_space_user.sql` with spaceId/userId/spaceRole/isDelete; verify DDL executes
- [x] 1.3 Add `cloud/sql/create_table_wiki_folder.sql` with spaceId/parentId/name/isDelete; verify DDL executes and self-referential parent is same-space only (enforced in service)
- [x] 1.4 Add `ALTER TABLE document_wiki` script adding spaceId/folderId/deleteTime/deleteBy (nullable first); verify columns exist
- [x] 1.5 Add startup initializer that seeds the single public wiki space idempotently; verify a second startup does not duplicate it

## 2. Backend: Space Domain

- [x] 2.1 Add `WikiSpace` entity/mapper/service (with `WikiSpaceServiceImpl`); verify compile via `mvn -DskipTests package`
- [x] 2.2 Add personal-space auto-provisioning on registration and lazily on login; verify a registered user and a pre-existing user both end up with exactly one personal space (unit test or DB check)
- [x] 2.3 Add team-space create API restricted to platform admin; verify non-admin create returns `NO_AUTH_ERROR`
- [x] 2.4 Add team membership APIs (admin adds/removes member, member list); verify added user sees the team under their joined list and a removed user does not
- [x] 2.5 Add space list API returning public space, joined team spaces, and own personal space per viewer; verify personal/team contents are never leaked to non-visible users
- [x] 2.6 Add member self-exit API (remove own membership row); verify the team space disappears from the user's joined list while documents they authored remain in the space
- [x] 2.7 Add admin team-space delete/restore/permanent-delete APIs; verify empty space deletes outright, non-empty space plus all contents logically deletes (deleteTime/deleteBy), restore brings the whole subtree back, and permanent delete requires a confirm flag

## 3. Backend: Access Check

- [x] 3.1 Implement `checkSpaceVisible(space, loginUser)` helper encoding public/personal/team rules (design Decision 3); verify each branch with unit tests
- [x] 3.2 Wire the helper into space/folder/document/recycle controllers; verify invisible-space operations return `NO_AUTH_ERROR`
- [x] 3.3 (P0) Detail cache-hit path now re-checks space visibility (fixes cache bypass read)

## 4. Backend: Folder Domain

- [x] 4.1 Add `WikiFolder` entity/mapper/service with create/rename/move/delete; verify nested folder creation beyond one level works
- [x] 4.2 Enforce folder belongs to the acting user's visible space; verify cross-space folder ops are rejected
- [x] 4.3 Enforce documents attach to a folder (reject create/move with no folder, per spec wiki-folder); verify API rejects folder-less document create
- [x] 4.4 Implement folder delete as logical delete of the folder plus its document/nested-folder subtree in one transaction; verify subtree rows flip to deleted together
- [x] 4.5 Reject cross-space folder moves (folder may only move inside its own space); verify moving a folder under another space's folder is rejected and its location is unchanged

## 5. Backend: Document Domain Rework

- [x] 5.1 Extend `DocumentWiki` entity and DTOs with spaceId/folderId/deleteTime/deleteBy; verify compile
- [x] 5.2 Rework create API to require destination space + folder, validating visibility; verify documents land in the chosen space/folder and can be loaded for right-panel content display
- [x] 5.3 Rework edit/delete to scope by space and drop the owner-only rule (any visible member may edit/delete any doc in the space); verify a member can edit another member's team document
- [x] 5.4 Add move API reassigning spaceId/folderId only (destination must be visible); verify bidirectional personal<->team<->public moves keep content and reject invisible destinations
- [x] 5.5 Extend Redis detail/list cache keys with space scope and invalidate per space after create/edit/delete/move/restore; verify stale cache is not served after a mutation
- [x] 5.6 Soft-remove `category` from document DTOs/APIs (keep DB column); verify create/edit succeed without category and compile passes
- [x] 5.7 Add authorized-space document search with explicit keyword match mode (`title`, `titleOrContent`, `content`); verify search never returns documents outside the login user's visible spaces
- [x] 5.8 (P3) Root-level documents (`folderId` null) may exist and be edited; move supports placing at space root (plan A)

## 6. Backend: Recycle Bin

- [x] 6.1 Add per-space recycle list API (logically deleted documents and folders with deleteTime/deleteBy); verify delete time and deleting user are returned
- [x] 6.2 Add restore API flipping isDelete=0 for a document or a folder subtree; verify whole-tree restore and root fallback when the original parent is permanently gone
- [x] 6.3 Add permanent-delete API requiring a confirm flag, physically deleting the item (and subtree); verify unconfirmed requests leave the item, confirmed requests remove it entirely
- [x] 6.4 Enforce recycle-bin read-only semantics; verify a recycled document/folder cannot be edited or moved and its content cannot be opened (only metadata, restore, and permanent delete available)
- [x] 6.5 (P0) Recycle chain bypasses global logical delete via hand-written SQL (list/restore/permanent-delete of documents and folder subtrees verified by live API tests)

## 7. Frontend

- [x] 7.1 Restructure the wiki entry into a two-pane shell with `[公开文档] [团队文档] [个人文档] [回收站]`; verify regions render per viewer
- [x] 7.2 Add folder/document tree component per space (infinite folder nesting, expand/collapse, documents under folders); verify nested folders and contained documents display in the left navigation
- [x] 7.3 Add location picker (space -> folder) reused by create and move; verify create requires folder and move works between regions
- [x] 7.4 Add recycle-bin view per space with delete time/deleter, restore and confirm-then-permanent-delete actions; verify flow end to end
- [x] 7.5 Regenerate/update frontend API files from backend OpenAPI; verify `npm run type-check` shows no errors in changed files and `npm run build-only` passes (repo-wide type-check still blocked by pre-existing picture-module errors; changed wiki files are clean)
- [x] 7.6 Add the admin-only "document space management" area listing all team spaces with membership management and create-team-space actions (plus team-space delete/restore flows); verify admin sees it and ordinary users do not
- [x] 7.7 Remove the `category` input from the editor and the search form; verify the UI no longer sends or displays category
- [x] 7.8 Add child-folder creation from any selected folder in the left tree; verify folder-under-folder creation works from the UI
- [x] 7.9 Keep the right panel filter/search controls as the default state; verify selecting a concrete document from the left tree or search results shows its title and content in the same panel
- [x] 7.10 Add search match-mode control for title-only, title-or-content, and content-only; verify each mode changes returned results as specified
- [x] 7.11 (F3) Create/edit save returns to the two-pane shell and opens the document via `?open=`; recycle restore/permanent-delete pass spaceId for cache invalidation
- [x] 7.12 (P2) Admin panel UI (team list / member management / delete / restore / permanent delete), folder rename / move / delete entries, document move modal, recycle-view stays on space switch
- [x] 7.13 (P3) Root-level documents render in the left tree under their space node

## 8. Migration & Data

- [x] 8.1 Write backfill for existing `document_wiki` rows into their author's personal space at root (folderId null); verify no Stage 1 document is orphaned without a spaceId
- [x] 8.2 Verify rollback path: old code against added nullable columns keeps working (no destructive change)
- [x] 8.3 (P3) `wiki_space` gains generated-column unique key `(type=0, ownerUserId)` to prevent concurrent duplicate personal spaces

## 9. Verification

- [x] 9.1 Run `openspec validate add-wiki-space-folder-model --strict` and fix any findings
- [x] 9.2 Run backend unit tests for the new services and `mvn -DskipTests package`; verify pass
- [x] 9.3 Run frontend `npm run build-only`; verify pass
- [ ] 9.4 Manually verify in the browser: personal/team/public visibility, admin-only team creation and member add, folder create/move/delete including nested folder creation from the UI, document create with folder, left-tree document display (incl. root docs), right-panel content display, authorized-space search modes, cross-space move, recycle restore and permanent delete — API-level flows already exercised end to end in P0-P3 rounds
- [ ] 9.5 Manually verify Stage 1 wiki list/detail/edit still function after the rework (regression)

## 10. Fix Rounds P0-P3 (Review Findings)

- [x] 10.1 (P0) Detail cache-hit bypasses space visibility — fixed with pre-cache `requireVisibleSpace`
- [x] 10.2 (P0) Recycle chain broken by global logical delete (list empty / manual `set isDelete` ignored / restore & physical delete no-op) — fixed with hand-written SQL on mappers
- [x] 10.3 (P1) Cache invalidation missing on recycle/folder/space lifecycle ops — unified `WikiCacheManager` wired into document/folder/recycle/space controllers
- [x] 10.4 (P1) Admin space list leaked every user's personal space — removed admin over-broad branch (Decision 11)
- [x] 10.5 (P2) Admin management UI, folder/doc move & rename & delete entries, recycle-view interaction — implemented in shell
- [x] 10.6 (P3) Team-space delete/restore/permanent-delete lifecycle rewritten with hand-written SQL + TEAM/deleted-state guards; verified physically deletes rows
- [x] 10.7 (P3) Root-level documents (folderId null): validation relaxed, move-to-root supported, `GET /documentWiki/root/list`, left-tree display
- [x] 10.8 (P3) Concurrent personal-space duplicate prevention via generated-column unique key

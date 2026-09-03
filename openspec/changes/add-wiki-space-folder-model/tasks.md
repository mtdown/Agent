# Tasks: Wiki Spaces, Folders, and Recycle Bin (Stage 2)

## 0. Pre-Implementation Review

- [ ] 0.1 Review Stage 1 `document_wiki` controller/service/entity and the existing `getLoginUser`/`isAdmin` helpers; verify expected files are present
- [ ] 0.2 Review picture module `Space`/`SpaceUser` membership style as a reference, confirming wiki tables stay independent (design Decision 1)
- [ ] 0.3 Confirm the four delta specs (wiki-space, wiki-folder, wiki-document, wiki-recycle-bin) map 1:1 to the four planned feature areas

## 1. Database

- [ ] 1.1 Add `cloud/sql/create_table_wiki_space.sql` with type/name/ownerUserId/isDelete and indexes; verify DDL executes on the local MySQL
- [ ] 1.2 Add `cloud/sql/create_table_wiki_space_user.sql` with spaceId/userId/spaceRole/isDelete; verify DDL executes
- [ ] 1.3 Add `cloud/sql/create_table_wiki_folder.sql` with spaceId/parentId/name/isDelete; verify DDL executes and self-referential parent is same-space only (enforced in service)
- [ ] 1.4 Add `ALTER TABLE document_wiki` script adding spaceId/folderId/deleteTime/deleteBy (nullable first); verify columns exist
- [ ] 1.5 Add startup initializer that seeds the single public wiki space idempotently; verify a second startup does not duplicate it

## 2. Backend: Space Domain

- [ ] 2.1 Add `WikiSpace` entity/mapper/service (with `WikiSpaceServiceImpl`); verify compile via `mvn -DskipTests package`
- [ ] 2.2 Add personal-space auto-provisioning on registration and lazily on login; verify a registered user and a pre-existing user both end up with exactly one personal space (unit test or DB check)
- [ ] 2.3 Add team-space create API restricted to platform admin; verify non-admin create returns `NO_AUTH_ERROR`
- [ ] 2.4 Add team membership APIs (admin adds/removes member, member list); verify added user sees the team under their joined list and a removed user does not
- [ ] 2.5 Add space list API returning public space, joined team spaces, and own personal space per viewer; verify personal/team contents are never leaked to non-visible users
- [ ] 2.6 Add member self-exit API (remove own membership row); verify the team space disappears from the user's joined list while documents they authored remain in the space
- [ ] 2.7 Add admin team-space delete/restore/permanent-delete APIs; verify empty space deletes outright, non-empty space plus all contents logically deletes (deleteTime/deleteBy), restore brings the whole subtree back, and permanent delete requires a confirm flag

## 3. Backend: Access Check

- [ ] 3.1 Implement `checkSpaceVisible(space, loginUser)` helper encoding public/personal/team rules (design Decision 3); verify each branch with unit tests
- [ ] 3.2 Wire the helper into space/folder/document/recycle controllers; verify invisible-space operations return `NO_AUTH_ERROR`

## 4. Backend: Folder Domain

- [ ] 4.1 Add `WikiFolder` entity/mapper/service with create/rename/move/delete; verify nested folder creation beyond one level works
- [ ] 4.2 Enforce folder belongs to the acting user's visible space; verify cross-space folder ops are rejected
- [ ] 4.3 Enforce documents attach to a folder (reject create/move with no folder, per spec wiki-folder); verify API rejects folder-less document create
- [ ] 4.4 Implement folder delete as logical delete of the folder plus its document/nested-folder subtree in one transaction; verify subtree rows flip to deleted together
- [ ] 4.5 Reject cross-space folder moves (folder may only move inside its own space); verify moving a folder under another space's folder is rejected and its location is unchanged

## 5. Backend: Document Domain Rework

- [ ] 5.1 Extend `DocumentWiki` entity and DTOs with spaceId/folderId/deleteTime/deleteBy; verify compile
- [ ] 5.2 Rework create API to require destination space + folder, validating visibility; verify documents land in the chosen space/folder
- [ ] 5.3 Rework edit/delete to scope by space and drop the owner-only rule (any visible member may edit/delete any doc in the space); verify a member can edit another member's team document
- [ ] 5.4 Add move API reassigning spaceId/folderId only (destination must be visible); verify bidirectional personal<->team<->public moves keep content and reject invisible destinations
- [ ] 5.5 Extend Redis detail/list cache keys with space scope and invalidate per space after create/edit/delete/move/restore; verify stale cache is not served after a mutation
- [ ] 5.6 Soft-remove `category` from document DTOs/APIs (keep DB column); verify create/edit succeed without category and compile passes

## 6. Backend: Recycle Bin

- [ ] 6.1 Add per-space recycle list API (logically deleted documents and folders with deleteTime/deleteBy); verify delete time and deleting user are returned
- [ ] 6.2 Add restore API flipping isDelete=0 for a document or a folder subtree; verify whole-tree restore and root fallback when the original parent is permanently gone
- [ ] 6.3 Add permanent-delete API requiring a confirm flag, physically deleting the item (and subtree); verify unconfirmed requests leave the item, confirmed requests remove it entirely
- [ ] 6.4 Enforce recycle-bin read-only semantics; verify a recycled document/folder cannot be edited or moved and its content cannot be opened (only metadata, restore, and permanent delete available)

## 7. Frontend

- [ ] 7.1 Restructure the wiki entry into a sidebar shell with `[公开文档] [我的团队] [我的个人区]`; verify regions render per viewer
- [ ] 7.2 Add folder tree component per space (infinite nesting, expand/collapse); verify nested folders display
- [ ] 7.3 Add location picker (space -> folder) reused by create and move; verify create requires folder and move works between regions
- [ ] 7.4 Add recycle-bin view per space with delete time/deleter, restore and confirm-then-permanent-delete actions; verify flow end to end
- [ ] 7.5 Regenerate/update frontend API files from backend OpenAPI; verify `npm run type-check` shows no errors in changed files and `npm run build-only` passes
- [ ] 7.6 Add the admin-only "document space management" area listing all team spaces with membership management and create-team-space actions (plus team-space delete/restore flows); verify admin sees it and ordinary users do not
- [ ] 7.7 Remove the `category` input from the editor and the search form; verify the UI no longer sends or displays category

## 8. Migration & Data

- [ ] 8.1 Write backfill for existing `document_wiki` rows into their author's personal space at root (folderId null); verify no Stage 1 document is orphaned without a spaceId
- [ ] 8.2 Verify rollback path: old code against added nullable columns keeps working (no destructive change)

## 9. Verification

- [ ] 9.1 Run `openspec validate add-wiki-space-folder-model --strict` and fix any findings
- [ ] 9.2 Run backend unit tests for the new services and `mvn -DskipTests package`; verify pass
- [ ] 9.3 Run frontend `npm run build-only`; verify pass
- [ ] 9.4 Manually verify: personal/team/public visibility, admin-only team creation and member add, folder create/move/delete, document create with folder, cross-space move, recycle restore and permanent delete
- [ ] 9.5 Manually verify Stage 1 wiki list/detail/edit still function after the rework (regression)

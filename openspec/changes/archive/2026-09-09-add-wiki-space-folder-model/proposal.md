# Change: Wiki Spaces, Folders, and Recycle Bin (Stage 2)

## Why

Stage 1 delivered a flat per-user `document_wiki` CRUD. The wiki product needs structured organization: documents currently have no space, no folder, and no shared collaboration. This change introduces the three-region wiki model (public / team / personal), nested folders, space-scoped access, and a recycle bin, so documents become navigable and shareable within the existing application.

## What Changes

- Introduce **wiki spaces** with three fixed types: `public` (one global space, every logged-in user may operate), `team` (multiple, membership-based; only platform `admin` can create a team space and add members), `personal` (one per user, created at registration or lazily on first login).
- Add **wiki folders**: infinitely nested folder tree per space (`parentId`), documents must live inside a folder.
- **Access is space-scoped**: whoever can see a space may freely operate on everything inside it (documents and folders, including items created by others). Documents themselves carry no per-document permission. Space membership rows keep a role field (`spaceRole`) as a forward-compatible placeholder; current behavior treats every visible member as fully privileged.
- Change document creation/movement: creating a document requires choosing a destination space (then a folder). Moving a document reassigns `spaceId`/`folderId` only; content is unchanged; movement is allowed in any direction (personal <-> team <-> public).
- Rework the wiki entry into a left navigation tree plus right content/search panel: left side shows `公开文档 -> folders -> documents`, `团队文档 -> folders -> documents`, `个人文档 -> folders -> documents`, and `回收站`; selecting a document opens its content on the right without leaving the shell.
- Fix wiki search/filter behavior: the default right panel search runs across all spaces visible to the current user, and keyword matching supports title-only, title-or-content, and content-only modes.
- Rework **delete into recycle-bin semantics**: first delete is a logical delete (reuse `isDelete`), recorded with delete time and deleting user; each space has its own recycle-bin view; recovery restores the whole sub-tree; permanent delete is physical (after secondary confirmation).
- Keep platform roles as-is (`user`/`admin`); no new platform tier. `admin` additionally manages team-space creation and membership, sees an extra "document space management" wiki area listing all team spaces, and may delete/restore team spaces.
- Soft-remove the Stage 1 `category` field from the document API and UI (DB column retained, no migration); space and folder replace it as the organizing dimension.

### Out of Scope

- Per-document permission settings (permission lives on the space only).
- Public-space content review/moderation (S4a: no review).
- RAG, upload parsing, version history, and wiki-wide analytics.
- Letting ordinary users create or self-join team spaces (admin-managed membership only).
- Splitting the platform `admin` role into finer tiers.

## Capabilities

### New Capabilities

- `wiki-space`: the three-region space model (`public`/`team`/`personal`), creation rules (admin-only for team spaces, personal space auto-provisioning), team membership managed by platform `admin`, and space-level visibility.
- `wiki-folder`: per-space nested folder tree; visible members may create, rename, move, and delete folders; documents attach to a folder.
- `wiki-document`: space-aware document CRUD where every visible member is fully privileged; destination selection on create; bidirectional move by reassigning space/folder; tree-based document browsing and authorized-space search.
- `wiki-recycle-bin`: recycle view per space over logically deleted documents/folders, recording delete time and deleter; whole-tree restore; physical permanent delete.

### Modified Capabilities

(none — Stage 1 delta specs were never synced to `openspec/specs/`; the Stage 1 `document_wiki` behavior change is captured under `wiki-document` above.)

## Impact

- **Database**: new `wiki_space`, `wiki_space_user`, `wiki_folder` tables; `document_wiki` gains `spaceId`, `folderId`, `deleteTime`, `deleteBy` (reuse `isDelete`); personal-space provisioning for existing users on login.
- **Backend**: new entities/mappers/services/controllers for space, space-user, folder, and recycle operations; `DocumentWikiController` reworked to require a space/folder context; permission checks move to space membership; Redis cache keys adjusted to space scope.
- **Frontend**: wiki entry becomes a sidebar layout with `[公开文档] [团队文档] [个人文档] [回收站]`; each visible space expands into nested folders and documents; selecting a document renders its content in the right panel; the default right panel keeps the search/filter controls.
- **Relationship to Stage 1**: supersedes the flat CRUD of `add-wiki-document-model`; that change remains in-progress and its verification tasks are unaffected, while its data model is extended here.

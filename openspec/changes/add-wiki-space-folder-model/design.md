# Design: Wiki Spaces, Folders, and Recycle Bin (Stage 2)

## Context

Stage 1 (`add-wiki-document-model`) produced a flat `document_wiki` CRUD with a textarea editor, Redis read caches, and owner-or-admin checks via `getLoginUser` + `checkDocumentWikiAuth`. The picture module already contains a proven space model — `Space(PRIVATE/TEAM)` + `SpaceUser` membership + Sa-Token permission annotations + a JSON-driven role→permission map — but that model is tightly coupled to pictures (permission codes like `picture:view`, space contexts resolved from picture ids).

The wiki product needs its own three-region model. See proposal.md for the motivation; this design covers how to implement it on top of the Stage 1 codebase.

## Goals / Non-Goals

**Goals:**
- A single `wiki_space` table holding all three region types so rules stay uniform.
- "Space is the permission boundary": visibility of a space implies full operation rights inside it; documents carry no permission fields.
- Nested per-space folders with documents always inside a folder; recycle-bin style delete (logical first, recorded, restorable, physical permanent delete only on confirmation).
- Platform `admin` manages team spaces and membership; ordinary users only operate inside spaces they can see.

**Non-Goals:**
- Per-document ACLs, public-space moderation, RAG, version history.
- Reusing picture `Space`/`Sa-Token` machinery wholesale for wiki (see Decision 3).
- Fine-grained space-role enforcement now (roles are stored, not yet differentiated).

## Decisions

### 1. Independent wiki tables (route Y), not picture `Space` reuse

New tables:
- `wiki_space(id, type, name, ownerUserId, createTime, updateTime, isDelete)` — `type` in {0 personal, 1 team, 2 public}; personal and public spaces also live here so every region shares one shape (user decision: "个人区需要建表，为了逻辑一致").
- `wiki_space_user(id, spaceId, userId, spaceRole, createTime, updateTime, isDelete)` — membership for team spaces; `spaceRole` defaults to `editor` and is a forward-compatible placeholder (see Decision 4). Personal/public spaces do not store membership rows; access derives from type rules.
- `wiki_folder(id, spaceId, parentId, name, createTime, editTime, updateTime, isDelete)` — `parentId` NULL = top-level; infinite nesting enforced by `parentId` pointing to a folder in the same space; a "recycle" for folders is the same `isDelete` flag.
- `document_wiki` gains `spaceId` (NOT NULL), `folderId` (nullable — see below), `deleteTime`, `deleteBy`; reuses `isDelete`.

Alternatives considered: reusing picture `Space` + its `StpInterfaceImpl` pipeline (rejected: couples wiki to picture semantics and would require re-architecting space contexts for two domains sharing one table).

### 2. One canonical public space, per-user personal space auto-provisioning

- A single public space row (`type=2`) is seeded on startup if absent (idempotent initializer, mirroring `AdminInitializer`).
- Personal space: created on user registration (and, for pre-existing users, lazily on first login — the same login hook checks existence and inserts). Deleting a personal space is not offered.
- Team spaces: created only by platform `admin` (enforced in controller); membership rows added by platform `admin` via the team-space user management surface. Ordinary users never create/join team spaces.

### 3. Access checks are plain service/controller logic, not a copied Sa-Token pipeline

A single `checkSpaceVisible(space, loginUser)` helper encodes the three rules:
- `type=public` → any logged-in user.
- `type=personal` → `space.ownerUserId == loginUser.id` OR platform admin.
- `type=team` → membership row exists in `wiki_space_user` OR platform admin.

Any visible space ⇒ full rights on its documents and folders (create/edit/delete/move/restore/permanent-delete), including items authored by others. Platform `admin` bypasses visibility for management actions (create team space, manage members, physical folder deletion is governed by the same "visible means full power" rule plus confirmations).

Why not copy picture's `@SaSpaceCheckPermission`? The wiki rule set is a flat "in or out" predicate resolved from `spaceId` — replicating `StpInterfaceImpl` request-context sniffing would add machinery without a second permission dimension today. `wiki_space_user.spaceRole` is stored so a future role tier can plug into the same helper without schema change.

### 4. Space roles: stored, uniform for now

`spaceRole` values mirror `SpaceRoleEnum` (`viewer`/`editor`/`admin`) for naming consistency, but Stage 2 treats every member identically (full power). Admin adds members and may set the role; enforcement of role differences is deferred. Rationale: the user expects roles to matter eventually (team self-management, read-only guests) and storing them now avoids a migration later; deferring enforcement avoids inventing product rules nobody has specified yet.

### 5. Folder rules

- Folders belong to exactly one space; every operation validates the folder's `spaceId` against the acting user's visibility.
- Creation/rename/move/delete of folders is allowed for any visible member (the earlier "admin-only folder delete" rule is superseded by the new "visible means full power + recycle bin" rule).
- The frontend must expose child-folder creation from any selected folder, passing that folder as `parentId`; users must be able to create folder-under-folder structures from the UI, not only through the backend API.
- Deleting a folder is a logical delete of the folder **and its whole subtree** (documents + nested folders) in one transaction; the subtree is recoverable as a unit.
- `document_wiki.folderId` is nullable at the DB layer: NULL means "space root" and exists purely as a migration/restore fallback. New/edited documents must pass a real folder id (UI enforces folder selection); records whose parent folder was permanently deleted fall back to root on restore.

### 6. Recycle bin = a view over logical deletes, per space

- First delete: `UPDATE ... SET isDelete=1, deleteTime=now(), deleteBy=<userId>` — no row movement. The recycle-bin endpoint queries `isDelete=1` within a space (documents and folders; folders bring their subtree).
- Restore: flip `isDelete=0` for the chosen subtree (recursive); if an ancestor folder is missing (permanently deleted), restore to that space's root.
- Permanent delete: physical `DELETE` after a second confirmation; cascades over the subtree.
- Redis detail/list cache keys are already space-scoped-ish; extend the key with `spaceId` and invalidate per space on create/edit/delete/restore/move.

### 7. Move = reassign location only

Moving a document (or folder) updates `spaceId`/`folderId` only; content untouched. Bidirectional between personal/team/public, subject to the destination being visible to the actor. The UI reuses the "choose destination space → choose folder" picker from create. No copy semantics; no permission conversion because permissions live on spaces, and the document inherits the destination space's rules automatically.

### 8. Frontend layout

Wiki entry becomes a two-pane shell:
- Left navigation is the primary browse tree: `公开文档 -> visible public space -> folders -> documents`, `团队文档 -> joined team spaces -> folders -> documents`, `个人文档 -> own personal space -> folders -> documents`, plus `回收站`.
- Each folder node can contain both child folders and documents. Folder nesting is unlimited and must be visible/operable from the tree.
- The right panel defaults to the existing filter/search controls. When the user selects a concrete document in the left tree or in search results, the right panel renders that document's title and content in-place.
- Document create/edit reuse `DocumentWikiEditor.vue`; a location picker (space -> folder) drives both create and move.
- The recycle-bin view lists deletable documents/folders with delete-time/deleter and restore/permanent-delete actions.

### 8a. Search/filter semantics

Search runs against the current user's authorized space set, not only the currently selected folder. The backend derives the visible public/team/personal space ids for the login user, intersects any explicit space filters with that allowed set, and rejects or ignores invisible targets. The keyword match mode is explicit:
- `title`: match title only.
- `titleOrContent`: match title or content.
- `content`: match content only.

Search results include document identity, title, summary/content preview, and space/folder context so selecting a result can open the full content in the right panel.

### 9. Member exit semantics

Being removed from a team space by the admin, or exiting voluntarily, removes only the membership row. Documents authored by that member stay in the space: documents belong to their space, not their author (spec `wiki-document`). The personal space is never affected by team membership changes.

### 10. Team-space deletion is an admin lifecycle outside the per-space recycle bin

A space-level recycle view cannot live inside a deleted space, so team-space deletion is its own admin-managed lifecycle in the "document space management" area:
- empty team space → physical delete after confirmation;
- non-empty team space → logical delete of the space row plus every document/folder row (with `deleteTime`/`deleteBy`) in one transaction; the deleted space appears in the management area where the admin may restore it (whole subtree) or permanently delete it (after confirmation).
Personal and public spaces are never deletable.

### 11. Admin wiki surface ("document space management" area)

The platform admin sees an extra wiki navigation area listing all team spaces regardless of membership, with membership management and create-team-space actions; personal and public regions behave as for ordinary users. The area lives inside the wiki UI as its own region (per P3), consistent with the project's existing pattern of admin routes hidden from non-admin menus, rather than a separate backend.

### 12. Recycle bin is read-only

Recycled documents/folders support only restore, permanent delete, and viewing `deleteTime`/`deleteBy`. Editing, moving, and opening content are unavailable inside the recycle view.

### 13. Soft-remove `category`

`document_wiki.category` stays in the database (no destructive migration) but is removed from DTOs, APIs, and the frontend editor/search form — space and folder now provide organization. Existing column values are ignored.

## Risks / Trade-offs

- [Team docs fully deletable by any member (incl. others' docs)] → Mitigated by recycle bin: destructive action is two-step (logical → physical with confirmation), and delete time/deleter are recorded for audit.
- [Moving into a space the actor can see but shouldn't later manage (e.g. personal→public makes the doc editable by everyone)] → Accepted by design ("visible means full power"); move UI should show a plain-language warning before crossing into public/other-team space.
- [Logical-delete subtree bookkeeping] → Restore and permanent-delete operate on the subtree snapshot taken at delete time (recursive query over spaceId+isDelete); parent-missing fallback to root keeps invariants simple.
- [Personal-space lazily created on login touches the login path] → Keep the check idempotent and transactional; worst case is one extra existence query per session.

## Migration Plan

1. Ship DDL: three new tables + `ALTER TABLE document_wiki ADD spaceId, folderId, deleteTime, deleteBy` (columns nullable at first).
2. Seed public space; add personal-space auto-provisioning on registration/login.
3. One-time backfill script: for each existing `document_wiki.userId`, ensure a personal space exists, then set `spaceId` (docs land at root, `folderId = NULL`).
4. Deploy backend (new controllers; `DocumentWikiController` now requires space context) together with frontend (sidebar layout, picker, recycle views). No destructive data change: rollback = keep old code paths against added nullable columns.

## Open Questions

- Sorting order of documents/folders within a folder (time-based default is fine).
- Recycle-bin pagination/UX thresholds (list size is tiny at this stage).

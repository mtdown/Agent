# Design

## Context

See [proposal.md](proposal.md). Cloud owns login identity, Wiki permission checks and persistence; `cloud_agent` owns the model tool loop. Existing `write_wiki_document(spaceId,title,content)` requires a raw ID and saves to the space root. `listVisibleSpaceVis` includes team viewers. Folder mutations and some document/recycle mutations currently authorize visibility instead of write access. The user Skill library is scoped to an owner or explicit shares, so a default Skill cannot be created once as an ordinary user's record and become available to all users. The default Agent loop has four model turns and a cumulative 16,000-character tool-result limit.

## Goals / Non-Goals

**Goals:** Give the Agent a user-scoped directory lookup and two distinct creation actions; keep permission, name collision and location validation in Cloud; provide a versioned, built-in workflow Skill to all logged-in users; make existing Wiki read/write entrypoints obey one space policy. Each of the three delivery stages can be reviewed before the next stage begins.

**Non-Goals:** Agent-driven search, edit, delete, manual document references, web search, creation of team spaces, or a new Wiki data model. Existing page edits still update the same document ID; the proposed future Agent edit/replacement flow is outside this change.

## Decisions

### 1. One space-access entrypoint with an explicit purpose

Add `requireSpaceAccess(spaceId, user, boolean isWrite)` and a corresponding predicate in `WikiSpaceService`. With `isWrite=false`, delegate to the existing visibility path without changing its behavior. With `isWrite=true`, use the edit rules: any logged-in user for public, owner or platform admin for personal, team admin/editor or platform admin for team; reject deleted spaces. Keep `requireVisibleSpace` and `requireEditableSpace` as readable compatibility wrappers around the new policy. Do not expose `isWrite` as a model-supplied argument: each Cloud controller/service sets it according to the fixed operation.

Add `requireFolderAccess(folderId, expectedSpaceId, user, isWrite)`: load the actual active folder, verify its recorded `spaceId` matches the expected destination, then check that actual space with the same purpose. Existing read helpers delegate with `false`. For a deleted recycle item, load through the existing include-deleted mapper and check its recorded space before restoring or permanently deleting; reject mismatch with any request-supplied `spaceId`.

Apply write checks to document add/import/edit/delete/move, folder create/rename/move/delete/restore/permanent delete, attachment/batch imports, and recycle restore/permanent delete, including batch paths. For a move check both source and destination. Keep list/detail/tree/search/recycle-list on visibility checks. The separate team-space administration rules remain unchanged. Alternative considered: keep each controller's visibility check and special-case public; rejected because a team viewer would retain write access through some paths.

### 2. Read-only directory tool with bounded responses

Cloud adds a dedicated user-scoped directory reader rather than returning `listFolderTree` (which also loads documents and author profiles). It derives the user from the authenticated Cloud-to-Agent request, lists visible spaces, retains only spaces passing the write predicate, and returns `spaceId`, `type`, `name` as decimal-string IDs. An optional selected `spaceId` requests a page of active folders with `folderId`, `parentId`, `name` and a continuation cursor. No document body enters this tool result. Stable sorting by folder ID makes paging deterministic. A bounded page protects the model context and the existing cumulative tool-result limit.

Register `list_writable_wiki_locations` in `AgentToolRegistry`, add the method to `AgentLibraryGateway`/HTTP gateway and an authenticated internal Cloud endpoint. Tool arguments contain an optional selected space and cursor, never a user ID. The system instruction selects the personal space when the user did not specify a destination; a named team must match one writable space, otherwise the Agent stops and asks. Cloud rechecks the ID at creation time. Alternative considered: filter the existing Wiki tree response in Agent; rejected because it includes read-only spaces and document data, and authorization would be duplicated outside Cloud.

### 3. Separate folder and document creation with deterministic names

Register `create_wiki_folder(spaceId, taskName)` and `create_wiki_document(spaceId, folderId, title, content)` as separate tools and Cloud handlers. The folder handler creates a new root folder for each Agent write using a normalized task name plus the Cloud server's `Asia/Shanghai` date in `YYYY-MM-DD` format. The document handler requires an active folder in the same writable space. The Agent proposes a concise title based on the content; Cloud trims, validates and falls back to a short leading heading/text when blank. Cloud applies `（1）`, `（2）` and so forth to an active sibling folder or document name collision, respecting the current 128-character database columns.

Each handler has its own transaction and uses a row lock on the destination space while checking sibling names and inserting, so concurrent Agent writes through these handlers allocate distinct names. Legacy page writes can still use their existing naming behavior; no global uniqueness migration is proposed. For document creation, reuse the current summary/metadata validation and RAG index event path and clear the space cache after success. Return actual folder/document IDs as strings. If folder creation succeeds but the later document call fails, retain the empty folder and report its ID and failure; do not pretend the two model-mediated calls are one transaction. Alternative considered: one monolithic write tool; rejected because the user wants inspectable atomic actions and a reusable workflow Skill.

The existing `write_wiki_document` name is retained as a compatibility entrypoint for saved user Skills. It routes to the same server-side folder/document functions, creates its own task folder from the supplied title and returns the resulting IDs; it no longer writes a document to the space root. The built-in workflow uses the new separate tools. This is a behavior change for legacy callers and must be called out in tests and release notes.

### 4. Built-in workflow Skill and loop budget

Represent the workflow as a code-managed, versioned built-in Skill in Cloud, separate from user-owned `agent_skill_item` rows. Put its summary in the per-user Skill catalog and serve its full validated definition through the existing `get_skill` path using a reserved built-in ID. User records cannot override that ID. The Skill describes when to use the Wiki write flow, the tool order, personal default, team ambiguity stop, result check and failure report. Cloud's allowed tool-name validation includes the new tools. Agent's fixed instruction prefers the built-in Skill for Wiki saves while preserving user Skill selection for unrelated tasks. The model receives tool declarations from `AgentToolRegistry`; Skill text is guidance and never grants permissions.

`get_skill`, directory lookup, folder creation, document creation and the final answer need at least five model turns when called sequentially. Increase the default limit from four to six and verify with a scripted model test. Keep the existing timeout and result budget; measure regression in Agent evaluation before considering a larger limit. Alternative considered: inline all workflow text in the system prompt to stay within four turns; rejected because it would not use the Skill catalog/get-skill contract requested for this capability.

### 5. Review and verification gates

Stage 1 delivers policy checks, read-only directory endpoint/tool and permission tests; stage 2 delivers the two creation tools, naming/location tests and cache/RAG checks; stage 3 delivers the built-in Skill and loop sequencing tests. After each stage, present changed files and relevant assertions for the user's review before proceeding. The final automated pass includes Cloud/Agent targeted tests, builds and strict OpenSpec validation. Run the AGENTS.md §8 assumption review and mutation check before reporting results. If formal tests fail, record the issue and stop for the owner's repair decision. After automated verification, use `stop-dev.ps1` then `start-dev.ps1` and hand the owner an explicit browser checklist; do not perform page tests by default.

## Risks / Trade-offs

- **Public content can be changed by any logged-in user** → apply the same rule consistently to all existing Wiki mutations; retain creator/delete audit fields and recycle semantics. Platform team-space administration remains separate.
- **Team viewers currently have write access through visibility checks** → update every mutation path and test read-allowed/write-denied pairs, including cache-hit paths and batch operations.
- **A model may choose the wrong team or claim a save that failed** → return only writable candidates, require a unique match, reauthorize on write and require the saved document ID in the final result.
- **Two tool calls can leave an empty task folder** → expose partial success and its folder ID; no silent cleanup that might delete later user content.
- **Concurrent names and long generated titles** → serialize Agent naming per destination space, cap names to 128 characters including suffix, and test collisions and rollback.
- **Built-in Skill and existing user Skills compete for attention** → reserve a unique built-in ID, give Wiki-write requests an explicit selection rule, and test tool traces rather than relying on prompt wording alone.
- **Six turns may increase latency and token use** → compare with the pre-change Agent baseline and preserve timeout/result caps.

## Migration Plan

1. Merge the permission and query stage without changing tables; verify old read paths and all write paths.
2. Add creation tools while keeping the legacy tool name as a compatibility entrypoint; add the built-in Skill after tool integration is verified.
3. If rollback is needed, disable the new tools and Skill and restore prior permission entrypoints in the task branch or deployment. Existing Wiki data is retained; any task folders created before rollback remain normal Wiki folders.

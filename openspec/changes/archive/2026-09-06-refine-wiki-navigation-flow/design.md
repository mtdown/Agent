## Context

See `proposal.md` for motivation. The current layout already has a black top navigation and a Wiki three-column workspace from `refine-wiki-layout`. The screenshot shows the remaining mismatch: the Wiki page still has an in-page tab row for `文档`, `回收站`, and `文档空间管理`, and document list items still offer a `查看` action that navigates to `/documentWiki/:id`.

## Goals / Non-Goals

**Goals:**

- Make the top navigation the only page-level switcher for Wiki document, recycle bin, and document space management.
- Remove the in-page Wiki secondary tabs.
- Remove `文档管理` from the top navigation.
- Keep document reading in the `/documentWiki` three-column workspace when launched from the document list.
- Keep existing edit, move, delete, search, recycle, and space-management behavior available.

**Non-Goals:**

- No backend API changes.
- No database or permission model changes.
- No redesign of the document detail page beyond removing it from the normal list-reading path.
- No removal of the `/documentWiki/:id` route unless implementation proves it is unused and safe to remove.

## Decisions

### Decision: Represent Wiki sub-interfaces through top navigation state

Top navigation should contain `WIKI文档`, `文档空间管理`, and `回收站` as direct entries. Selecting one opens the corresponding interface immediately. The Wiki page itself should not render a second tab row for these options.

Implementation can continue using route query state such as `/documentWiki?region=recycle` and `/documentWiki?region=manage`, or it can introduce dedicated paths if that is cleaner. The user-visible behavior is the important contract: one top click reaches the intended interface.

Alternative considered: keep page tabs as a local backup. This was rejected because it duplicates the top navigation and keeps the clutter shown in the screenshot.

### Decision: Remove `文档管理` as a separate top-level entry

`文档管理` should be removed from the top navigation. The document list and document workspace already cover document access and document operations, while administrator-only document space administration has its own `文档空间管理` entry.

Alternative considered: keep `文档管理` but route it to the normal document workspace. This was rejected because it would add a redundant label for the same user task.

### Decision: List reading opens inline in the center column

Document list reading actions should call the existing inline open behavior and update the selected document in the center column. They should not navigate to `/documentWiki/:id` from normal list usage.

The existing detail route can remain for compatibility with bookmarked or externally shared links. If kept, it should not be surfaced as the primary `查看` action in the list.

Alternative considered: redirect `/documentWiki/:id` back to `/documentWiki?open=<id>`. This is useful but optional; preserving the direct route is lower risk for existing links.

## Risks / Trade-offs

- Removing in-page tabs can make the current sub-interface less obvious after navigation -> top navigation active state must clearly highlight `WIKI文档`, `回收站`, or `文档空间管理`.
- Query-based direct interfaces can still share the same component -> keep route-to-state synchronization simple and covered by tests.
- Removing the `查看` navigation path from lists may surprise users who expect a separate detail page -> preserve direct detail route compatibility while making inline reading the normal path.
- Administrator-only `文档空间管理` must remain hidden from ordinary users -> keep role-based filtering in the top navigation.

## Migration Plan

1. Create or switch to `feature/wiki-navigation-flow开发` from the latest `main`.
2. Add a failing frontend navigation-flow verification that checks the removed top nav item, removed in-page tabs, and inline document reading behavior.
3. Remove `文档管理` from `GlobalHeader`.
4. Remove the Wiki page tab row and render the correct interface directly from top navigation route state.
5. Update document list read/view actions so list reading opens in the current `/documentWiki` workspace center column.
6. Verify ordinary user and administrator navigation visibility.
7. Run frontend type check and build verification.
8. Record any failures or blockers in `IssueLog.xlsx` before further repair loops.

## Rollback Plan

Revert the frontend files changed by this feature branch. Since this change is frontend-only and keeps backend behavior unchanged, rollback does not require database or API migration.

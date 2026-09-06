## 1. Preparation

- [x] 1.1 Create or switch to `feature/wiki-navigation-flow开发` from the latest `main` and verify the current branch before editing.
- [x] 1.2 Inspect `GlobalHeader.vue`, the router, `DocumentWikiListPage.vue`, and `WikiDocumentList.vue`, and verify the implementation points still match `proposal.md` and `design.md`.
- [x] 1.3 Confirm `IssueLog.xlsx` exists and is writable so any implementation or validation problem can be recorded.

## 2. Navigation Contract Tests

- [x] 2.1 Add or update a frontend navigation-flow test that verifies the top navigation excludes `文档管理` while keeping `WIKI文档`, `回收站`, and `文档空间管理` available according to permissions.
- [x] 2.2 Add or update a frontend navigation-flow test that verifies `DocumentWikiListPage.vue` no longer renders the in-page secondary tabs `文档`, `回收站`, and `文档空间管理`.
- [x] 2.3 Add or update a frontend navigation-flow test that verifies normal document list reading does not navigate to `/documentWiki/:id`.

## 3. Top Navigation

- [x] 3.1 Remove the `文档管理` top navigation entry from `GlobalHeader.vue` and verify the navigation-flow test observes it as absent.
- [x] 3.2 Preserve role-based filtering for `文档空间管理` and other administrator entries, and verify ordinary-user and administrator navigation expectations.

## 4. Wiki Page Flow

- [x] 4.1 Remove the in-page secondary tab row from `DocumentWikiListPage.vue` and verify opening `/documentWiki` displays the Wiki document workspace directly.
- [x] 4.2 Route the top navigation `回收站` entry to the recycle bin interface directly and verify no second in-page tab click is required.
- [x] 4.3 Route the top navigation `文档空间管理` entry to the space-management interface directly for administrators and verify ordinary users do not receive that top navigation entry.
- [x] 4.4 Keep route-state synchronization simple, and verify direct entry plus browser back/forward restore the intended Wiki sub-interface.

## 5. Inline Document Reading

- [x] 5.1 Update document list open/read actions so selecting a document keeps the browser URL on `/documentWiki` and displays the selected document in the center column.
- [x] 5.2 Preserve existing edit, move, and delete actions, and verify each action still reaches its existing flow.
- [x] 5.3 Verify the right outline updates when the inline-selected document changes.
- [x] 5.4 Keep the existing `/documentWiki/:id` route available if it is still used by direct links, and verify normal list reading no longer depends on it.

## 6. Validation and Handoff

- [x] 6.1 Run the frontend navigation-flow test and verify it passes.
- [x] 6.2 Run `npm run type-check` in `cloud_front` and verify it passes.
- [x] 6.3 Run `npm run pure-build` in `cloud_front` and verify it passes.
- [x] 6.4 For local preview or manual testing, stop services with `stop-dev.ps1`, then start services with `start-dev.ps1`, and verify the documented top navigation flow in the browser.
- [x] 6.5 Record every error, blocker, or test failure in `IssueLog.xlsx` with the branch name, change ID, stage, cause, fix plan, status, and verification result.
- [x] 6.6 Report the completed changes, validation results, any problems found, and any suggested fix plan before entering another fix-test loop.

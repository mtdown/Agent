## 1. Navigation Entry Updates

- [x] 1.1 Remove the top navigation `文档创建` menu item from `GlobalHeader.vue` and verify the header still highlights `/documentWiki`, recycle, and document space management routes correctly.
- [x] 1.2 Keep `/add_documentWiki` and `/edit_documentWiki/:id` router entries available for direct-link compatibility and verify route definitions remain present.

## 2. Space Tree Creation Entry

- [x] 2.1 Change the selected-space toolbar button in `WikiSpaceTree.vue` from `新建文件夹` to `新建文档` and verify the toolbar no longer exposes `新建文件夹` as its primary action.
- [x] 2.2 Add a create-document event from `WikiSpaceTree.vue` that emits the current selected space and folder context, and verify `DocumentWikiListPage.vue` can receive it.
- [x] 2.3 Preserve folder creation and folder management in the tree node action menu and verify node menu labels still include folder create, rename, move, and delete actions where applicable.

## 3. Center Column Inline Editor

- [x] 3.1 Extend `DocumentWikiEditor.vue` to support create-mode initial space/folder defaults and verify standalone add/edit pages still render the editor correctly.
- [x] 3.2 Add center-column modes to `DocumentWikiListPage.vue` for browse, preview, create, and edit, and verify only the center `wiki-document-column` content is replaced when editing.
- [x] 3.3 Wire inline document creation to `addDocumentWikiUsingPost` and verify a successful save closes the editor, refreshes workspace data, and previews the created document.
- [x] 3.4 Wire inline document editing to `editDocumentWikiUsingPost` and verify a successful save closes the editor, refreshes workspace data, and previews the edited document.
- [x] 3.5 Implement cancel behavior for inline create/edit and verify the center column returns to the previous preview or current directory list.

## 4. Document List Edit Actions

- [x] 4.1 Change selected-preview and list-item edit actions in `WikiDocumentList.vue` to emit an edit event instead of navigating to `/edit_documentWiki/:id`, and verify normal workspace editing keeps the browser on `/documentWiki`.
- [x] 4.2 Keep open, move, and delete events unchanged and verify existing move/delete workflows remain callable from both list rows and selected preview.

## 5. Tests And Verification

- [x] 5.1 Add or update focused frontend source tests to verify top navigation removes `文档创建`, space navigation uses `新建文档`, and workspace edit actions no longer router-push to `/edit_documentWiki/:id`.
- [x] 5.2 Run the focused frontend tests from `cloud_front` and record pass/fail results.
- [x] 5.3 Run frontend type checking and build verification from `cloud_front`; if failures occur in formal testing, report results, likely cause, and proposed fix before starting another fix-test loop.
- [x] 5.4 Start local services for manual verification using `stop-dev.ps1` followed by `start-dev.ps1`, then verify `/documentWiki` can create and edit documents in the center column without leaving the three-column workspace. User manual verification reported all items passed on 2026-09-08.
- [x] 5.5 Record any encountered development, test, build, or manual verification issues in `IssueLog.xlsx` with status and verification result.

## 6. Review And Handoff

- [x] 6.1 Update this task list with verification outcomes and the final result after implementation.
- [x] 6.2 Summarize code changes, tests run, passing items, failing items, discovered issues, initial cause analysis, and recommended next step for user confirmation before upload.
- [x] 6.3 After user approval, upload the task branch using `upload.ps1` with an appropriate commit message and verify the remote task branch is ready for merge request / pull request.

## Verification Result

- OpenSpec strict validation passed for `add-wiki-inline-document-editor`.
- Focused frontend source tests passed from `cloud_front`: `documentWikiEditor.test.mjs`, `openapi.config.test.mjs`, `wikiLayout.test.mjs`, `frontendStartupGuard.test.mjs`, and `wikiNavigationFlow.test.mjs`.
- Frontend type checking passed with `npm run type-check`.
- Frontend production build passed with `npm run build-only`; Vite reported only chunk-size warnings.
- Local services were started with `stop-dev.ps1` followed by `start-dev.ps1` for user manual verification.
- User manual verification reported all requested wiki document navigation, create, and edit flows passed on 2026-09-08.
> 2026-09-09 补勾：负责人确认上述人工验收与上传任务均已实际完成，此前仅漏勾选。

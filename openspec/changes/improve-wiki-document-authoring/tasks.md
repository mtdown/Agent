## 1. Branch And Baseline

- [x] 1.1 Switch implementation work to `feature/wiki-wysiwyg-document-create开发` from latest `main` and verify the active branch before editing code.
- [x] 1.2 Inspect current working tree changes and verify unrelated user changes are not reverted or mixed into the implementation.
- [x] 1.3 Run the available frontend baseline check from `cloud_front` and record any pre-existing failures before implementation.

## 2. WYSIWYG Editor

- [x] 2.1 Add the chosen Vue 3 rich-text editor dependencies to `cloud_front/package.json` and verify dependency installation succeeds.
- [x] 2.2 Replace the Markdown body editor in `DocumentWikiEditor.vue` with a single WYSIWYG authoring surface and verify users do not see a split Markdown source/preview layout.
- [x] 2.3 Add a compact formatting toolbar for paragraph/headings, bold, italic, bullet list, ordered list, quote, undo, and redo, and verify each action changes the visible editor content directly.
- [x] 2.4 Submit new rich-text content as `contentFormat: 'html'` and verify the create/edit payload contains HTML content.
- [x] 2.5 Preserve or explicitly validate image insertion behavior using the existing Wiki image upload endpoint; if image insertion cannot be preserved in this pass, record the deferred scope in `IssueLog.xlsx`.

## 3. Workspace Create Flow

- [x] 3.1 Extend `WikiSearchBar.vue` to emit a `create` action from a `创建文档` button placed to the right of the search button, and verify the existing search action still works.
- [x] 3.2 Add center-column create mode to `DocumentWikiListPage.vue` and verify clicking `创建文档` replaces the center content with the creation form without leaving `/documentWiki`.
- [x] 3.3 Pass the selected left-navigation space/folder into `DocumentWikiEditor.vue` as the initial document location and verify selected folder creation preselects both `spaceId` and `folderId`.
- [x] 3.4 Verify selected space-root creation preselects `spaceId` and leaves `folderId` empty.
- [x] 3.5 On successful create, refresh the tree/list and open the new document in the workspace; verify the new document appears under the selected location.
- [x] 3.6 On cancel, return the center column to the previous browsing state and verify the URL remains `/documentWiki`.

## 4. Navigation And Compatibility

- [x] 4.1 Remove the standalone `文档创建` top-navigation item from `GlobalHeader.vue` and verify the menu no longer displays it.
- [x] 4.2 Preserve direct `/add_documentWiki` compatibility by keeping the page usable or redirecting into `/documentWiki` create mode, and verify direct legacy access does not break.
- [x] 4.3 Verify `WIKI文档`, `文档空间管理`, and `回收站` navigation behavior remains unchanged according to permissions.

## 5. Rendering And Backend Compatibility

- [x] 5.1 Update document detail/workspace rendering so `contentFormat=html` displays rich-text content, and verify saved bold/headings/lists render as formatted text.
- [x] 5.2 Verify existing `contentFormat=markdown` documents still render with Markdown preview.
- [x] 5.3 Verify existing `contentFormat=plain` or missing-format documents still display as plain text.
- [x] 5.4 If backend validation rejects `contentFormat=html`, update the allowed format validation and verify the relevant backend tests pass.

## 6. Verification And Reporting

- [x] 6.1 Run OpenSpec validation for `improve-wiki-document-authoring` and verify it passes.
- [x] 6.2 Run frontend type check/build from `cloud_front` and report pass/fail results.
- [x] 6.3 Run relevant backend tests if backend validation is changed and report pass/fail results.
- [x] 6.4 Record encountered errors, blockers, deferred issues, and verification outcomes in `IssueLog.xlsx`.
- [x] 6.5 After testing, report completed changes, passed checks, failed checks, discovered issues, likely causes, and proposed next fixes; if tests fail, wait for user confirmation before another fix-test cycle.

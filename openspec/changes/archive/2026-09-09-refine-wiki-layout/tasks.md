## 1. Preparation

- [x] 1.1 Create or switch to `feature/wiki-layout开发` from the latest `main` and verify the branch name before code edits
- [x] 1.2 Inspect current frontend routes, global layout, header, sidebar, and Wiki document components; verify the implementation points match `proposal.md` and `design.md`
- [x] 1.3 Confirm `IssueLog.xlsx` is present and writable before implementation; verify any later blockers can be recorded there

## 2. Global Navigation Layout

- [x] 2.1 Remove `GlobalSider` from the main application shell and verify main pages no longer display the previous global left sidebar
- [x] 2.2 Update the top navigation entries to `WIKI文档`, `文档创建`, `文档空间管理`, `回收站`, `图库功能`, `图片管理`, `文档管理`, `图片空间管理`, and `用户管理`; verify each visible entry reaches the intended page or state
- [x] 2.3 Preserve role-based navigation filtering and verify ordinary users do not see administrator-only entries while administrators do
- [x] 2.4 Apply the black top navigation visual treatment and verify active navigation highlighting remains visible

## 3. Wiki Document Three-Column Layout

- [x] 3.1 Refactor the Wiki document page into left tree, center content, and right outline columns; verify all three areas render together on desktop width
- [x] 3.2 Adapt the left column to show Wiki spaces, folders, and document navigation only; verify it does not include gallery, picture management, or user management entries
- [x] 3.3 Adapt the center column to show selected document title, metadata, and body content; verify selecting a document updates the center content
- [x] 3.4 Add the right document outline from current document headings and verify selecting an outline entry navigates to the matching document section
- [x] 3.5 Add an empty outline state for documents without headings and verify the document remains readable

## 4. Visual Styling and Responsiveness

- [x] 4.1 Apply warm off-white page background and paper-like document panels; verify the result matches the approved demo direction
- [x] 4.2 Apply orange scrollbar styling to scrollable Wiki tree, document body, and outline areas; verify default browser scrollbars remain usable if custom styling is unsupported
- [x] 4.3 Verify the top navigation handles narrow widths without text overlap, using wrapping or horizontal scrolling as needed
- [x] 4.4 Verify the Wiki layout remains usable on a narrow viewport, with tree, content, and outline all accessible without overlapping content

## 5. Validation and Handoff

- [x] 5.1 Run frontend type checks and verify no type errors are introduced
- [x] 5.2 Run relevant frontend build or lint checks available in the project and verify no new failures are introduced
- [x] 5.3 Perform manual UI verification for normal user and administrator navigation visibility, and record the observed result
- [x] 5.4 Record any implementation, build, or manual verification issue in `IssueLog.xlsx` with status and verification result
- [x] 5.5 Report completed changes, verification commands, passing items, failing items, discovered issues, suspected causes, and recommended next action before any additional fix-test loop

## 1. Task Setup

- [x] 1.1 Create the feature branch with `start-task.ps1 -Type feature -Name wiki-multiformat-document-import` and verify the branch tracks `origin/feature/wiki-multiformat-document-import开发`
- [x] 1.2 Run `openspec validate add-wiki-multiformat-document-import --strict` and verify the planning artifacts are valid before implementation
- [x] 1.3 Review existing Wiki document, space, folder, cache, and frontend workspace files and verify the implementation touches only the required modules

## 2. Backend Import Pipeline

- [x] 2.1 Remove `.docx` upload support and the Apache POI dependency from this stage
- [x] 2.2 Add a document import service boundary for supported file validation, format detection, title derivation, raw content output, and metadata output; verify with unit tests for `.md`, `.html`, `.htm`, empty files, and unsupported extensions
- [x] 2.3 ~~Store uploaded HTML as original raw HTML~~ **方向变更（2026-09-08 负责人确认）**：手测证实本地单文件 HTML 无法高保真还原（缺少 `*_files/` 配套资源、外链图防盗链占位、动态页无正文），改为清洗为结构化 Markdown（保留 h1-h3 结构标记与正文块，丢弃脚本/样式/导航/图片），`contentFormat=markdown`
- [x] 2.4 Update document validation to allow `html` content format while preserving existing `plain` and `markdown` behavior; verify existing document service tests still pass
- [x] 2.5 Add `POST /api/documentWiki/import` multipart endpoint with login, space, folder, save, metadata, summary, and cache clearing behavior; verify controller/service tests cover success, permission failure, folder mismatch, invalid file, and parser failure
- [x] 2.6 Add `HtmlToMarkdownConverter`（jsoup）清洗管道：标题层级/段落/列表/表格/代码块转 Markdown，噪音节点剔除，无可提取文本时拒绝导入；`WikiDocumentImportServiceTest` 覆盖清洗、`.htm`、空壳 HTML 拒绝等用例（待负责人在 IDEA 复跑）
- [x] 2.7 `DocumentWikiQueryRequest` 增加 `spaceType`，`prepareVisibleDocumentWikiQuery` 按空间类型收窄 `visibleSpaceIds`，支持"公开文档"聚合分页查询；缓存 key 含完整请求 JSON，无串缓存风险

## 3. Frontend Upload Flow

- [x] 3.1 Add or regenerate frontend API typings/wrapper for the import endpoint and verify TypeScript can call it with file, `spaceId`, optional `folderId`, and optional title
- [x] 3.2 Add an upload action beside `新建文档` in the Wiki space navigation toolbar and verify it is available only when a target space is selected
- [x] 3.3 Wire upload target resolution from the current tree selection and verify selected folders upload into that folder while selected space roots upload into the root
- [x] 3.4 After upload success, refresh the tree/list state and open the created document in the center column; verify the URL remains `/documentWiki`
- [x] 3.5 Show clear user messages for unsupported file type, empty file, parse failure, permission failure, and successful upload; verify frontend source tests cover the visible messages or branches

## 4. Format-Aware Viewing And Editing

- [x] 4.1 Add a shared document viewer component that renders Markdown with `MdPreview`, raw HTML through sandboxed iframe `srcdoc`, and legacy plain content as readable text; verify source tests cover all three format branches
- [x] 4.2 Replace the current simplified preview rendering in the Wiki center column with the shared viewer and verify Markdown content is rendered instead of split into plain paragraphs
- [x] 4.3 Update outline extraction to support Markdown headings and HTML heading tags; verify source/unit tests cover Markdown, HTML, and no-heading documents
- [x] 4.4 Disable HTML document editing in this stage and verify edit actions for HTML documents show a clear preview-only message（现仅适用于方向变更前的存量 html 行）
- [x] 4.5 Preserve Markdown-only create mode and Markdown edit mode with the existing Markdown editor; verify new document creation still submits `contentFormat = "markdown"`
- [x] 4.6 `MdEditor` 编辑器暖色主题适配（`--md-*` 变量覆盖，编辑区/工具栏/预览区不再是大片白底）；`MdPreview` 预览同步暖色（需求 1）
- [x] 4.7 右侧大纲双模式：文档打开（预览/编辑）时显示该文档 h1-h3 大纲并可点击跳转（修复编辑模式大纲被清空的 bug）；仅选中文件夹时显示文件夹下文档标题列表，点击打开（需求 2）
- [x] 4.8 导航分层浏览：左树新增可点击"公开文档"聚合节点（列出全部公开空间文档）；点击空间（如 团队文档/研发部）递归列出该空间全部文档；均按 20/页分页；文件夹选择保持内联列表不分页（需求 2）

## 5. Compatibility And Regression Checks

- [x] 5.1 Verify existing document create/edit/delete/move/recycle/search behavior still works for Markdown and plain legacy documents through backend tests or focused frontend source tests
- [ ] 5.2 Verify imported Markdown and imported HTML participate in list, search, move, delete, recycle, and cache refresh behavior
- [x] 5.3 Record any development, build, or test issue in `IssueLog.xlsx` with branch, change id, phase, error, impact, solution, status, and verification result

## 6. Automated Verification

- [x] 6.1 Run `openspec validate add-wiki-multiformat-document-import --strict` and verify it passes
  - 2026-09-08: passed (`Change 'add-wiki-multiformat-document-import' is valid`).
- [x] 6.2 Run backend unit tests for the affected Wiki document/import services and controllers and report pass/fail results
  - 2026-09-08: `WikiDocumentImportServiceTest`、`DocumentWikiControllerEditFormatTest`、`DocumentWikiControllerImportTest` 三个测试类全部通过（由负责人在 IDEA 执行）。
  - 2026-09-08 17:05: HTML 清洗管道与 spaceType 查询改造后，后端测试已同步更新（`WikiDocumentImportServiceTest` 重写清洗断言、`DocumentWikiControllerImportTest` mock 对齐 markdown 产出），**待负责人在 IDEA 复跑 `cloud` 模块 test 确认**。
- [x] 6.3 Run frontend type check/build or the project's focused frontend tests and report pass/fail results
  - 2026-09-08: `vue-tsc --build` 退出码 0；`node --test wikiMultiformatDocumentFlow.test.mjs` 通过（当前 11/11）。
  - 2026-09-08 17:05: 本批次（编辑器暖色/大纲双模式/导航分页）改完后复跑：`vue-tsc --build --force` 退出码 0，前端测试 11/11；`openspec validate --strict` 通过。
- [x] 6.4 If any formal test fails after implementation, stop after recording the issue and report the result, likely cause, and proposed fix before entering another fix-test loop

## 7. Local Service And Human Acceptance

- [x] 7.1 After automated verification passes, run `stop-dev.ps1` then `start-dev.ps1` from the current task branch directory and verify the frontend and backend service addresses are available
  - 2026-09-07: `stop-dev.ps1` succeeded. `start-dev.ps1` launched frontend but backend timed out because Redis rejected the configured credentials (`WRONGPASS invalid username-password pair or user is disabled`). Recorded in `IssueLog.xlsx`; services were stopped again.
  - 2026-09-08 14:38: 后端以 `--spring.profiles.active=local` 启动成功（`Started CloudApplication in 6.804 seconds`），Redis 未再报 WRONGPASS；前端 Vite 已在 `127.0.0.1:3000` 运行，HMR 已加载本轮全部前端改动。
  - 2026-09-08 15:42: 当前 8123 上的后端仍是 14:38 构建的旧 jar（PID 63636），**未包含本轮后端改动**。AI 会话内无法执行 `mvn` / `taskkill` 等原生命令，需负责人重新 `stop-dev.ps1` → `start-dev.ps1`（或在 IDEA 中重新 package）后再手测。
  - 2026-09-08 16:12: 负责人首轮手测反馈 4 项问题（Markdown 预览配色、HTML 上传"正文过长"、Markdown 编辑中列空白、右侧大纲无法跳转）。已修复并在 headless 浏览器中复现验证通过（见 IssueLog 第 77–80 行）。其中 **HTML 上传长度上限属后端改动，需重建后端后生效**；前端 HMR 已即时生效。
- [ ] 7.2 Ask the负责人 to manually test upload of `.md`, `.html`, and `.htm` files into a selected space/folder at `http://127.0.0.1:3000/documentWiki`
- [ ] 7.3 Ask the负责人 to manually test viewing Markdown rendering, raw HTML iframe rendering, and right-side outline behavior
- [ ] 7.4 Ask the负责人 to manually test editing uploaded Markdown with Markdown editor and verify uploaded HTML documents are preview-only and cannot be edited
- [ ] 7.5 Keep manual acceptance tasks incomplete until the负责人 returns page test results

## 8. Upload For Review

- [ ] 8.1 After code changes, automated checks, and负责人 acceptance are complete, run `upload.ps1 -Message "feat: add wiki multiformat document import"` and verify the remote task branch is pushed
- [ ] 8.2 Report the pushed branch and remind that merge to `main` must be performed by the负责人 through merge request / pull request review

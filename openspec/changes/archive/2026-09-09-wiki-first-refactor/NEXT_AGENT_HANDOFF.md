# Next Agent Handoff: wiki-first-refactor

## Current state

- Branch: `feature/wiki-first-refactor`.
- OpenSpec change: `openspec/changes/wiki-first-refactor`.
- M1 backend data/upload foundation is complete.
- M2 Markdown editor, image upload, preview rendering, OpenAPI generation, and user acceptance are complete.
- The next implementation module is M3: frontend tree rewrite.

## Completed work

- Added Wiki attachment upload path around Wiki space visibility:
  - `POST /api/documentWiki/image/upload`
  - COS key format: `wiki/{wikiSpaceId}/{uuid}.{ext}`
  - `wiki_attachment` persistence.
- Added `document_wiki` fields for Markdown, source tracing, and RAG reservation:
  - `contentFormat`, `sourceType`, `sourceUrl`, `contentHash`, `contentVersion`, `visibility`, `metadataJson`.
- Added Markdown editor through `md-editor-v3` in `DocumentWikiEditor.vue`.
- Added Markdown preview in `DocumentWikiDetailPage.vue`, with old `plain` documents still displayed as plain text.
- Fixed local OpenAPI generation:
  - local profile disables Knife4j basic auth.
  - `openapi.config.js` supports auth env vars and maps `int64` to `string | number`.
- Fixed regenerated recycle API import aliases in `DocumentWikiListPage.vue`.
- Fixed image upload regression found during acceptance:
  - `DocumentWikiEditor.vue` no longer converts `formState.spaceId` through `Number()`.
  - `GlobalExceptionHandler` maps bad upload requests such as GET or missing multipart file to `PARAMS_ERROR` instead of system error.
- Updated `IssueLog.xlsx` with M2/OpenAPI and image-upload acceptance issues.

## Verified commands

From `C:\Users\origin\IdeaProjects\Agent\cloud`:

```powershell
mvn -Dtest=DocumentWikiServiceImplTest test
mvn -Dtest=OpenApiLocalProfileTest test
mvn -Dtest=GlobalExceptionHandlerTest test
mvn '-Dtest=GlobalExceptionHandlerTest,WikiAttachmentServiceTest' test
```

From `C:\Users\origin\IdeaProjects\Agent\cloud_front`:

```powershell
node --test openapi.config.test.mjs
node --test documentWikiEditor.test.mjs openapi.config.test.mjs
npm run openapi
npm run build-only
```

Manual/API checks:

- `/api/v2/api-docs` returns JSON on the local profile.
- Direct GET to `/api/documentWiki/image/upload?spaceId=2095513150191665200` returns `code=40000`.
- Upload with rounded/nonexistent space ID `2095513150191665200` returns `code=40400`.
- Upload with real space ID `2095513150191665153` returns `code=0`.
- User confirmed browser paste-image acceptance passed.

## Remaining tasks

Start with M3 in `tasks.md`:

- `3.1` Create `WikiSpaceTree.vue` with grouped space roots and collapsible folder-only tree.
- `3.2` Change node interaction: click folder selects it; actions move to hover/right-click menu.
- `3.3` Rewrite right-side document list filtering by selected space + folder/root.
- `3.4` Validate nested folder creation and refresh persistence.
- `3.5` Wire `DocumentWikiListPage.vue` to the new tree and remove flattened indentation rendering.
- `3.6` Browser regression: space switching, folder CRUD, nesting, recycle bin, search.
- `3.7` Update `IssueLog.xlsx`, report, and wait for user confirmation.

Then continue:

- M4 component split and Wiki-first navigation.
- M5 full regression and README closeout.

## Important constraints for the next Agent

- Read root `AGENTS.md` before editing.
- Keep the module loop: develop, test, report, wait for user confirmation.
- Do not merge to `main` without explicit user authorization.
- Preserve unrelated existing changes in the dirty worktree.
- Do not commit secrets from local config or logs.
- For frontend ID handling, preserve Snowflake IDs as strings across route/query/API calls unless a value is known to be a small enum or count.
- Keep M3 scoped to tree/list behavior; do not start M4 navigation or broad component splitting until M3 is accepted.

## Local services

Dev services were restarted after the latest fix:

- Backend: `http://127.0.0.1:8123`
- Frontend: `http://127.0.0.1:3000`
- Start: `powershell -ExecutionPolicy Bypass -File .\start-dev.ps1`
- Stop: `powershell -ExecutionPolicy Bypass -File .\stop-dev.ps1`

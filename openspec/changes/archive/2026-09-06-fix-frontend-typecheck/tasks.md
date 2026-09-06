# Tasks: Fix Frontend Typecheck

> **Execution rule**: this change fixes only the 52 legacy frontend TypeScript errors left after wiki incremental type fixes. Do not broaden into UI redesign, backend behavior changes, or credential cleanup.
> **Branch**: use `fix/frontend-typecheck修复`, cut from current `main` when implementation starts.
> **Type contract priority**: backend DTO/VO/Controller -> generated OpenAPI typings -> frontend API functions -> page props/state -> UI framework callback types.

## 0. Baseline And Scope

- [x] 0.1 Create/check branch `fix/frontend-typecheck修复` from current `main`; verify `git status --short --branch` shows the expected branch and no unrelated staged files
- [x] 0.2 Run `npm run build` in `cloud_front`; verify the baseline still fails in `vue-tsc --build` with the remaining legacy errors and save the error count/distribution for `IssueLog.xlsx`
- [x] 0.3 Confirm the previously fixed wiki incremental categories remain absent: `@tsconfig/node22`, `vue-cropper` declaration, generated `requestType`, `DocumentWikiEditor`, and `WikiSpaceTree`; verify by searching the build output and affected files
- [x] 0.4 Review `cloud_front/src/api/typings.d.ts`, `cloud_front/src/request.ts`, and the failing component files; verify every planned fix follows the type contract priority above

## 1. API Response Type Contract

- [x] 1.1 Inspect `cloud_front/src/request.ts` and generated API return types; verify whether a shared typed response wrapper or local response narrowing is the smallest stable fix
- [x] 1.2 Fix `res.data.code`, `res.data.data`, and `res.data.message` errors in picture upload, URL upload, cropper, out-painting, and affected pages without using broad `any`; verify this error class disappears or decreases after `npm run build`
- [x] 1.3 Add guards for optional response data before nested field access; verify success and failure paths still show user-facing messages
- [x] 1.4 Re-run `npm run build`; record response-type error count movement in `IssueLog.xlsx`

## 2. Entity ID Precision And Route Parameters

- [x] 2.1 Define or reuse a minimal frontend entity-id type compatible with backend Long/Snowflake ids (`string | number`); verify no general `Number(...)` route/query coercion is introduced
- [x] 2.2 Normalize route path/query ids in `AddPicturePage.vue`, `AddSpacePage.vue`, `PictureDetailPage.vue`, `SpaceDetailPage.vue`, and admin pages; verify API calls accept the normalized ids
- [x] 2.3 Fix component prop types that currently require `number` where generated OpenAPI typings allow `string | number`; verify picture and space components still receive the same values
- [x] 2.4 Search changed files for risky id conversions; verify Snowflake ids are not coerced into unsafe JavaScript numbers
- [x] 2.5 Re-run `npm run build`; record ID-related error count movement in `IssueLog.xlsx`

## 3. Vue State Typing

- [x] 3.1 Fix `ref([])` and reactive collection state that TypeScript infers as `never[]`; verify picture lists, space detail lists, and admin tables have explicit element types
- [x] 3.2 Fix pagination and count state assignments, including `total`, `current`, and `pageSize`; verify Ant Design Vue pagination still receives numeric values
- [x] 3.3 Fix object state defaults where templates read possibly missing numeric fields; verify fallback values are explicit instead of relying on `undefined`
- [x] 3.4 Re-run `npm run build`; record state-typing error count movement in `IssueLog.xlsx`

## 4. UI Callback And Event Types

- [x] 4.1 Fix implicit `any` parameters in `PictureList.vue`, admin table renderers, pagination callbacks, and menu/click handlers; verify each callback has a local or framework-compatible type
- [x] 4.2 Keep existing variable and function names unless they hide a different domain concept; verify diffs do not include unrelated renames
- [x] 4.3 Re-run `npm run build`; record implicit-any error count movement in `IssueLog.xlsx`

## 5. Nullable Data And Browser Runtime Types

- [x] 5.1 Fix `ImageOutPainting.vue` optional output handling; verify missing output produces the existing failure message path instead of a runtime exception
- [x] 5.2 Replace browser-inappropriate `NodeJS.Timeout` timer typing with a browser-safe timer type; verify no Node namespace error remains
- [x] 5.3 Fix `catch (error)` handlers that directly read `error.message`; verify error messages remain readable for unknown thrown values
- [x] 5.4 Re-run `npm run build`; record nullable/runtime-type error count movement in `IssueLog.xlsx`

## 6. Regression Verification

- [x] 6.1 Run `npm run build` in `cloud_front`; verify both `vue-tsc --build` and Vite build pass with zero TypeScript errors
- [x] 6.2 Run `npm run build-only` in `cloud_front`; verify build still passes and wiki editor chunk splitting remains visible in output
- [x] 6.3 Run focused frontend checks/scripts already present in the repo, including `node documentWikiEditor.test.mjs` and `node openapi.config.test.mjs`; verify they pass
- [x] 6.4 Start backend with local profile and frontend on a non-reserved port; verify both services are reachable
- [x] 6.5 Regress picture workflows: browse list, upload image, open detail; verify HTTP/API responses return success
- [x] 6.6 Regress space workflows: open/load space detail and picture list; verify permission-controlled actions and data loading still behave as before
- [x] 6.7 Run backend smoke verification only if frontend fixes touched generated API contracts or backend-facing assumptions; verify no backend behavior changed
- [x] 6.8 Update `IssueLog.xlsx` with baseline count, per-category count movement, final zero-error result, and remaining known limitations if any
- [x] 6.9 Report results and wait for user confirmation before merging or pushing to `main`

## Verification Result

- 2026-09-06: baseline 52 legacy TS errors captured (tmp/typecheck-baseline.txt); wiki incremental categories re-checked, 0 regression.
- Category fixes: response-nullable/runtime 13, entity-id/route 11, Vue state 12, UI callbacks 11, others 5 => 52 -> 0 after one pass.
- npm run build: PASS (vue-tsc 0 errors + Vite build OK); npm run build-only: PASS, DocumentWikiEditor chunk split intact.
- node documentWikiEditor.test.mjs / openapi.config.test.mjs: PASS (fail 0).
- API regression on local profile (backend 8123, front 3000): login, picture list/detail, file upload, URL upload, in-space upload+delete (quota released), space detail/picture list/member list, cross-space permission 40101 block - all code 0.
- Known issues recorded in IssueLog.xlsx: pre-existing backend defect deleting public-gallery pictures (50001 额度更新失败, deferred, out of scope); two 72B test pictures remain in public gallery because of it.
- Branch fix/frontend-typecheck修复 NOT merged to main; awaiting user confirmation.

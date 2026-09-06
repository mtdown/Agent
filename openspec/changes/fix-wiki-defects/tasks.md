# Tasks: Fix Wiki Defects (Post Wiki-First Review)

> **Execution rule**: one phase at a time. Each phase is a complete develop → test → report → await-confirmation cycle (AGENTS.md §4). Do not start a phase before the previous one is confirmed.
> **Source**: the 12 defects recorded in `docs/代码缺陷清单.md`. Phase order follows the severity ranking agreed with the user.
> **Branch**: `fix/wiki-defects修复` (to be cut from `main` when phase 1 starts).

## 0. Pre-Implementation Review

- [x] 0.1 Review `WikiAttachmentServiceImpl` upload path; confirm the COS-then-save ordering and the absence of `@Transactional` (D1)
- [x] 0.2 Confirm `documentId` is written without a space-ownership check (D2)
- [x] 0.3 Confirm wiki search resolves to `queryWrapper.like("content", ...)` (D3)
- [x] 0.4 Confirm `getDocumentWikiVisPage` calls `getDocumentWikiVis` per row, each issuing a user lookup (D4)
- [x] 0.5 Confirm sharding config is fully commented (D9), `mvn test` needs external Redis (D10), `README.md` line 98 claims permission checks are incomplete (D11)
- [x] 0.6 Confirm the picture module already fixed the same N+1 (recorded in `README.md`), establishing the pattern wiki should follow
- [x] 0.7 Confirm with the user that sharding will be **retired** rather than enabled, before phase 4 starts

---

## 1. Phase 1 — Attachment Correctness (P0)

**Goal**: object store and database must never diverge; attachments may not reference another space's document.

- [x] 1.1 Annotate `WikiAttachmentServiceImpl.uploadImage` with `@Transactional(rollbackFor = Exception.class)`; verify compile
- [x] 1.2 Delete the COS object when `save(wikiAttachment)` returns false; verify with a test that forces the save to fail and asserts `cosManager.deleteObject` was called with the uploaded path
- [x] 1.3 Validate `documentId` ownership: when non-null, load the document and assert `document.getSpaceId()` equals the acting `wikiSpaceId`; verify a cross-space id is rejected with no COS write
- [x] 1.4 Keep the null-`documentId` path working for uploads that happen before the document is saved; verify an upload without `documentId` succeeds and stores null
- [x] 1.5 Add unit tests `WikiAttachmentServiceTest` for: orphan compensation, cross-space rejection, null documentId, successful upload; verify all pass
- [x] 1.6 Run `mvn -Dtest=WikiAttachmentServiceTest,DocumentWikiServiceImplTest,DocumentWikiSpaceRulesTest test`; verify 0 failures
- [x] 1.7 Run `mvn -DskipTests package`; verify BUILD SUCCESS
- [x] 1.8 Record results in `IssueLog.xlsx` and report (changes, passing tests, failures, fix proposal)

**DoD**: an attachment row and its COS object are always created together or not at all; no attachment can point at another space's document.

---

## 2. Phase 2 — Full-Text Search (P0)

**Goal**: replace `LIKE '%keyword%'` with an indexed, Chinese-segmented, relevance-ranked search that still respects visibility.

- [x] 2.1 Add `cloud/sql/alter_table_document_wiki_fulltext.sql` creating a `FULLTEXT (title, content)` index `WITH PARSER ngram`; verify the DDL executes on local MySQL
- [x] 2.2 Confirm the MySQL version supports `ngram` (5.7+ / 8.x); verify with `SHOW INDEX FROM document_wiki`
- [x] 2.3 Add `MATCH (title, content) AGAINST (? IN NATURAL LANGUAGE MODE)` to the document query wrapper when `searchText` is present; verify keyword search returns results
- [x] 2.4 Keep `searchText` absent on the plain list path; verify ordering still falls back to `editTime DESC`
- [x] 2.5 Order keyword results by match relevance; verify a more relevant document sorts above a less relevant one
- [x] 2.6 Apply `visibleSpaceIds` filtering on the full-text path; verify no document from an invisible space is ever returned
- [x] 2.7 Verify cache invalidation still fires for search/list buckets after create/edit/delete/move/restore; verify a stale result is not served after a mutation
- [x] 2.8 Measure before/after timing on a seeded dataset and record the numbers in `IssueLog.xlsx`
- [x] 2.9 Run `mvn -DskipTests package` and `npm run build-only`; verify both pass
- [x] 2.10 Record results in `IssueLog.xlsx` and report

**DoD**: keyword search uses the full-text index, segments Chinese, ranks by relevance, and never crosses the visibility boundary.

---

## 3. Phase 3 — Document List N+1 (P1)

**Goal**: one author query per page instead of one per row, matching the picture module.

- [x] 3.1 Collect distinct author ids from the page records; verify the collection step handles an empty page without querying
- [x] 3.2 Resolve authors with a single `userService.listByIds(...)` into a map; verify at most one extra query per page
- [x] 3.3 Map authors back onto each `DocumentWikiVis`; verify every row carries its author and rows with a missing user get a null `user` instead of failing
- [x] 3.4 Add a unit or integration assertion on query count; verify the count no longer grows with page size
- [x] 3.5 Run `mvn -Dtest=DocumentWikiServiceImplTest,DocumentWikiSpaceRulesTest test`; verify 0 failures
- [x] 3.6 Run `mvn -DskipTests package`; verify BUILD SUCCESS
- [x] 3.7 Record results in `IssueLog.xlsx` and report

**DoD**: assembling a page of twenty documents issues a constant number of author queries.

---

## 4. Phase 4 — Project Hygiene (P1, defensive)

**Goal**: remove the three liabilities an interviewer can expose with one question.

- [x] 4.1 Decide and record the sharding outcome: remove `DynamicShardingManager`/`PictureShardingAlgorithm`, or annotate the commented config in `application-local.yml` with an explicit "not enabled, and why" note; verify no dead code remains unexplained
- [x] 4.2 Make `mvn test` runnable without external Redis (test profile with an embedded Redis or a mock); verify `mvn test` completes with no Redis authentication failure
- [x] 4.3 Correct `README.md` line 98: replace the "permission checks are incomplete" claim with the factual current status; verify the statement matches actual behavior, and if the gap remains, describe it as a scoped known limitation
- [x] 4.4 Re-run the wiki test subset under the new configuration; verify 0 failures
- [x] 4.5 Record results in `IssueLog.xlsx` and report

**DoD**: `mvn test` runs clean without external services; sharding status is explicit; `README.md` makes no false claim.

---

## 5. Phase 5 — Upload Hardening (P1/P2)

**Goal**: trust file content over filename, and require edit rights rather than visibility.

- [x] 5.1 Replace `requireVisibleSpace` with an edit-rights check on the upload path; verify a view-only member is rejected with `NO_AUTH_ERROR` and nothing is written to COS
- [x] 5.2 Add magic-number content sniffing in `validImage`, accepting only real jpg/jpeg/png/gif/webp; verify a non-image renamed to `.png` is rejected
- [x] 5.3 Normalize the suffix to lower case when building the COS object path; verify `PHOTO.JPG` produces a `.jpg` path
- [x] 5.4 Extend `WikiAttachmentServiceTest` for the three cases above; verify all pass
- [x] 5.5 Run `mvn -Dtest=WikiAttachmentServiceTest test` and `mvn -DskipTests package`; verify both pass
- [x] 5.6 Record results in `IssueLog.xlsx` and report

**DoD**: disguised files are rejected, viewers cannot upload, object paths use consistent casing.

---

## 6. Phase 6 — Polish (P2)

**Goal**: clear stale caches and stop shipping the editor on first paint.

- [x] 6.1 Clear existing `agentWiki:documentWiki:*` cache keys so no entry lacks `contentFormat`; verify a detail request repopulates with the field present
- [x] 6.2 Lazy-load the create/edit document routes and move the Markdown editor into its own chunk; verify the initial bundle no longer contains it
- [x] 6.3 Record the main bundle size before and after in `IssueLog.xlsx`; verify a measurable reduction
- [x] 6.4 Run `npm run build-only`; verify build passes and the chunk split appears in the output
- [x] 6.5 Record results in `IssueLog.xlsx` and report

**DoD**: main bundle is measurably smaller; no stale cache entries.

---

## 7. Final Verification And Handover

- [x] 7.1 Manual regression: login → space switch → create document → paste image → save → detail render → edit → delete → recycle → restore
- [x] 7.2 Manual regression of the demoted picture module: browse, upload, detail all still work
- [x] 7.3 Verify legacy `plain` documents still render correctly after the search and cache changes
- [x] 7.4 Reconcile every `IssueLog.xlsx` entry recorded during this change
- [x] 7.5 Report the consolidated result; **await explicit user confirmation before merging to `main`**
- [x] 7.6 After merge, append a "Wiki defect remediation" section to `README.md`

---

## Execution Order

```
Phase 1 (attachment correctness, P0)  -> confirm
Phase 2 (full-text search, P0)        -> confirm
Phase 3 (list N+1, P1)                -> confirm
Phase 4 (project hygiene, P1)         -> confirm
Phase 5 (upload hardening, P1/P2)     -> confirm
Phase 6 (polish, P2)                  -> confirm
Phase 7 (final verification)
```

Only **Phase 1** is active at a time. Later phases stay unchecked until the previous one is confirmed.

## Verification Record

### Phase 1 — Attachment Correctness

- `mvn -Dtest=WikiAttachmentServiceTest test`: PASS, 11 tests, 0 failures.
- `mvn '-Dtest=WikiAttachmentServiceTest,DocumentWikiServiceImplTest,DocumentWikiSpaceRulesTest' test`: PASS, 26 tests, 0 failures.
- `mvn -DskipTests package`: initially failed at `spring-boot-maven-plugin:repackage`; retry completed with BUILD SUCCESS.
- `IssueLog.xlsx`: row 44 updated for the transient package failure; status `已修复`, verification `通过`.

### Phase 2 — Full-Text Search

- `mvn -Dtest=DocumentWikiServiceImplTest test`: RED first with 3 expected failures, then PASS, 11 tests, 0 failures.
- `cloud/sql/alter_table_document_wiki_fulltext.sql`: executed on local MySQL 9.4.0 via JDBC.
- `SHOW INDEX FROM document_wiki`: returned `idx_document_wiki_title_content_fulltext` for `title` and `content`, `Index_type=FULLTEXT`.
- Temporary keyword search data verified visible-space filtering and relevance ordering; cleanup deleted all 3 verification rows.
- Cache invalidation verified by code inspection: create/edit/delete/move/restore paths call `wikiCacheManager.clearSpace` or `clearDocument`, and `clearSpace` clears both scoped list cache and `all` list cache.
- Local 1200-row timing sample: `LIKE` average 2.032ms, `MATCH` average 2.282ms. The small local data set did not show a speedup, but confirms the indexed query path is functional.
- `mvn -DskipTests package`: BUILD SUCCESS.
- `npm run build-only`: PASS; Vite build completed with the existing large chunk warning.
- `IssueLog.xlsx`: appended row 45 for Phase 2 verification and timing.

### Phase 3 — Document List N+1

- `mvn -Dtest=DocumentWikiServiceImplTest test`: RED first with expected author batching failures, then PASS, 14 tests, 0 failures.
- `getDocumentWikiVisPage` now collects distinct positive author ids, skips empty pages without a user query, calls `userService.listByIds(...)` once per page, and maps `UserVis` back to rows.
- Missing or deleted author rows degrade gracefully: the row remains in the page with `user = null`.
- `mvn '-Dtest=DocumentWikiServiceImplTest,DocumentWikiSpaceRulesTest' test`: PASS, 21 tests, 0 failures.
- `mvn -DskipTests package`: BUILD SUCCESS.
- `IssueLog.xlsx`: appended row 46 for Phase 3 verification.

### Phase 4 — Project Hygiene

- User confirmed sharding is not enabled and should be treated as retired.
- Removed the commented-out sharding dead code classes `DynamicShardingManager` and `PictureShardingAlgorithm`.
- `application-local.yml` now records that ShardingSphere is retired and not enabled because the current data volume does not justify sharding and no sharded schema or migration path is maintained.
- `README.md` now states the current permission behavior: frontend controls are shown or hidden by current-space permissions, while backend checks remain authoritative.
- `CloudApplicationTests` now activates the `local` profile, so plain `mvn test` loads `application-local.yml` and uses local Redis instead of the default external Redis.
- `SaTokenConfigureTest` now loads both `SaTokenConfigure` and `StpKitRegisterConfig`, verifying the complete startup behavior for the picture-space `StpKit.SPACE` registration.
- `mvn test`: PASS, 45 tests, 0 failures.
- `mvn '-Dtest=WikiAttachmentServiceTest,DocumentWikiServiceImplTest,DocumentWikiSpaceRulesTest' test`: PASS, 32 tests, 0 failures.
- `mvn -DskipTests package`: BUILD SUCCESS.
- `IssueLog.xlsx`: rows 47 and 48 updated for the Sa-Token and Redis/profile findings; appended row 49 for Phase 4 verification.

### Phase 5 — Upload Hardening

- `WikiAttachmentServiceImpl.uploadImage` now calls `wikiSpaceService.requireEditableSpace(...)` instead of `requireVisibleSpace(...)`, so visible-only users are rejected before COS writes.
- `WikiSpaceService` now exposes `checkSpaceEditable(...)` and `requireEditableSpace(...)`. Platform admins can edit every wiki space, personal spaces are editable by their owner, and team spaces are editable only by `admin` or `editor` members.
- `validImage` now verifies image magic numbers for jpg/jpeg/png/gif/webp instead of trusting the filename suffix alone.
- COS object paths continue to normalize suffixes to lower case; `PHOTO.PNG` produces a `.png` path.
- TDD RED: `mvn -Dtest=WikiAttachmentServiceTest test` failed first because `requireEditableSpace` did not exist, then failed on two outdated test fixtures after the implementation added content sniffing.
- `mvn '-Dtest=WikiAttachmentServiceTest,WikiSpaceServiceImplTest' test`: PASS, 23 tests, 0 failures.
- `mvn -Dtest=WikiAttachmentServiceTest test`: PASS, 14 tests, 0 failures.
- `mvn -DskipTests package`: BUILD SUCCESS.
- `mvn test`: PASS, 52 tests, 0 failures.
- `IssueLog.xlsx`: appended row 50 for Phase 5 verification.

### Phase 6 — Polish

- Redis cache cleanup against local Redis database 0 completed for `agentWiki:documentWiki:*`: deleted 0 keys, remaining 0 keys.
- Added `DocumentWikiControllerCacheTest` to verify a cache miss repopulates the wiki detail cache with `contentFormat`.
- `mvn -Dtest=DocumentWikiControllerCacheTest test`: PASS, 1 test, 0 failures.
- `cloud_front/src/router/index.ts` now lazy-loads wiki detail/create/edit routes, allowing `md-editor-v3` and `DocumentWikiEditor` to move out of the first-paint route bundle.
- Baseline frontend build main bundle: `index-CpBRkh3O.js`, 2,588.74 kB raw / 825.86 kB gzip.
- After route split main bundle: `index-Cfxd6yBW.js`, 1,696.32 kB raw / 519.69 kB gzip.
- Main bundle reduction: 892.42 kB raw / 306.17 kB gzip.
- Split editor chunk present: `DocumentWikiEditor.vue_vue_type_script_setup_true_lang-DmgkTcpI.js`, 698.38 kB raw / 230.66 kB gzip.
- Main bundle check: `index-Cfxd6yBW.js` contains no `md-editor` or `MdEditor` markers.
- `npm run build-only`: PASS; Vite still reports the existing >500 kB chunk warning, but the editor chunk split appears in the output.
- `mvn test`: PASS, 53 tests, 0 failures.
- `IssueLog.xlsx`: appended row 51 for Phase 6 verification.

### Phase 7 — Final Verification And Handover

- Backend local service started with the `local` profile on port 8123 and connected to local MySQL/Redis.
- Wiki API regression completed with a real logged-in session: login, visible-space list, team-space creation for space switching, PNG paste upload, Markdown document save, detail fetch, edit, delete to recycle bin, recycle listing, restore, and post-restore detail fetch all returned `code=0`.
- Wiki regression created temporary verification data: wiki space `2095544464810774531`, Markdown document `2096284369115443201`, plain document `2096284370231128066`.
- Legacy `plain` document verification completed through the detail API: the returned detail kept `contentFormat=plain`; detail page code renders non-Markdown content through the plain `.content` block.
- Picture module regression completed with a real logged-in session: picture list, PNG upload, and picture detail all returned `code=0`; uploaded verification picture id `2096284523935592449`.
- `mvn -DskipTests package`: BUILD SUCCESS.
- `npm run build`: FAILED in `vue-tsc` type checking. Failures include incompatible `@tsconfig/node22` lib configuration, missing `vue-cropper` Vue declaration, generated API calls passing `requestType` to `AxiosRequestConfig`, and existing component type errors around generic API responses, possible `undefined`, implicit `any`, and string/number mismatches.
- `npm run dev` initially failed on ports 5173, 5174, and 5175 with `listen EACCES: permission denied`; `netsh interface ipv4 show excludedportrange protocol=tcp` confirmed Windows reserves port range 5151-5250. Retried on port 3000 and Vite started successfully at `http://127.0.0.1:3000/`.
- Configuration safety review found plaintext third-party service secrets in `application-local.yml`; values are not copied here. This is recorded as a follow-up security issue rather than changed inside this wiki-defects scope.
- `IssueLog.xlsx`: rows 44-51 reconciled as fixed/passing; rows 52-54 appended for Phase 7 pending issues.

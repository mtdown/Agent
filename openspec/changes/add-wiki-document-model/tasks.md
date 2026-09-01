## 1. Original Flow Review

- [ ] 1.1 Review existing `Picture`, `Space`, and `SpaceUser` model behavior and verify the notes identify preserved modules, modified modules, new modules, and out-of-scope modules before implementation starts.
- [ ] 1.2 Walk through the existing image flow from frontend page to controller, service, mapper, and database, and verify the review explains how `spaceId == null` and `spaceId != null` work today.
- [ ] 1.3 Confirm the stage 1 replacement scope with the user and verify the user understands that `Document` is added beside `Picture`, not replacing it.

## 2. Database Schema

- [ ] 2.1 Create `cloud/sql/create_table_document.sql` with `document`, `document_version`, and `document_chunk` tables, and verify the SQL includes logical delete fields and useful indexes for `spaceId`, `userId`, `documentId`, and query fields.
- [ ] 2.2 Review the new SQL against `cloud/sql/create_cloud_table.sql` style and verify no existing table or column is removed or renamed.

## 3. Entity and Mapper Layer

- [ ] 3.1 Add `Document`, `DocumentVersion`, and `DocumentChunk` entity classes under `cloud/src/main/java/com/et/cloud/model/entity`, and verify field names match the SQL column names and MyBatis-Plus annotations.
- [ ] 3.2 Add `DocumentMapper`, `DocumentVersionMapper`, and `DocumentChunkMapper` under `cloud/src/main/java/com/et/cloud/mapper`, and verify each extends `BaseMapper` for the matching entity.
- [ ] 3.3 Run backend compilation and verify there is no import conflict between `com.et.cloud.model.entity.Document` and `org.jsoup.nodes.Document`.

## 4. DTO and Vis Layer

- [ ] 4.1 Add document request DTOs for create, edit, update, query, and chunk creation, and verify they follow the existing request object style and include `serialVersionUID` where the project convention uses it.
- [ ] 4.2 Add `DocumentVis` and `DocumentVersionVis`, and verify `DocumentVis` converts stored JSON tag strings into `List<String>` like `PictureVis` does.
- [ ] 4.3 Add document status constants or enums for parse and embedding statuses, and verify values match the design: pending, processing, success, failed.

## 5. Service Layer

- [ ] 5.1 Add `DocumentService`, `DocumentVersionService`, and `DocumentChunkService` interfaces and implementations, and verify they follow the existing MyBatis-Plus service pattern.
- [ ] 5.2 Implement document validation and verify blank titles are rejected while optional summary, content, category, tags, cover image reference, file metadata, and space ownership are accepted.
- [ ] 5.3 Implement document query wrapper behavior and verify search text matches title, summary, content, or category and space filtering follows the existing picture query pattern.
- [ ] 5.4 Implement document create with version creation in one transaction and verify a newly created document gets version number 1.
- [ ] 5.5 Implement document edit with version creation in one transaction and verify edits update `editTime` and create the next version number without changing owner or space ownership.
- [ ] 5.6 Implement document logical delete and verify normal document queries exclude deleted records.
- [ ] 5.7 Implement document version list behavior and verify versions return newest first.
- [ ] 5.8 Implement document chunk save/query behavior and verify chunks return in `chunkIndex` order.

## 6. Controller Layer

- [ ] 6.1 Add `DocumentController` under `/document` and verify it returns `BaseResponse` through `ResultUtils` like existing controllers.
- [ ] 6.2 Add create, edit, delete, detail, paginated list, version list, and chunk list endpoints, and verify each endpoint maps cleanly to the matching service behavior.
- [ ] 6.3 Require login for create, edit, and delete endpoints, and verify full space-level document permission checks are left for stage 4.
- [ ] 6.4 Ensure detail and list responses include owner display information and an initialized permission list, and verify the response shape is ready for later frontend use.

## 7. Tests and Verification

- [ ] 7.1 Add focused backend tests for document validation and verify tests fail before implementation and pass after implementation.
- [ ] 7.2 Add focused backend tests for query wrapper search and space filtering and verify tests fail before implementation and pass after implementation.
- [ ] 7.3 Add focused backend tests for version number creation and verify tests fail before implementation and pass after implementation.
- [ ] 7.4 Run `mvn test` from `cloud` and verify the backend test suite completes or record the exact pre-existing failures.
- [ ] 7.5 Run `mvn -DskipTests package` from `cloud` and verify backend compilation succeeds.
- [ ] 7.6 Verify existing picture upload classes, controller routes, service methods, and SQL remain present after the change.

## 8. Final Review

- [ ] 8.1 Review changed files and verify stage 1 did not implement document upload parsing, frontend Wiki pages, RAG, URL crawling, or Agent orchestration.
- [ ] 8.2 Review naming and verify no stale wording suggests `Picture` was deleted or replaced.
- [ ] 8.3 Run `openspec validate --change add-wiki-document-model --strict` and verify the OpenSpec change passes validation.
- [ ] 8.4 Summarize what was added, what was preserved, what was deferred, test results, and any remaining risks for user review.

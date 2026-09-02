# Design: DocumentWiki MVP

## Context

The current project is a cloud image gallery with Spring Boot, MyBatis-Plus, MySQL, Redis, Tencent COS, and a Vue frontend. The target product direction is an enterprise wiki with document editing, document upload, RAG, URL crawling, and later agent collaboration.

This change intentionally implements only Stage 1: a visible and working `DocumentWiki` CRUD module with online editing. It should be small enough to complete on the current codebase and clear enough to use as the foundation for later stages.

## Goals

- Add a document feature users can actually operate from the frontend.
- Keep MySQL as the permanent storage for saved document data.
- Reuse the existing Redis setup for cache.
- Reuse existing login and permission style from picture CRUD.
- Keep the existing picture model available for later wiki image insertion.
- Avoid introducing document upload, parsing, chunks, RAG, spaces, or agents in this change.

## Non-Goals

- No team/private wiki space behavior.
- No multi-format file parser.
- No URL crawling.
- No RAG chunk table or embedding pipeline.
- No version table.
- No object storage requirement for document text.
- No rich collaborative editor.

## Naming Decision

Use `DocumentWiki` for Java classes and frontend domain names.

Reason:

- `Document` is too generic and conflicts conceptually with common parser classes such as `org.jsoup.nodes.Document`.
- `DocumentWiki` communicates that this is a wiki document module in the current product direction.

Naming conventions:

- Java entity: `DocumentWiki`
- Controller: `DocumentWikiController`
- Service: `DocumentWikiService`
- Mapper: `DocumentWikiMapper`
- DTO package: `dto/documentWiki`
- VO class: `DocumentWikiVis`
- API path: `/documentWiki`
- Database table: `document_wiki`

## Data Model

`document_wiki`

- `id`: primary key.
- `title`: document title.
- `content`: saved document body, stored as `longtext`.
- `summary`: optional short summary for list display.
- `category`: optional category string.
- `tags`: optional JSON string, following the existing picture tag style.
- `userId`: creator/owner user id.
- `viewCount`: read count.
- `createTime`: creation time.
- `editTime`: last user edit time.
- `updateTime`: row update time.
- `isDelete`: soft delete flag.

Stage 1 does not add `spaceId`, version tables, chunk tables, or upload file tables. These can be introduced later when team/private wiki and RAG are implemented.

## Storage And Cache

### MySQL

MySQL is the source of truth for saved document content. Every create/edit/delete operation must write to MySQL first.

### Redis

Redis reuses the existing application Redis connection. It is used for read cache only:

- document detail cache
- document list query cache

Suggested key style:

- `agentWiki:documentWiki:detail:{id}`
- `agentWiki:documentWiki:list:{queryHash}`

After create, edit, or delete, related cache entries must be invalidated. Redis must not be treated as permanent content storage in Stage 1.

### Object Storage

Stage 1 does not need object storage for online text documents. Existing COS configuration remains available for the picture module and future file/image upload work.

## Backend Flow

### Create

1. Require login.
2. Validate title/content length.
3. Save `DocumentWiki` to MySQL with current user id.
4. Clear document list cache.
5. Return created document id.

### Read List

1. Accept query conditions such as title, category, tags, and paging.
2. Try Redis cache for repeated queries.
3. Query MySQL if cache misses.
4. Return `DocumentWikiVis` page data.

### Read Detail

1. Validate document id.
2. Try Redis detail cache.
3. Query MySQL if cache misses.
4. Reject deleted/missing documents.
5. Return `DocumentWikiVis`.

### Edit

1. Require login.
2. Confirm current user can edit the document.
3. Validate new title/content.
4. Update MySQL content and `editTime`.
5. Clear related Redis cache.
6. Return success.

### Delete

1. Require login.
2. Confirm current user can delete the document.
3. Soft delete in MySQL.
4. Clear related Redis cache.
5. Return success.

## Frontend Flow

### Document List

- Show document title, summary, category/tags, creator, and edit time.
- Provide create, view, edit, and delete entry points.

### Document Detail

- Show saved title and content.
- Provide edit and delete actions when the user has permission.

### Online Editor

- Shared editor component for create and edit pages.
- Stage 1 can use a stable textarea-based editor to avoid adding a rich-editor dependency too early.
- Save calls backend create/edit APIs and then navigates to detail or list.

## Replacement Checkpoint

Before implementing, review the original picture operation flow:

- list
- detail
- create/upload
- edit
- delete
- permission check
- Redis/MySQL usage

Then implement the analogous `DocumentWiki` flow using document text instead of uploaded image files.

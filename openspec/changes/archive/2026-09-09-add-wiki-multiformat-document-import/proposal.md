## Why

Wiki documents currently behave as a Markdown-oriented text record even though the model already has a `contentFormat` field. Users need to bring local `.html` and `.md` files into a document space, view them with the right rendering, and avoid forcing uploaded HTML pages through Markdown or lossy rich-text editing.

## What Changes

- Add a local document upload/import entry in the Wiki space navigation area.
- Support uploading `.md`, `.html` / `.htm` files into the currently selected Wiki space and optional folder.
- Keep manually created documents Markdown-only.
- Store uploaded Markdown as Markdown content.
- Clean uploaded HTML into structured Markdown (h1-h3 structure marks + plain text blocks; scripts/styles/navigation/images dropped) — changed from the earlier raw-HTML plan after manual testing showed local downloads cannot render faithfully (missing sibling resources, anti-hotlinked images, JS-rendered pages).
- Render document preview by `contentFormat`: Markdown preview for Markdown, sandboxed iframe preview for legacy raw-HTML documents only, and plain text fallback for legacy plain content.
- Do not provide editing for legacy raw-HTML documents in this stage.
- Add a clickable "公开文档" aggregate node and space-level paged (20/page) all-document listing in the navigation; the right outline column shows the open document's headings or the selected folder's document titles.
- Apply warm-theme styling to the Markdown editor and preview surfaces.
- Preserve existing space visibility, folder visibility, document move/delete/recycle, search, cache invalidation, and image upload behavior.

## Capabilities

### New Capabilities

- `wiki-multiformat-document-import`: Upload local Markdown and HTML documents into Wiki spaces and render them according to their saved format.

### Modified Capabilities

- `wiki-navigation-flow`: The Wiki workspace gains an upload action in the space navigation workflow and opens imported documents in the existing three-column workspace.

## Impact

- Backend API: add a multipart import endpoint under `DocumentWikiController`; `DocumentWikiQueryRequest` gains `spaceType` for region-level paged listing.
- Backend parsing: document import service cleans HTML to Markdown via jsoup (`HtmlToMarkdownConverter`).
- Backend validation: enforce file type, size, content, and permission checks; `html` remains a valid `contentFormat` only for legacy rows.
- Backend dependencies: no Word parsing dependency is needed after `.docx` is removed from scope; jsoup is used for HTML cleaning.
- Database: reuse `document_wiki.content`, `contentFormat`, `sourceType`, and `metadataJson`; no new table is expected for the MVP.
- Frontend API: document import request wrapper; `spaceType` added to query typings.
- Frontend UI: upload controls in the space tree toolbar, aggregate "公开文档" node, paged all-document listing, dual-mode outline column, warm-themed editor/viewer, sandboxed iframe only for legacy HTML rows, Markdown-only editing.
- Tests: backend parser/controller/service tests, frontend source tests for upload/navigation/editor routing, and OpenSpec validation.

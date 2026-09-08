## Context

See `proposal.md` for motivation. The current Wiki document model already has `content`, `contentFormat`, `sourceType`, `sourceUrl`, and `metadataJson`, but backend validation only allows `plain` and `markdown`. The main `/documentWiki` workspace uses a fixed Markdown editor for create/edit, while the center preview currently renders a simplified plain-text block instead of delegating to a format-aware viewer.

The existing permission model is space-centered: document create/read actions must continue to go through visible Wiki space and folder checks. The existing `WikiAttachmentService` covers image uploads for Markdown editor content, but there is no general local document import endpoint.

## Goals / Non-Goals

**Goals:**

- Introduce a format-aware import pipeline for `.md`, `.html`, and `.htm`.
- Keep new manual authoring Markdown-only.
- Clean uploaded HTML into structured Markdown (headings + plain text blocks) instead of storing raw markup.
- Do not open legacy raw-HTML documents in an editor in this stage.
- Reuse the existing `document_wiki` table fields instead of adding a separate binary document table for the MVP.
- Keep uploaded documents inside the existing Wiki workspace, tree, list, preview, edit, search, move, delete, recycle, and cache flows.

**Non-Goals:**

- No `.docx` upload or Word preview/editing in this stage.
- No export back to `.docx` or `.html`.
- No collaborative editing, version history, or conflict resolution changes.
- No new object-storage requirement for the original uploaded document file in this MVP.

## Decisions

### Clean uploaded HTML into structured Markdown

Uploaded `.html` and `.htm` files are cleaned server-side into structured Markdown and saved with `contentFormat = "markdown"`. The cleaner keeps `h1`-`h6` headings (levels 1-3 are the primary structure marks for outline and later corpus use) and plain text blocks (paragraphs, lists, tables, code blocks); it drops scripts, styles, navigation, footers, images and other page chrome. Images are not processed in this stage — only text is kept. The original source format, file name, size, and MIME type are recorded in `metadataJson` (`sourceExtension` stays `html`/`htm`, `importedAs` is `markdown`).

Rationale (supersedes the earlier raw-HTML decision): faithful rendering of a locally downloaded HTML page is inherently unachievable for single-file import — browser-saved pages reference sibling `*_files/` CSS/JS/image resources that the upload cannot bring along, remote images are blocked by anti-hotlinking ("此图片来自 XXX 不可引用" placeholders), and dynamically rendered pages often ship without their article text at all. Users observed broken layouts and missing content in manual testing, so the direction was changed on 2026-09-08 to value text and structure over visual fidelity. Cleaned Markdown also feeds outline extraction, search, and future RAG/corpus pipelines without extra processing.

Alternatives considered:

- Store raw HTML + sandboxed iframe (the earlier decision): gives near-browser fidelity only when all resources are self-contained or hotlink-friendly, which real downloaded pages rarely are; rejected after manual testing.
- Fetch and re-host remote images during import: significant scope (anti-hotlink bypass, failure fallback, size/format checks); deferred — images are dropped for now.
- Support Word now: removed from current scope because POI text extraction does not preserve Word layout, and higher-fidelity Word preview requires a separate converter such as LibreOffice or an Office document service.

Legacy compatibility: documents already stored with `contentFormat = "html"` before this change keep their raw content and still render in the sandboxed iframe viewer branch (read-only, no scripts). The viewer's HTML branch exists solely for that legacy data; new imports never produce it.

### Add one multipart import API

Add `POST /api/documentWiki/import` with multipart fields:

- `file`: required local file.
- `spaceId`: required target Wiki space.
- `folderId`: optional target folder.
- `title`: optional override; default from file base name.

The controller should authenticate the user, require visible/editable target space according to the existing Wiki document creation behavior, validate folder ownership within the selected space, call the import service, save a `DocumentWiki`, clear space caches, and return the created document id.

The import pipeline should be isolated behind a service boundary, for example:

- `WikiDocumentImportService`: validates file metadata, detects supported type, produces normalized document content.
- `ImportedWikiDocument`: title/content/contentFormat/source metadata output object.

This keeps parsing testable without depending on controller behavior.

### Use server-side parsing and safe preview boundaries

Markdown import stores UTF-8 text as Markdown. HTML import parses UTF-8 text with jsoup, cleans it into Markdown via `HtmlToMarkdownConverter`, and rejects pages from which no text can be extracted. The backend rejects empty files, oversized files, and unsupported file extensions. It also validates the saved document using existing document validation.

New imports therefore contain no markup, so no HTML ever enters the host page from them. Legacy raw HTML documents (stored before the cleaning change) are still not rendered with `v-html`; they go through the sandboxed iframe with `srcdoc` and without `allow-scripts`, with `allow-same-origin` declared purely so the outline column can locate and scroll headings inside the frame document.

### Keep outline anchors aligned across formats

The outline column addresses headings as `wiki-heading-<index>` (1-based, matching md-editor's heading numbering) in document order:

- Markdown (including cleaned HTML imports): `MdPreview` is given `mdHeadingId` so rendered headings carry exactly those ids. Outline extraction strips fenced code blocks and matches `#{1,6}` so its ordering matches the renderer.
- Legacy raw HTML documents: headings are addressed by position through `frame.contentDocument.querySelectorAll('h1, h2, h3, h4')`, which avoids mutating the stored raw HTML while still allowing the outline to scroll the isolated document.

The outline column also serves folder navigation: when a folder is selected but no document is open, it lists the folder's document titles as clickable entries instead of showing an empty placeholder; when a document is open (preview or inline edit), it shows that document's heading outline.

### Scope the workspace to warm-theme styling

Both the Markdown editor (`MdEditor`) and preview (`MdPreview`) are themed via `:deep()` overrides (editor: `--md-*` CSS variables; preview: background/typography rules) so document surfaces use the warm-theme panel background (`--wiki-panel`) and text color (`--wiki-text`) instead of the library's default white surfaces.

### Region and space level document listing

The left navigation tree gains a clickable aggregate node `公开文档` that selects every visible public space (`type = 2`) at once; team/personal group nodes stay non-selectable containers. Selecting a space (e.g. 团队文档/研发部) or the aggregate node lists **all** documents it covers (recursing into folders) in the middle column, paged at 20 per page. Folder selections keep listing that folder's own documents inline without paging.

Backend: `DocumentWikiQueryRequest` gains `spaceType`; `prepareVisibleDocumentWikiQuery` narrows `visibleSpaceIds` to that type when present, so both paged list endpoints (plain and cached) serve the aggregate query without new endpoints. The Redis cache key already hashes the full request JSON (including `spaceType` and the per-user `visibleSpaceIds`), so region queries cannot collide with or leak across other queries.

### Switch viewer and editor by `contentFormat`

Introduce a dedicated viewer component for center preview:

- Markdown: use `MdPreview`.
- Legacy HTML (pre-cleaning imports only): render raw HTML in a sandboxed iframe preview.
- Plain or missing format: display readable plain text.

Introduce a format-aware editor wrapper:

- Create mode: always initialize Markdown editor and submit `contentFormat = "markdown"`.
- Edit Markdown: use Markdown editor and preserve Markdown format.
- Edit HTML: not supported in this stage. The UI should disable or hide edit actions for HTML documents and show a clear unsupported-edit message if an HTML edit route is reached.

### Keep navigation local to the Wiki workspace

The upload action belongs beside `新建文档` in the left space navigation toolbar. It is enabled only after a target space exists. If a folder is selected, uploads target that folder; otherwise they target the selected space root.

After a successful upload, the workspace refreshes visible spaces/tree/list as needed and opens the new document in the center column without routing away from `/documentWiki`.

### Metadata and compatibility

`metadataJson` should record at least:

- `sourceFileName`;
- `sourceExtension`;
- `sourceMimeType`;
- `sourceSize`;
- `importedAs`;
- `importedAt`.

Existing documents remain compatible:

- missing `contentFormat` or `plain` renders as plain text;
- existing Markdown documents continue to render and edit as Markdown;
- list/search queries continue to use title/content fields.

## Risks / Trade-offs

- [HTML import can introduce XSS] -> New imports are cleaned to Markdown server-side, so no markup reaches the client. Legacy raw HTML rows render only in a sandboxed iframe without script permission.
- [Large uploads can exceed database content limits] -> Enforce file size and post-conversion content length before saving; reject with a clear message. Cleaning shrinks content substantially (tags, scripts and styles are dropped), so the 6,000,000 character limit is rarely hit.
- [HTML local sibling resources are missing] -> Single-file HTML upload can never bring `*_files/` resources along; this is exactly why raw rendering was abandoned in favor of text extraction. Images are dropped rather than fetched in this stage.
- [Cleaning loses page-specific layout] -> Accepted trade-off: the knowledge base values text and heading structure over visual fidelity. Cleaned Markdown remains searchable, outline-able and corpus-ready.

## Migration Plan

1. Add backend import service for Markdown and HTML.
2. Add import endpoint and tests without changing existing create/edit endpoint behavior.
3. Allow `html` in document validation.
4. Add frontend upload API and navigation upload control.
5. Add format-aware viewer components and wire them into the center column and detail page.
6. Regenerate or update frontend API typings after backend endpoint is available.
7. Run OpenSpec validation, backend tests, frontend type/build checks, and then start local services with project scripts for human page testing.

Rollback is straightforward because no new table is required: remove the import endpoint/UI and stop creating cleaned-Markdown imports. Existing imported documents (cleaned Markdown or legacy raw HTML) remain readable through the format-aware viewer if the import entry is rolled back.

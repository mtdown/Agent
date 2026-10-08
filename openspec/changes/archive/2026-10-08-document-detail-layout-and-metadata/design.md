# Design

## Context

The Wiki preview is rendered by `WikiDocumentList.vue` in the center column, with Markdown rendered by `DocumentWikiContentViewer.vue`. URL imports already preserve `sourceUrl` and extensible `metadataJson`; `FetchedPage` currently exposes only HTML, final URL, and page title. The document view model currently omits source and metadata fields.

## Goals / Non-Goals

**Goals:**

- Keep article provenance separate from the Wiki creator and editing timestamps.
- Extract metadata from new webpage imports and display available values in the reading view.
- Make the title and action row visually consistent with the requested reading layout.
- Hide list-search controls while an individual document is open, restoring them on return to the list.

**Non-Goals:**

- Reprocessing or backfilling metadata for existing articles.
- Adding a rich-text/HTML editing experience.
- Changing Wiki permissions, import destinations, or the four document actions.

## Decisions

### Reuse `metadataJson` for source article metadata

Store normalized `publishedAt`, `sourceSite`, and `originalAuthor` values alongside the existing URL import metadata. Continue using the existing document `sourceUrl` field as the canonical link. Expose these fields through the document view model so new imports do not need to infer article metadata from rendered body text. For legacy documents that already contain a leading metadata block in Markdown, parse it at preview time, hide it from the rendered article body, and use its available provenance as a display fallback. This is display-only and does not rewrite or bulk backfill stored documents. This avoids a schema migration and keeps source metadata separate from `userId` and edit timestamps.

For URL and local HTML imports, inspect standard article/Open Graph metadata, common named metadata aliases, and JSON-LD article values. Also parse recognized leading Markdown front matter for article title, publication date, source URL/site, and author, then remove the entire front matter block from the stored article body. Prefer explicit HTML metadata over front matter values; use a valid URL hostname as the source-site label only when no site name is available. Missing author or publication date remains absent. Local files without a source URL do not receive a fabricated link.

### Keep metadata extraction limited to new imports

Run extraction in the import path before saving each new webpage document. Do not update existing rows or change their stored content as a backfill. The normal document creator remains the authenticated Wiki user.

### Render article provenance in the Wiki preview

Keep the existing four preview actions. Style the back-to-list control with the same typography and visual weight as the other actions. Present the title centered and more prominent than the body, followed by publication time, linked source site, optional original author, and system creator. Use the existing Markdown renderer and apply a two-character first-line indent to body paragraphs.

### Keep list search controls out of the reading view

Conditionally render `WikiSearchBar` only when no document is selected. This keeps keyword, match-mode, and space controls available in list/search mode, hides them during inline document preview, and naturally restores them when the existing back action clears the selected document. Search state remains intact.

## Risks / Trade-offs

- [Source sites use inconsistent metadata keys] → Support common standard aliases and JSON-LD; leave unavailable optional fields absent rather than guessing.
- [An extracted URL may be unsafe or malformed] → Render source links only for valid HTTP/HTTPS URLs and use normal external-link protections.
- [Old articles lack normalized source metadata] → Keep their creator and existing content readable; do not invent provenance or run a backfill.

## Migration Plan

No database migration is needed because source metadata uses the existing JSON column and source URL field. Deploy the importer and view-model changes together. Rollback is a code revert; old data remains untouched.

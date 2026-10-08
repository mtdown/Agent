# Proposal

## Why

Imported article metadata is currently mixed into or missing from the reading experience, and the preview header does not visually distinguish navigation from document information. New imports need to retain source details so readers can identify the publication date, source, and original author without confusing the original author with the Wiki creator.

## What Changes

- Improve the document preview header and article information layout while retaining the four existing actions.
- Hide the keyword, matching-mode, and space search controls while a document is open; restore them when returning to the list.
- Extract publication date, source site, source URL, and original author when importing new article pages; keep the Wiki creator as a separate system field.
- Separate recognized leading article metadata front matter from the imported body so those fields do not render as article text.
- Do not backfill or reprocess articles that were imported before this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `wiki-document-actions`: define consistent preview action styling and the title and article metadata presentation.
- `wiki-multiformat-document-import`: define metadata extraction for newly imported article pages and preserve optional-field behavior.

## Impact

- Frontend Wiki document preview component and generated API view types.
- Backend webpage import metadata extraction and document view model.
- Existing `metadataJson` storage is reused; no database migration or historical data backfill is required.
- Tests for webpage metadata extraction, imported document serialization, and preview rendering.

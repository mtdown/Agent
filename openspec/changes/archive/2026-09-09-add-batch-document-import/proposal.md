## Why

The Wiki already supports importing an individual local document, but users still need a dependable way to collect multiple webpages and files into a selected Wiki destination. This change adds a unified batch-document workflow without replacing the existing multi-format import implementation.

## What Changes

- Add a `批量文档` entry to top navigation for logged-in users.
- Add a batch-document page that accepts a newline-separated list of webpage URLs and a target Wiki space with an optional folder.
- Implement Java-based webpage download, content selection, cleanup, and Markdown conversion using the behavior of `F:/AIProject/my/03-html-cleaner/fetch_url.py` and its cleaner as the reference.
- Add local multi-file upload to the same page and reuse the existing local document import behavior for each supported file.
- Return a distinct result for every URL or file so successful imports remain available when other items fail.
- Keep all created documents as normal Markdown Wiki documents, with source and import metadata for traceability.
- Exclude WeChat MCP integration from this change.

## Capabilities

### New Capabilities

- `batch-document-import`: Authenticated batch webpage and local-file import into Wiki destinations with per-item results.

### Modified Capabilities

- `wiki-navigation-flow`: Top navigation exposes the batch-document page to authenticated users.

## Impact

- Frontend: top navigation, batch-document route/page, destination picker, URL/file inputs, and result display.
- Backend: safe webpage fetcher, Java HTML-to-Markdown cleaner, batch import APIs and outcome models.
- Data: reuse existing `document_wiki` source and metadata fields without a new table.
- Tests: cleaner fixtures, URL safety/limits, per-item batch isolation, navigation visibility, and document persistence.

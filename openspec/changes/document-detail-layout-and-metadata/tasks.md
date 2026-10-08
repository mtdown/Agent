# Tasks

## 1. Article metadata extraction

- [x] 1.1 Add backend tests for publication-time, source-site, source-URL, and original-author extraction from common HTML metadata, JSON-LD, and recognized front matter; verify the targeted Maven test fails before implementation and passes afterward.
- [x] 1.2 Extract available article metadata during new URL, HTML, and metadata-bearing Markdown imports, remove recognized leading front matter from the stored body, retain metadata in `metadataJson`, and verify missing optional fields do not fail import or replace the Wiki creator.
- [x] 1.3 Expose source URL and normalized article metadata through the document view model and verify a backend serialization test preserves them while creator fields remain separate.

## 2. Document preview presentation

- [x] 2.1 Add frontend flow assertions for preview action labels, metadata ordering, optional author, and safe source links; verify the assertions fail before implementation and pass afterward.
- [x] 2.2 Update the preview title, actions, metadata row, legacy metadata display fallback, search-bar visibility, and Markdown paragraph styling; verify the Wiki frontend build passes.

## 3. Integration and hand testing

- [x] 3.1 Run targeted backend tests, frontend flow tests, frontend build, and OpenSpec validation; record results and pause on failure, report the cause and proposed correction, and wait for owner confirmation before a new repair-test cycle.
- [ ] 3.2 Start the task-branch local services with `stop-dev.ps1` followed by `start-dev.ps1`, then provide the owner with manual checks for a newly imported article and an article without author metadata.

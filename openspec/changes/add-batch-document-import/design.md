## Context

The Wiki already imports a single local `.md` / `.html` / `.htm` file: `DocumentWikiController.importDocumentWiki`
delegates to `WikiDocumentImportService.parse(MultipartFile, String)`, which cleans HTML with
`HtmlToMarkdownConverter` (Jsoup) and stores the result as a Markdown `document_wiki` row
(`sourceType=UPLOAD`, `metadataJson` with file/extension/import metadata).

There is no server-side webpage download capability today. The reference behavior lives in
`F:/AIProject/my/03-html-cleaner/fetch_url.py` + `html_cleaner.py`: download → strip non-content DOM
(L1) → locate the article container with a known-site priority list and a generic hint fallback (L2) →
drop short UI-only lines (L3) → serialize to Markdown (L4).

Batch import adds two new concerns on top: every submitted URL/file must produce its own result
without one failure killing the batch, and fetching an arbitrary user-supplied URL must not let the
server be used to reach internal networks. See `proposal.md` for motivation and
`specs/batch-document-import/spec.md` for the requirements.

## Goals / Non-Goals

**Goals:**
- One Java cleaning pipeline that URL imports and local `.html`/`.htm` uploads both use.
- A fetch path with explicit, per-hop address validation and enforced timeout / redirect / size limits.
- A batch API returning a stable per-item result (input label, status, message, created document id).
- Keep every created document a normal Markdown Wiki document with `sourceType` / `sourceUrl` / `metadataJson`.

**Non-Goals:**
- Replacing or removing the existing single-file `POST /api/documentWiki/import` endpoint.
- WeChat MCP integration, async job queue / progress polling, duplicate detection, scheduling.
- Downloading and re-hosting images referenced by imported pages.
- New tables or columns (`document_wiki` fields are reused as-is).

## Decisions

### 1. One Jsoup cleaning pipeline, ported from the Python reference; shared by URL and local HTML

New package-private `WebPageMarkdownCleaner` (same package as `HtmlToMarkdownConverter`) implements the
four layers with Jsoup:

- **L1 discard**: `script / style / noscript / link / meta / iframe / svg`, `[hidden]`, `[aria-hidden=true]`,
  inline `display:none` / `visibility:hidden` / `opacity:0`, and overlay-share classes
  (`.weui-mask`, `.overlay`, `.popup`, `.modal`, `.share*`, `.rich_media_tool`, `.qr_code`, …).
  A content-like container (id/class matching `js_content|rich_media_content|trs_editor|article|content`)
  with more than 200 characters is **not** dropped by the hide rules — WeChat-style pages pre-hide the
  real article, and dropping it loses the whole body.
- **L2 container selection**: known-container priority list (`#js_content`, `.rich_media_content`,
  `.Post-RichText`, `.RichText.ztext`, `.trs_editor_view`, `.TRS_UEDITOR`, `#zoom`, `article`,
  `.article-content`, …) with a >40 character minimum; otherwise the largest `div/section/td` whose
  id/class matches `content|article|main|list|body|doc|text|editor` and does **not** match the chrome
  hint (`footer|header|nav|side|menu|comment|recommend|share|bread|qrcode|login|…`), requiring >=120
  characters; otherwise the parent element with the most `<p>` children (>=2).
- **L3 noise lines**: remove a line when, after stripping punctuation and a UI phrase list
  (微信/知乎/导航翻页/通用), nothing meaningful remains — and only for short lines (<=30 chars),
  or meta lines (<=60 chars) matching the "发布于 / 著作权归作者所有 / 阅读原文 / …" pattern.
  Short-line de-duplication is applied **only** when the container came from the generic class-hint
  fallback, because legitimate repeats (bylines, sub-headings) occur inside real article containers.
- **L4 serialization**: reuse the existing `HtmlToMarkdownConverter.convert(String)` on the selected
  container's HTML so headings, paragraphs, lists, tables, code and blockquotes keep their current
  Markdown shape.

`WikiDocumentImportServiceImpl` switches its HTML branch to `WebPageMarkdownCleaner`, so a URL import
and a local HTML upload run the identical policy (spec: "HTML inputs use a consistent cleaning policy").

Alternatives considered:
- Shell out to the Python script with `ProcessBuilder` — rejected: adds a Python + beautifulsoup4
  runtime dependency to a Spring Boot service and makes deployment/ops non-hermetic.
- Keep using `HtmlToMarkdownConverter` as-is — rejected: it has no container selection and no
  noise-line filtering, so overlays, sharing controls and navigation chrome survive (spec violation).

### 2. `WebPageFetcher` seam with manual redirect following

`WebPageFetcher` (interface) returns `FetchedPage { html, finalUrl, pageTitle }`.
`HttpWebPageFetcher` uses `HttpURLConnection` with `setInstanceFollowRedirects(false)` and follows
redirects manually (max 3 hops), re-validating every hop.

Rationale: the safety check must run per hop, not once. Hutool's `HttpUtil` / auto-redirect clients
follow a redirect to `127.0.0.1` or `169.254.169.254` after the initial host passed validation, which
defeats the SSRF check. The interface also lets batch tests run offline with a stub fetcher.

### 3. URL safety: scheme + resolved-address checks, per hop

Reject anything that is not `http` / `https`; reject a host whose `InetAddress` resolution contains a
loopback, link-local, site-local (private), multicast or unspecified address, and reject non-standard
ports implicitly by not allowing them to be smuggled in. Failures return a per-item result with an
"unsafe" message and never open a connection. Known limitation: DNS rebinding between validation and
connect is not defended against in this stage (internal tool, see Risks).

### 4. Decoding follows the reference: declared charset → meta charset → UTF-8 → GB18030

`Content-Type` charset first, then `<meta charset=…>` read with Jsoup, then a strict UTF-8 attempt with
a GB18030 fallback (Chinese government/news sites are frequently GB18030). This mirrors
`fetch_url.decode_html` and prevents the mojibake that plain UTF-8 decoding produces on those pages.

### 5. Two batch endpoints under `/documentWiki/batch`, both returning per-item results

- `POST /api/documentWiki/batch/url` — JSON `{ spaceId, folderId?, urls: string[] }`
- `POST /api/documentWiki/batch/file` — multipart `{ spaceId, folderId?, files: MultipartFile[] }`

Both return `BaseResponse<List<BatchImportItemResult>>`, where each item is
`{ input, status, message, documentId, title }`. Space editability and folder visibility are validated
once per request; every item is processed inside its own `try/catch` so a download, parse, or save
failure becomes one failed item while the rest of the batch still lands.

A single mixed endpoint was rejected: a URL list has no natural multipart representation, and a mixed
payload makes per-item labels ambiguous.

### 6. `WikiBatchImportService` orchestrates; the controller stays thin

`WikiBatchImportServiceImpl` owns: login check (via `UserService.getLoginUser`), editable-space check,
folder resolution, per-item loop, document persistence, and cache clearing.
`DocumentWikiBatchController` only parses the request and delegates.

Document persistence in the batch service builds the `DocumentWiki` row exactly like the existing
single-file import (title, content, `contentFormat=markdown`, `sourceType`, `sourceUrl`, `metadataJson`,
empty tags JSON, summary, userId, spaceId, folderId, viewCount, editTime, `validDocumentWiki`, `save`,
`WikiCacheManager.clearSpace`). The ~15 lines are duplicated instead of extracted into
`DocumentWikiService` because `DocumentWikiControllerImportTest` asserts that the controller itself
calls `documentWikiService.save(...)`; extracting the logic would break that existing test and widen
this change beyond its scope. Noted as a follow-up refactor.

Local files reuse `WikiDocumentImportService.parse(file, null)` per file — same validation, same
metadata, same extensions. URLs build an `ImportedWikiDocument` with `sourceType=URL`, `sourceUrl` set
to the final URL after redirects, and a title from `<title>` (falling back to the URL).

### 7. Limits are configurable with safe defaults

`wiki.batch-import.*`: `max-items` (20), `connect-timeout-ms` (10000), `read-timeout-ms` (20000),
`max-redirects` (3), `max-response-bytes` (5 MiB). Bound with `@Value` defaults so the service still
works without extra configuration and stays unit-testable with plain constructors.

### 8. Frontend: one page reached from a login-only navigation entry

- `GlobalHeader` gains `loginOnly` on `RawNavItem`; entries with `loginOnly: true` are filtered out when
  there is no logged-in user (today only `adminOnly` exists, which is insufficient for a
  "logged-in users" rule).
- New route `/documentWiki/batch` → `pages/documentWiki/DocumentWikiBatchImportPage.vue`, and the
  header's `current` computed keeps the batch path highlighted.
- The page reuses `listVisibleSpaceUsingGet` for the destination space and the existing folder tree
  data for the optional folder, offers a newline-separated URL textarea plus a multi-file upload
  (`.md`/`.html`/`.htm`), and renders a result table with input, status, message, and a link to the
  created document.

## Risks / Trade-offs

- **Heuristic cleaning can still leave UI text on unknown sites** → Mitigation: the noise filter only
  removes *short* lines that empty out after UI-phrase removal, and short-line de-duplication is
  limited to the generic fallback container; recognized article containers are never de-duplicated.
- **DNS rebinding / redirect to a private IP after validation** → Mitigation: every hop is re-validated;
  full rebinding defense (pinning the resolved IP for the connection) is out of scope for this internal
  tool and is recorded as a follow-up if the tool is ever exposed publicly.
- **Slow or huge pages hold request threads** → Mitigation: read timeout, max response size, and a
  per-batch item cap; an async queue is explicitly a non-goal for this change.
- **Sharing the cleaner changes existing HTML upload output** → Mitigation: `WikiDocumentImportServiceTest`
  assertions are updated in the same change; the previous Markdown shape (headings/paragraphs/lists/
  tables/code) is preserved by reusing `HtmlToMarkdownConverter` for serialization.
- **Hand-written frontend API wrappers** → The generated `src/api/*` files come from `npm run openapi`
  against a running backend; the new wrappers are added in the generated style and should be
  regenerated once the backend is up, to confirm the signatures match.

## Migration Plan

- No schema change and no data backfill: imported pages are ordinary Markdown `document_wiki` rows.
- Deploy backend and frontend together; the navigation entry only appears for logged-in users, and the
  old single-file import path is untouched.
- Rollback: revert the deploy. Documents already created remain valid Markdown documents; nothing needs
  to be undone in the database.

## Open Questions

- Should images inside imported pages be re-hosted as Wiki attachments? Deferred — out of scope here,
  and it needs an attachment/link-rewrite design of its own.

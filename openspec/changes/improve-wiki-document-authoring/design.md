## Context

See `proposal.md` for motivation. The current frontend already has a reusable `DocumentWikiEditor.vue`, but it uses `md-editor-v3`, which exposes a Markdown-oriented authoring model. `DocumentWikiListPage.vue` owns the three-column Wiki workspace state, and `WikiSearchBar.vue` owns the search row where the new in-workspace `创建文档` action should appear. The backend already stores body content in `document_wiki.content` and tracks the format in `contentFormat`.

## Goals / Non-Goals

**Goals:**

- Make document creation and editing feel like normal document writing: one visible editing surface, formatted text shown directly.
- Save new rich-text content as HTML with `contentFormat=html`.
- Keep existing `markdown` and `plain` documents readable.
- Move normal document creation into `/documentWiki` so the selected left-navigation space/folder controls the default location.
- Keep the implementation scoped to the Wiki authoring experience.

**Non-Goals:**

- No collaborative editing, comments, version history, or permissions redesign.
- No database schema change unless current validation rejects `contentFormat=html`.
- No migration of existing Markdown documents to HTML.
- No redesign of recycle bin or document space management.

## Decisions

### Decision: Replace Markdown editing with Tiptap WYSIWYG editing

Use Tiptap for the editor surface, with `@tiptap/vue-3`, `@tiptap/pm`, and `@tiptap/starter-kit`. StarterKit covers the first rich-text scope: paragraph, heading, bold, italic, bullet list, ordered list, blockquote, undo, and redo. Add the Tiptap image extension only if the implementation preserves image insertion in the first pass.

Reason:

- Tiptap is a Vue 3-compatible rich-text editor built around editable document content, not a Markdown source/preview interface.
- It lets the toolbar change the document model while the user sees formatted output immediately.
- The existing `DocumentWikiEditor.vue` can remain the reusable boundary for create/edit pages.

Alternatives considered:

- Keep `md-editor-v3` and force single-pane mode. This removes the split view but users would still author Markdown syntax such as `**text**`, which does not meet the requested writing experience.
- Use a full document suite-style editor. That is heavier than the current need and would increase integration risk before the basic Wiki workflow stabilizes.

### Decision: Save new rich-text content as HTML

For new WYSIWYG documents, submit `contentFormat: 'html'` and serialize the editor content as HTML. Detail rendering checks `contentFormat`:

- `html`: render sanitized or trusted application-authored HTML in the document body.
- `markdown`: keep the existing Markdown preview rendering for older documents.
- `plain` or missing: display escaped plain text as today.

Reason:

- HTML is the closest persisted form to what the user sees in a rich-text editor.
- Converting rich text back to Markdown can lose editor-specific structure and creates unnecessary edge cases.
- The current database content field can already hold text content, so this avoids schema churn.

### Decision: Workspace creation is a center-column mode

`DocumentWikiListPage.vue` should own a small center-mode state, for example `browse | create | read`. When the user clicks `创建文档`, the center column switches to create mode and renders `DocumentWikiEditor.vue` with an initial location derived from `currentSelection`:

```text
+-------------------+-----------------------------+----------------+
| left navigation   | center column               | right outline  |
+-------------------+-----------------------------+----------------+
| selected space    | browse/search/list          | selected doc   |
| or folder         |            |                | outline        |
|                   | 创建文档 -> create editor   | empty in create|
+-------------------+-----------------------------+----------------+
```

Create mode behavior:

- If a folder is selected, prefill both `spaceId` and `folderId`.
- If a space root is selected, prefill `spaceId` and clear `folderId`.
- On successful save, refresh spaces/tree/list and open the new document in the workspace.
- On cancel, return to browse/read state without leaving `/documentWiki`.

### Decision: The editor accepts an initial location

Extend `DocumentWikiEditor.vue` with an initial-location prop rather than making it read route state directly. The parent page decides context; the editor only renders fields, fetches selectable spaces/folders, and emits submitted values.

Reason:

- Keeps the editor reusable for standalone compatibility routes and future edit forms.
- Avoids coupling form internals to `DocumentWikiListPage.vue` route/query behavior.

### Decision: Remove standalone create navigation, keep direct route compatibility

Remove `文档创建` from `GlobalHeader.vue`. Keep `/add_documentWiki` available only as a compatibility path or redirect into `/documentWiki` create mode. Normal users should discover creation from inside the Wiki workspace after selecting a location.

## Risks / Trade-offs

- [Risk] HTML rendering can introduce unsafe markup if arbitrary HTML is accepted. -> Mitigation: serialize only editor-generated HTML on the client and add sanitization or constrained rendering before using `v-html`.
- [Risk] Backend validation may currently reject `contentFormat=html`. -> Mitigation: update the allowed content formats and add focused service validation coverage.
- [Risk] Replacing the editor may temporarily regress image paste/drag behavior. -> Mitigation: either include the Tiptap image extension and reuse `uploadWikiImageUsingPost`, or explicitly defer image insertion and keep the task visible.
- [Risk] Existing Markdown documents edited through the new editor may need conversion semantics. -> Mitigation: first pass may save edited documents as HTML after loading rendered/converted content, while read-only viewing remains format-aware.

## Migration Plan

1. Switch implementation work to `feature/wiki-wysiwyg-document-create开发` from latest `main`.
2. Install the chosen rich-text editor dependencies in `cloud_front`.
3. Replace the body portion of `DocumentWikiEditor.vue` with a WYSIWYG editor and toolbar.
4. Add an initial-location prop to the editor and apply it from `DocumentWikiListPage.vue`.
5. Add center-column create mode in `DocumentWikiListPage.vue` and wire `WikiSearchBar.vue` to emit `create`.
6. Remove the standalone `文档创建` top navigation entry and preserve `/add_documentWiki` compatibility.
7. Update document detail rendering for `html` while preserving `markdown` and `plain`.
8. Update backend validation/tests only if `html` is rejected.
9. Run focused frontend build/type checks and relevant backend tests, then record issues in `IssueLog.xlsx`.

Rollback is limited to the new frontend dependency, editor component changes, workspace create-mode wiring, and any small backend validation allowance. Existing saved documents remain format-tagged and should not require rollback migration.

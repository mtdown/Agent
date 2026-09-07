## Why

The current Wiki document authoring flow still feels like a Markdown tool: the editor can show a split source/preview experience, and document creation is exposed as a separate top-level page. Users expect Wiki writing to behave like normal document editing, where formatted text is shown directly while typing and document creation happens in the current workspace context.

## What Changes

- Replace the Markdown split editor experience with a true WYSIWYG rich-text editor for Wiki document creation and editing.
- Save newly authored rich-text document content as HTML by default.
- Keep existing Markdown and plain-text documents readable by preserving their current display paths.
- Add an in-workspace `创建文档` action next to the Wiki search action.
- When a user selects a Wiki space or folder in the left navigation and clicks `创建文档`, replace the center workspace content with the document creation form.
- Automatically prefill the creation form location from the selected left-navigation space/folder.
- Remove the standalone `文档创建` top-navigation entry; keep route compatibility only if needed for direct legacy access.

## Capabilities

### New Capabilities

- `wiki-document-authoring`: WYSIWYG document authoring, HTML save format, and workspace-context document creation.

### Modified Capabilities

- `wiki-navigation-flow`: Top-level navigation and Wiki workspace flow change so document creation is initiated inside the Wiki document page rather than as a separate navigation destination.

## Impact

- Frontend dependencies: add a Vue 3 WYSIWYG editor stack, preferably Tiptap (`@tiptap/vue-3`, `@tiptap/pm`, `@tiptap/starter-kit`) plus image support if image insertion remains in scope.
- Frontend components: update `DocumentWikiEditor.vue`, `DocumentWikiListPage.vue`, `WikiSearchBar.vue`, `GlobalHeader.vue`, router handling, and document detail rendering.
- Backend/API: no database schema change is expected because `document_wiki.content` and `contentFormat` already support format-tagged text content. Service validation may need to allow `contentFormat=html` if it does not already.
- Compatibility: existing `markdown` and `plain` documents must continue to render correctly.

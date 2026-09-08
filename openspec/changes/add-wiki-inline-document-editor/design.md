## Context

See `proposal.md` for motivation. The current Wiki page already has a three-column workspace in `DocumentWikiListPage.vue`: left space tree, center document list/preview, and right outline. Document reading from the list is already inline, but creation still comes from the top navigation `/add_documentWiki`, and editing from the workspace still navigates to `/edit_documentWiki/:id`.

The shared `DocumentWikiEditor.vue` already supports both create and edit form fields, Markdown editing, folder selection, and image upload. The main implementation decision is how to host that editor in the center column without duplicating editor behavior.

## Goals / Non-Goals

**Goals:**

- Make `/documentWiki` the normal workspace for reading, creating, and editing Wiki documents.
- Replace the center column contents with `DocumentWikiEditor.vue` for create/edit mode.
- Keep the selected space/folder context from the left tree when creating a document.
- Keep direct `/add_documentWiki` and `/edit_documentWiki/:id` pages available for compatibility unless later cleanup proves they are safe to remove.
- Keep folder management available through tree node menus.

**Non-Goals:**

- No backend API changes.
- No database, permission, or attachment model changes.
- No replacement of `DocumentWikiEditor.vue` or Markdown editor dependency.
- No redesign of recycle bin, document space management, or gallery flows.

## Decisions

### Decision: Treat the center column as a small workspace state machine

`DocumentWikiListPage.vue` should own a local center-column mode such as `browse`, `preview`, `create`, and `edit`.

```text
+------------------+      click document       +------------------+
| browse list       | -----------------------> | preview document |
+------------------+                           +------------------+
        |                                             |
        | click new document                          | click edit
        v                                             v
+------------------+       save / cancel       +------------------+
| create editor    | -----------------------> | edit editor      |
+------------------+                           +------------------+
```

The editor should be rendered inside the existing center `wiki-document-column`, replacing the search/list/preview area only while editing. The left tree and right outline columns stay mounted so the user does not lose workspace context.

Alternative considered: keep using router navigation with query flags such as `/documentWiki?mode=create`. This is useful for deep-linking but heavier than needed for this interaction fix. Local state keeps the change focused and avoids creating new route contracts.

### Decision: Reuse `DocumentWikiEditor.vue`

The inline create/edit surface should reuse `DocumentWikiEditor.vue` instead of creating a second editor component. If the editor needs a default location, extend it with optional props for `initialSpaceId` and `initialFolderId` or equivalent existing-pattern inputs.

Rationale: the editor already centralizes form validation, folder loading, Markdown editing, and image upload behavior. Reuse avoids drift between standalone pages and inline workspace editing.

Alternative considered: move all create/edit logic into `DocumentWikiListPage.vue`. Rejected because it would duplicate form behavior and make the composition component too large.

### Decision: Convert workspace edit actions from router navigation to emitted events

`WikiDocumentList.vue` should emit an `edit` event for list-item and selected-preview edit actions. The parent page can then load the full document if needed and switch the center column to edit mode.

Rationale: the parent already owns `selectedDocument`, document loading, tree refresh, and current folder list refresh. Keeping routing out of the child component matches the existing `open`, `move`, and `delete` event pattern.

### Decision: Space tree toolbar creates documents; node menu still manages folders

`WikiSpaceTree.vue` should replace its selected-space toolbar button label/action with `新建文档` and emit a create-document event carrying the current tree selection. Existing node menu actions for folder creation and management remain in place.

Rationale: a toolbar button represents the most common document authoring action, while folder management remains contextual to a selected space or folder node.

### Decision: Save refreshes navigation and document list before previewing the saved document

After inline create or edit succeeds, `DocumentWikiListPage.vue` should:

1. refresh visible spaces / folder tree enough to reflect document counts and folder contents,
2. refresh the current directory list or search results when relevant,
3. load or update the saved document,
4. return the center column to document preview mode.

This keeps the visible workspace coherent after a mutation.

## Risks / Trade-offs

- **Risk: editor props and watcher defaults fight each other** -> Mitigation: initialize create mode with explicit space/folder defaults and ensure the editor only applies defaults when no document is being edited.
- **Risk: refreshing the whole tree after every save feels slow** -> Mitigation: start with the existing `refresh` helpers for correctness; optimize to targeted refresh only if manual testing shows a delay.
- **Risk: cancel behavior loses prior preview/list state** -> Mitigation: keep the previous center mode and selected document before entering create/edit mode.
- **Risk: standalone add/edit routes diverge from inline behavior** -> Mitigation: keep both pages using the same `DocumentWikiEditor.vue`.

## Migration Plan

1. Remove the `文档创建` menu item from the top navigation.
2. Add create-document event support to `WikiSpaceTree.vue` and change the toolbar button label/action.
3. Add an edit event to `WikiDocumentList.vue` and remove normal workspace edit router pushes.
4. Add center-column create/edit modes to `DocumentWikiListPage.vue`.
5. Wire inline create to `addDocumentWikiUsingPost` and inline edit to `editDocumentWikiUsingPost`.
6. Refresh tree/list state after successful create/edit, then show the saved document in preview.
7. Add focused frontend source tests for navigation/menu/editor wiring.
8. Run frontend type checks and build checks.

Rollback is limited to the frontend files above. No backend or database migration is required.

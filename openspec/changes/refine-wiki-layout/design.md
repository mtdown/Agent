## Context

See `proposal.md` for motivation. The current frontend is a Vue application using Ant Design Vue layout and menu components. The main shell renders `GlobalHeader` above a nested layout that includes `GlobalSider` and the routed page content. Wiki document pages already have space/folder/document components, including tree, list, detail, recycle, and space management pieces.

The approved visual reference is the standalone `wiki-layout-demo.html` prototype created during exploration. It establishes the intended direction: black top navigation, warm off-white background, orange scrollbars, and three Wiki document columns.

## Goals / Non-Goals

**Goals:**

- Make the top navigation the only global navigation surface.
- Remove the current global left sidebar from the application shell.
- Keep Wiki-specific tree navigation inside the Wiki document page only.
- Convert the Wiki document reading experience to three columns:
  - left: Wiki spaces, folders, and document navigation;
  - center: selected document title, metadata, and content;
  - right: outline for the current document.
- Preserve existing user/admin menu visibility behavior.
- Keep the migration frontend-focused and avoid backend behavior changes.

**Non-Goals:**

- No redesign of document CRUD rules, space permissions, recycle-bin semantics, or cache behavior.
- No database schema changes.
- No new image management business capability.
- No implementation of document version history, comments, collaboration, or advanced editor features.
- No merge to `main` without user authorization.

## Decisions

### Decision: Remove `GlobalSider` from the layout shell

`BasicLayout` should no longer render the global left sidebar. This gives every page a consistent top-navigation-only application shell and prevents global navigation from competing with Wiki document navigation.

Alternative considered: keep `GlobalSider` but hide it on Wiki pages only. This was rejected because the user's confirmed direction is a global top navigation layout, not a Wiki-only exception.

### Decision: Keep all global destinations in `GlobalHeader`

`GlobalHeader` should own the full top navigation list:

- `WIKI文档`
- `文档创建`
- `文档空间管理`
- `回收站`
- `图库功能`
- `图片管理`
- `文档管理`
- `图片空间管理`
- `用户管理`

The header should continue filtering administrator-only entries based on the current login user role. Existing route paths should be reused where possible. Where an entry points to a panel that currently lives inside the Wiki page, implementation can either route to a dedicated page wrapper or open the corresponding Wiki page state, but the top navigation label must remain stable.

Alternative considered: group items into dropdown menus such as "文档" and "管理". This was rejected for the first implementation because the user wants the explicit top-level sequence visible.

### Decision: Treat Wiki left tree as page-local navigation

The left column in the Wiki document page is not a replacement for `GlobalSider`; it is part of the document workspace. It should show Wiki spaces, folders, and document navigation only. It must not include unrelated gallery, picture, or user management entries.

This keeps the mental model simple:

```text
+--------------------------------------------------------------+
||                  black global top navigation                ||
++-------------------+--------------------------+---------------+
|| Wiki tree         | document title + content | doc outline   ||
|| spaces/folders    | selected Wiki document   | headings only ||
++-------------------+--------------------------+---------------+
```

### Decision: Use the demo as visual baseline, adapted to Ant Design Vue

The migration should preserve the demo's proportions and palette while using the project's existing Vue and Ant Design Vue components. CSS should define a small set of Wiki layout variables for black navigation, off-white background, paper-like panels, muted borders, and orange scrollbar styling.

Alternative considered: implement the demo as plain HTML inside Vue. This was rejected because it would duplicate interaction behavior and make future maintenance harder.

### Decision: Generate the right outline from document headings

The right outline should represent the currently selected document, not the folder tree. In the first implementation, it can parse rendered Markdown/HTML headings after content render and build anchor links. If heading parsing is unreliable for some content, an empty or minimal outline state is acceptable as long as the page remains usable.

Alternative considered: store outline data in the backend. This was rejected because the change is a layout and reading experience refinement, not a document data-model change.

## Risks / Trade-offs

- Header overcrowding on smaller desktop widths -> allow horizontal scrolling or responsive wrapping for the top navigation, and verify no text overlap.
- Removing `GlobalSider` may affect non-Wiki pages that relied on it for navigation -> ensure all former global sidebar destinations have top navigation entries or reachable in-page links.
- Existing Wiki components may already assume a two-column layout -> migrate incrementally and keep data-fetching behavior unchanged while changing placement.
- Right outline generation may fail for documents without headings -> show an unobtrusive empty state instead of blocking document reading.
- Custom scrollbar styling differs by browser -> use standard fallback behavior where browser-specific styling is unsupported.

## Migration Plan

1. Create or switch to a feature branch named `feature/wiki-layout开发` from the latest `main`.
2. Update the application shell to remove `GlobalSider` and use the top navigation as the only global navigation.
3. Update `GlobalHeader` route entries, labels, active state mapping, black theme styling, and role-based filtering.
4. Refactor Wiki document page layout into three columns while reusing existing Wiki data components where practical.
5. Add or adapt page wrappers for document space management, recycle bin, and document management if top navigation entries need direct routes.
6. Apply the approved off-white background and orange scrollbar styling to Wiki layout surfaces.
7. Run frontend type checks and relevant manual UI verification.
8. Record any encountered errors or blockers in `IssueLog.xlsx`.

## Rollback Plan

Revert the frontend layout and Wiki page changes from the feature branch before merge. Since no backend or database changes are planned, rollback should be limited to frontend files and generated route/menu changes.

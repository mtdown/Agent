## Context

See `proposal.md` for motivation. The Wiki workspace already ships a warm palette (`#f5eddf` page / `#fffaf1` panel / `#ded0bc` border / `#e07a2d` accent) declared inside `DocumentWikiListPage.vue`. The same warm palette now needs to become the shared visual baseline across the rest of the application, and the Wiki document page needs a tighter, equal-height three-column layout.

The approved visual baseline is the existing Wiki page itself. Other pages (`HomePage`, `PictureManagePage`, `SpaceManagePage`, `UserManagePage`, `SpaceUserManagePage`, `WikiSpaceManagePanel`, etc.) currently render their cards and tables with Ant Design Vue defaults (white surfaces, blue selection), which feel disconnected.

## Goals / Non-Goals

**Goals:**

- Promote the warm palette from a Wiki-page-local stylesheet to an application-wide theme via CSS variables and a single shared stylesheet.
- Re-style Ant Design Vue surfaces (cards, tables, pagination, list, empty, inputs, dropdowns) so they inherit the warm palette by default on the targeted pages.
- Make the three Wiki columns share one height: each column scrolls independently, and the visual container is the same height.
- Remove the redundant `公开文档` group wrapper from the space navigation tree; public spaces mount directly at the root.
- Reorder the Wiki search `匹配模式` radio group so it reads `标题 | 正文 | 标题或正文` left to right.

**Non-Goals:**

- No backend, API, database schema, or permission changes.
- No new product capability; this is a presentation/IA refinement.
- No changes to other navigation entries, role-based filtering, or routing.
- No merge to `main` without explicit user authorization.

## Decisions

### Decision: Centralize the warm theme in a single shared stylesheet

Add `cloud_front/src/styles/warm-theme.css` exposing CSS variables and component-level overrides. Import it once from `src/main.ts` after `ant-design-vue/dist/reset.css`. Variables:

```css
:root {
  --wiki-bg: #f5eddf;
  --wiki-panel: #fffaf1;
  --wiki-panel-head: #fbf3e7;
  --wiki-border: #ded0bc;
  --wiki-muted: #f1eadf;
  --wiki-text: #251f18;
  --wiki-text-muted: #7b6c5d;
  --wiki-accent: #e07a2d;
  --wiki-accent-soft: #fff0df;
  --wiki-accent-hover: #fde6c8;
  --wiki-shadow: 0 1px 0 rgba(82, 56, 28, 0.04);
}
```

The stylesheet overrides Ant Design Vue surfaces where they currently paint pure white (`.ant-card`, `.ant-card-head`, `.ant-table`, `.ant-table-thead th`, `.ant-table-tbody > tr > td`, `.ant-pagination .ant-pagination-item`, `.ant-list`, `.ant-empty`, `.ant-select-selector`, `.ant-input`, `.ant-modal-content`, `.ant-tree-node-content-wrapper`, `.ant-tree-node-selected`, `.ant-checkbox`, `.ant-radio-button-wrapper`).

`DocumentWikiListPage.vue` is updated to consume the same CSS variables instead of hard-coded hex values. `BasicLayout.vue`'s hard-coded `#f5eddf` / `#e8dac6` are also replaced with variables.

Alternative considered: configure Ant Design Vue theme tokens via `<a-config-provider :theme="...">`. This was rejected because a custom warm palette tokenization across all antd components is heavier than a targeted CSS override, and the existing scope is a small set of surfaces.

### Decision: Equal-height three-column workspace

`DocumentWikiListPage.vue`'s `.wiki-shell` switches from `align-items: start` to `align-items: stretch`, gains a fixed shell height, and each column becomes `height: 100%; overflow: auto`.

```text
+--------------------------------------------------+
| #documentWikiListPage (padding 16, bg warm)      |
|  +---- wiki-shell (height calc(100vh - 138px) ----+
|  | left panel  | center panel  | right panel    |
|  | tree        | doc list      | outline        |
|  | h=100%      | h=100%        | h=100%         |
|  | scroll      | scroll        | scroll         |
|  +-----------------------------------------------+
```

Each column already has its own scrollbar; the shell height + `align-items: stretch` ensures they are the same height. Below 900px the shell collapses to a single column and the fixed height is dropped.

### Decision: Public spaces live at the navigation tree root

In `WikiSpaceTree.vue`, drop the `公开文档` group wrapper and render public-type spaces alongside the existing `团队文档` and `个人文档` groups at the top level. Public spaces keep their `space:` node type and folder children. The auto-select-on-first-load logic still picks the first public space if it exists, otherwise the first team space, otherwise the first personal space. The `nodeType === 'group'` exclusion in `onSelect` and the existing render templates still apply.

Alternative considered: remove all group wrappers (公开/团队/个人). This was rejected because the user confirmed keeping 团队/个人分组.

### Decision: Reorder 匹配模式 radio group

In `WikiSearchBar.vue`, swap the `<a-radio-button>` order so `标题` → `正文` → `标题或正文`. The `useWikiSearch.ts` default stays `titleOrContent`; the visible labels move, but the default selection continues to render `标题或正文` (now last). This matches the user's explicit ordering requirement.

### Decision: Theme scope for non-Wiki pages

Apply the warm theme to the page containers and Ant Design Vue surfaces on the following pages only, all via the shared stylesheet (no per-page CSS additions):

- `HomePage.vue` (图库功能卡片)
- `SpaceDetailPage.vue`, `PictureDetailPage.vue`, `MySpacePage.vue` (gallery surfaces)
- `admin/PictureManagePage.vue`, `admin/SpaceManagePage.vue`, `admin/SpaceUserManagePage.vue`, `admin/UserManagePage.vue`
- `documentWiki/components/WikiSpaceManagePanel.vue` (文档空间管理)

Each page gets a small `<style scoped>` block that sets the page wrapper to `background: var(--wiki-bg)` and `padding: 16px`, mirroring the Wiki document page. Everything else inherits from the shared stylesheet.

## Risks / Trade-offs

- Global CSS overrides could leak into the black top navigation header. Mitigation: scope overrides with `:where()` selectors that never match elements inside `.ant-layout-header` / `.ant-menu`.
- Selected/hover colors for `a-tree` and `a-table` rows become warm tints; this is a visual change but reversible via variable edits.
- Fixed shell height on the Wiki page assumes the header + footer heights from `BasicLayout.vue` stay stable. If they change, the `calc(100vh - 138px)` value should be revisited.
- The navigation tree change slightly shifts the default first-load selection; tests rely on the auto-select behavior, so manual verification is included in tasks.

## Migration Plan

1. Switch to `feature/wiki-theme-polish开发` (done).
2. Add `cloud_front/src/styles/warm-theme.css` and import it from `main.ts`.
3. Replace hard-coded palette values in `BasicLayout.vue` and `DocumentWikiListPage.vue` with CSS variables.
4. Rework `.wiki-shell` to equal-height three columns.
5. Flatten the navigation tree (remove `公开文档` group wrapper).
6. Reorder `匹配模式` radio group in `WikiSearchBar.vue`.
7. Apply the shared theme page wrappers to `HomePage`, `SpaceDetailPage`, `PictureDetailPage`, `MySpacePage`, `admin/PictureManagePage`, `admin/SpaceManagePage`, `admin/SpaceUserManagePage`, `admin/UserManagePage`, `WikiSpaceManagePanel`.
8. Run frontend type checks (`vue-tsc`) and record findings.
9. Record any encountered errors or blockers in `IssueLog.xlsx`.

## Rollback Plan

Revert the change branch before merge. Since no backend, database, or API surface is touched, rollback is limited to frontend files (one new stylesheet + edits to ~10 files). Removing the `main.ts` import disables the shared theme; reverting each page's wrapper styles restores local styling.
## Why

The current Wiki experience mixes global navigation and document navigation, making the document area feel crowded after the document feature is in place. The UI needs a clearer reading-oriented layout: global pages in the top navigation, document structure inside the Wiki document page.

## What Changes

- Remove the current global left sidebar from the main application layout.
- Replace the current top navigation with a black horizontal navigation bar.
- Keep the top navigation as the only global navigation surface.
- Show these top-level entries: `WIKI文档`, `文档创建`, `文档空间管理`, `回收站`, `图库功能`, `图片管理`, `文档管理`, `图片空间管理`, `用户管理`.
- Preserve existing role-based visibility rules so administrator-only entries remain hidden from ordinary users.
- Redesign the Wiki document reading page as a three-column layout:
  - Left column: tree navigation for Wiki spaces, folders, and document directory.
  - Center column: document title, metadata, and body content.
  - Right column: outline generated from the current document headings.
- Use a warm off-white page background and orange scrollbars for the Wiki document experience.
- Keep existing backend APIs and document data behavior unchanged unless a UI integration issue requires a small compatibility adjustment.

## Capabilities

### New Capabilities

- `wiki-layout`: Covers the user-visible global navigation layout and the Wiki document three-column reading experience.

### Modified Capabilities

- None.

## Impact

- Frontend layout shell: `cloud_front/src/layouts/BasicLayout.vue`.
- Global navigation components: `cloud_front/src/components/GlobalHeader.vue` and `cloud_front/src/components/GlobalSider.vue`.
- Routing and active navigation mapping: `cloud_front/src/router/index.ts`.
- Wiki document pages and components under `cloud_front/src/pages/documentWiki/`.
- Admin and gallery page entry points may need navigation label/path alignment, but their business behavior should remain unchanged.
- No planned database schema, backend API, cache, or permission model changes.

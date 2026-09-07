# Tasks

## 1. Shared warm theme stylesheet

- [x] **T1.1** Create `cloud_front/src/styles/warm-theme.css` with CSS variables (`--wiki-bg`, `--wiki-panel`, `--wiki-panel-head`, `--wiki-border`, `--wiki-muted`, `--wiki-text`, `--wiki-text-muted`, `--wiki-accent`, `--wiki-accent-soft`, `--wiki-accent-hover`, `--wiki-shadow`) and antd component overrides scoped to `[data-warm-page]` (cards, card headers, tables, pagination, list, empty, inputs, selects, tree node wrappers and selected nodes, radio buttons, modal, drawer, tabs, dropdowns, buttons, image mask, spin/skeleton).

## 2. Wire theme into the entrypoint

- [x] **T2.1** Import `./styles/warm-theme.css` in `cloud_front/src/main.ts` after `ant-design-vue/dist/reset.css`.

## 3. Layout & workspace

- [x] **T3.1** Replace hard-coded palette hex values in `cloud_front/src/layouts/BasicLayout.vue` with `var(--wiki-bg, …)` / `var(--wiki-panel-head, …)`.
- [x] **T3.2** Add `data-warm-page` to `#documentWikiListPage` in `DocumentWikiListPage.vue`.
- [x] **T3.3** Convert `.wiki-shell` to equal-height three columns (`align-items: stretch`, fixed shell height `calc(100vh - 138px)`, `min-height: 360px`, columns `height: 100%`) and reset column heights on narrow viewports.
- [x] **T3.4** Make `.wiki-panel` use `display: flex; flex-direction: column; min-height: 0` so the equal-height grid row actually fills. Make `.panel-head` `flex-shrink: 0`.
- [x] **T3.5** Replace remaining hex literals in the wiki page stylesheet with CSS variables; clear the inline transparent background on `.wiki-space-tree`.

## 4. Navigation tree root structure

- [x] **T4.1** Drop the `公开文档` group wrapper in `WikiSpaceTree.vue`; render public-type spaces as root nodes alongside `团队文档` / `个人文档` group wrappers. Update first-load auto-selection to prefer public spaces, falling back to the first non-empty group.

## 5. Search controls

- [x] **T5.1** Reorder the `匹配模式` radio group in `WikiSearchBar.vue` to `标题 | 正文 | 标题或正文`. Default selection (`titleOrContent`) remains `标题或正文`.

## 6. Targeted pages opt-in to the warm theme

- [x] **T6.1** Add `data-warm-page` + 16px padding to:
  - `HomePage.vue`
  - `SpaceDetailPage.vue`
  - `PictureDetailPage.vue`
  - `admin/PictureManagePage.vue`
  - `admin/SpaceManagePage.vue`
  - `admin/SpaceUserManagePage.vue`
  - `admin/UserManagePage.vue`
- [x] **T6.2** `WikiSpaceManagePanel.vue` inherits `data-warm-page` from its parent region in `DocumentWikiListPage.vue`; no additional wrapper required.

## 7. Cleanup

- [x] **T7.1** Remove the leftover `console.log` in `admin/PictureManagePage.vue` that printed the review status options on every import.

## 8. Verification

- [x] **V1** `vue-tsc --build` exits 0 (no type errors).
- [x] **V2** `vite build` exits 0; bundle emits `dist/assets/index-DYkNcm2e.js` (1.7 MB, expected for antd + tdesign + bytemd). Chunk-size warning is pre-existing and unrelated.
- [ ] **V3** Manual browser verification requires the user to start the dev server. After the user runs `start-dev.ps1`, please confirm visually:
  - Wiki 三栏高度一致（桌面）
  - 公开空间直接挂在树根
  - 搜索匹配模式顺序：标题 → 正文 → 标题或正文
  - 图库卡片 / 图片管理 / 图片空间管理 / 用户管理 / 文档空间管理不再出现大块白色

## Rollback Plan

If any V3 check fails, revert the change branch before merge. Since no backend, API, database, or permission surface is touched, rollback is limited to:
- The single new file `cloud_front/src/styles/warm-theme.css`.
- The one-line import in `cloud_front/src/main.ts` (remove it).
- The page-level edits (root `data-warm-page` attribute, the small `<style scoped>` padding blocks, the radio-button order in `WikiSearchBar.vue`, and the navigation-tree restructuring in `WikiSpaceTree.vue`).
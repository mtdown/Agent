## Why

Wiki 工作区已经具备暖色（米黄 + 橙色）主题，但主题只覆盖到 Wiki 文档页本身：空间导航树背景仍是 Ant Design 默认灰白，三栏高度各自随内容伸缩导致页面右侧留白参差，而图库、图片管理、图片空间管理、用户管理以及文档空间管理等页面仍是整片纯白卡片/表格，与暖色主题割裂。同时空间导航树把「公开文档」包在同名分组层下，出现两层同名节点，用户理解成本高。

## What Changes

- 空间导航树背景与其余面板统一为暖色主题（面板底色、悬停、选中态、分隔线都跟随主题）。
- 移除「公开文档」分组层：公开空间直接作为导航树根节点渲染，文件夹仍挂在空间节点下；团队文档 / 个人文档分组保持不变。
- Wiki 三栏（空间导航 / 正文 / 本文大纲）改为等高：三列高度一致、内部各自滚动，不再出现长短不一的留白。
- 暖色主题扩展到全站管理页与图库卡片：文档空间管理、图库功能卡片、图片管理、图片空间管理、用户管理页面中的卡片、表格、表头、分页、列表等白色区域改为主题色。
- 搜索「匹配模式」选项顺序调整为从左到右：标题 → 正文 → 标题或正文。

## Capabilities

### New Capabilities

- `wiki-workspace-theme`: 覆盖全站共享的暖色主题外观（面板、卡片、表格、分页、表单控件）以及 Wiki 三栏等高工作区布局。

### Modified Capabilities

- `wiki-navigation-flow`: 空间导航树根节点结构变化（公开空间不再挂同名分组层），以及文档搜索「匹配模式」选项顺序变化。

## Impact

- 全局主题与布局：`cloud_front/src/layouts/BasicLayout.vue`，新增共享主题样式（CSS 变量 + 全局覆盖）。
- Wiki 页面与组件：`cloud_front/src/pages/documentWiki/DocumentWikiListPage.vue`、`components/WikiSpaceTree.vue`、`components/WikiSearchBar.vue`、`components/WikiSpaceManagePanel.vue`。
- 图库与图片页面：`cloud_front/src/pages/HomePage.vue`、`SpaceDetailPage.vue`、`PictureDetailPage.vue`、`MySpacePage.vue`。
- 管理页面：`cloud_front/src/pages/admin/PictureManagePage.vue`、`SpaceManagePage.vue`、`SpaceUserManagePage.vue`、`UserManagePage.vue`。
- 不涉及后端接口、数据库表结构与权限模型变更；仅前端展示层调整。

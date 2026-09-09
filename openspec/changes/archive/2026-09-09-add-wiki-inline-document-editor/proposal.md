## Why

Wiki 文档工作区已经把阅读流程收敛到 `/documentWiki` 三栏布局，但创建和编辑文档仍通过顶部“文档创建”入口或列表“编辑”按钮跳转到独立页面。这个跳转打断空间导航、目录上下文和三栏阅读体验，导致用户在同一 Wiki 工作流中反复离开当前工作区。

## What Changes

- 从顶部导航栏移除“文档创建”入口，避免把新建文档作为独立页面级导航。
- 将空间导航栏顶部的“新建文件夹”按钮替换为“新建文档”按钮。
- 点击“新建文档”时，在 Wiki 三栏中间列打开文档编辑器，替换当前文档列表或文档预览内容。
- 点击文档列表或文档预览中的“编辑”时，在同一个中间列打开编辑器，而不是跳转 `/edit_documentWiki/:id`。
- 保存创建或编辑后回到中间列文档预览，并刷新左侧空间树与当前目录文档列表。
- 保留文件夹创建、重命名、移动、删除等树节点菜单能力，不把本次入口调整扩大为文件夹管理改造。
- 保留 `/add_documentWiki` 与 `/edit_documentWiki/:id` 路由作为兼容入口，正常 Wiki 工作区操作不再依赖这些路由。

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `wiki-navigation-flow`: Wiki 文档创建和编辑流程改为在 `/documentWiki` 三栏工作区中间列内联完成，顶部导航不再暴露独立“文档创建”入口。

## Impact

- 前端导航：`cloud_front/src/components/GlobalHeader.vue`。
- Wiki 工作区组合层：`cloud_front/src/pages/documentWiki/DocumentWikiListPage.vue`。
- 空间导航树：`cloud_front/src/pages/documentWiki/components/WikiSpaceTree.vue`。
- 文档列表/预览操作：`cloud_front/src/pages/documentWiki/components/WikiDocumentList.vue`。
- 共用编辑器：`cloud_front/src/components/DocumentWikiEditor.vue`。
- 可选兼容页面：`cloud_front/src/pages/documentWiki/AddDocumentWikiPage.vue`、`cloud_front/src/pages/documentWiki/EditDocumentWikiPage.vue` 保留但不作为主路径。
- 不涉及后端接口、数据库结构、权限模型或新增前端依赖。

# wiki-navigation-flow Specification

## Purpose

Defines the refined Wiki navigation flow where top navigation owns page-level switching and document reading happens inside the main `/documentWiki` three-column workspace instead of a separate detail route.

## Requirements

### Requirement: Top navigation removes document management entry
The system SHALL remove `文档管理` from the top navigation bar while keeping the remaining Wiki, recycle, space-management, gallery, picture-management, picture-space-management, and user-management entries available according to permissions.

#### Scenario: User views top navigation
- **WHEN** a user opens the application
- **THEN** the top navigation does not display `文档管理`
- **AND** the top navigation still displays the other entries the user is allowed to access

### Requirement: Wiki page has no secondary page-switching tabs
The system SHALL NOT display the in-page secondary navigation tabs `文档`, `回收站`, and `文档空间管理` inside the Wiki document main page.

#### Scenario: User opens Wiki document page
- **WHEN** a user opens `/documentWiki`
- **THEN** the page does not display the secondary tab row for `文档`, `回收站`, and `文档空间管理`
- **AND** the page displays the Wiki document workspace directly

### Requirement: Top navigation opens Wiki sub-interfaces directly
The system SHALL let users open the Wiki document workspace, recycle bin, and document space management through top navigation entries instead of through in-page tabs.

#### Scenario: User opens Wiki documents from top navigation
- **WHEN** a user selects `WIKI文档` from the top navigation
- **THEN** the system opens the document workspace directly

#### Scenario: User opens recycle bin from top navigation
- **WHEN** a user selects `回收站` from the top navigation
- **THEN** the system opens the recycle bin interface directly without requiring a second in-page tab click

#### Scenario: Administrator opens document space management from top navigation
- **GIVEN** the logged-in user is an administrator
- **WHEN** the user selects `文档空间管理` from the top navigation
- **THEN** the system opens the document space management interface directly without requiring a second in-page tab click

### Requirement: Document reading stays in main Wiki workspace
The system SHALL open documents from the Wiki document list inside `/documentWiki` by updating the three-column workspace center area, rather than navigating to `/documentWiki/:id`. The system SHALL also open document editing inside the same center column when users edit a document from the Wiki workspace.

#### Scenario: User opens a document from the list
- **WHEN** a user selects a document open/read action from the document list on `/documentWiki`
- **THEN** the URL remains on `/documentWiki`
- **AND** the center column displays the selected document content
- **AND** the right outline updates for the selected document

#### Scenario: User edits a selected document
- **WHEN** a user selects edit from a selected document preview or document list item on `/documentWiki`
- **THEN** the URL remains on `/documentWiki`
- **AND** the center column replaces the document list or preview with the document editor
- **AND** the editor is initialized with the selected document content and location

#### Scenario: User edits or moves a document
- **WHEN** a user selects edit, move, or delete from a document list item
- **THEN** edit opens the document editor inside the `/documentWiki` center column
- **AND** the existing move or delete behavior remains available

#### Scenario: User saves an inline edit
- **WHEN** a user saves changes from the inline document editor
- **THEN** the editor closes
- **AND** the center column displays the saved document content
- **AND** the left navigation tree and current directory document list are refreshed

#### Scenario: User cancels inline creation or editing
- **WHEN** a user cancels from the inline document editor
- **THEN** the editor closes
- **AND** the center column returns to the previously selected document or current directory document list

#### Scenario: User moves or deletes a document
- **WHEN** a user selects move or delete from a document list item or selected document preview
- **THEN** the existing move or delete behavior remains available

#### Scenario: Existing document detail route is visited directly
- **WHEN** a user directly opens an existing `/documentWiki/:id` link
- **THEN** the system may keep the route available for backward compatibility
- **AND** normal document list reading and editing do not depend on navigating to that route

### Requirement: Top navigation removes document creation entry
The system SHALL NOT display a top navigation entry dedicated to creating Wiki documents. Creating Wiki documents SHALL be initiated from the Wiki document workspace instead of from a separate page-level navigation item.

#### Scenario: User views top navigation
- **WHEN** a user opens the application top navigation
- **THEN** the top navigation displays `WIKI文档`
- **AND** the top navigation does not display `文档创建`

### Requirement: Space navigation primary action creates documents
The system SHALL render the space navigation toolbar primary action as `新建文档` when a Wiki space is selected. Activating this action SHALL open a document creation editor inside the Wiki workspace center column.

#### Scenario: User creates a document from a selected space
- **WHEN** a user selects a Wiki space in the space navigation tree
- **THEN** the space navigation toolbar displays `新建文档`
- **AND** the toolbar does not display `新建文件夹` as its primary action

#### Scenario: User opens inline document creation
- **WHEN** a user clicks `新建文档` from the space navigation toolbar
- **THEN** the browser remains on `/documentWiki`
- **AND** the center column displays the document editor
- **AND** the editor is initialized to the currently selected space and folder context when available

### Requirement: Folder management remains available from node actions
The system SHALL keep folder creation and folder management actions available from space or folder node action menus after the space navigation toolbar primary action changes to document creation.

#### Scenario: User opens node action menu
- **WHEN** a user opens the action menu for a selectable space or folder node
- **THEN** folder creation remains available from that node menu
- **AND** existing folder rename, move, and delete actions for folder nodes remain available

### Requirement: Space navigation tree root structure

The system SHALL mount public-type Wiki spaces (type = 2) directly at the root level of the navigation tree, alongside the `团队文档` (type = 1) and `个人文档` (type = 0) group wrappers. The system SHALL NOT introduce a separate `公开文档` group wrapper around public spaces.

#### Scenario: Public spaces appear at root

- **WHEN** a user with at least one public space opens `/documentWiki`
- **THEN** the navigation tree renders public spaces as root-level nodes with their folders nested underneath
- **AND** the tree does not wrap public spaces inside a `公开文档` group

#### Scenario: Team and personal groups remain

- **WHEN** a user with team or personal spaces opens `/documentWiki`
- **THEN** the navigation tree still renders `团队文档` and `个人文档` group wrappers for those space types

#### Scenario: Selecting a root public space

- **WHEN** a user clicks a public space node rendered at the root level
- **THEN** the Wiki workspace selects the space and lists its documents

### Requirement: Wiki workspace uses equal-height three columns

The system SHALL render the Wiki workspace as three columns (space navigation / document content / outline) that share the same height. Each column SHALL scroll independently. The system SHALL keep the columns equal-height on desktop viewports.

#### Scenario: Three columns share one height

- **WHEN** a user opens `/documentWiki` on a desktop viewport
- **THEN** the three columns have the same outer height
- **AND** each column scrolls independently when its content exceeds the column height

#### Scenario: Single column on narrow viewport

- **WHEN** a user opens `/documentWiki` on a viewport narrower than 900px
- **THEN** the layout collapses to a single column with no fixed height

### Requirement: Wiki search match-mode radio order

The system SHALL render the `匹配模式` radio group on the Wiki document page with the options in the order `标题`, `正文`, `标题或正文` from left to right.

#### Scenario: Radio group order

- **WHEN** a user opens `/documentWiki` and views the search bar
- **THEN** the radio buttons appear in the order `标题`, `正文`, `标题或正文`
- **AND** the default selection remains `标题或正文`

### Requirement: Authenticated top navigation exposes batch documents
The system SHALL display a `批量文档` entry in top navigation for authenticated users and route it to the batch-document page.

#### Scenario: Authenticated user opens batch documents
- **WHEN** an authenticated user selects `批量文档` from top navigation
- **THEN** the system opens the batch-document page
- **AND** the page provides controls for batch URL import and batch local-file upload

#### Scenario: Unauthenticated visitor cannot start import
- **WHEN** a visitor is not authenticated
- **THEN** the system does not expose an executable batch-document import entry
- **AND** the visitor cannot submit batch URL or file import requests

### Requirement: Space navigation exposes document upload action
The system SHALL provide a local document upload action in the Wiki workspace space navigation area whenever a user has selected a target Wiki space.

#### Scenario: User uploads from selected folder
- **WHEN** a user selects a Wiki space folder in the left navigation and starts a supported document upload
- **THEN** the upload targets the selected space and folder
- **AND** the URL remains on `/documentWiki`
- **AND** the uploaded document opens in the center column after the upload succeeds

#### Scenario: User uploads from selected space root
- **WHEN** a user selects a Wiki space root in the left navigation and starts a supported document upload
- **THEN** the upload targets the selected space root
- **AND** the URL remains on `/documentWiki`
- **AND** the uploaded document opens in the center column after the upload succeeds

### Requirement: 选择文件夹必须按 folderId 重新查询中间栏

中间栏的文件夹文档列表 MUST 通过 `listDocumentWikiVisByPageWithCacheUsingPost` 传入 `folderId` 查询后端获得，MUST NOT 读取空间树节点上缓存的 `folder.documents`。该列表 MUST 与其它两种选择（空间、聚合）保持一致：**每页 20 条并渲染分页器**。

> 说明：此处的"文件夹列表"指中间栏选中文件夹后展示的文档列表；左侧空间导航树由 `listFolderTreeUsingGet` 一次性返回整棵树，不分页不截断，本需求不涉及、保持原样。

#### Scenario: 中间栏是文档预览时点击子文件夹

- **GIVEN** 中间栏正在预览某篇文档
- **WHEN** 用户点击左侧空间导航中的任意子文件夹
- **THEN** 中间栏切换为该文件夹的文档列表
- **AND** 之前打开的文档被清空，不再停留在预览态

#### Scenario: 搜索态下点击空间树节点

- **GIVEN** 搜索关键词框中有内容，中间栏显示的是搜索结果
- **WHEN** 用户点击左侧空间导航中的任意节点
- **THEN** 关键词被清空、搜索态退出
- **AND** 中间栏刷新为该节点对应位置的文档列表
- **AND** 不再出现"点击节点但中间栏毫无变化"的情况

#### Scenario: 文件夹文档与树缓存解耦

- **WHEN** 用户选中一个文件夹
- **THEN** 中间栏数据来自后端按 `folderId` 的查询结果
- **AND** 位置栏"共 N 篇文档"取后端返回的总数

#### Scenario: 文件夹文档按每页 20 条分页

- **GIVEN** 某个文件夹下的文档数量超过 20 条
- **WHEN** 用户选中该文件夹
- **THEN** 中间栏只列出当前页的 20 条文档
- **AND** 列表底部渲染分页器，总数取后端返回的 `total`
- **AND** 切换页码时按同一 `folderId` 重新查询

#### Scenario: 空间与聚合模式分页行为不变

- **WHEN** 用户选中的是单个空间或"公开文档"聚合节点
- **THEN** 中间栏仍按每页 20 条分页，行为与改动前一致

### Requirement: 中间栏提供返回入口与 Escape 回退

中间栏在文档预览态 MUST 提供可见的"返回列表"按钮；系统 MUST 同时支持 `Escape` 键回退，且 MUST NOT 在用户正在输入或存在弹层时抢走按键。

#### Scenario: 通过按钮从预览返回列表

- **GIVEN** 中间栏正在预览某篇文档
- **WHEN** 用户点击"返回列表"按钮
- **THEN** 中间栏回到当前所在空间/文件夹的文档列表

#### Scenario: Escape 从预览返回列表

- **GIVEN** 中间栏正在预览某篇文档，且焦点不在任何输入框内
- **WHEN** 用户按下 `Escape`
- **THEN** 中间栏回到当前所在位置的文档列表

#### Scenario: Escape 先退出编辑器

- **GIVEN** 中间栏处于新建或编辑态
- **WHEN** 用户按下 `Escape`
- **THEN** 编辑器被取消，回到预览态或列表态
- **AND** 需要再按一次才会返回文档列表

#### Scenario: 输入中的 Escape 不被拦截

- **GIVEN** 焦点位于输入框、文本域或可编辑区域内
- **WHEN** 用户按下 `Escape`
- **THEN** 中间栏不执行返回操作，按键交由当前组件处理

#### Scenario: 弹层打开时的 Escape 不被拦截

- **GIVEN** 页面上存在可见的确认框或下拉弹层
- **WHEN** 用户按下 `Escape`
- **THEN** 中间栏不执行返回操作，按键交由弹层处理

### Requirement: 搜索栏空间默认选中当前空间

搜索栏的空间下拉框 MUST 默认选中用户当前所在的空间，标签沿用 `区域 / 空间名` 形式；用户 MUST 能手动清除以恢复"全部可见空间"。

#### Scenario: 进入空间后打开搜索栏

- **GIVEN** 用户当前位于"公开文档"空间中名为"公开文档"的空间
- **WHEN** 用户查看搜索栏
- **THEN** 空间下拉框显示 `公开文档 / 公开文档`

#### Scenario: 切换空间后默认值跟随

- **WHEN** 用户在左侧导航切换到另一个空间
- **THEN** 搜索栏空间下拉同步为该空间

#### Scenario: 选中聚合节点时回落全部空间

- **WHEN** 用户选中的是"公开文档"聚合节点（跨多个空间，无单一 spaceId）
- **THEN** 空间下拉框回落为 placeholder"全部可见空间"

### Requirement: 空间导航节点的操作入口常驻可见

空间导航树中节点的 `⋯` 操作入口 MUST 默认可见，MUST NOT 依赖鼠标悬停才显形；文件夹节点的菜单 MUST 提供 `新建子文件夹`、`重命名`、`移动`、`删除` 四项。

#### Scenario: 未悬停时也能看到操作入口

- **WHEN** 用户查看左侧空间导航中的任意非分组节点
- **THEN** `⋯` 操作入口直接可见，无需先把鼠标移到该节点上

#### Scenario: 文件夹节点提供重命名入口

- **GIVEN** 用户在空间导航中展开到某个文件夹
- **WHEN** 用户点击该文件夹的 `⋯`
- **THEN** 菜单中出现 `重命名` 并可直接修改文件夹名称
- **AND** 菜单同时提供 `新建子文件夹`、`移动`、`删除`

#### Scenario: 空间节点仍只提供新建

- **GIVEN** 用户点击的是空间节点而非文件夹节点
- **WHEN** 用户展开其 `⋯` 菜单
- **THEN** 菜单中提供 `新建文件夹`
- **AND** 不出现重命名、移动、删除等仅适用于文件夹的操作

### Requirement: Public navigation tolerates login-status failure
The system SHALL render allowed non-admin page content when the initial login-status check fails because the backend is unavailable or the request errors.

#### Scenario: Backend is unavailable during first page load
- **WHEN** a user opens the application and the login-status request fails before the first route is rendered
- **THEN** the top navigation remains visible
- **AND** the main content area renders the requested non-admin page instead of an empty router-view

#### Scenario: Admin route is opened without confirmed admin identity
- **WHEN** a user opens an admin route and the system cannot confirm an administrator login
- **THEN** the system redirects the user to the login page
- **AND** the protected admin page is not rendered

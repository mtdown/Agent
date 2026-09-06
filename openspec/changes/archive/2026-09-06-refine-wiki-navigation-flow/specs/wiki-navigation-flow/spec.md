## Purpose

Defines the refined Wiki navigation flow where top navigation owns page-level switching and document reading happens inside the main `/documentWiki` three-column workspace instead of a separate detail route.

## ADDED Requirements

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
The system SHALL open documents from the Wiki document list inside `/documentWiki` by updating the three-column workspace center area, rather than navigating to `/documentWiki/:id`.

#### Scenario: User opens a document from the list
- **WHEN** a user selects a document open/read action from the document list on `/documentWiki`
- **THEN** the URL remains on `/documentWiki`
- **AND** the center column displays the selected document content
- **AND** the right outline updates for the selected document

#### Scenario: User edits or moves a document
- **WHEN** a user selects edit, move, or delete from a document list item
- **THEN** the existing edit, move, or delete behavior remains available

#### Scenario: Existing document detail route is visited directly
- **WHEN** a user directly opens an existing `/documentWiki/:id` link
- **THEN** the system may keep the route available for backward compatibility
- **AND** normal document list reading does not depend on navigating to that route

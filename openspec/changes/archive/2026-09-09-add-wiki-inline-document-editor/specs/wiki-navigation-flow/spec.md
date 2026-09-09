## ADDED Requirements

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

## MODIFIED Requirements

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

## Purpose

Defines the Wiki application's reading-oriented layout: global navigation appears only in a black top navigation bar, while the Wiki document page uses a three-column structure for folder navigation, document content, and document outline.

## ADDED Requirements

### Requirement: Global navigation uses top bar only
The system SHALL remove the global left sidebar and SHALL expose global page navigation through a black top navigation bar.

#### Scenario: Logged-in user opens the application
- **WHEN** a logged-in user opens any main application page
- **THEN** the page displays a black top navigation bar
- **AND** the page does not display the previous global left sidebar

#### Scenario: Top navigation shows configured entries
- **WHEN** a user can access the main application shell
- **THEN** the top navigation includes `WIKI文档`, `文档创建`, `文档空间管理`, `回收站`, `图库功能`, `图片管理`, `文档管理`, `图片空间管理`, and `用户管理`

#### Scenario: Current top navigation item is highlighted
- **WHEN** a user navigates to a page represented in the top navigation
- **THEN** the corresponding top navigation item is visibly highlighted

### Requirement: Navigation keeps role-based visibility
The system SHALL preserve existing permission-based menu visibility when moving global navigation to the top bar.

#### Scenario: Ordinary user views navigation
- **GIVEN** the logged-in user is not an administrator
- **WHEN** the user opens the application
- **THEN** administrator-only entries are not visible in the top navigation

#### Scenario: Administrator views navigation
- **GIVEN** the logged-in user is an administrator
- **WHEN** the user opens the application
- **THEN** administrator management entries are visible in the top navigation

### Requirement: Wiki document page uses three columns
The system SHALL display the Wiki document page with three document-specific columns: left tree navigation, center document content, and right document outline.

#### Scenario: User opens Wiki document page
- **WHEN** a user opens the Wiki document page
- **THEN** the left column displays Wiki spaces, folders, and document navigation
- **AND** the center column displays the selected document title, metadata, and body content
- **AND** the right column displays the selected document's outline

#### Scenario: User selects a tree item
- **WHEN** a user selects a space, folder, or document from the left tree
- **THEN** the selected item is visibly highlighted
- **AND** the center content updates according to the selected item

#### Scenario: User uses document outline
- **GIVEN** the selected document contains headings
- **WHEN** the user views the right outline
- **THEN** the outline displays heading entries for the current document
- **AND** selecting an outline entry moves the center content to the corresponding section

### Requirement: Wiki layout follows approved visual direction
The system SHALL apply the approved visual direction to the Wiki layout: warm off-white page background, paper-like document panels, and orange scrollbars for scrollable Wiki areas.

#### Scenario: User views Wiki document layout
- **WHEN** a user opens the Wiki document page
- **THEN** the page background uses a warm off-white tone
- **AND** the document panels remain visually distinct from the page background
- **AND** scrollable Wiki navigation, content, and outline areas use orange scrollbars where the browser supports custom scrollbar styling

#### Scenario: User opens Wiki layout on a narrow screen
- **WHEN** the viewport is too narrow to comfortably show three columns
- **THEN** the Wiki document layout remains usable without content overlap
- **AND** the document navigation, content, and outline remain available

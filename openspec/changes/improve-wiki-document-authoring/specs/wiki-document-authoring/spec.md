## Purpose

Defines the Wiki document writing experience: users author documents in a normal rich-text surface, save rich-text content as HTML, and create new documents from the selected workspace location.

## ADDED Requirements

### Requirement: WYSIWYG document editor
The system SHALL provide a Wiki document editor where formatted content is shown directly while the user types, without requiring the user to edit Markdown source syntax or use a split source/preview layout.

#### Scenario: User formats text while writing
- **GIVEN** a logged-in user is creating or editing a Wiki document
- **WHEN** the user applies formatting such as bold text, headings, quotes, or lists
- **THEN** the editor displays the formatted content directly in the editing area
- **AND** the user does not need to see Markdown markers such as `**text**` to understand the final formatting

#### Scenario: Editor does not use split Markdown preview
- **GIVEN** a logged-in user is creating or editing a Wiki document
- **WHEN** the editor is displayed
- **THEN** the main body editor appears as one authoring surface
- **AND** the page does not require a side-by-side Markdown source and preview layout

### Requirement: Rich-text documents save as HTML
The system SHALL save newly authored rich-text Wiki document content with an HTML content format so the saved document can be rendered as the same formatted content the user authored.

#### Scenario: Create rich-text document
- **GIVEN** a logged-in user creates a Wiki document from the WYSIWYG editor
- **WHEN** the user saves valid title and body content
- **THEN** the system stores the body content as HTML
- **AND** the saved document records its content format as `html`

#### Scenario: Edit rich-text document
- **GIVEN** a logged-in user can edit an existing HTML Wiki document
- **WHEN** the user changes formatted content and saves
- **THEN** the system persists the updated HTML body content
- **AND** the document remains marked with content format `html`

### Requirement: Existing document formats remain readable
The system SHALL continue to render existing Wiki documents according to their saved content format.

#### Scenario: View markdown document
- **GIVEN** an existing Wiki document is saved with content format `markdown`
- **WHEN** the user opens the document for reading
- **THEN** the system renders the Markdown content as formatted document content

#### Scenario: View plain text document
- **GIVEN** an existing Wiki document is saved with content format `plain` or no rich-text format
- **WHEN** the user opens the document for reading
- **THEN** the system displays the original text content without corrupting or interpreting it as HTML

### Requirement: Create document from selected workspace location
The system SHALL allow a user to create a Wiki document from the currently selected left-navigation space or folder inside the Wiki document workspace.

#### Scenario: Create from selected folder
- **GIVEN** a logged-in user has selected a folder in the left Wiki navigation
- **WHEN** the user clicks `创建文档` in the document workspace
- **THEN** the center workspace area displays the document creation form
- **AND** the form location is prefilled with the selected folder and its owning space

#### Scenario: Create from selected space root
- **GIVEN** a logged-in user has selected a Wiki space root in the left navigation
- **WHEN** the user clicks `创建文档` in the document workspace
- **THEN** the center workspace area displays the document creation form
- **AND** the form location is prefilled with the selected space root and no folder

#### Scenario: Save created document in selected location
- **GIVEN** a logged-in user is creating a document from a selected space or folder
- **WHEN** the user saves a valid document
- **THEN** the new document belongs to the prefilled space and folder location
- **AND** the workspace refreshes so the new document can be opened from that location

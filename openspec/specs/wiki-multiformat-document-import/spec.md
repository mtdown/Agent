# wiki-multiformat-document-import Specification

## Purpose
Allows users to import local Markdown and HTML files into Wiki spaces as normal Wiki documents that can be viewed, searched, moved, deleted, and selectively edited after upload.

## Requirements

### Requirement: User can upload supported document files into a Wiki space
The system SHALL allow a logged-in user with access to a Wiki space to upload a local `.md`, `.html`, or `.htm` file into that space and optional selected folder.

#### Scenario: Upload Markdown into selected folder
- **WHEN** a logged-in user uploads a non-empty `.md` file while a Wiki space and folder are selected
- **THEN** the system creates a Wiki document in the selected space and folder
- **AND** the document title defaults from the uploaded file name when no explicit title is supplied
- **AND** the document content is stored as Markdown

#### Scenario: Upload HTML into space root
- **WHEN** a logged-in user uploads a non-empty `.html` or `.htm` file while only a Wiki space is selected
- **THEN** the system creates a Wiki document in the selected space root
- **AND** the document content is cleaned into structured Markdown (h1-h3 headings and plain text blocks kept; scripts, styles, navigation and images dropped)
- **AND** the document content format is Markdown
- **AND** the source extension and import metadata are recorded

#### Scenario: Upload HTML without extractable text
- **WHEN** a logged-in user uploads an `.html` file that contains no extractable text (for example only scripts and styles)
- **THEN** the system rejects the upload with a clear no-content message
- **AND** no Wiki document is created

#### Scenario: Upload unsupported file type
- **WHEN** a logged-in user uploads a file whose extension and detected type are not supported
- **THEN** the system rejects the upload
- **AND** no Wiki document is created
- **AND** the user receives a clear unsupported-file message

### Requirement: HTML script content never executes in the Wiki workspace
The system SHALL ensure uploaded HTML cannot run scripts in or modify the Wiki application page.

#### Scenario: HTML contains script content
- **WHEN** a logged-in user uploads an HTML file containing script tags, event-handler attributes, or unsafe links
- **THEN** the import pipeline drops all markup and keeps only text and heading structure
- **AND** the stored Markdown contains no script, style, or event-handler content

#### Scenario: Legacy raw HTML document is viewed
- **WHEN** a user opens a document that was stored as raw HTML before the cleaning change
- **THEN** the center preview renders it in a sandboxed iframe without script permission
- **AND** its scripts do not execute in the Wiki application

### Requirement: Documents render according to saved content format
The system SHALL render Wiki document previews using the saved content format instead of displaying all document bodies as plain text.

#### Scenario: View Markdown document
- **WHEN** a user opens a Wiki document whose content format is Markdown
- **THEN** the center preview renders Markdown headings, paragraphs, lists, links, and images as formatted content

#### Scenario: View HTML document
- **WHEN** a user opens a Wiki document whose content format is HTML (legacy import only)
- **THEN** the center preview renders the original HTML in a sandboxed iframe

#### Scenario: View legacy plain document
- **WHEN** a user opens a Wiki document whose content format is plain or missing
- **THEN** the center preview displays the content as readable plain text

### Requirement: Editing is Markdown-only in this stage
The system SHALL allow Markdown document editing and prevent uploaded HTML documents from entering a lossy editor.

#### Scenario: Create new document
- **WHEN** a user creates a new document from the Wiki workspace
- **THEN** the editor saves it as Markdown
- **AND** the user is not asked to create a new HTML or Word document from scratch

#### Scenario: Edit uploaded Markdown
- **WHEN** a user edits an uploaded Markdown document
- **THEN** the system opens a Markdown editor initialized with the saved Markdown content
- **AND** saving preserves the Markdown content format

#### Scenario: Edit uploaded HTML
- **WHEN** a user attempts to edit an uploaded HTML document
- **THEN** the system does not open an HTML editor
- **AND** the user receives a clear message that HTML original-page documents are preview-only in this stage

### Requirement: Imported documents participate in existing Wiki document workflows
The system SHALL treat imported documents as normal Wiki documents for visibility, folder placement, search, move, delete, recycle, and cache refresh behavior.

#### Scenario: Imported document appears in directory
- **WHEN** a supported document upload succeeds
- **THEN** the target directory refreshes to include the new document
- **AND** the system opens the new document in the Wiki workspace

#### Scenario: Imported document is searched
- **WHEN** a user searches for text that exists in an imported document's title or content
- **THEN** the matching imported document can appear in search results according to existing visibility rules

### Requirement: Navigation regions list all covered documents with paging
The system SHALL let users browse every document covered by a navigation selection, not only root-level documents.

#### Scenario: Select public aggregate node
- **WHEN** a user clicks the "公开文档" aggregate node in the left navigation
- **THEN** the middle column lists all documents across every visible public space, paged at 20 per page

#### Scenario: Select a team space
- **WHEN** a user clicks a space such as "团队文档 / 研发部"
- **THEN** the middle column lists all documents in that space including those inside folders, paged at 20 per page

#### Scenario: Select a folder
- **WHEN** a user clicks a folder in the navigation tree
- **THEN** the middle column lists that folder's own documents
- **AND** the right outline column lists the folder's document titles as clickable entries

### Requirement: Outline column follows the active document or folder
The system SHALL keep the right outline column useful in preview, edit, and folder-selection states.

#### Scenario: Document open in preview or edit
- **WHEN** a document is open in preview or inline edit mode
- **THEN** the outline column lists the document's h1-h3 headings
- **AND** clicking an outline entry scrolls the document to the matching heading

#### Scenario: Folder selected without an open document
- **WHEN** a folder is selected and no document is open
- **THEN** the outline column lists the folder's document titles
- **AND** clicking a title opens that document

### Requirement: Document surfaces follow the warm theme
The system SHALL render the Markdown editor and preview with the workspace warm theme instead of the component library's default white surfaces.

#### Scenario: Edit Markdown document
- **WHEN** a user opens the Markdown editor
- **THEN** the editing area uses the warm panel background and text colors consistent with the rest of the workspace

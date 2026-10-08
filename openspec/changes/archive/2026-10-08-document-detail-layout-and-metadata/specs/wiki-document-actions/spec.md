# Spec Delta

## ADDED Requirements

### Requirement: Document preview presents title and provenance as reading metadata

The system SHALL present the document title as a centered heading larger than the article body, followed by available publication time, source site, original author, and Wiki creator information. The source site SHALL link to the document's source URL when that URL is a valid HTTP or HTTPS URL. The original author SHALL be omitted when unavailable and SHALL remain distinct from the Wiki creator.

For a legacy Markdown document whose leading lines contain recognized article metadata fields, the system SHALL hide that metadata block from the rendered article body and use available values as preview metadata fallbacks. This behavior SHALL NOT modify or bulk backfill the stored document.

#### Scenario: Preview an imported article with complete provenance
- **WHEN** a user previews a document with a title, publication time, source site, source URL, original author, and creator
- **THEN** the title is centered and visually larger than the body
- **AND** publication time, linked source site, original author, and creator appear below the title in that order
- **AND** the original author and Wiki creator are displayed as separate values

#### Scenario: Preview an article without an original author
- **WHEN** a user previews a document whose original author is unavailable
- **THEN** no original-author label or empty placeholder is displayed
- **AND** any available publication time, source site, and Wiki creator remain visible

#### Scenario: Preview a document without a valid source URL
- **WHEN** a user previews a document with a source-site label but no valid HTTP or HTTPS source URL
- **THEN** the source-site label is displayed as plain text and is not rendered as a link

#### Scenario: Preview a legacy Markdown article with inline metadata
- **WHEN** a user previews a Markdown document beginning with recognized fields such as `pubDate` and `sourceUrl`
- **THEN** the recognized leading metadata block is omitted from the rendered article body
- **AND** available publication time and source site are displayed below the title
- **AND** the source site links to the parsed HTTP or HTTPS source URL
- **AND** the stored document remains unchanged

### Requirement: Document preview actions use consistent typography

The system SHALL retain only the `返回列表`, `移动`, `编辑`, and `删除` actions in the preview action row, and SHALL render their text with consistent size and typography.

#### Scenario: View preview actions
- **WHEN** a user views an open document
- **THEN** the preview action row contains `返回列表`, `移动`, `编辑`, and `删除`
- **AND** the text for `返回列表` matches the typography and size of the other actions

### Requirement: Article paragraphs use first-line indentation

The system SHALL indent the first line of each rendered article paragraph by two Chinese character widths.

#### Scenario: Read a multi-paragraph article
- **WHEN** a user previews an article containing multiple paragraphs
- **THEN** each paragraph's first line is indented by two Chinese character widths
- **AND** headings, lists, and metadata are not given paragraph indentation

### Requirement: List search controls are hidden during document preview

The system SHALL hide the keyword, matching-mode, and space search controls while an individual document is open in the inline preview. The system SHALL preserve the current search state and restore the controls when the user returns to the list.

#### Scenario: Open a document from the list
- **WHEN** a user opens a document in the inline preview
- **THEN** the keyword, matching-mode, and space search controls are not displayed
- **AND** the document preview actions and article content remain available

#### Scenario: Return from preview to the list
- **WHEN** a user selects `返回列表`
- **THEN** the list search controls are displayed again
- **AND** their current values remain unchanged

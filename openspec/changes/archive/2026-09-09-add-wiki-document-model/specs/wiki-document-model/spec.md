# wiki-document-model Specification

## ADDED Requirements

### Requirement: Create Wiki Document

The system SHALL allow a logged-in user to create a wiki document with a title and body content.

#### Scenario: Create document successfully

- **GIVEN** the user is logged in
- **AND** the request contains a valid title and content
- **WHEN** the user submits the create document action
- **THEN** the system SHALL save a `DocumentWiki` record to MySQL
- **AND** return the created document id
- **AND** clear affected document list cache entries

#### Scenario: Reject invalid document creation

- **GIVEN** the user is logged in
- **AND** the request is missing a title or contains invalid content
- **WHEN** the user submits the create document action
- **THEN** the system SHALL reject the request
- **AND** no document record SHALL be created

### Requirement: Query Wiki Document List

The system SHALL allow users to query paged wiki document lists.

#### Scenario: Query document list

- **GIVEN** wiki documents exist
- **WHEN** the user opens the document list page
- **THEN** the system SHALL return non-deleted documents
- **AND** include list display fields such as title, summary, category, tags, creator, and edit time

#### Scenario: Use Redis list cache

- **GIVEN** an equivalent document list query has been requested recently
- **WHEN** the user requests the same list query again
- **THEN** the system MAY return the list result from Redis cache
- **AND** the result SHALL still exclude deleted documents

### Requirement: View Wiki Document Detail

The system SHALL allow users to view a single wiki document detail.

#### Scenario: View document detail successfully

- **GIVEN** a non-deleted wiki document exists
- **WHEN** the user opens the document detail page
- **THEN** the system SHALL return the document title and saved body content

#### Scenario: Use Redis detail cache

- **GIVEN** a document detail has been requested recently
- **WHEN** the user requests the same document detail again
- **THEN** the system MAY return the detail result from Redis cache

#### Scenario: Deleted document is not visible

- **GIVEN** a wiki document has been soft deleted
- **WHEN** the user requests its detail
- **THEN** the system SHALL reject or return no visible document result

### Requirement: Edit Wiki Document Online

The system SHALL provide an online editor page for creating and editing wiki document content.

#### Scenario: Save edited document

- **GIVEN** the user is logged in
- **AND** the user can edit the selected document
- **WHEN** the user changes the document title or content in the online editor and saves
- **THEN** the system SHALL persist the updated content to MySQL
- **AND** update the document edit time
- **AND** clear affected Redis cache entries

#### Scenario: Reject unauthorized edit

- **GIVEN** the user is logged in
- **AND** the user does not have edit permission for the selected document
- **WHEN** the user submits an edit action
- **THEN** the system SHALL reject the request
- **AND** the saved document content SHALL remain unchanged

### Requirement: Delete Wiki Document

The system SHALL allow a permitted user to soft delete a wiki document.

#### Scenario: Delete document successfully

- **GIVEN** the user is logged in
- **AND** the user can delete the selected document
- **WHEN** the user submits the delete action
- **THEN** the system SHALL soft delete the document in MySQL
- **AND** clear affected Redis cache entries
- **AND** the document SHALL no longer appear in normal list results

#### Scenario: Reject unauthorized delete

- **GIVEN** the user is logged in
- **AND** the user does not have delete permission for the selected document
- **WHEN** the user submits the delete action
- **THEN** the system SHALL reject the request
- **AND** the document SHALL remain visible to permitted users

### Requirement: Stage 1 Scope Boundary

The system SHALL keep Stage 1 limited to basic `DocumentWiki` CRUD and online editing.

#### Scenario: No later-stage behavior in Stage 1

- **GIVEN** Stage 1 is implemented
- **WHEN** the document feature is inspected
- **THEN** it SHALL NOT require personal wiki spaces, team wiki spaces, document version history, RAG chunks, URL crawling, or document-file upload parsing
- **AND** it SHALL NOT require object storage for saved text content

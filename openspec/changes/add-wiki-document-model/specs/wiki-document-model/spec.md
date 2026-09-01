## Purpose

This capability introduces Wiki documents as first-class knowledge records while preserving image assets for use as document covers, embedded images, and workspace materials.

## ADDED Requirements

### Requirement: Wiki documents can be created
The system SHALL allow an authenticated user to create a Wiki document with a title, optional summary, optional content, optional category, optional tags, optional cover image reference, optional source file metadata, and optional space ownership.

#### Scenario: Create public Wiki document
- **WHEN** an authenticated user creates a document without a space identifier
- **THEN** the system stores the document as a public Wiki document owned by that user

#### Scenario: Create space Wiki document
- **WHEN** an authenticated user creates a document with a valid space identifier
- **THEN** the system stores the document under that Wiki space and keeps the creating user as the document owner

#### Scenario: Reject invalid title
- **WHEN** an authenticated user creates a document with a blank title
- **THEN** the system rejects the request with a parameter error

### Requirement: Wiki documents can be edited
The system SHALL allow an authenticated user to edit the metadata and content of an existing Wiki document without changing its document identifier, owner, or space ownership.

#### Scenario: Edit document content
- **WHEN** an authenticated user edits an existing document title, summary, content, category, tags, or cover image reference
- **THEN** the system updates the document and records a new edit time

#### Scenario: Reject editing missing document
- **WHEN** an authenticated user edits a document identifier that does not exist
- **THEN** the system rejects the request with a not found error

### Requirement: Wiki documents can be queried
The system SHALL expose document detail and paginated document list behavior for public Wiki documents and space-owned Wiki documents.

#### Scenario: Query document detail
- **WHEN** a caller requests a document detail by an existing document identifier
- **THEN** the system returns the document fields, owner display information, parsed tags, and an initialized permission list

#### Scenario: Query paginated public documents
- **WHEN** a caller queries documents without a space identifier and requests public records only
- **THEN** the system returns paginated public Wiki documents

#### Scenario: Query paginated space documents
- **WHEN** a caller queries documents with a valid space identifier
- **THEN** the system returns paginated Wiki documents that belong to that space

#### Scenario: Search documents by text
- **WHEN** a caller provides a search keyword
- **THEN** the system matches documents by title, summary, content, or category

### Requirement: Wiki documents can be deleted
The system SHALL allow an authenticated user to logically delete an existing Wiki document so it no longer appears in normal document queries.

#### Scenario: Delete existing document
- **WHEN** an authenticated user deletes an existing document
- **THEN** the system marks the document as deleted and excludes it from normal query results

#### Scenario: Reject deleting missing document
- **WHEN** an authenticated user deletes a document identifier that does not exist
- **THEN** the system rejects the request with a not found error

### Requirement: Wiki document versions are recorded
The system SHALL record immutable version entries for Wiki documents when a document is created or edited.

#### Scenario: Initial version after creation
- **WHEN** an authenticated user creates a Wiki document
- **THEN** the system records version number 1 with the created title, summary, and content

#### Scenario: Incremental version after edit
- **WHEN** an authenticated user edits a Wiki document that already has versions
- **THEN** the system records a new version with the next version number and the updated title, summary, and content

#### Scenario: List document versions
- **WHEN** a caller requests versions for an existing document
- **THEN** the system returns version records ordered from newest to oldest

### Requirement: Wiki document chunks are available for future retrieval
The system SHALL support storing and querying text chunks associated with a Wiki document and its space for future retrieval-augmented generation.

#### Scenario: Store document chunk
- **WHEN** the system is given a document identifier, chunk index, chunk content, and optional token count
- **THEN** the system stores the chunk with the associated document and space identifiers

#### Scenario: Query document chunks
- **WHEN** a caller requests chunks for an existing document
- **THEN** the system returns non-deleted chunks in chunk index order

### Requirement: Image assets remain available
The system SHALL preserve existing image asset behavior so images can continue to be uploaded, stored, queried, and associated with spaces independently of Wiki documents.

#### Scenario: Existing image upload behavior remains available
- **WHEN** an authenticated user uploads an image through the existing image upload behavior
- **THEN** the system continues to store the image with its URL, thumbnail URL, metadata, owner, and optional space identifier

#### Scenario: Wiki document references image asset
- **WHEN** a Wiki document stores a cover image reference
- **THEN** the system stores the reference without modifying or deleting the underlying image asset

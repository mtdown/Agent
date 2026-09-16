## Purpose

Allows a user to import a single JSON file that contains an entire corpus as a set of independent Wiki documents, so that externally published question-answering datasets can be loaded into a Wiki space without hand-splitting them into one file per document.

## ADDED Requirements

### Requirement: User can import a JSON file as multiple Wiki documents

The system SHALL allow an authenticated user with access to a writable Wiki space to submit a single `.json` file and have it split into one Wiki document per entry.

#### Scenario: JSON array imports as multiple documents

- **WHEN** a logged-in user uploads a valid JSON file while a writable Wiki space and optional folder are selected
- **THEN** the system creates one Wiki document per entry contained in the file
- **AND** every created document is placed in the selected space and optional folder
- **AND** each created document is stored as Markdown
- **AND** the response reports an independent result for every entry

#### Scenario: Entry count is not capped

- **WHEN** a user uploads a JSON file whose entry count exceeds the per-request item limit applied to multi-file batch uploads
- **THEN** the system processes every entry in the file
- **AND** the request is not rejected for exceeding an entry-count limit

#### Scenario: File exceeds the accepted size

- **WHEN** an uploaded JSON file exceeds the accepted file size
- **THEN** the system rejects the request with a clear size message
- **AND** no Wiki document is created

### Requirement: JSON entries are read and split incrementally

The system SHALL read entries from the uploaded JSON file incrementally, so that a large corpus file does not have to be fully materialized in memory before documents are created.

#### Scenario: Large corpus file

- **WHEN** a user uploads a JSON file containing a large corpus
- **THEN** the system reads the file entry by entry
- **AND** a document is created as soon as its entry has been read

### Requirement: Each entry's original fields are preserved as metadata

The system SHALL retain the original fields of every JSON entry on the created document as a single JSON metadata record, excluding only the fields used as the document title and content.

#### Scenario: Entry carries fields beyond title and content

- **WHEN** an entry contains fields other than the document title and content
- **THEN** the created document stores those fields as one JSON metadata record
- **AND** the record remains available for later source traceability

#### Scenario: Entry supplies no explicit title

- **WHEN** an entry has no usable title field
- **THEN** the system derives a non-empty document title instead of failing the entry

#### Scenario: Entry title exceeds the document title limit

- **WHEN** an entry's title is longer than the document title limit
- **THEN** the created document title is truncated to fit the limit
- **AND** the entry's complete original title remains in the metadata record

#### Scenario: Entry carries a source url

- **WHEN** an entry carries a source url
- **THEN** the created document records that url as its source url
- **AND** the url also remains present in the metadata record

#### Scenario: Metadata record exceeds the stored size limit

- **WHEN** an entry's metadata record would exceed the stored metadata size limit
- **THEN** the system drops the least important fields until the record fits
- **AND** the stored record remains valid JSON
- **AND** the entry is still imported

### Requirement: A single malformed entry does not abort the import

The system SHALL isolate every entry so that one unusable entry never prevents the remaining entries from being imported.

#### Scenario: Entry has no usable content

- **WHEN** an entry has no usable content
- **THEN** the system reports a failure result for that entry
- **AND** the system continues importing the remaining entries

#### Scenario: Uploaded file is not valid JSON

- **WHEN** the uploaded file cannot be parsed as JSON
- **THEN** the system rejects the request with a clear message
- **AND** no Wiki document is created

#### Scenario: JSON contains no importable entries

- **WHEN** the uploaded JSON parses successfully but contains no importable entry
- **THEN** the system reports that nothing could be imported
- **AND** no Wiki document is created

### Requirement: JSON import reuses the existing destination and permission rules

The system SHALL resolve the target space and folder and enforce access using the same rules as the existing batch import entry points.

#### Scenario: Destination is selected and authorized

- **WHEN** a user uploads a JSON file with a selected space and optional folder
- **THEN** the system applies the same space edit permission check and folder resolution as existing batch imports
- **AND** documents are created only in the resolved destination

#### Scenario: User cannot write to the destination

- **WHEN** the resolved space is not editable by the uploading user
- **THEN** the system rejects the request
- **AND** no Wiki document is created

### Requirement: Imported JSON documents enter the existing indexing flow

The system SHALL rely on the existing document indexing flow to split and vectorize imported documents, and SHALL NOT introduce a second chunking implementation.

#### Scenario: Imported documents become retrievable

- **WHEN** documents created from a JSON import are persisted
- **THEN** the existing asynchronous indexing flow splits and indexes them
- **AND** the chunks are produced by the same chunking entry point used for every other document

#### Scenario: Import reports per-entry document references

- **WHEN** a JSON import finishes
- **THEN** each successful entry's result carries a stable identifier of the entry it came from
- **AND** each successful entry's result carries the identifier of the document it created
- **AND** the two identifiers together map source entries back to Wiki documents without relying on entry ordering

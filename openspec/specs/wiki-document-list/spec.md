# wiki-document-list Specification

## Purpose
Defines that paginated wiki document queries resolve their authors in a single batch, matching the approach already used by the picture module, instead of issuing one author query per returned row.

## Requirements

### Requirement: Document list loads authors in one batch

The system SHALL resolve the authors of a page of documents with a single batched query and map them onto the page, keeping the total query count constant with respect to page size.

#### Scenario: Page of twenty documents by several authors

- **GIVEN** a page request returning twenty documents authored by three distinct users
- **WHEN** the page is assembled
- **THEN** the author lookup SHALL issue at most one additional query
- **AND** every row SHALL carry its author

#### Scenario: Page of twenty documents by the same author

- **GIVEN** a page request returning twenty documents all authored by one user
- **WHEN** the page is assembled
- **THEN** the author lookup SHALL issue one query, not twenty

#### Scenario: Empty page

- **GIVEN** a query that matches no documents
- **WHEN** the page is assembled
- **THEN** no author query SHALL be issued

### Requirement: Authorless and deleted authors degrade gracefully

The system SHALL produce a page successfully when a document has no author id or references a user row that no longer exists.

#### Scenario: Missing author row

- **GIVEN** a document whose `userId` does not match any user row
- **WHEN** the page is assembled
- **THEN** the row SHALL be returned with a null `user` field
- **AND** the request SHALL NOT fail

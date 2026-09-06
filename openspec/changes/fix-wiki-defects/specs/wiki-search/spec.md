## Purpose

Defines wiki document search as a relevance-ranked full-text query restricted to the caller's visible spaces, replacing the previous `LIKE '%keyword%'` scan that cannot use an index and cannot segment Chinese text.

## ADDED Requirements

### Requirement: Search uses a full-text index

The system SHALL serve document keyword search from a MySQL `FULLTEXT` index over `title` and `content`, configured with the `ngram` parser so Chinese text is segmented.

#### Scenario: Keyword matches by meaning, not by exact substring

- **GIVEN** a document whose content contains the phrase "设计数据库"
- **WHEN** a visible user searches for "数据库设计"
- **THEN** the document SHALL be returned

#### Scenario: Search is not a full table scan

- **GIVEN** the `document_wiki` table has a `FULLTEXT` index on `title` and `content`
- **WHEN** a keyword search runs
- **THEN** the execution plan SHALL use the full-text index rather than a full scan

### Requirement: Results are ordered by relevance

The system SHALL order full-text results by match relevance, and only fall back to edit-time ordering when no keyword is supplied.

#### Scenario: Keyword supplied

- **GIVEN** several documents match a keyword with differing relevance
- **WHEN** the search runs
- **THEN** higher-relevance documents SHALL appear first

#### Scenario: No keyword supplied

- **GIVEN** a list request with no `searchText`
- **WHEN** the query runs
- **THEN** results SHALL be ordered by `editTime` descending as before

### Requirement: Search never crosses the visibility boundary

The system SHALL restrict every search result to spaces the caller can see, including on the full-text path.

#### Scenario: Search excludes invisible spaces

- **GIVEN** documents matching the keyword exist in both a visible space and an invisible space
- **WHEN** the caller searches
- **THEN** only documents from visible spaces SHALL be returned

#### Scenario: Cached search respects visibility after invalidation

- **GIVEN** a cached search result set
- **WHEN** a mutation invalidates the space cache and the caller searches again
- **THEN** results SHALL be recomputed and SHALL still exclude invisible spaces

### Requirement: Cached search results are invalidated on mutation

The system SHALL clear cached search/list results for a space whenever a document in that space is created, edited, deleted, moved, or restored.

#### Scenario: Document edited then searched

- **GIVEN** a cached search result for space S
- **WHEN** a document in S is edited and the caller searches again
- **THEN** the returned set SHALL reflect the edit

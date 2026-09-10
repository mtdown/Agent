# wiki-rag-pipeline Specification

## Purpose

Lets the Wiki turn stored Markdown documents into searchable semantic chunks, keeps the chunk index consistent with document lifecycle (create/edit/recycle/restore/permanent delete), and exposes permission-filtered semantic retrieval for downstream consumers (AI assistant panel and future open API).

## ADDED Requirements

### Requirement: Markdown documents are chunked into semantic slices

The system SHALL split each Markdown document into chunks aligned to its heading hierarchy, so that policy-style clause structure is preserved per chunk.

#### Scenario: Document with headings is split by heading boundaries
- **WHEN** a Markdown document contains multiple heading levels (h1-h6)
- **THEN** the system creates chunks whose boundaries fall on heading lines
- **AND** each chunk records its heading path (for example "三、补助标准 > (二)发放方式")

#### Scenario: Oversized section is further split
- **WHEN** a single heading section exceeds the configured maximum chunk length (600 characters)
- **THEN** the system splits that section into multiple chunks by paragraph

#### Scenario: Tiny section is merged
- **WHEN** a heading section is shorter than the configured minimum chunk length (100 characters)
- **THEN** the system merges it with an adjacent chunk instead of producing a fragment

#### Scenario: Adjacent chunks keep overlap
- **WHEN** a document is chunked into multiple chunks
- **THEN** adjacent chunks share an overlap of roughly 80 characters so clause context is not cut off

### Requirement: Chunk metadata captures citation anchors

The system SHALL extract and store, for every chunk, the citation anchors needed for grounded answers.

#### Scenario: Document number is extracted
- **WHEN** a chunk's source document contains a government document number matching the pattern `[\u4e00-\u9fa5]{2,12}〔\d{4}〕\d+号` (for example 渝府办发〔2026〕24号)
- **THEN** the chunk stores that document number in its metadata
- **AND** the document title is stored redundantly on the chunk

#### Scenario: Document without a number
- **WHEN** a document contains no matching document number
- **THEN** chunking still succeeds with the document number left empty

### Requirement: Chunks are embedded and searchable by semantics

The system SHALL vectorize chunks via a configurable OpenAI-compatible embedding endpoint and SHALL support cosine-similarity retrieval over authorized chunks.

#### Scenario: Embedding endpoint is configurable
- **WHEN** the operator changes the embedding base-url / model configuration
- **THEN** newly built chunks use the new endpoint without code changes
- **AND** existing vectors remain queryable until their chunks are invalidated and rebuilt

#### Scenario: Semantic query returns top-K authorized chunks
- **WHEN** a consumer submits a natural-language query with an explicit space scope
- **THEN** the system returns the top-K most similar ACTIVE chunks within that scope
- **AND** each result carries chunk text, document id, document title, document number and heading path

#### Scenario: Embedding API unavailable during indexing
- **WHEN** the embedding endpoint fails or is not configured while documents are being saved
- **THEN** document saving still succeeds
- **AND** the indexing failure is recorded for later retry (via rebuild) without blocking the save

### Requirement: Retrieval is hard-filtered by space permission

The system SHALL restrict semantic retrieval to chunks whose space is both visible to the requesting user and included in the requested scope. Permission filtering SHALL happen inside the retrieval service before similarity ranking, never by instructing the model.

#### Scenario: Scope intersects with visible spaces
- **WHEN** a user requests retrieval with a scope of multiple spaces, some of which the user cannot see
- **THEN** only chunks in the intersection of requested scope and user-visible spaces are searched
- **AND** chunks from non-visible spaces never appear in results even if semantically closest

#### Scenario: Scope has no visible space
- **WHEN** the requested scope contains no space visible to the user
- **THEN** the retrieval returns an empty result set with the effective scope reported as empty

#### Scenario: Retrieval reports effective scope
- **WHEN** retrieval completes
- **THEN** the system reports the effective space set searched and the number of authorized documents, so callers can display "检索范围：X 个空间 / N 篇授权文档"

### Requirement: Chunk lifecycle follows document lifecycle

The system SHALL keep chunk state consistent with document state through every lifecycle transition.

#### Scenario: Document created
- **WHEN** a Wiki document is created through any existing entry point (single import, URL import, manual create, batch file import, batch URL import)
- **THEN** its chunks are built asynchronously after the document transaction commits

#### Scenario: Document edited
- **WHEN** a document is edited and saved
- **THEN** the chunks of the previous content version are marked INVALID
- **AND** new chunks for the new content version are built
- **AND** retrieval never returns chunks of a stale content version

#### Scenario: Document moved to recycle bin
- **WHEN** a document is logically deleted into the recycle bin
- **THEN** all its chunks become INVALID and are immediately excluded from retrieval

#### Scenario: Document restored from recycle bin
- **WHEN** a deleted document is restored and its content version is unchanged
- **THEN** its existing chunks become ACTIVE again without re-chunking
- **AND** if the content version changed, chunks are rebuilt instead

#### Scenario: Document permanently deleted
- **WHEN** a document is permanently deleted from the recycle bin
- **THEN** its chunks are physically removed from the chunk table

#### Scenario: Space deleted
- **WHEN** a Wiki space is deleted
- **THEN** all chunks of documents in that space are invalidated

### Requirement: Index failures never block document operations

The system SHALL decouple indexing from document persistence so that indexing problems cannot fail user-facing document operations.

#### Scenario: Indexing throws an error
- **WHEN** the asynchronous indexing pipeline fails for one document
- **THEN** the originating document save/edit/delete API result is unaffected
- **AND** the failure is logged and the document remains eligible for rebuild

# wiki-rag-pipeline Specification

## Purpose

Lets the Wiki turn stored Markdown documents into searchable semantic chunks, keeps the chunk index consistent with document lifecycle (create/edit/recycle/restore/permanent delete), and exposes permission-filtered semantic retrieval for downstream consumers (AI assistant panel and future open API).

## Requirements

### Requirement: Markdown documents are chunked into semantic slices

The system SHALL split each Markdown document into chunks aligned to its heading hierarchy, so that clause structure is preserved per chunk. The chunking parameters (character cap, minimum chunk length, overlap, and sentence-boundary characters) SHALL be selected from the document's language: documents whose content is predominantly Chinese use 600 characters / 100 characters / 80 characters with `。；` as sentence boundaries, and documents whose content is predominantly English use 1800 characters / 100 characters / 100 characters with `.!?;` as sentence boundaries. The selection SHALL be a deterministic function of the document content, SHALL NOT depend on the import entry point that created the document, and SHALL NOT depend on a model call.

#### Scenario: Document with headings is split by heading boundaries

- **WHEN** a Markdown document contains multiple heading levels (h1-h6)
- **THEN** the system creates chunks whose boundaries fall on heading lines
- **AND** each chunk records its heading path (for example "三、补助标准 > (二)发放方式")

#### Scenario: Oversized section is further split

- **WHEN** a single heading section exceeds the maximum chunk length configured for the document's language (600 characters for Chinese, 1800 characters for English)
- **THEN** the system splits that section into multiple chunks by paragraph

#### Scenario: Tiny section is merged

- **WHEN** a heading section is shorter than the configured minimum chunk length (100 characters)
- **THEN** the system merges it with an adjacent chunk instead of producing a fragment

#### Scenario: Adjacent chunks keep overlap

- **WHEN** a document is chunked into multiple chunks
- **THEN** adjacent chunks share an overlap of the configured number of characters (roughly 80 for Chinese, 100 for English) so clause context is not cut off

#### Scenario: Predominantly Chinese document keeps existing chunking

- **WHEN** a document's content is predominantly Chinese
- **THEN** the chunking entry point uses the existing character cap, overlap, and sentence-boundary characters
- **AND** the resulting chunks are identical to those produced before language-aware parameters were introduced

#### Scenario: English document is chunked with English parameters

- **WHEN** a document's content is predominantly English
- **THEN** the chunking entry point uses the English character cap, overlap, and sentence-boundary characters for that document

#### Scenario: English text is not split in the middle of a word

- **WHEN** an English section exceeds the English character cap
- **THEN** the split is placed at a sentence boundary when one is available in the window
- **AND** otherwise at a word boundary
- **AND** the split never falls inside a word, a decimal number, or a common abbreviation

#### Scenario: Language detection is deterministic

- **WHEN** the same content is chunked more than once
- **THEN** the selected parameters are the same every time
- **AND** the selection does not depend on a model call

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

### Requirement: Retrieved chunks carry their document title

The system SHALL expose the originating document title with every retrieval hit and SHALL NOT prepend the title to the stored chunk text.

#### Scenario: Retrieval result identifies its source document

- **WHEN** a retrieval query returns a chunk that came from an imported document
- **THEN** the hit carries the title of the document the chunk belongs to
- **AND** the stored chunk text does not contain an added title prefix


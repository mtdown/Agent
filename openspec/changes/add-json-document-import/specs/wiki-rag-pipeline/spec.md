## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Retrieved chunks carry their document title

The system SHALL expose the originating document title with every retrieval hit and SHALL NOT prepend the title to the stored chunk text.

#### Scenario: Retrieval result identifies its source document

- **WHEN** a retrieval query returns a chunk that came from an imported document
- **THEN** the hit carries the title of the document the chunk belongs to
- **AND** the stored chunk text does not contain an added title prefix

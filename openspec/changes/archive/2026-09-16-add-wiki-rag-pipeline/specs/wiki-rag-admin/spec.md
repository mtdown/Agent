# wiki-rag-admin Specification

## Purpose

Gives administrators operational control over the RAG index: backfilling existing documents, reconciling orphan chunks, and inspecting chunk state — so the index can be repaired without touching documents.

## ADDED Requirements

### Requirement: Administrator can trigger an idempotent full backfill

The system SHALL provide an admin-only endpoint that rebuilds the chunk index for existing Markdown documents, safe to re-run.

#### Scenario: Backfill processes existing documents
- **WHEN** an administrator triggers a full backfill
- **THEN** every non-deleted Markdown document without a valid chunk set gets chunked, embedded and indexed
- **AND** the operation returns a report of total / created / skipped / failed counts with per-failure reasons

#### Scenario: Backfill is idempotent
- **WHEN** a backfill is re-run over documents already indexed with unchanged content hash
- **THEN** those documents are skipped without duplicating chunks
- **AND** re-running after an interruption produces a consistent index

#### Scenario: Backfill respects access control
- **WHEN** a non-admin user calls the backfill endpoint
- **THEN** the request is rejected

### Requirement: Daily reconciliation removes orphan chunks

The system SHALL run a scheduled reconciliation that detects chunks inconsistent with document state and repairs them.

#### Scenario: Active chunks belong to a deleted or recycled document
- **WHEN** the reconciliation finds ACTIVE chunks whose document is logically deleted or missing
- **THEN** those chunks are marked INVALID

#### Scenario: Reconciliation reports its outcome
- **WHEN** a reconciliation run completes
- **THEN** the run logs how many orphan chunks were invalidated

### Requirement: Chunk state is inspectable per document

The system SHALL expose a query that lists a document's chunks with their metadata and status, restricted to users who can view that document.

#### Scenario: Viewer lists chunks of a visible document
- **WHEN** a user who can view a document requests its chunk list
- **THEN** the system returns each chunk's index, heading path, document number, status and text preview

#### Scenario: Chunk listing respects document visibility
- **WHEN** a user without view permission on a document requests its chunk list
- **THEN** the request is rejected, so chunk listing cannot be used to bypass document visibility

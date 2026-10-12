# RAG evaluation ID trace Specification

## Requirement: Explain document and evidence ID lineage

The project SHALL provide a Chinese walkthrough of a real first-version paired evaluation question, showing how source and target documents map to chunks, how a pair ID is assigned, how questions are assigned IDs, and how gold evidence is bound.

### Scenario: Trace a paired question end to end

- **WHEN** a reader follows the documented `A-p-02-2` example
- **THEN** they can identify the interpretation article as question source and the policy original as retrieval target
- **AND** they can follow `metadataId` to `docId`, `pairId`, question ID, target `chunkIndex`, and database `chunkId`
- **AND** they understand that the gold coordinate uses `docId + chunkIndex`, while `chunkId` is the database row identifier

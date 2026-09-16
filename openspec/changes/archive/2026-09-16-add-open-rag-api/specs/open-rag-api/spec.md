# open-rag-api Specification

## Purpose

Lets external callers (e.g. a local Qwen agent) use the wiki as a cloud knowledge base through API-Key-authenticated read-only RAG endpoints. Keys are managed by their owner in the app; retrieval reuses the permission-filtered service layer so a key can only ever reach spaces visible to its owner.

## ADDED Requirements

### Requirement: API key management

Logged-in users SHALL manage personal API keys (create / list / delete) in the app. Keys SHALL be stored hashed (SHA-256); the plaintext key SHALL be returned exactly once, at creation time.

#### Scenario: Create a key
- **WHEN** a logged-in user creates a key with name "本地 Agent"
- **THEN** a key `cpk_...` is generated and the plaintext is displayed once with a copy button
- **AND** only name / prefix / create time are shown afterwards in the list

#### Scenario: Delete revokes immediately
- **WHEN** the owner deletes a key
- **THEN** subsequent requests carrying that key are rejected with 40101

#### Scenario: Only the owner manages a key
- **WHEN** a user tries to delete a key owned by someone else
- **THEN** the delete fails and the key remains active

### Requirement: Key-authenticated retrieval

`POST /api/open/rag/search` SHALL authenticate via the `X-API-Key` header and return the same result shape as the in-app `/rag/search`, with permission filtering inherited from the service layer.

#### Scenario: Valid key searches within owner's visible spaces
- **WHEN** a caller presents a valid key and queries "低空经济"
- **THEN** results contain only chunks in spaces visible to the key's owner

#### Scenario: Unauthorized scope yields nothing
- **WHEN** the request narrows scope to a space the key owner cannot see
- **THEN** that space contributes no hits and is excluded from effectiveSpaceIds

#### Scenario: Missing / invalid / revoked key
- **WHEN** a request carries no key, a malformed key, or a deleted key
- **THEN** the endpoint responds 40101 with a uniform message that does not reveal which case occurred

### Requirement: Key-authenticated streamed ask

`POST /api/open/rag/ask` SHALL authenticate the same way and stream the identical SSE event protocol (meta / reason / delta / done / error) as the in-app assistant endpoint.

#### Scenario: Streamed answer with citations
- **WHEN** a caller with a valid key asks "渝府办发〔2026〕24号说了什么"
- **THEN** the stream emits meta with citations and a delta answer with [n] markers, ending with done

#### Scenario: Zero-hit short circuit
- **WHEN** the query has no hit in the owner's visible scope
- **THEN** the stream emits meta with empty citations and a delta refusal without calling the LLM

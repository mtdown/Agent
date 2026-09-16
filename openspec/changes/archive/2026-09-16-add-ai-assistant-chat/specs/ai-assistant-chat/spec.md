# ai-assistant-chat Specification

## Purpose

Gives logged-in users a visible "AI 助手" entry and a chat panel that answers policy questions over the RAG pipeline (capability `wiki-rag-pipeline`) with streamed answers, permission-filtered scope selection, and clickable citations whose document numbers come from chunk metadata (never trusted from LLM output).

## ADDED Requirements

### Requirement: AI assistant navigation entry

The system SHALL show an "AI 助手" entry in the top navigation for logged-in users, linking to the assistant chat page.

#### Scenario: Logged-in user sees the entry
- **WHEN** a logged-in user browses any page
- **THEN** the top navigation shows the "AI 助手" menu item
- **AND** clicking it navigates to `/aiAssistant`

#### Scenario: Anonymous user does not see the entry
- **WHEN** an anonymous user browses pages
- **THEN** the top navigation does not show the "AI 助手" menu item

### Requirement: Retrieval scope selection

The panel SHALL offer a multi-select of the user's visible spaces plus an "all authorized spaces" shortcut, and the effective retrieval scope SHALL be the intersection of the selection and the user's visible spaces.

#### Scenario: Multiple spaces selected
- **WHEN** the user selects two spaces and asks a question
- **THEN** the backend retrieves only within (selected ∩ visible) spaces
- **AND** the SSE meta event reports that effective set and the authorized document count within it

#### Scenario: Unauthorized spaces are not selectable
- **WHEN** the user opens the scope selector
- **THEN** only visible spaces (personal + joined team + public) appear as options

#### Scenario: All-authorized shortcut
- **WHEN** the user checks the "全部授权空间" shortcut
- **THEN** the effective scope equals all of the user's visible spaces

### Requirement: Streamed answering

Answers SHALL be rendered incrementally, with the model's reasoning output displayed separately from the answer body.

#### Scenario: Streaming render
- **WHEN** an answer is being generated
- **THEN** the answer body renders progressively per delta event
- **AND** reasoning output (if any) is shown in a collapsed grey block

#### Scenario: Out-of-corpus question is refused
- **WHEN** retrieval returns zero hits in the effective scope
- **THEN** the system does not call the LLM and returns an explicit "no supporting evidence found" reply
- **AND** no fabricated document number appears

#### Scenario: LLM not configured
- **WHEN** RAG_LLM_API_KEY is not configured and the user asks a question
- **THEN** a readable error is delivered via the SSE error event
- **AND** other features are unaffected

### Requirement: Citation rendering and jump

Citations SHALL be rendered from retrieval-hit chunk metadata; the LLM only outputs `[n]` markers.

#### Scenario: Citation click navigates to the document
- **WHEN** the user clicks a `[n]` marker or a citation entry
- **THEN** the app navigates to the document detail page (`/documentWiki/{docId}`)

#### Scenario: Citation data comes from metadata
- **WHEN** an answer completes
- **THEN** the citation list shows each hit's document title and document number taken from chunk metadata (docNumber)
- **AND** document numbers inside the LLM text are never used as the citation source

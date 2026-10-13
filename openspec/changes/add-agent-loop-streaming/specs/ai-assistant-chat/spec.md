# Spec Delta

## ADDED Requirements

### Requirement: Agent preview in assistant panel
The assistant panel SHALL offer logged-in users a P0-1 Agent preview with streamed answer text and a compact metrics line while retaining the existing RAG question flow.

#### Scenario: Agent preview answer
- **WHEN** a logged-in user selects Agent preview and asks a question
- **THEN** the answer appears incrementally and the final message shows input, output and total tokens when supplied, first-answer-text time, and total time

#### Scenario: Existing RAG answer
- **WHEN** a logged-in user uses the existing RAG mode
- **THEN** its retrieval scope, citations, and streamed answer behavior remain available

#### Scenario: Anonymous access
- **WHEN** an anonymous caller requests the Agent preview through cloud
- **THEN** cloud rejects the request before contacting the Agent service

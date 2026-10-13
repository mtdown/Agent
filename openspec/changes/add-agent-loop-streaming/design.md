# Design: P0-1 Agent loop and streaming

## Context

See proposal.md. The existing `cloud` service authenticates browser users and has a streamed `/rag/ask` path. Its `RagLlmClient` accepts one system/user pair and emits text/reasoning, but does not expose a multi-turn tool-call protocol. The assistant page already consumes SSE and displays partial answers. The project mandates one task branch, OpenSpec, and root start/stop scripts; no Git worktrees.

## Goals / Non-Goals

**Goals:** Add a separate Java service with a bounded loop, authenticated cloud bridge, visible streaming preview, and per-request metrics. Preserve the old RAG entry.

**Non-Goals:** P0-1 does not add knowledge tools, database access, conversation persistence, or policy-grounded answers. The preview is labeled as a technical preview until P0-2 adds authorized evidence.

## Decisions

### 1. Same repository, separate Spring Boot process

Create `cloud_agent/` with its own Maven build and HTTP port 8124. The existing `cloud` remains the only browser-facing backend. This matches the approved two-Java-process topology while allowing each service to run independently.

### 2. Loop abstraction rather than prompt chaining

Each model turn yields ordered answer deltas, optional tool requests, finish reason, and optional token usage. The loop appends tool results to the message history and invokes the model again only when tool calls occur. A registry validates and dispatches calls. P0-1 has no production tools; tests inject a deterministic fake tool to verify the loop. This adapts `learn-claude-code` s01/s02 while allowing later RAG tools without changing the loop.

### 3. Streaming model adapter and provider compatibility

Use the existing OpenAI-compatible provider configuration pattern, but implement a separate Agent adapter that understands streamed `tool_calls` fragments. Do not change `RagLlmClient`'s single-turn contract. Verify actual provider tool-call streaming with a bounded integration probe before enabling tools in P0-2. A model that returns no tools still streams final text in P0-1.

### 4. Authenticated streaming bridge

Cloud authenticates the user, then forwards to an internal Agent endpoint using a service credential. Agent binds to loopback locally; deployment must provide a secret and private service network. The browser has no Agent URL or service key. Cloud relays SSE frames without buffering full answers and closes the upstream stream when the browser disconnects.

### 5. Limits and metrics

Configure maximum model turns, request timeout, and maximum accumulated tool-result size. Emit exactly one terminal `done` or `error`. `firstAnswerMs` starts at cloud request acceptance and stops at first answer-body delta; `totalMs` ends at terminal completion. Sum provider usage across turns only when every turn reports usage; otherwise send null. The UI displays unavailable usage explicitly.

## Risks / Trade-offs

- Provider streams differ in tool-call and usage fields → parse defensively, test with captured fixtures, and treat missing usage as null.
- Two-process SSE can buffer or leak work after disconnect → copy chunks immediately and close upstream on downstream error; test cancellation.
- P0-1 has no knowledge evidence → preview must not claim to answer from the policy corpus; existing RAG remains the policy answer path.
- Local startup now needs another port/process → extend the required root scripts rather than introducing ad hoc commands.

## Migration Plan

Add the service and cloud proxy behind a preview mode. Update root start/stop scripts to manage port 8124. Verify existing RAG tests and build. Roll back by disabling/removing preview route and Agent process; no data migration is needed.

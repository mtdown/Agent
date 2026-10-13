# Proposal: minimal streamed Agent loop (P0-1)

## Why

The current AI assistant streams one RAG generation request, but has no model/tool/result loop that later read-only knowledge tools can join. P0-1 establishes a separately started Agent service and observable streaming loop before changing retrieval or conversation history.

## What Changes

- Add a Java Agent service in this repository with a bounded model/tool/result loop and an OpenAI-compatible streaming model adapter.
- Add a logged-in, cloud-mediated preview path that streams Agent text and reports total model token use, time to first answer text, and total request time.
- Add local start/stop support for the second Java process using the existing project scripts.
- Keep the existing `/rag/ask` behavior and knowledge permissions unchanged. P0-1 registers no production knowledge or database tools; P0-2 will add those.

## Capabilities

### New Capabilities

- `agent-loop`: Bounded Agent turns, streaming results, usage/timing metrics, cancellation, and failure semantics.

### Modified Capabilities

- `ai-assistant-chat`: A logged-in user can select the P0-1 Agent preview and view its streamed answer with metrics while existing RAG mode continues to work.

## Acceptance Criteria

- A final text response streams incrementally and yields one terminal `done` event.
- A requested tool is dispatched through a registry, its result is returned to the model, and the next model turn can finish. Unknown or malformed tool requests fail safely.
- The loop stops at its configured turn limit, on timeout, or when the client disconnects.
- The preview displays provider-reported input/output/total tokens when available, first answer text latency, and total duration. Missing usage is shown as unavailable, never as zero.
- Existing RAG answers and citations remain available.

## Impact

New `cloud_agent/` Maven service; cloud authenticated streaming bridge; assistant page/API; `start-dev.ps1` and `stop-dev.ps1`; local configuration and tests. No database schema or corpus change.

## Rollback

Remove the Agent preview route and service from the start scripts/deployment. Existing `/rag/ask` remains independent and usable.

# Tasks

## 1. Agent service and loop

- [x] 1.1 Add an independent Java service with bounded model/tool/result turns; verify unit tests cover final text, a tool round trip, unknown tools, turn limit, timeout, and cancellation.
- [x] 1.2 Add the OpenAI-compatible streaming adapter; verify parser tests cover text chunks, fragmented tool calls, usage, and malformed events.
- [x] 1.3 Add a protected internal SSE endpoint with one terminal event and metrics; verify endpoint tests cover authentication, streamed deltas, unavailable usage, and failure.

## 2. Cloud bridge and assistant page

- [x] 2.1 Add a logged-in cloud SSE proxy to Agent; verify automated tests cover rejected unauthenticated requests and forwarded/cancelled streams.
- [x] 2.2 Add an Agent preview mode on the assistant page with streamed answer and token/latency display; verify frontend type check/build and preserve the RAG route.

## 3. Local operation and integration

- [x] 3.1 Extend root start/stop scripts to build/start/stop port 8124 using a shared internal credential; verify script syntax and port management.
- [x] 3.2 Run OpenSpec validation, service tests/build, cloud tests/build, and frontend build; record results and any issues in IssueLog.xlsx.
- [x] 3.3 Start local services through stop-dev.ps1 then start-dev.ps1 and deliver addresses and manual browser checks; keep page hand-test status pending until the owner reports results.

## Verification / Result

- 2026-10-13: Owner reported that the P0-1 preview conversation and previously listed page checks work. The earlier single `Connection reset` was not reproduced and is deferred in `IssueLog.xlsx`; reopen if it recurs.
- `openspec validate add-agent-loop-streaming --strict`: passed.
- `cloud_agent`: `mvn verify` passed, 12 tests.
- `cloud_front`: `npm run build` passed.
- `cloud`: initial `mvn verify` failed at Spring Boot repackage because the locally running backend held the target jar open on Windows. After `stop-dev.ps1`, `mvn verify` passed with 299 tests and a repackaged executable jar. The cause and resolution are recorded in `IssueLog.xlsx`.
- 2026-10-13: `stop-dev.ps1` stopped the jar-holding processes; `start-dev.ps1 -NoTunnel -NoPause` rebuilt and started backend (8123), Agent (8124), and frontend (3000). After receiving the final manual checklist, the owner directed work to proceed to the next step; this is recorded as acceptance of P0-1 manual testing.

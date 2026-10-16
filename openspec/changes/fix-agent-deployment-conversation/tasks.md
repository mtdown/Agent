# Tasks

## 1. Conversation source and local acceptance

- [x] 1.1 Discard the owner-approved staged JDBC copy and verify the checkout matches the committed mapper source with `git status` and blob IDs.
- [x] 1.2 Document the clean-worktree requirement for main acceptance and verify the instructions describe the observed checkout refusal in `deploy/README.md`.

## 2. Agent deployment

- [x] 2.1 Add a regression check for Agent packaging, shared key injection, and internal-only networking; verified it fails against the original deployment files.
- [x] 2.2 Build/package the Agent service in CI and Compose, inject the shared credential, and verify the regression check passes.
- [x] 2.3 Update deployment verification and rollback for both jars; verify script checks and documentation.
- [x] 2.4 Replace runtime `deepseek-chat` defaults with `deepseek-flash` in backend and Agent; verified the deployment regression check and module builds.

## 3. Integration and acceptance

- [x] 3.1 Run OpenSpec validation, relevant tests, build and static deployment checks; results recorded below.
- [x] 3.2 Perform AGENTS.md §8 assumption, contract, risk and mutation checks; results recorded below.
- [x] 3.3 Start local services with `stop-dev.ps1` followed by `start-dev.ps1 -NoTunnel -NoPause`; the scripts reported backend 8123, Agent 8124 and frontend 3000 listening.
- [x] 3.4 Owner manually tests Agent questions and history using the addresses below; owner reported "通过" on 2026-10-16.
- [ ] 3.5 After the owner's manual result and approval, use `upload.ps1` to publish semantic commits to this task branch; leave merge to the owner.

## Verification / Result

Automated verification on 2026-10-16: `openspec validate fix-agent-deployment-conversation --strict`, `node agentDeployment.test.mjs`, `bash -n deploy/deploy.sh deploy/rollback.sh`, cloud targeted Maven tests (15 tests, 0 failures/errors), Agent Maven tests (15 tests, 0 failures/errors), `mvn -q -DskipTests package` in each Java module, and frontend `npm run build` all passed. `git diff --check` passed. The regression test failed before deployment changes and passed afterward. Three temporary-copy mutations (wrong backend shared key, public Agent port, missing Agent backup) each made the test fail; restoring the copy made it pass. No production Agent request has been verified yet.

Local services started in the required order. Frontend: `http://127.0.0.1:3000`; backend: `http://127.0.0.1:8123`; Agent: `http://127.0.0.1:8124` (internal endpoint). `start-dev.ps1` reported suggested login `admin / 12345678`. The current process has `RAG_LLM_API_KEY` set; its value was not printed. Owner reported local page acceptance passed on 2026-10-16. Production deployment remains unverified until the branch is merged and CI deploys it.

### AGENTS.md §8.2 implicit assumptions and failure symptoms

- Spring property resolution maps `AGENT_BASE_URL` and `AGENT_INTERNAL_KEY` to the proxy's `agent.*` settings; if false, Agent requests return a missing-credential or unavailable event. Local service startup and owner page acceptance must check this boundary.
- The same nonempty generated key reaches both containers through one `.env`; if false, the Agent endpoint returns 401. Compose configuration and the key-mismatch mutation cover this invariant.
- `agent` resolves over the Compose network and binds port 8124 inside its container; if false, the backend returns unavailable. Compose config checks the URL, bind address, and absence of a published port.
- The deployed model API key is a DeepSeek-compatible credential; if absent, CI stops before upload; if incompatible, Agent returns a model error during page acceptance.
- `deepseek-flash` is accepted by the selected model provider; if not, a real Agent request fails even though the Java services start. The model name follows the owner's instruction and the provider documentation; page acceptance remains open.
- The ECS host has enough memory for a second JVM; if false, the Agent container exits or restarts. Deployment checks container running state and prints its logs.
- Backup files are made before extracting the new bundle; if false, rollback restores the new version. The workflow order is asserted and a missing-backup mutation makes the test fail.
- Conversation ownership and deletion behavior remain the committed Mapper implementation; if a stale JDBC file returns, the persistence-mapping test fails before upload.

### AGENTS.md §8.2 likely production failures and early detection

1. Missing or incompatible `RAG_LLM_API_KEY`: CI rejects missing key; an incompatible key surfaces as a model error in the owner's Agent page test.
2. Agent JVM exits from memory pressure: deployment prints Agent status and logs; operator checks container restarts and host memory.
3. Model endpoint delay or name mismatch: services stay up but streaming fails; run one real question and inspect Agent logs before accepting deployment.
4. Backup or rollback mismatch: compare `.bak` artifacts with the previous release and exercise rollback in a controlled deployment window.

### AGENTS.md §8.2 mentor questions

1. Can the 2 GB server sustain a second JVM alongside MySQL, Redis, backend and Nginx? The Agent heap is capped at 192 MB; observe actual memory during deployment.
2. How do we prove the internal key is shared without logging it? Compose config is checked with dummy values; a real page request confirms authentication after deployment.
3. Does rollback restore the previous topology as well as the previous code? The workflow backs up Compose and both image definitions before extraction; rollback restores those files and restarts with `--remove-orphans`.

### AGENTS.md §8.3 submit-time checks

- External signatures: no REST path, authentication policy, DTO, table or error-code change. Configuration keys `AGENT_BASE_URL`, `AGENT_INTERNAL_KEY`, and `RAG_LLM_FAST_MODEL` are now supplied to production containers; backend and Agent model defaults change to `deepseek-flash`.
- Implicit assumptions: enumerated above, with remaining production-only checks assigned to owner acceptance.
- Tests can fail: three temporary-copy mutations each turned the deployment regression test red, and the original unmodified copy remained green.

## Rollback

For a deployment, restore both previous jars with `deploy/rollback.sh`. For local uncommitted changes, revert only this change's files on the task branch after review.

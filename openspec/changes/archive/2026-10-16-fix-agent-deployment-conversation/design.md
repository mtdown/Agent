# Design

## Context

The local start script launches both Java processes and generates a shared key. Production CI currently builds only `cloud`, and Compose runs only the backend. `AgentPreviewProxy` returns the observed error before making a network request when its key is empty. The old `JdbcTemplate` source was staged exactly from an earlier commit; it has been discarded with owner authorization.

## Goals / Non-Goals

**Goals:** Replicate the local two-service topology in production, keep Agent traffic private, and make deployment failure observable.

**Non-Goals:** Change the Agent HTTP contract, replace conversation persistence, or bypass Git's protection for a dirty checkout.

## Decisions

- CI builds `cloud_agent` as a second jar. A separate small runtime image runs it with an explicit memory cap. This matches the local topology and keeps the existing backend image unchanged.
- Compose gives the backend `AGENT_BASE_URL=http://agent:8124` and supplies `AGENT_INTERNAL_KEY` to both containers. CI creates a random key if no GitHub secret is provided, writes it only to the deployment `.env`, and never prints it. An explicit secret can provide a stable key.
- CI checks for `RAG_LLM_API_KEY` before packaging. Agent needs this existing model credential, and a missing value should not create another deployed failure.
- Backend and Agent fast-model defaults are `deepseek-flash`, including the Java property fallback and Compose environment. Historical evaluation records keep the model names actually used in those runs.
- The main acceptance script continues to rely on Git's clean-checkout protection; the authorized stale staged file is removed. Its documented prerequisite tells the owner to finish or preserve task changes before switching branches.
- Deployment verifies the Agent container is running in addition to existing web/API probes. Rollback copies and restores both jars together.

## Risks / Trade-offs

- [Two JVMs increase memory use] → Cap the Agent heap and metaspace separately; check the actual server memory during acceptance.
- [A fresh generated key changes each deployment] → Both services receive the same value from one `.env`, and Compose restarts both on deployment.
- [Container running does not prove model completion] → The owner performs a real Agent question on the deployed page after merge and deployment.

## Migration Plan

Build the Agent jar and image, deploy both services with one private key, then verify the site and container status. If acceptance fails, restore both previous jars with the rollback script and restart both services. Existing database volumes remain untouched.

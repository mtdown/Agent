# Proposal

## Why

The AI assistant is available on the deployed site, but every Agent request fails because the production stack omits its private Agent service and shared credential. A staged copy of an older conversation store also blocked local main acceptance; the owner has authorized discarding that copy.

## What Changes

- Restore the committed mapper-based conversation store and verify the existing regression guard against the old JDBC implementation.
- Keep task-branch changes from being overwritten by the local main acceptance script; document its clean-worktree prerequisite.
- Build and deploy the Agent service beside the backend, provide a shared internal credential, and check that both services start.
- Document deployment and rollback for the added service.
- Use `deepseek-flash` wherever the backend or Agent currently defaults to the retired `deepseek-chat` name.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `deployment-pipeline`: The deployed stack must include the Agent service and connect it to the backend with a private credential.

## Impact

CI workflow, deployment archive, Docker Compose, deployment and rollback scripts, model defaults, deployment documentation, and local Git index state. No public API or database schema change. Historical evaluation records are unchanged.

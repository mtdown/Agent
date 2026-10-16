# Proposal

## Why

The first Agent-enabled deployment rebuilt the backend while leaving the four-day-old Nginx container running. CI observed a healthy homepage but 60 consecutive API 502 responses even though the backend had started. Nginx's fixed upstream address can become stale when Compose replaces the backend container.

## What Changes

- Resolve the backend service through Docker DNS while proxying requests, so backend container replacement does not strand Nginx on an old address.
- Reload and validate the mounted Nginx configuration during deployment, then keep the existing frontend and API health gate.
- Add a regression check for the proxy configuration and deployment reload.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `deployment-pipeline`: Deployment must keep API proxying working after the backend container is replaced, without requiring manual Nginx restart.

## Impact

Changes are limited to `deploy/nginx.conf`, `deploy/deploy.sh`, deployment validation, and related documentation. No API, database, credential, or public port contract changes. Rollback is the previous Nginx configuration and deployment script followed by `docker compose up -d` and Nginx reload/recreate.

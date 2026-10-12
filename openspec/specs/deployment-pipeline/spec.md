# deployment-pipeline Specification

## Purpose
Pushes to the deployment branch are built in CI and published to the cloud host without manual steps, while database and cache stay off the public network and secrets never enter the repository.

## Requirements

### Requirement: Push-triggered build and remote deploy
The system SHALL build the backend jar and frontend bundle in CI on push to the deployment branch, then upload them to the server and restart the container stack.

#### Scenario: Build artifacts in CI, not on the server
- **WHEN** a commit is pushed to the deployment branch
- **THEN** CI compiles the backend jar and frontend bundle and produces a single deployment archive

#### Scenario: Upload and restart
- **WHEN** the archive is produced
- **THEN** CI copies it to `/opt/cloudwiki` over SSH and runs `deploy.sh`, which extracts it and restarts the stack

#### Scenario: Failed build does not touch the running version
- **WHEN** a CI build step fails
- **THEN** the pipeline stops and the currently running containers are left untouched

### Requirement: Only the reverse proxy is publicly reachable
The system SHALL expose only the Nginx port to the host and keep MySQL, Redis, and the backend reachable by container network service name only.

#### Scenario: Single public port
- **WHEN** the stack starts
- **THEN** only Nginx publishes a host port and the other services publish none

#### Scenario: Internal service discovery
- **WHEN** the backend connects to MySQL or Redis
- **THEN** it addresses them by compose service name on the container network

### Requirement: Secrets stay outside the repository
The system SHALL source all runtime credentials from GitHub Secrets and materialize them into a server-side `.env` that is never committed.

#### Scenario: Secrets are injected at deploy time
- **WHEN** CI assembles the deployment archive
- **THEN** it writes `.env` from secret values only and the file is absent from version control

#### Scenario: Empty optional credentials do not block startup
- **WHEN** an optional object-storage credential is unset
- **THEN** the stack supplies a non-empty placeholder so application startup still succeeds

### Requirement: Deployment verification covers the backend, not just the homepage
The system SHALL consider a deployment successful only when both the static homepage and a real backend endpoint respond.

#### Scenario: Backend liveness is probed
- **WHEN** `deploy.sh` finishes restarting containers
- **THEN** it probes both `/` and a backend API endpoint and succeeds only when the API is not `000`, `502`, or `503`

#### Scenario: Failure surfaces logs
- **WHEN** the backend does not become ready within the probe window
- **THEN** the script prints container status and backend logs and exits non-zero

### Requirement: Rollback preserves the previous jar
The system SHALL keep the previous backend jar so a bad release can be reverted without rebuilding.

#### Scenario: Previous jar is retained
- **WHEN** a deployment starts
- **THEN** the current `app.jar` is copied to `app.jar.bak` before the new one is applied

#### Scenario: Rollback restores the previous jar
- **WHEN** `rollback.sh` is executed
- **THEN** `app.jar.bak` is restored and the backend container is restarted

### Requirement: Cross-platform differences are pinned in configuration
The system SHALL pin MySQL version, character set, time zone, and line endings in configuration so behavior does not depend on the host OS.

#### Scenario: Database character set and time zone are explicit
- **WHEN** the MySQL container starts
- **THEN** it runs with `utf8mb4` server character set and `+08:00` default time zone, and every service sets `TZ=Asia/Shanghai`

#### Scenario: Shell scripts keep LF endings
- **WHEN** deployment scripts are checked out on Windows
- **THEN** `.gitattributes` forces LF so they execute correctly on Linux

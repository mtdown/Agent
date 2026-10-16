# Spec Delta

## ADDED Requirements

### Requirement: Deployed Agent conversations are served by a private Agent service
The deployment SHALL build and run the Agent service on the internal container network, configure the backend to reach it by service name, and provide the same nonempty private credential to both services. The Agent service SHALL NOT publish a host port.

#### Scenario: Agent request on deployed site
- **WHEN** a logged-in user sends an Agent question to the deployed site
- **THEN** the backend forwards it to the internal Agent service without reporting that its credential is unconfigured

#### Scenario: Internal-only Agent endpoint
- **WHEN** the deployment stack starts
- **THEN** the Agent service is reachable by the backend on the private network and has no published host port

#### Scenario: Missing model credential
- **WHEN** the model API credential needed by the Agent service is absent at deployment time
- **THEN** deployment fails with a clear configuration error before replacing the running stack

### Requirement: Agent deployment can be verified and rolled back
The deployment SHALL package the Agent artifact, check that its container starts, and retain the previous artifact for rollback with the backend.

#### Scenario: Agent container fails to start
- **WHEN** the Agent service exits during deployment
- **THEN** the deployment check fails and displays its status and logs

#### Scenario: Rollback to previous application version
- **WHEN** an operator runs the rollback script after an Agent-enabled deployment
- **THEN** the previous backend and Agent artifacts are restored together and their containers are restarted

## MODIFIED Requirements

### Requirement: Secrets stay outside the repository
The system SHALL source externally supplied runtime credentials from GitHub Secrets and materialize them into a server-side `.env` that is never committed. When no Agent internal key is supplied, CI SHALL generate a temporary internal service credential, share it only with the backend and Agent, and never print or commit it.

#### Scenario: Secrets are injected at deploy time
- **WHEN** CI assembles the deployment archive
- **THEN** it writes `.env` from secret values and the file is absent from version control

#### Scenario: Empty optional credentials do not block startup
- **WHEN** an optional object-storage credential is unset
- **THEN** the stack supplies a non-empty placeholder so application startup still succeeds

#### Scenario: Agent internal credential is absent from GitHub Secrets
- **WHEN** CI deploys the Agent without an explicitly configured internal service key
- **THEN** it generates one random key for that deployment, passes the same value to backend and Agent, and does not print or commit it

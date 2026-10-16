# Spec Delta

## ADDED Requirements

### Requirement: API proxy remains available after backend replacement
The deployment SHALL route API requests to the current backend container after Compose replaces the backend, without requiring an operator to restart the reverse proxy manually.

#### Scenario: Backend is replaced during deployment
- **WHEN** a deployment replaces the backend container while the reverse proxy container remains running
- **THEN** the reverse proxy sends subsequent API requests to the new backend container and the deployment health check succeeds once the backend is ready

#### Scenario: Reverse proxy configuration is updated
- **WHEN** a deployment delivers a revised reverse proxy configuration
- **THEN** the running reverse proxy loads the revised configuration before deployment health checks determine success

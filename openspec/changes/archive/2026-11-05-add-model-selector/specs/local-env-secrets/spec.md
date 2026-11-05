# Spec Delta

## Purpose

Consolidates local model API keys into one gitignored project-root file using the same variable names as the cloud deployment, lets the local development UI write a key into that file, and makes the local startup script and the Python evaluation runner resolve configuration through one consistent order. Cloud secrets remain a separate physical store.

## ADDED Requirements

### Requirement: Local startup tunnel opt-in

The development startup script SHALL NOT start a temporary public tunnel by default. The `-Tunnel` switch SHALL explicitly opt in; `-NoTunnel` SHALL remain supported and take precedence when both switches are present. This requirement does not manage any separately installed Windows service.

#### Scenario: Default startup
- **WHEN** the development startup script runs without tunnel switches
- **THEN** it does not start a cloudflared quick tunnel

#### Scenario: Explicit tunnel choice
- **WHEN** the script runs with `-Tunnel` and without `-NoTunnel`
- **THEN** it attempts to start the quick tunnel
- **AND** when both switches are supplied it does not start a tunnel

### Requirement: Single local secrets file with unified variable names

The system SHALL keep model API keys in exactly one local file at the project root, and that file SHALL be ignored by Git. Its variable names SHALL match the names registered in the deployment environment example, so that local and cloud deployments differ only in values, not in names. Configuration that is specific to the evaluation runner — database connection settings, base URLs, model names, and evaluation API keys — SHALL remain in the evaluation environment file. The obsolete local secrets file SHALL be removed after its contents are migrated.

#### Scenario: One key, one name
- **WHEN** a developer inspects the local configuration files
- **THEN** each model API key appears once, under its deployment-registered variable name
- **AND** the evaluation environment file contains only evaluation-specific configuration

#### Scenario: Secrets file is never tracked
- **WHEN** Git evaluates the project-root local secrets file
- **THEN** the ignore rules match that path and the file cannot be committed by a broad add command

#### Scenario: Existing keys survive migration
- **WHEN** the migration reads the obsolete local secrets file and the evaluation environment file
- **THEN** it verifies the duplicated key values are identical before creating the new file
- **AND** no key value is lost by the migration

### Requirement: Development-only local write endpoint

The system SHALL expose a local endpoint during development that accepts a model API key value and upserts it into the project-root local secrets file. The endpoint SHALL be registered only for the development server and SHALL be disabled for a session launched by the startup script with an effective `-Tunnel` option. The front-end SHALL derive the endpoint from a development-only constant that is eliminated from production artifacts. Writing a key SHALL preserve comments, ordering, and all other keys in the file, and SHALL be performed through an atomic replacement.

#### Scenario: Key is written and readable downstream
- **WHEN** a developer submits a valid key through the local development UI
- **THEN** the key is upserted into the project-root local secrets file
- **AND** the evaluation configuration loader resolves that same value on its next invocation

#### Scenario: Tunnel session has no write endpoint
- **WHEN** the startup script launches development mode with `-Tunnel` and without `-NoTunnel`
- **THEN** the local write endpoint is not registered and the UI disables local key entry

#### Scenario: Production artifact has no endpoint
- **WHEN** a standard production build is produced
- **THEN** its assets contain no local endpoint string and no callable write entry point

#### Scenario: Other keys and comments survive a write
- **WHEN** a key is written into an existing local secrets file
- **THEN** all comments and all other key-value lines remain intact
- **AND** the written key appears exactly once with no duplicate line

### Requirement: Validation of local writes

The local write endpoint SHALL NOT check connection source, Origin, or a designated custom header. It SHALL accept only key names present in the deployment environment example's model API key allowlist, and SHALL reject `VITE_`-prefixed names, evaluation-specific names, and any unregistered name. It SHALL reject empty values, values containing line breaks, and values above the documented length limit. Status responses SHALL contain only boolean configuration state and SHALL NOT echo the key value.

#### Scenario: Local development request without Origin or custom header
- **WHEN** the endpoint is enabled and a valid request has no Origin or designated custom header
- **THEN** the request is processed according to key and value validation

#### Scenario: Disallowed names and malformed values are refused
- **WHEN** a request contains an unregistered key name, a `VITE_` name, a line break, an empty value, or an overlong value
- **THEN** the endpoint returns a client error and the secrets file is unchanged

#### Scenario: No plaintext in responses
- **WHEN** a read or write request succeeds
- **THEN** the response contains only the allowlisted names and their configured booleans, never a key value

### Requirement: Consistent local configuration loading

The development startup script SHALL read the project-root local secrets file and inject only model-related `RAG_*` and `DEEPSEEK_*` variables into the backend and agent process environments, skipping empty values so that a system-level variable of the same name is not overwritten. The Python evaluation configuration loader SHALL resolve values in the order of process environment, then the project-root local secrets file, then the evaluation environment file. It SHALL populate the historical `LLM_API_KEY` and `EMBEDDING_API_KEY` names from the unified DashScope key so existing scripts continue to work unchanged. Evaluation scripts that previously parsed the evaluation environment file themselves SHALL use the shared loader instead.

#### Scenario: Startup injection respects system variables
- **WHEN** the local secrets file omits a model key that is present in the system user environment
- **THEN** the startup script does not inject an empty override and the system value remains effective

#### Scenario: Startup injection is scoped
- **WHEN** the local secrets file contains any non-model variable
- **THEN** the startup script does not inject it into the backend process environment

#### Scenario: Historical scripts keep working
- **WHEN** an existing evaluation script calls the shared configuration loader
- **THEN** it receives both the unified `RAG_*` names and the historical `LLM_API_KEY` / `EMBEDDING_API_KEY` aliases
- **AND** all evaluation-specific settings are still present

#### Scenario: Process environment takes precedence
- **WHEN** a model key is set in the process environment and also present in the local secrets file
- **THEN** the loader resolves the process environment value

### Requirement: Cloud isolation and no plaintext leakage

Cloud deployment secrets SHALL continue to originate from CI secrets and the server-side environment file; the local secrets file SHALL NOT be uploaded, referenced by deployment configuration, or required by a cloud build. No API key value SHALL be written into browser storage, a committed file, a build artifact, or an evaluation result. The UI SHALL state that writing a key locally requires restarting the backend and agent, and that cloud builds do not provide the local write capability.

#### Scenario: Cloud build is independent of local secrets
- **WHEN** a cloud build runs without any project-root local secrets file
- **THEN** the build succeeds and the deployed configuration is unchanged

#### Scenario: Restart notice
- **WHEN** a developer saves a key through the local development UI
- **THEN** the UI states that the backend and agent must be restarted before the new value takes effect

#### Scenario: No value in browser or artifact
- **WHEN** a key is submitted from the management dialog
- **THEN** the value appears only in the gitignored local secrets file
- **AND** it is absent from browser storage, response bodies, and production assets

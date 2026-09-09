# project-hygiene Specification

## Purpose
Defines that the project's test suite runs without external services, that sharding code is either enabled or honestly retired rather than left as dead configuration, and that `README.md` does not claim a known-unfinished feature is complete.

## Requirements

### Requirement: Backend tests run without an external Redis

The system SHALL allow `mvn test` to execute the backend suite without requiring a reachable external Redis instance.

#### Scenario: Full suite on a clean machine

- **GIVEN** a machine with no Redis running
- **WHEN** `mvn test` is executed
- **THEN** the suite SHALL complete
- **AND** failures, if any, SHALL NOT be caused by Redis authentication

#### Scenario: Session-dependent tests still meaningful

- **GIVEN** tests that exercise login state
- **WHEN** they run under the test configuration
- **THEN** they SHALL continue to assert real behavior rather than being skipped

### Requirement: Sharding is either enabled or retired

The system SHALL NOT ship sharding classes whose configuration is entirely commented out with no note explaining their status.

#### Scenario: Retired path chosen

- **GIVEN** the team decides not to enable sharding
- **WHEN** the change is applied
- **THEN** the configuration SHALL carry an explicit comment recording that sharding is not enabled and why
- **OR** the sharding classes SHALL be removed

#### Scenario: Enabled path chosen

- **GIVEN** the team decides to enable sharding
- **WHEN** the change is applied
- **THEN** the configuration SHALL be active
- **AND** picture list and upload SHALL be verified against the sharded tables

### Requirement: README does not claim unfinished work is done

The system SHALL keep `README.md` free of statements asserting that a known-incomplete feature works, without either completing the feature or recording its actual status.

#### Scenario: Permission note

- **GIVEN** `README.md` states that permission checks are incomplete
- **WHEN** the hygiene phase is applied
- **THEN** the statement SHALL be replaced with the current factual status
- **AND** if the gap remains, it SHALL be described as a known limitation with its scope

### Requirement: Editor bundle is not loaded on first paint

The system SHALL NOT include the Markdown editor in the main bundle so that routes which never render it do not pay for its download.

#### Scenario: Wiki list visited without opening the editor

- **GIVEN** a user opens the wiki list
- **WHEN** the initial bundle is fetched
- **THEN** the editor code SHALL NOT be part of it

#### Scenario: Editor opened

- **GIVEN** a user navigates to create or edit a document
- **WHEN** the editor chunk is requested
- **THEN** the editor SHALL load and function normally

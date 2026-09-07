## Purpose

This capability standardizes how repository work starts, uploads, and reaches merge review while keeping local-only files out of shared Git history.

## ADDED Requirements

### Requirement: Remote-first task branch creation
The system SHALL provide a standard command that creates a remote task branch from the latest `origin/main` before creating the local tracking branch.

#### Scenario: Create remote feature branch
- **WHEN** a user starts a feature task with a valid kebab-case name
- **THEN** the command creates `origin/feature/<name>开发` from `origin/main` and checks out a local branch tracking it

#### Scenario: Create remote fix branch
- **WHEN** a user starts a fix task with a valid kebab-case name
- **THEN** the command creates `origin/fix/<name>修复` from `origin/main` and checks out a local branch tracking it

### Requirement: Tracked branch upload
The system SHALL provide a standard command that uploads only the current tracked non-main task branch.

#### Scenario: Upload task branch
- **WHEN** a user runs the upload command with a commit message on a tracked task branch
- **THEN** the command displays pending files, commits changed files when present, and pushes to the upstream branch

#### Scenario: Reject untracked or main upload
- **WHEN** a user runs the upload command on `main`, `master`, a detached checkout, or a branch without upstream tracking
- **THEN** the command refuses to upload and explains the branch requirement

### Requirement: Local-only files excluded from shared history
The system SHALL exclude personal IDE state, local diagnostics, temporary logs, tool caches, and one-off debug artifacts from normal tracking.

#### Scenario: Local artifacts are ignored
- **WHEN** local diagnostic files, runtime logs, or assistant tool caches exist
- **THEN** Git status does not list them as upload candidates

#### Scenario: Previously tracked local files stop being tracked
- **WHEN** local-only files were previously tracked
- **THEN** they are removed from Git tracking while remaining available on the local machine

### Requirement: Merge remains remote review
The system SHALL end the development upload flow at the pushed remote task branch and leave merge into remote `main` to the project owner through a merge request or pull request.

#### Scenario: Upload prompts review
- **WHEN** a branch upload completes
- **THEN** the command prints merge request or pull request guidance and does not merge into local or remote `main`

### Requirement: Main preview is manual acceptance only
The system SHALL provide dedicated main preview commands for the project owner to manually inspect the latest remote `main` without changing the current task branch.

#### Scenario: Start latest main for acceptance
- **WHEN** the project owner runs the main preview start command
- **THEN** the command synchronizes the main worktree with `origin/main` and starts services from the main worktree

#### Scenario: Agent development still uses task branch scripts
- **WHEN** an AI coding assistant tests work during development
- **THEN** it uses `stop-dev.ps1` and `start-dev.ps1` from the current task branch instead of the main preview scripts

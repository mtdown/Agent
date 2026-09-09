## Why

The project needs a repeatable upload workflow that can be used after cloning from GitHub and does not depend on personal IDE state, temporary logs, or ad hoc Git commands. Work should start from a remote branch, continue locally on the tracked branch, push back to remote, and then enter manual merge review.

## What Changes

- Add a remote-first task branch workflow: create the remote branch from `origin/main`, then check it out locally with upstream tracking.
- Add a standard upload command that commits and pushes the current tracked task branch without merging.
- Track shared development scripts `start-dev.ps1` and `stop-dev.ps1` for consistency with `AGENTS.md`.
- Stop tracking local-only IDE state and generated directory listing files while keeping local copies intact.
- Ignore local diagnostics, logs, caches, and one-off debug artifacts.
- Update `AGENTS.md` to require the standard branch/upload scripts for future work.

## Capabilities

### New Capabilities

- `git-upload-workflow`: Defines branch creation, upload, ignore, and merge-request review behavior.

### Modified Capabilities

- None.

## Impact

- Adds root scripts for remote-first branch creation and branch upload.
- Updates `.gitignore`.
- Updates `AGENTS.md`.
- Removes Git tracking for local-only files without deleting local copies.
- Adds OpenSpec artifacts and a focused verification script.

## Context

See `proposal.md` for motivation. The repository already requires branch-based work and manual review before merging to `main`; the missing rule is a reusable command path that creates remote branches first and then uploads only the tracked task branch.

## Goals / Non-Goals

**Goals:**

- Make task branches remote-first: `origin/main` -> remote task branch -> local tracking branch.
- Keep `main` protected from direct upload and merge.
- Keep required collaboration scripts tracked.
- Exclude local-only IDE state, diagnostics, logs, caches, and one-off debug files from normal upload.
- Provide owner-only main preview scripts for manual acceptance without changing the current task branch.

**Non-Goals:**

- Do not automatically create or merge GitHub pull requests.
- Do not delete local IDE files from disk.
- Do not alter application runtime behavior.

## Decisions

### Remote branch is created before local checkout

`start-task.ps1` will fetch `origin`, create `refs/heads/<task-branch>` on remote from `origin/main`, fetch that branch, and then create a local tracking branch. This matches the desired project flow and prevents local-only task branches from becoming the source of truth.

### Upload requires a tracked non-main branch

`upload.ps1` will refuse `main` / `master`, require an upstream branch, display pending changes, commit with an explicit message when changes exist, push to upstream, and print merge request guidance. It will not merge.

### Remove tracking for local-only files

Files such as `.idea/workspace.xml` and `cloud/project.txt` do not support build, run, test, or collaboration. They will be removed from the Git index with `git rm --cached`, leaving local files untouched.

### Keep main preview separate from development testing

`start-main-dev.ps1` and `stop-main-dev.ps1` will operate on a dedicated main worktree and delegate to that worktree's `stop-dev.ps1` / `start-dev.ps1`. These scripts are for project owner acceptance only. AI development and test runs must continue to use the current task branch's normal service scripts.

## Risks / Trade-offs

- Remote branch creation can fail if the branch already exists -> script will stop and ask the user to choose another branch name or check out the existing remote branch.
- Upload script cannot infer whether every changed file belongs to the task -> it displays status before committing so the user can stop before upload.
- Existing clones may keep local IDE files on disk -> `.gitignore` prevents them from returning as new upload candidates after tracking is removed.

## Migration Plan

1. Add OpenSpec artifacts.
2. Add verification script.
3. Update `.gitignore`.
4. Add `start-task.ps1` and `upload.ps1`.
5. Remove Git tracking for local-only tracked files while preserving local copies.
6. Add main preview scripts for owner manual acceptance.
7. Update `AGENTS.md`.
8. Run focused verification and OpenSpec validation.

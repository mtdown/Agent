## 1. Planning

- [x] 1.1 Create proposal and verify it captures the remote-first upload workflow
- [x] 1.2 Create spec and verify scenarios cover remote branch creation, upload, ignore rules, and merge review
- [x] 1.3 Create design and verify tracked/untracked file decisions are explicit

## 2. Verification

- [x] 2.1 Add workflow verification script and verify it fails before implementation
- [x] 2.2 Verify branch script creates remote branch before local tracking checkout
- [x] 2.3 Verify upload script requires tracked non-main branch and never merges
- [x] 2.4 Verify local-only ignore rules are effective

## 3. Implementation

- [x] 3.1 Update `.gitignore` and verify local diagnostics, logs, caches, and IDE state are ignored
- [x] 3.2 Add `start-task.ps1` and verify remote-first branch creation dry run
- [x] 3.3 Add `upload.ps1` and verify tracked branch upload dry run
- [x] 3.4 Stop tracking local-only tracked files and verify local files are preserved
- [x] 3.5 Update `AGENTS.md` and verify script usage is required for future branch/upload work
- [x] 3.6 Add main preview scripts and verify they are documented as manual acceptance only

## 4. Validation

- [x] 4.1 Run focused workflow verification and record result
- [x] 4.2 Run `openspec validate standardize-upload-workflow --strict` and record result
- [x] 4.3 Report final changed files, ignored files, stash state, and remote branch state

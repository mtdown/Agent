---
name: openspec-archive-change-local
description: Archive a manager-confirmed completed OpenSpec change after independent acceptance and spec synchronization in this project's coordinated workflow.
---

# Archive a completed change

Use the change and task branch confirmed by the manager. Do not select another change or store independently. Follow the project role rules; archiving is performed only after all stages and independent acceptance are complete. If identity, ownership, store, authorization, or results are unclear, stop and report to the manager.

If a store is selected, discover its ID with `openspec store list --json` and keep `--store "<id>"` on every applicable OpenSpec command throughout. Otherwise use the nearest local OpenSpec root. Resolve paths from the selected store's status, not from a hardcoded repository path.

## Check prerequisites

1. Confirm the change, selected store, task branch, and authority to sync and archive. An explicitly authorized complete workflow needs no repeated confirmation for ordinary operations. Stop at a user-specified pause, when the action exceeds authorization, or when a decision is required; do not infer consent.
2. Run `openspec status --change "<name>" --json` with the selected store flag. Read `schemaName`, `planningHome`, `changeRoot`, `artifactPaths`, and artifact statuses. All required artifacts must be `done` or explicitly `skipped`; otherwise stop. Read the actual tasks file and confirm every task is complete. A missing or incomplete tasks file, incomplete stage, or missing independent PASS evidence blocks archive.
3. Validate the change with `openspec validate "<name>" --strict` using the same store. Stop on failure. If available, `openspec instructions archive --change "<name>" --json` may provide advisory context; a failed or unsupported advisory lookup does not override the required checks. Apply relevant context only when consistent with confirmed paths, authorization and checks.

## Sync and verify before moving

Use only `artifactPaths.specs.existingOutputPaths` from status as the source of delta specs. If there are no entries, record that no delta specs exist. If entries exist, compare every delta with the corresponding main spec under the store-aware `<planningHome.root>/openspec/specs/<capability-path>/spec.md`, preserving the full capability path. Run `openspec-sync-specs-local` for **all** entries with the same confirmed change and store, and wait synchronously until it finishes; never move the source while sync is in flight. The local sync obtains valid specs instructions before writes and validates its output.

After sync, independently compare every delta against its main spec: added requirements must be present; modified descriptions and scenarios must be reflected while unrelated scenarios remain; removed requirements must be absent (including an empty retired capability, subject to any explicitly reported retained spec); renamed requirements must exist under the new name and not the old. Validate the resulting main specs and change. If sync fails, validation fails, any comparison is inconsistent, or any delta remains unapplied, report the evidence and stop without archiving. Do not treat partial sync as success.

## Archive and verify

Use `<planningHome.changesDir>/archive` as the destination. The target is the change name if it already begins with `YYYY-MM-DD-`; otherwise prefix the current date once. Check the exact destination before moving. If it exists, stop without overwriting or renaming it. Create the archive directory if necessary and move the entire `changeRoot` to the target, preserving all files including `.openspec.yaml` metadata.

Verify the original change path is absent, the target exists with its metadata and expected artifacts, and OpenSpec status/list reflects the archived state at the selected root. Report the change, schema, store, destination, validation and sync results, plus any verification failure. If the move or post-move verification fails, report the actual state and stop; do not claim success or silently overwrite anything. The manager handles subsequent stage upload under the project rules.

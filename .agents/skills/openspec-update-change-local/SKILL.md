---
name: openspec-update-change-local
description: Use when revising an existing OpenSpec change's planning in this project's coordinated workflow.
---

# Update a change locally

Revise only planning artifacts of the existing change confirmed by the manager. Do not create a change or modify implementation, tests, scripts, or business code. Follow the manager's task and authorization boundaries.

## Identify the change and its store

Use the change ID and branch confirmed by the manager. If the change is missing, its ownership is unclear, or the requested scope is ambiguous, stop and report to the manager. Do not select or create another change yourself.

If the change belongs to a registered store, run `openspec store list --json` to identify it and retain `--store <id>` on applicable commands throughout the workflow. Otherwise use the nearest local `openspec/` root. Inspect the confirmed change:

```bash
openspec status --change "<name>" --json
```

Append `--store <id>` when applicable. Use the returned schema, `planningHome`, `changeRoot`, `actionContext` and `artifactPaths`; respect their scope and do not assume a particular schema or repository path. For a substantial revision, obtain its schema instructions with `openspec instructions "<artifact-id>" --change "<name>" --json`, with the same store selection.

## Revise and reconcile

1. Read the relevant existing artifacts and the change's other existing artifacts. Edit only concrete files in `artifactPaths.<id>.existingOutputPaths`; do not write to a glob `resolvedOutputPath`, create missing artifacts, or add files under a glob. Report missing artifacts to the manager for separate planning.
2. A test failure may be recorded as task status, repair work and retest evidence in an existing task artifact or associated report as authorized. Keep the original fixed acceptance and expected behavior unchanged; never lower them to make a failure pass.
3. If requirements, external behavior, or design need to change, report the need and evidence to the manager. Only after the relevant decision, update affected existing planning artifacts within the authorized scope and reconcile their acceptance and tasks. Do not infer approval from a failed test.
4. Check all existing artifacts for consistency in both directions, including scope, design, observable scenarios, acceptance and tasks. If they are already consistent, make no unnecessary edits. If reconciliation requires an unavailable artifact, exceeds authorization, or remains ambiguous, stop and report the gap rather than guessing.

Explicit authorization for the full workflow covers routine revisions within its scope; do not repeatedly request confirmation for each artifact. Honor a user-specified stop or review point. Stop for a decision when the scope exceeds authorization or the requirements are ambiguous, and follow the manager's escalation rules.

Report the change ID, revised files, consistency checks, missing artifacts, unresolved decisions, and next handoff to the manager. Updating planning does not implement the resulting work or establish test PASS.

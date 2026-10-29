---
name: openspec-apply-change-local
description: Use when a developer is assigned one OpenSpec atomic task in this project's coordinated workflow.
license: MIT
metadata:
  author: openspec-local
  source: openspec-apply-change 1.11.0
---

# Apply one assigned OpenSpec task

Use the manager's task card and [AGENTS_rules.md](../../../AGENTS_rules.md). The card must identify the change, one atomic task, allowed files, fixed acceptance and verification. If the identity, scope, authorization or acceptance is missing or ambiguous, stop and report to the manager. Do not select or implement another task.

## Resolve the OpenSpec context

If the card names a standalone store or the change lives in one, run `openspec store list --json` to identify it and retain `--store <id>` on every applicable OpenSpec command for this change. Otherwise use the nearest local `openspec/` root. Do not guess between stores or changes; stop and report if their identity or ownership cannot be determined.

Run the following for the assigned change, appending the selected store flag when applicable:

```bash
openspec status --change "<name>" --json
openspec instructions apply --change "<name>" --json
```

Inspect the schema, `planningHome`, `changeRoot`, `actionContext`, edit constraints, task list, progress and state. Read every path in `contextFiles`, including the schema's task artifact. Treat CLI `context` as required project input and consider each `operationGuidance` entry as optional advice. Neither is evidence of completion or a reason to bypass CLI state. If either conflicts with the controlling task card, explicit user choice, CLI state or constraints, report the conflict; do not silently disregard it or copy runtime guidance into files.

If the CLI reports `blocked`, required artifacts are absent, context cannot be read, the assigned task cannot be identified or the task conflicts with the planning artifacts, stop and report the evidence to the manager. Do not create missing artifacts or bypass a blocker. If `all_done` is reported, report that state rather than implementing an unassigned task. Confirm the assigned task is pending and within the allowed edit roots before proceeding.

## Implement and hand off

Implement only the assigned atomic task within the task card's scope and authorization. Keep the fixed acceptance unchanged, run its specified self-checks, and keep an honest record of ordinary resolved errors for independent testing. If work cannot continue, or the requirement, external behavior or design must change, stop and report to the manager. Do not silently expand scope or change the contract.

On completion, give the manager the development receipt required by `AGENTS_rules.md`, including actual files, contract impact, assumptions, self-check commands and results, records, risks and workspace state. Mark the receipt `READY_FOR_TEST` only when implementation is ready for independent testing; otherwise report `PARTIAL` or `BLOCKED` as appropriate. Stop writing the tested files after handoff. Self-checks do not establish independent acceptance PASS. Do not mark any `tasks.md` checkbox; the manager owns task status after independent acceptance.

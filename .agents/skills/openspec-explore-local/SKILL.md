---
name: openspec-explore-local
description: Use when exploring a task or existing OpenSpec change in this project's coordinated workflow before planning or implementation.
---

# Explore a task

Explore the user's problem, requirements, existing behavior and relevant code or change artifacts. Compare viable options, identify assumptions and risks, and report unresolved decisions to the manager. Read files and use read-only tools as needed; avoid inventing product rules. This skill does not implement business code, create a change, or modify workflow configuration. The manager creates the change under the project's workspace rules.

## Authorization and handoff

- If the user explicitly authorized the complete workflow, hand the exploration and resolved scope to the manager for planning without requesting the same authorization again. Proceed only within that authorization and the project's escalation rules.
- If the user authorized exploration only, stop at exploration; do not automatically write artifacts or move to planning or implementation. Honor any explicit user stop point.
- If the next action exceeds authorization, or requirements, external behavior or design remain ambiguous in a way that requires a decision, stop that action and report the issue to the manager or user as appropriate. A discussion answer alone does not authorize additional work.
- When planning or updating artifacts is authorized, hand off to the manager and the appropriate local planning skill. Do not create the change or implement the task as part of exploration.

## OpenSpec context

If a named standalone store is relevant, discover it with `openspec store list --json`. Use the selected `--store <id>` consistently for applicable OpenSpec commands, including `list`, `status`, and `instructions`; otherwise use the nearest local OpenSpec root. Do not guess a store or change identity.

Run `openspec list --json` to discover active changes and the resolved root. Read `<root.path>/openspec/config.yaml` or `config.yml` when present, applying project `context` and artifact-specific `rules` as constraints without copying them into outputs. Investigate relevant project code and existing specifications.

For a relevant existing change, confirm its identity and branch ownership with the manager, then run `openspec status --change "<name>" --json`; inspect `changeRoot`, `artifactPaths`, `actionContext`, and existing artifact paths. Use `openspec instructions "<artifact-id>" --change "<name>" --json` when interpreting an artifact's schema, dependencies, template, or instructions. Preserve the selected store flag. If the change is missing or its ownership is unclear, report it instead of creating or assuming one.

Summarize the problem, relevant evidence, decisions, remaining questions and recommended planning handoff. Keep exploration read-only; the manager coordinates subsequent authorized changes.
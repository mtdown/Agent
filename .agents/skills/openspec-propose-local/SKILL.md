---
name: openspec-propose-local
description: Use when planning an existing, manager-created OpenSpec change for this project's coordinated development workflow.
---

# OpenSpec Propose Local

Plan the change assigned by the manager. This skill produces planning artifacts only; hand off to the manager for development rather than implementing code. The manager confirms the change belongs to the current task branch and provides its name. If the change is absent, its ownership is unclear, or the branch does not match, stop and report the issue to the manager. Do not create a change.

## Scope and context

Understand the requested outcome and fixed acceptance before writing. Report material ambiguity affecting scope, observable behavior, compatibility or acceptance to the manager; record reasonable minor assumptions. Use the change's configured schema; an alternative must already be explicitly selected for that change. Read the current artifacts and project constraints before editing; do not overwrite existing work blindly.

If a registered store is named or contains the work, run `openspec store list --json`, identify its id, and keep `--store "<id>"` on all applicable OpenSpec commands throughout this workflow. Otherwise use the nearest configured OpenSpec root. Resolve paths from CLI output, not assumptions about repository paths.

## Build artifacts

1. Run `openspec status --change "<name>" --json`. Read `planningHome`, `changeRoot`, `artifactPaths`, `actionContext`, `applyRequires` and each artifact's `requires` and `status`.
2. Compute the required set as `applyRequires` and its transitive dependencies. Process ready artifacts in dependency order. Existing artifacts need inspection, not blind replacement; read completed dependencies from disk again before each artifact.
3. For each missing artifact, run `openspec instructions <artifact-id> --change "<name>" --json`. Follow its `instruction` and `template`, its `resolvedOutputPath`, and its `dependencies`. Treat `context` and `rules` as constraints, not content to copy into the output. If creation is delegated to a specific skill or command, use it and verify the resulting file exists. If the output path is a glob, use the instruction to choose the concrete path.
4. Respect `skipped` status and `skipped`/`warning` instructions: do not create skipped artifacts. Skip an otherwise required artifact only when its own instruction marks it conditional; record the reason. A conditional dependency skipped this way does not prevent producing its dependent artifact. Do not infer that a required spec is optional.
5. Verify each written artifact exists, then rerun status and continue until the required transitive set is complete or legitimately skipped. Status alone checks existence, so inspect dependency edges even for artifacts marked done. Preserve capability paths and use the schema's structure.
6. Run `openspec status --change "<name>"` and `openspec validate "<name>" --strict` with the selected store if applicable. Inspect the artifacts for consistency, fixed acceptance, and actionable tasks. On failure or unresolved ambiguity stop and report evidence; do not declare readiness.

## Handoff and authorization

Report the change name and resolved location, artifacts created or retained, conditional skips and reasons, validation results, and any unresolved issue to the manager. If the user has authorized the complete development task and planning is ready, hand off for the manager to dispatch the specified atomic task without requesting the same authorization again. If the request is planning only or the user specified a stop point, stop at that boundary. Scope changes or unresolved decisions still require escalation under project rules. This planning role never starts implementation itself.

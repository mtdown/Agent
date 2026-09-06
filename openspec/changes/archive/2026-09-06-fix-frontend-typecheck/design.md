## Context

See `proposal.md` for motivation. The current frontend can pass `npm run build-only`, but `npm run build` still fails in `vue-tsc --build` with 52 legacy TypeScript errors after the wiki incremental errors were removed.

The remaining failures are concentrated in legacy picture, space, and admin pages. They are mostly caused by weak API response typing, inconsistent entity-id handling, under-typed Vue refs, implicit callback parameter types, and missing nullable checks.

## Goals / Non-Goals

**Goals:**

- Make `npm run build` pass without hiding errors behind broad `any`.
- Align frontend types with backend DTO/VO names and generated OpenAPI typings.
- Preserve Snowflake id precision by keeping ids compatible with `string | number`.
- Keep fixes local and behavioral-neutral.
- Leave a clear IssueLog trail from 52 legacy errors to zero.

**Non-Goals:**

- No UI redesign.
- No picture module business rewrite.
- No backend behavior change.
- No rollback of the wiki-first or fix-wiki-defects commits.
- No credentials cleanup in this change.

## Decisions

### Decision 1: Contract priority

Use this order when resolving naming and type conflicts:

```text
Backend DTO/VO/Controller
  -> generated OpenAPI typings.d.ts
  -> frontend API function signatures
  -> page props, route/query adapters, and state
  -> UI framework callback types
```

Rationale: backend contracts define what the server actually accepts and returns; generated typings are the frontend mirror of that contract. Page-local types should adapt to those contracts instead of inventing narrower assumptions.

Alternative considered: patch each component in isolation. Rejected because it would likely reintroduce drift the next time OpenAPI is regenerated.

### Decision 2: Entity ids stay precision-safe

Treat backend Long/Snowflake ids as a frontend entity-id contract compatible with `string | number`. Do not use `Number(...)` as a general route/query normalization strategy.

Rationale: JavaScript numbers cannot safely represent every backend Long id. Type fixes must not make the app compile by reintroducing precision loss.

Alternative considered: convert all ids to `number` for simplicity. Rejected because it can corrupt large ids.

### Decision 3: Prefer typed helpers over scattered assertions

If multiple components repeat the same response or id normalization pattern, introduce a small typed helper. If an issue appears in one place only, use a local type annotation.

Rationale: a helper is useful only when it prevents repeated mistakes; otherwise local annotations keep the change smaller.

Alternative considered: add one broad global `any` response type. Rejected because it would weaken the build gate and hide real defects.

### Decision 4: Preserve current naming unless the name is wrong

Keep existing names such as `fetchData`, `dataList`, `searchParams`, `loading`, and `total` where they match local project style. Rename only when a variable name hides a different domain concept.

Rationale: this is a type-safety remediation, not a readability rewrite.

Alternative considered: rename broadly while touching files. Rejected because it increases review risk without helping the build gate.

### Decision 5: Verify by category

Fix one error category at a time and run `npm run build` after each category to record the error-count movement.

Rationale: the project already had confusion between incremental and legacy type failures. Category-level verification creates a clear audit trail.

Alternative considered: fix everything first, run one final build. Rejected because it would make future triage harder if errors remain.

## Risks / Trade-offs

- **Risk: broad type assertions hide real nullable bugs** -> Prefer guards for optional response fields and only use narrow assertions where a generated type is known to be incomplete.
- **Risk: fixing ids by converting to numbers reopens precision loss** -> Keep the entity-id contract explicit and verify no new `Number(route...)` pattern is introduced.
- **Risk: OpenAPI regeneration reintroduces request typing drift** -> Prefer generator config or stable wrapper typing when the same generated pattern recurs.
- **Risk: behavior changes while editing old pages** -> Keep edits type-only and run picture/space regression paths after build passes.

## Migration Plan

1. Create a repair branch from current `main`, expected name `fix/frontend-typecheck修复`.
2. Capture the baseline `npm run build` output and error count.
3. Apply fixes category by category.
4. After each category, rerun `npm run build` and record count movement.
5. Final verification requires `npm run build`, `npm run build-only`, and picture/space workflow regression.
6. Update `IssueLog.xlsx` with the completed remediation result.

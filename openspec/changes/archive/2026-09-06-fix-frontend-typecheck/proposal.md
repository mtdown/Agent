## Why

`npm run build` still fails after the wiki-related incremental TypeScript errors were fixed: `vue-tsc --build` now reports 52 remaining frontend type errors concentrated in legacy picture, space, and admin pages. These are not new wiki defects, but they block the project from using the full frontend build as a reliable quality gate.

This change creates a focused boundary for fixing the legacy type debt without changing business behavior, rewriting UI, or weakening types to hide errors.

## What Changes

- Establish a project-level frontend type contract for API responses, entity ids, route/query parameters, Vue state, UI callbacks, and nullable response fields.
- Fix the remaining 52 legacy TypeScript errors reported by `npm run build`.
- Keep the previously submitted wiki incremental fixes intact and avoid reverting OpenAPI int64 precision safeguards.
- Preserve existing UI behavior and naming style unless a local name is demonstrably misleading.
- Record the before/after error count and verification result in `IssueLog.xlsx`.

## Capabilities

### New Capabilities

- `frontend-typecheck`: Defines that the frontend full build must pass `vue-tsc --build` and Vite build, with types aligned to backend DTO/VO contracts and generated OpenAPI typings.

### Modified Capabilities

- None.

## Impact

- **Frontend**: expected edits in legacy picture, space, and admin Vue components; request typing may be clarified in `src/request.ts` or local call sites if needed.
- **Type contracts**: entity ids must remain safe for backend Long/Snowflake values and must not be blindly coerced to `number`.
- **Build pipeline**: `npm run build` becomes the acceptance gate, not only `npm run build-only`.
- **Backend**: no backend behavior change is intended.
- **Documentation / process**: `IssueLog.xlsx` must clearly show that the 52 remaining errors were legacy debt and how they were reduced to zero.

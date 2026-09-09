## Why

The frontend can start while the backend is unavailable, but the first route navigation currently awaits the login-status request without error handling. When that request throws, Vue Router aborts startup and the main content area renders only an empty router-view comment.

## What Changes

- Keep public/non-admin route navigation running when the initial login-status request fails.
- Preserve admin-route protection: admin pages still redirect to login when the user is missing or not an admin.
- Register frontend plugins before mounting the Vue application.
- Add a focused regression test for the empty main-content failure mode.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `wiki-navigation-flow`: Wiki/front-page navigation must render allowed page content even when the login-status request cannot reach the backend.

## Impact

- Affected frontend code:
  - `cloud_front/src/stores/useLoginUserStore.ts`
  - `cloud_front/src/access.ts`
  - `cloud_front/src/main.ts`
- Affected tests:
  - `cloud_front` route-startup regression coverage.
- No backend API, database, or dependency changes.

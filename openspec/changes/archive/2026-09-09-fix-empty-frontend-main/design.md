## Context

The frontend imports `access.ts` during application startup. Its global router guard calls `fetchLoginUser()` on the first navigation so permissions are known before admin-route checks run. When the backend is unavailable, the Axios rejection escapes the guard and Vue Router aborts startup before rendering the route component.

## Goals / Non-Goals

**Goals:**

- Treat a failed initial login-status request as an unauthenticated state for routing purposes.
- Keep non-admin routes renderable even when the backend is temporarily unavailable.
- Keep admin routes protected unless an administrator identity is confirmed.
- Ensure Vue plugins are registered before the app is mounted.

**Non-Goals:**

- Change backend login APIs or response codes.
- Add offline data mode for Wiki content requests.
- Change the visual layout or navigation menu structure.

## Decisions

- Catch login-status request failures at the store boundary and reset the login user to the existing unauthenticated shape. This keeps callers simple and makes "could not confirm login" equivalent to "not logged in" for startup navigation.
- Keep the router guard's admin-route check based on the stored `userRole`. If the backend request fails, no admin role is present and admin routes continue to redirect to login.
- Add a route-startup regression test around the store fetch behavior, because the failure mode is caused by an uncaught startup request rather than by Wiki layout rendering.
- Move `VueCropper` registration before `app.mount('#app')` so all plugins are installed during normal Vue initialization.

Alternatives considered:

- Catch only inside `access.ts`: this would protect the current guard but still leave `fetchLoginUser()` unsafe for future startup callers.
- Ignore the login-status request when backend is unavailable by skipping it entirely: this would hide useful login state when the backend is healthy.

## Risks / Trade-offs

- Backend outage still prevents data-backed Wiki lists from loading after the shell renders -> page-level API handlers will surface their existing error messages.
- A user with a valid session may appear unauthenticated during a transient login-status failure -> the next explicit login-state fetch can restore the session once the backend is reachable.

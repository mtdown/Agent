## 1. Regression Coverage

- [x] 1.1 Add a frontend regression test that mocks the login-status API rejection and verify it fails against the current `fetchLoginUser()` behavior.

## 2. Frontend Fix

- [x] 2.1 Update login-user fetching so request failures leave the user unauthenticated instead of throwing through route startup, and verify the regression test passes.
- [x] 2.2 Register Vue plugins before mounting the frontend app and verify the application entry keeps the expected initialization order.

## 3. Verification And Records

- [x] 3.1 Run frontend type checking/build verification and record the results.
- [x] 3.2 Restart local services through the project scripts and verify `/` renders non-empty main content when the backend is unavailable or login-status fetching fails.
- [x] 3.3 Record the issue and outcome in `IssueLog.xlsx`.

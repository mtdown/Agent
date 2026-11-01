## ADDED Requirements

### Requirement: Proxy regression reports startup failures and preserves its verification
The deployment pipeline SHALL run the proxy regression with a bounded startup readiness check and preserve actionable diagnostics when a test container cannot start or its configuration is invalid, without treating failure as success.

#### Scenario: Proxy startup is delayed
- **WHEN** the regression proxy has not yet become ready
- **THEN** the test waits within a bounded interval before replacing its configuration and proceeds only after it is ready

#### Scenario: Proxy exits before readiness
- **WHEN** the proxy exits or fails to become ready in time
- **THEN** the test fails and reports available test-container status and logs before cleaning up its own resources

#### Scenario: Delivered configuration is invalid
- **WHEN** the shipped proxy configuration fails validation
- **THEN** the test fails with diagnostics and does not report a successful regression

#### Scenario: Backend replacement is verified
- **WHEN** the regression backend is replaced with a new address
- **THEN** the test verifies the proxy remains running and routes the unchanged API path to the replacement without a restart

#### Scenario: Test temporary directory is retained
- **WHEN** the regression creates a temporary directory
- **THEN** it reports the exact directory and a targeted cleanup command for the administrator and leaves that directory in place

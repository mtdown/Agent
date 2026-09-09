## ADDED Requirements

### Requirement: Public navigation tolerates login-status failure
The system SHALL render allowed non-admin page content when the initial login-status check fails because the backend is unavailable or the request errors.

#### Scenario: Backend is unavailable during first page load
- **WHEN** a user opens the application and the login-status request fails before the first route is rendered
- **THEN** the top navigation remains visible
- **AND** the main content area renders the requested non-admin page instead of an empty router-view

#### Scenario: Admin route is opened without confirmed admin identity
- **WHEN** a user opens an admin route and the system cannot confirm an administrator login
- **THEN** the system redirects the user to the login page
- **AND** the protected admin page is not rendered

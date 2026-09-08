## ADDED Requirements

### Requirement: Authenticated top navigation exposes batch documents
The system SHALL display a `批量文档` entry in top navigation for authenticated users and route it to the batch-document page.

#### Scenario: Authenticated user opens batch documents
- **WHEN** an authenticated user selects `批量文档` from top navigation
- **THEN** the system opens the batch-document page
- **AND** the page provides controls for batch URL import and batch local-file upload

#### Scenario: Unauthenticated visitor cannot start import
- **WHEN** a visitor is not authenticated
- **THEN** the system does not expose an executable batch-document import entry
- **AND** the visitor cannot submit batch URL or file import requests

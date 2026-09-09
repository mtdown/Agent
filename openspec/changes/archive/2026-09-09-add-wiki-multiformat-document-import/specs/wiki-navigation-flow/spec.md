## ADDED Requirements

### Requirement: Space navigation exposes document upload action
The system SHALL provide a local document upload action in the Wiki workspace space navigation area whenever a user has selected a target Wiki space.

#### Scenario: User uploads from selected folder
- **WHEN** a user selects a Wiki space folder in the left navigation and starts a supported document upload
- **THEN** the upload targets the selected space and folder
- **AND** the URL remains on `/documentWiki`
- **AND** the uploaded document opens in the center column after the upload succeeds

#### Scenario: User uploads from selected space root
- **WHEN** a user selects a Wiki space root in the left navigation and starts a supported document upload
- **THEN** the upload targets the selected space root
- **AND** the URL remains on `/documentWiki`
- **AND** the uploaded document opens in the center column after the upload succeeds

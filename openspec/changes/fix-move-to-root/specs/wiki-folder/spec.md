# wiki-folder Delta

## MODIFIED Requirements

### Requirement: Folder movement stays within its space

The system SHALL allow moving a folder to another location inside the same space — either under a parent folder or to the space root (no parent) — and SHALL reject attempts to move a folder into a folder of a different space. A successful move SHALL persist the chosen destination, including the root destination (parent cleared).

#### Scenario: Reject cross-space folder move

- **GIVEN** a folder in one space
- **WHEN** a user attempts to move it under a folder belonging to another space
- **THEN** the request is rejected and the folder's location is unchanged

#### Scenario: Move a folder to the space root

- **GIVEN** a folder nested under a parent folder in a space the user can see
- **WHEN** the user moves it to the space root
- **THEN** the folder's parent is cleared so it appears as a top-level folder of the space
- **AND** the folder's subtree and contained documents follow it unchanged

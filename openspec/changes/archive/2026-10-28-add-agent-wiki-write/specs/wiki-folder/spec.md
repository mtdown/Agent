# Spec Delta

## MODIFIED Requirements

### Requirement: Folder operations by visible members

The system SHALL allow a user with write access to a space to create, rename, move, and logically delete any folder in that space, regardless of who created its contents. A user with visibility only SHALL be able to browse the folders but SHALL NOT mutate them.

#### Scenario: Member creates a folder
- **GIVEN** a user has write access to a space
- **WHEN** the user creates a folder in that space
- **THEN** the folder is created under the chosen parent or at the top level

#### Scenario: Member renames a folder
- **GIVEN** a folder in a space the user may write
- **WHEN** the user renames the folder
- **THEN** the folder name is updated

#### Scenario: Member moves a folder
- **GIVEN** a folder in a space the user may write
- **WHEN** the user moves it to another folder in the same space
- **THEN** the folder and its entire subtree follow the new location

#### Scenario: Member deletes a folder
- **GIVEN** a folder in a space the user may write
- **WHEN** the user deletes the folder
- **THEN** the folder and all documents and nested folders under it enter the space's recycle bin

#### Scenario: Viewer cannot mutate a team folder
- **GIVEN** a user may view but not write a team space
- **WHEN** that user creates, renames, moves, or deletes a folder there
- **THEN** the operation is rejected and the folder tree is unchanged

### Requirement: Folder movement stays within its space

The system SHALL allow a user with write access to a folder's space to move it to another location inside that same space, either under a parent folder or to the space root. It SHALL reject a move into a folder of a different space. A successful move SHALL persist the chosen destination, including a root destination with no parent.

#### Scenario: Reject cross-space folder move
- **GIVEN** a folder in one space
- **WHEN** a user attempts to move it under a folder belonging to another space
- **THEN** the request is rejected and the folder's location is unchanged

#### Scenario: Move a folder to the space root
- **GIVEN** a folder nested under a parent folder in a space the user may write
- **WHEN** the user moves it to the space root
- **THEN** the folder's parent is cleared so it appears as a top-level folder of the space
- **AND** the folder's subtree and contained documents follow it unchanged

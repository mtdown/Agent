# wiki-folder Specification

## Purpose
Defines per-space wiki folders organized as an infinitely nested tree, the operations users with space write access may perform on folders, and the rule that documents live inside folders.

## Requirements

### Requirement: Folders are organized in a nested tree per space

The system SHALL maintain one folder tree per wiki space, where folders belong to exactly one space and may nest to any depth.

#### Scenario: Folder tree belongs to its space

- **GIVEN** a wiki space with folders
- **WHEN** a folder tree is loaded for that space
- **THEN** only folders of that space are shown, rooted at the space's top-level folders

#### Scenario: Folders can nest infinitely

- **GIVEN** a visible member has selected any folder in a visible space
- **WHEN** a visible member creates a folder inside another folder of the same space
- **THEN** the new folder is nested below the selected parent and can itself contain more child folders without a fixed depth limit

#### Scenario: Nested folders are visible in the left tree

- **GIVEN** a visible space has folders nested more than one level deep
- **WHEN** the user expands that space in the wiki left navigation
- **THEN** each child folder appears under its parent in the same hierarchy used by create and move operations

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

### Requirement: Documents must be placed inside folders

The system SHALL require a destination folder when creating or moving a document.

#### Scenario: Creating a document requires a folder

- **GIVEN** a user creating a document in a visible space
- **WHEN** the user submits the create action without choosing a folder
- **THEN** the request is rejected until a folder is chosen

#### Scenario: Creating a document inside a chosen folder

- **GIVEN** a user creating a document in a visible space
- **WHEN** the user chooses a folder and submits
- **THEN** the document is created inside that folder

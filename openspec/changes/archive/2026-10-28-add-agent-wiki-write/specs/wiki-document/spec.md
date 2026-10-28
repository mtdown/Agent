# Spec Delta

## MODIFIED Requirements

### Requirement: Create document in a visible space

The system SHALL require a destination space and a folder inside it when creating a document. The destination SHALL be writable by the current user; visibility alone SHALL NOT authorize creation.

#### Scenario: Create document in a visible space
- **GIVEN** a logged-in user who may write a public, joined team, or personal space
- **WHEN** the user creates a document choosing that space and a folder inside it
- **THEN** the document is saved with the chosen space and folder as its location

#### Scenario: Reject creation in an invisible space
- **GIVEN** a logged-in user who cannot write the target space
- **WHEN** the user attempts to create a document in that space
- **THEN** the request is rejected and no document is created

### Requirement: Edit any document in a visible space

The system SHALL allow a user with write access to a space to edit any active document inside it, including documents authored by other users. Visibility alone SHALL NOT authorize editing.

#### Scenario: Member edits another member's document
- **GIVEN** a user with write access to a team space containing a document authored by another member
- **WHEN** the user edits and saves the document
- **THEN** the document content is updated for the whole space

#### Scenario: Viewer cannot edit a team document
- **GIVEN** a user can read but not write a team space
- **WHEN** the user attempts to edit one of its documents
- **THEN** the edit is rejected and the document is unchanged

### Requirement: Delete any document in a visible space

The system SHALL allow a user with write access to a space to logically delete any active document inside it. Deletion SHALL record its time and deleting user and move the document to that space's recycle bin. Visibility alone SHALL NOT authorize deletion.

#### Scenario: Member deletes a document
- **GIVEN** a user with write access to a space containing a document
- **WHEN** the user deletes the document
- **THEN** the document no longer appears in normal listings and enters the space's recycle bin with the delete time and deleting user recorded

#### Scenario: Viewer cannot delete a team document
- **GIVEN** a user can read but not write a team space
- **WHEN** the user attempts to delete one of its documents
- **THEN** the deletion is rejected and the document remains active

### Requirement: Move documents between spaces and folders

The system SHALL let a user with write access to both the source and destination spaces move an active document by changing its space and folder while preserving its content. The destination SHALL be within the selected space. A successful move SHALL persist the chosen destination exactly, including a root destination with no folder.

#### Scenario: Move a document to a folder in another visible space
- **GIVEN** a user may write both the source and destination spaces
- **WHEN** the user moves a document from its current folder to a chosen folder in the destination space
- **THEN** the document's space and folder are updated, its content is unchanged, and it appears in the destination location

#### Scenario: Reject move to an invisible destination
- **GIVEN** a user cannot write the destination space
- **WHEN** the user attempts to move a document there
- **THEN** the request is rejected and the document's location is unchanged

#### Scenario: Reject move out of a read-only source
- **GIVEN** a user can read but not write the source space
- **WHEN** the user attempts to move its document to a writable destination
- **THEN** the request is rejected and the document's location is unchanged

#### Scenario: Move a document to the space root within the same space
- **GIVEN** a document inside a folder in a space the user may write
- **WHEN** the user moves it to the space root
- **THEN** the document's folder is cleared so it appears in the space's root document list

#### Scenario: Move a document to the root of another visible space
- **GIVEN** a document in a writable source space, and a different writable destination space
- **WHEN** the user moves it to the destination space's root without choosing a folder
- **THEN** the document's space is updated and its previous folder reference is cleared
- **AND** it appears in the destination space's root document list

### Requirement: Document ownership follows the space

The system SHALL treat documents as belonging to their containing space, so that the author leaving the space (removed or voluntary exit) does not change the document's location or availability. Remaining members SHALL retain visibility according to their membership and SHALL mutate the document only with space write access.

#### Scenario: Document stays after its author exits the space
- **GIVEN** a team space containing a document authored by a member
- **WHEN** the author is removed from the team space or exits voluntarily
- **THEN** the document remains in the team space and visible to the remaining members
- **AND** only remaining members with space write access may operate on it

# wiki-document Delta

## MODIFIED Requirements

### Requirement: Move documents between spaces and folders

The system SHALL let a user who can see both the source and destination change a document's location by reassigning its space and folder, leaving the document content unchanged, in any direction between public, team, and personal spaces. A successful move SHALL persist the chosen destination exactly, including a root destination (folder cleared).

#### Scenario: Move a document to a folder in another visible space

- **GIVEN** a user who can see a source space and a destination space
- **WHEN** the user moves a document from its current folder to a chosen folder in the destination space
- **THEN** the document's space and folder are updated, its content is unchanged, and it appears in the destination location

#### Scenario: Reject move to an invisible destination

- **GIVEN** a user who cannot see a destination space
- **WHEN** the user attempts to move a document there
- **THEN** the request is rejected and the document's location is unchanged

#### Scenario: Move a document to the space root within the same space

- **GIVEN** a document inside a folder in a space the user can see
- **WHEN** the user moves it to the space root
- **THEN** the document's folder is cleared and it appears in the space's root document list

#### Scenario: Move a document to the root of another visible space

- **GIVEN** a document inside a folder in a source space, and a destination space the user can see
- **WHEN** the user moves it to the destination space's root without choosing a folder
- **THEN** the document's space is updated and its previous folder reference is cleared, so it appears in the destination space's root document list

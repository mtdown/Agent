## Purpose

Defines space-aware document CRUD where any user who can see a space may fully operate on its documents (including documents authored by others), and documents can move between spaces and folders by changing their location.

## ADDED Requirements

### Requirement: Create document in a visible space

The system SHALL require a destination space (public, a joined team space, or the personal space) and a folder inside it when creating a document, and only allow destinations the user can see.

#### Scenario: Create document in a visible space

- **GIVEN** a logged-in user who can see a space
- **WHEN** the user creates a document choosing that space and a folder
- **THEN** the document is saved with the chosen space and folder as its location

#### Scenario: Reject creation in an invisible space

- **GIVEN** a logged-in user who cannot see a target space
- **WHEN** the user attempts to create a document in that space
- **THEN** the request is rejected and no document is created

### Requirement: View documents across visible spaces

The system SHALL let a user browse documents in every space they can see: the public space, each team space they belong to, and their own personal space.

#### Scenario: Personal region lists own documents

- **GIVEN** a user with documents in their personal space
- **WHEN** the user opens their personal region
- **THEN** those documents are listed under the personal space's folder tree

#### Scenario: Team region lists joined teams' documents

- **GIVEN** a user who is a member of one or more team spaces
- **WHEN** the user opens the team region
- **THEN** the team spaces the user belongs to are listed with their folder trees and documents

### Requirement: Edit any document in a visible space

The system SHALL allow any user who can see a space to edit any document inside it, including documents authored by other users.

#### Scenario: Member edits another member's document

- **GIVEN** a user who can see a team space containing a document authored by another member
- **WHEN** the user edits and saves the document
- **THEN** the document content is updated for the whole space

### Requirement: Delete any document in a visible space

The system SHALL allow any user who can see a space to delete any document inside it; deletion first moves the document to the space's recycle bin (logical delete).

#### Scenario: Member deletes a document

- **GIVEN** a user who can see a space containing a document
- **WHEN** the user deletes the document
- **THEN** the document no longer appears in normal listings and enters the space's recycle bin with the delete time and the deleting user recorded

### Requirement: Move documents between spaces and folders

The system SHALL let a user who can see both the source and destination change a document's location by reassigning its space and folder, leaving the document content unchanged, in any direction between public, team, and personal spaces.

#### Scenario: Move a document to a folder in another visible space

- **GIVEN** a user who can see a source space and a destination space
- **WHEN** the user moves a document from its current folder to a chosen folder in the destination space
- **THEN** the document's space and folder are updated, its content is unchanged, and it appears in the destination location

#### Scenario: Reject move to an invisible destination

- **GIVEN** a user who cannot see a destination space
- **WHEN** the user attempts to move a document there
- **THEN** the request is rejected and the document's location is unchanged

### Requirement: Document ownership follows the space

The system SHALL treat documents as belonging to their containing space, so that the author leaving the space (removed or voluntary exit) does not change the document's location or availability.

#### Scenario: Document stays after its author exits the space

- **GIVEN** a team space containing a document authored by a member
- **WHEN** the author is removed from the team space or exits voluntarily
- **THEN** the document remains in the team space, visible and operable by the remaining members

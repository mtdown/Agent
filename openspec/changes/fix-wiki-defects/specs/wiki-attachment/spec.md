## Purpose

Defines correctness and safety rules for uploading images into a wiki space: the object-store write and the database row must not diverge, an attachment may only reference a document inside the acting space, upload requires edit-level access, and the file's real content is trusted over its name.

## MODIFIED Requirements

### Requirement: Attachment upload does not leave orphaned objects

The system SHALL NOT retain an object in COS when the corresponding `wiki_attachment` row fails to persist.

#### Scenario: Row save fails after COS write

- **GIVEN** a logged-in user uploads a valid image to a space they may edit
- **WHEN** `cosManager.putObject` succeeds but `save(wikiAttachment)` returns false
- **THEN** the system SHALL delete the just-written COS object
- **AND** SHALL surface `OPERATION_ERROR` to the caller
- **AND** SHALL leave no `wiki_attachment` row for that object

#### Scenario: Row save succeeds

- **GIVEN** a logged-in user uploads a valid image to a space they may edit
- **WHEN** both the COS write and the row save succeed
- **THEN** the system SHALL return the object URL
- **AND** SHALL record size, mime type, content hash, uploader, and acting space

### Requirement: `documentId` must belong to the acting space

The system SHALL reject an upload whose `documentId` refers to a document outside the space being uploaded to.

#### Scenario: Cross-space document id

- **GIVEN** document A belongs to space S1 and the caller may edit space S2
- **WHEN** the caller uploads an image with `spaceId=S2` and `documentId=A`
- **THEN** the system SHALL reject with `NO_AUTH_ERROR` or `PARAMS_ERROR`
- **AND** SHALL NOT create any attachment row or COS object

#### Scenario: Matching document id

- **GIVEN** document B belongs to space S2 and the caller may edit S2
- **WHEN** the caller uploads with `spaceId=S2` and `documentId=B`
- **THEN** the attachment SHALL be linked to document B

#### Scenario: No document id

- **GIVEN** the caller uploads before the document is saved
- **WHEN** `documentId` is omitted
- **THEN** the attachment SHALL be created with a null `documentId` and linked later on save

### Requirement: Uploading requires edit access, not mere visibility

The system SHALL allow an image upload only when the caller holds edit rights on the target wiki space.

#### Scenario: Viewer attempts upload

- **GIVEN** a user who can see space S but holds view-only rights
- **WHEN** that user calls `POST /documentWiki/image/upload` with `spaceId=S`
- **THEN** the system SHALL reject with `NO_AUTH_ERROR`
- **AND** SHALL NOT write anything to COS

#### Scenario: Editor uploads

- **GIVEN** a user with edit rights on space S
- **WHEN** that user uploads a valid image
- **THEN** the upload SHALL succeed

### Requirement: File content is verified, not just its suffix

The system SHALL verify that an uploaded file's real content is an allowed image type, independent of its filename suffix.

#### Scenario: Disguised file

- **GIVEN** a non-image payload renamed to `evil.png`
- **WHEN** the caller uploads it
- **THEN** the system SHALL reject with `PARAMS_ERROR`
- **AND** SHALL NOT write anything to COS

#### Scenario: Genuine image

- **GIVEN** a genuine PNG whose content header matches its suffix
- **WHEN** the caller uploads it
- **THEN** the upload SHALL succeed

### Requirement: Object paths use normalized suffixes

The system SHALL build COS object paths with a lower-cased suffix regardless of the casing used in the uploaded filename.

#### Scenario: Upper-case suffix

- **GIVEN** a caller uploads `PHOTO.JPG`
- **WHEN** the object path is generated
- **THEN** the path SHALL end in `.jpg`

## MODIFIED Requirements

### Requirement: Agent creates a task folder and document using separate tools

For each newly authored content write, the Agent SHALL call a folder creation tool and then a document creation tool for the selected space. For an explicitly requested webpage import, the Agent may use the user-selected existing writable folder or space root instead. The folder SHALL use a normalized task-derived name followed by the server-local calendar date; the document SHALL use a concise title derived from the written content. The service SHALL append `（1）`, `（2）` and so on to a name that already exists among active siblings of the same kind. The document tool SHALL require a valid active folder in its selected space and SHALL return both the document and location IDs as strings.

#### Scenario: New task write
- **GIVEN** a user asks to save content in a writable space
- **WHEN** the Agent completes the write
- **THEN** a new task-and-date folder is created in that space
- **AND** the new document appears in that folder with a content-derived title
- **AND** the user sees the saved space, folder and document names and can open the document
- **AND** no raw space, folder or document ID is shown in the conversation

#### Scenario: Duplicate names
- **GIVEN** an active sibling folder or document already has the proposed name
- **WHEN** another item of the same kind is created in that parent
- **THEN** the newly created item receives the first free numbered suffix
- **AND** the existing item is unchanged

#### Scenario: Cross-space or deleted folder
- **GIVEN** a folder ID belongs to another space or is logically deleted
- **WHEN** the Agent requests a document write into that folder
- **THEN** the request is rejected without creating a document

#### Scenario: Permission changes between lookup and write
- **GIVEN** a team editor lost write permission after the directory query
- **WHEN** the Agent calls a creation tool
- **THEN** the tool rechecks current permission and rejects the write

#### Scenario: Folder created but document save fails
- **GIVEN** the folder creation tool succeeded and a later document write fails
- **WHEN** the Agent receives the failed result
- **THEN** it reports the failure and the created folder's display name and location
- **AND** it does not claim that a document was saved

#### Scenario: Content is missing
- **GIVEN** a user requests a Wiki save without providing the content to save
- **WHEN** the Agent prepares the write
- **THEN** it requests the missing content before creating a folder


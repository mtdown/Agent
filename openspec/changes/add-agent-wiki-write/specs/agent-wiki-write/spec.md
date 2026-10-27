# Spec Delta

## Purpose

定义登录用户通过 Agent 查询可写 Wiki 目录并创建任务文件夹与文档的可观察行为，使用户无需提供数据库 ID，同时保证目标匹配、权限复核、命名与结果反馈可验证。

## ADDED Requirements

### Requirement: Agent lists writable destinations for the current user

The system SHALL provide the Agent with only the current logged-in user's writable public, personal, and team spaces and a bounded, pageable listing of active folders in a selected writable space. Each returned space and folder SHALL include its real ID as a decimal string, name, type or parent relationship as applicable. The caller SHALL NOT choose a different user identity in tool arguments.

#### Scenario: Public and personal destinations
- **GIVEN** a logged-in ordinary user
- **WHEN** the Agent queries writable destinations
- **THEN** the user's personal space and the public space are returned with their real IDs
- **AND** another user's personal space is absent

#### Scenario: Team viewer is excluded
- **GIVEN** a user who can read one team space but cannot edit it, and can edit another team space
- **WHEN** the Agent queries writable destinations
- **THEN** only the editable team space is returned

#### Scenario: Deleted folders are excluded
- **GIVEN** an editable space with active and logically deleted folders
- **WHEN** the Agent queries writable destinations
- **THEN** only active folders in that space are returned

#### Scenario: Large folder tree remains queryable
- **GIVEN** a writable space with more folders than one tool result can hold
- **WHEN** the Agent requests its folders
- **THEN** the response includes a bounded page and a way to request the next page

### Requirement: Agent resolves the write destination without asking for an ID

When the user does not name a destination, the system SHALL present structured choices for the current user's writable spaces, with that user's personal space marked as the recommended default; it SHALL NOT create a folder or document until the user selects a choice. When the user names a space type or team name, the Agent SHALL use only a unique writable match. An absent or ambiguous match SHALL stop the write and request clarification through structured choices where candidates exist; the Agent SHALL NOT guess a space ID.

#### Scenario: Unspecified destination offers a recommended personal choice
- **GIVEN** a logged-in user asks the Agent to save content without naming a space
- **WHEN** the Agent resolves the destination
- **THEN** it presents the writable spaces as clickable choices, with the user's personal space recommended
- **AND** it creates no folder or document before the user clicks a choice

#### Scenario: Unique named team
- **GIVEN** exactly one writable team space matches the team name in the user's request
- **WHEN** the Agent resolves the destination
- **THEN** it uses that space's returned ID

#### Scenario: Ambiguous team name
- **GIVEN** multiple writable team spaces match the requested team name
- **WHEN** the Agent resolves the destination
- **THEN** it presents named, clickable candidates
- **AND** it creates no folder or document before the choice

#### Scenario: Named team is not writable
- **GIVEN** the requested team is absent from the user's writable destinations
- **WHEN** the Agent tries to resolve the destination
- **THEN** it reports that no writable match exists
- **AND** it does not silently write to the personal space

### Requirement: Agent creates a task folder and document using separate tools

For each new content write, the Agent SHALL call a folder creation tool and then a document creation tool for the selected space. The folder SHALL use a normalized task-derived name followed by the server-local calendar date; the document SHALL use a concise title derived from the written content. The service SHALL append `（1）`, `（2）` and so on to a name that already exists among active siblings of the same kind. The document tool SHALL require a valid active folder in its selected space and SHALL return both the document and location IDs as strings.

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

### Requirement: Built-in document write Skill guides Agent tool use

The system SHALL make a maintained document write workflow Skill available to every logged-in Agent user. The Skill SHALL direct destination lookup, ambiguity handling, folder creation, document creation, and result checking in that order. Tool implementations SHALL enforce identity, authorization, location, and naming independently of the Skill text.

#### Scenario: User asks to save content
- **WHEN** a logged-in user asks the Agent to save a Wiki document
- **THEN** the Agent follows the built-in workflow and invokes the registered tools
- **AND** it does not ask the user for a raw `spaceId`

#### Scenario: Tool fails
- **WHEN** any write tool reports failure
- **THEN** the Agent stops the workflow and describes the actual result
- **AND** it does not report success without a saved document ID

### Requirement: Wiki choices and results are structured and hide internal IDs

The system SHALL keep real Wiki IDs in tool results and server-side interaction state for authorization and follow-up tool calls. User-visible Agent messages, choice labels, created-item results, tool errors, and reloaded conversation history SHALL NOT show raw space, folder, document or owner IDs. Personal spaces SHALL display as `个人空间`, public spaces as `公开空间`, team spaces by their team names, and folders/documents by their actual names in a human-readable path. Display labels SHALL be derived from space type and existing names, without a separate name-mapping table; legacy persisted personal-space names such as `个人区-<userId>` SHALL NOT be shown. An internal ID SHALL NOT be inferred from a display label alone.

#### Scenario: Legacy personal-space name
- **GIVEN** a personal-space database row is still named `个人区-1971148507794014209`
- **WHEN** the Agent presents it as a destination or saved location
- **THEN** the user sees `个人空间` without the suffix or the raw ID

#### Scenario: A user selects a writable option
- **GIVEN** the Agent has presented a pending choice owned by the current user and conversation
- **WHEN** the user clicks its option token
- **THEN** the server resolves the target ID from stored interaction state, rechecks current write permission and resumes the pending write
- **AND** the browser request does not need to supply a raw Wiki ID

#### Scenario: A token is stale, reused or belongs to another user
- **GIVEN** a pending choice has expired, has already been consumed, or belongs to another user or conversation
- **WHEN** a client submits its token
- **THEN** the server rejects the selection without creating any Wiki item

#### Scenario: Permission changes after choices are shown
- **GIVEN** a user loses write access after the options are displayed
- **WHEN** the user clicks the formerly writable option
- **THEN** the write is rejected and the user receives a safe explanation without an internal ID

#### Scenario: Success and partial failure are reloaded
- **GIVEN** a Wiki write succeeded, or its folder succeeded but its document failed
- **WHEN** the user reopens the conversation
- **THEN** the same structured result is displayed with names and, for a saved document, an open action
- **AND** no raw Wiki ID appears in visible text

#### Scenario: Tool data cannot leak through model streaming
- **GIVEN** a Wiki tool response contains real IDs or a legacy personal-space name
- **WHEN** the Agent emits an intermediate or final response
- **THEN** unreviewed model text containing those values is not streamed to the user
- **AND** selection and write outcomes are rendered from verified structured data

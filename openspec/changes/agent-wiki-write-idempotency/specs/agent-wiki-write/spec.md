## ADDED Requirements

### Requirement: Agent single document creation supports idempotent operations

For the single document creation tool, the system SHALL issue an operation identifier per distinct tool invocation and bind it to the authenticated user and exact request. The internal creation endpoint SHALL accept the identifier optionally for compatibility; when absent, existing behavior SHALL remain unchanged without an idempotency guarantee. For an identified operation, document creation and the durable success record SHALL commit atomically. No guarantee is made for folder creation, import, external indexing, caching, or separately initiated tool invocations.

#### Scenario: Repeated and concurrent identical request
- **WHEN** the same authenticated user sends the same operation identifier and request concurrently or repeatedly
- **THEN** at most one document is committed and successful responses identify that same document

#### Scenario: Conflicting request
- **WHEN** the same user reuses an identifier with a different space, folder, title, or content
- **THEN** the system rejects the conflict and creates no additional document

#### Scenario: Transaction failure
- **WHEN** an error causes the creation transaction to roll back
- **THEN** neither the document nor a successful operation record is committed

#### Scenario: Distinct invocation
- **WHEN** two separately initiated document tool calls have identical content
- **THEN** they receive different operation identifiers and retain the existing distinct-creation behavior

### Requirement: Unknown document creation can be resolved safely

The system SHALL provide an authenticated internal status query bound to the current user and current access permissions. It SHALL return a confirmed result only for an authorized, committed and accessible document. An absent, in-flight, inaccessible, or unqueryable record SHALL NOT be asserted to have failed or succeeded. When a write response is uncertain, the Agent SHALL make at most three status queries; if success cannot be confirmed, it SHALL retain unknown and block later writes for that turn. A confirmed result SHALL be presented through the existing structured result mechanism.

#### Scenario: Response lost after commit
- **WHEN** the document transaction commits but its response is lost and the original user retains access
- **THEN** querying the same operation identifier returns the same document result without creating another document

#### Scenario: Permission revoked
- **WHEN** another user queries an operation or the original user has lost access to its target
- **THEN** no document content, target details, or success result is disclosed and no new document is created

#### Scenario: Uncommitted, missing or unavailable status
- **WHEN** the status is absent, still uncommitted, or the query service fails
- **THEN** the Agent reports unknown, does not automatically issue a new write, and blocks subsequent writes for the turn

#### Scenario: Existing client and service compatibility
- **WHEN** an existing client sends a creation request without an operation identifier or an Agent encounters an older service without status query
- **THEN** the existing request retains its prior behavior and an unresolved write remains unknown without automatic retry

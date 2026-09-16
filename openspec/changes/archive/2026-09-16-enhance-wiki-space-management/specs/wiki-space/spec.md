## ADDED Requirements

### Requirement: Team spaces can be renamed

The system SHALL allow a team space to be renamed by the platform admin or by a member of that space who carries the admin space role. Personal spaces SHALL NOT be renamable by anyone, including the platform admin and the owner, because their name is system-owned and uniformly `个人区`. A rename MUST change only the space name and MUST NOT move documents or alter space membership.

#### Scenario: Team space admin member renames a team space

- **GIVEN** a user whose membership in a team space carries the admin space role
- **WHEN** that user renames the team space
- **THEN** the space is listed under the new name for all members

#### Scenario: Platform admin renames a team space

- **GIVEN** the platform admin
- **WHEN** the admin renames any team space
- **THEN** the space is listed under the new name for all members

#### Scenario: Personal space rename is rejected

- **GIVEN** a personal space
- **WHEN** a rename request is submitted for it, by its owner or by the platform admin
- **THEN** the request is rejected and the space name stays `个人区`

#### Scenario: Unauthorized rename is rejected

- **GIVEN** a user who is neither the platform admin nor an admin member of the target team space
- **WHEN** that user submits a rename request
- **THEN** the request is rejected and the space name is unchanged

#### Scenario: Blank name is rejected

- **WHEN** a rename request is submitted with a blank name
- **THEN** the request is rejected and the space keeps its current name

### Requirement: Personal spaces are named 个人区

The system SHALL name every personal space `个人区`. The name MUST be produced from a single default constant shared by every creation path, and MUST NOT contain the owner's user id or any other suffix. Existing personal spaces whose name carries a user id suffix (for example `个人区-1971148507794014209`) or the legacy default `个人文档` SHALL be normalized to `个人区`. The name is system-owned: no actor, including the owner and the platform admin, can change it.

The left navigation group heading for personal spaces (`个人文档`) is a separate, fixed UI label and SHALL NOT be affected by this naming rule.

#### Scenario: Newly provisioned personal space is named 个人区

- **GIVEN** a user account that has no personal space yet
- **WHEN** the user's personal space is provisioned
- **THEN** the space is named `个人区`
- **AND** the space node under the personal group shows no user id suffix

#### Scenario: Legacy suffixed name is normalized

- **GIVEN** a personal space named `个人区-1971148507794014209`
- **WHEN** the normalization script is applied
- **THEN** the space is named `个人区`
- **AND** its documents, folders and owner are unchanged

#### Scenario: Legacy default name is normalized

- **GIVEN** a personal space named `个人文档`
- **WHEN** the normalization script is applied
- **THEN** the space is named `个人区`

#### Scenario: Group heading stays 个人文档

- **GIVEN** the wiki navigation tree is rendered
- **WHEN** the personal group is displayed
- **THEN** the group heading reads `个人文档`
- **AND** the space node beneath it reads `个人区`

#### Scenario: Backfill script uses the same default name

- **GIVEN** the personal space backfill script is run against a database with users that have no personal space
- **WHEN** the insert completes
- **THEN** every created personal space is named `个人区`

### Requirement: Team members are chosen from existing users

The system SHALL present a selectable list of existing user accounts when a team member is added, so the actor never has to supply a raw user id, and SHALL reject a member addition that does not resolve to an existing user.

#### Scenario: Member picker lists existing users

- **GIVEN** a platform admin adding a member to a team space
- **WHEN** the member control is opened
- **THEN** existing user accounts are offered as selectable options
- **AND** the actor can narrow the options by searching a user name

#### Scenario: Selected user becomes a member

- **GIVEN** a platform admin adding a member to a team space
- **WHEN** the admin picks a user from the list and confirms with a space role
- **THEN** that user becomes a member of the team space with the chosen role

#### Scenario: Unknown user cannot be added

- **WHEN** a member addition is submitted for a user that does not exist
- **THEN** the request is rejected and no membership row is created

# wiki-space Specification

## Purpose
Defines the wiki space model with three region types (public, team, personal), how personal spaces are provisioned, how team spaces are created and staffed by the platform admin, and the space-level visibility rules that gate all wiki access.

## Requirements

### Requirement: Wiki space types

The system SHALL support three wiki space types: a single global public space, multiple team spaces, and one personal space per user.

#### Scenario: Public space is global and unique

- **GIVEN** the wiki module is available
- **WHEN** the system initializes or any logged-in user opens the wiki
- **THEN** exactly one public space exists and is listed under the public region

#### Scenario: Personal space belongs to one user

- **GIVEN** a user account exists
- **WHEN** the personal region is inspected
- **THEN** the user has exactly one personal space owned by that user

#### Scenario: Team spaces are independent

- **GIVEN** multiple team spaces exist
- **WHEN** a user inspects the team region
- **THEN** the user only sees the team spaces whose membership they are part of

### Requirement: Personal space provisioning

The system SHALL ensure every user has a personal space, creating it at registration and lazily for pre-existing users.

#### Scenario: New user gets a personal space at registration

- **WHEN** a user registers successfully
- **THEN** a personal space owned by the new user is created automatically

#### Scenario: Existing user is provisioned on login

- **GIVEN** a pre-existing user without a personal space
- **WHEN** that user logs in and accesses the wiki
- **THEN** the system creates the personal space automatically and the user can operate inside it

### Requirement: Team space creation is admin-only

The system SHALL allow only the platform admin to create team spaces.

#### Scenario: Admin creates a team space

- **WHEN** a platform admin submits a team space creation request
- **THEN** the team space is created

#### Scenario: Ordinary user cannot create a team space

- **WHEN** a non-admin user submits a team space creation request
- **THEN** the request is rejected with no team space created

### Requirement: Team membership is managed by the platform admin

The system SHALL let the platform admin add users to a team space; membership rows may carry a space role as a forward-compatible attribute.

#### Scenario: Admin adds a member to a team space

- **WHEN** a platform admin adds a user to a team space
- **THEN** the user becomes a member of that team space and it appears under the user's joined team list

#### Scenario: Membership carries a role attribute

- **WHEN** the admin adds a member to a team space
- **THEN** the membership record stores a space role value that can be set by the admin

#### Scenario: Non-member cannot see a team space

- **GIVEN** a user is not a member of a team space and is not the platform admin
- **WHEN** the user requests the team space or its contents
- **THEN** the request is rejected and the space is not visible

### Requirement: Space visibility rules

The system SHALL grant visibility to a space according to its type: public spaces to every logged-in user, personal spaces to their owner (or platform admin), and team spaces to their members (or platform admin).

#### Scenario: Public space is visible to every logged-in user

- **GIVEN** a logged-in user who is not a member of any team space
- **WHEN** the user opens the wiki
- **THEN** the public region and its contents are visible

#### Scenario: Personal space is visible only to its owner

- **GIVEN** a user who is not the owner of another user's personal space and not the platform admin
- **WHEN** the user requests that personal space
- **THEN** the request is rejected and the space is not visible

#### Scenario: Platform admin bypasses space membership

- **GIVEN** a platform admin
- **WHEN** the admin requests any personal or team space
- **THEN** the admin is treated as visible for that space regardless of ownership or membership

### Requirement: Member exit does not remove documents

The system SHALL keep documents in the space where they were created when their author leaves the space, whether removed by the platform admin or by voluntary exit.

#### Scenario: Documents remain after author exits

- **GIVEN** a team space containing documents authored by a member
- **WHEN** that member is removed by the platform admin or exits voluntarily
- **THEN** the documents stay in the team space and remain visible and operable by the remaining members

### Requirement: Team space deletion by platform admin

The system SHALL let the platform admin delete a team space with a secondary confirmation; an empty team space is removed outright, while a non-empty team space is logically deleted together with all of its documents and folders, and the admin can later restore or permanently delete the deleted team space.

#### Scenario: Delete an empty team space

- **GIVEN** a team space with no documents or folders
- **WHEN** a platform admin confirms its deletion
- **THEN** the team space is removed permanently

#### Scenario: Delete a non-empty team space

- **GIVEN** a team space containing documents and folders
- **WHEN** a platform admin confirms its deletion
- **THEN** the team space and all of its contents are logically deleted and stop appearing in normal listings

#### Scenario: Restore a deleted team space

- **GIVEN** a logically deleted team space
- **WHEN** a platform admin restores it
- **THEN** the team space reappears together with its documents and folders

#### Scenario: Permanent delete of a team space requires confirmation

- **GIVEN** a logically deleted team space
- **WHEN** a platform admin confirms its permanent deletion
- **THEN** the team space and all of its contents are physically removed

### Requirement: Document space management area for platform admin

The system SHALL show the platform admin an additional wiki area listing all team spaces, with membership management and team-space creation, and SHALL hide that area from ordinary users.

#### Scenario: Admin sees all team spaces in the management area

- **GIVEN** a platform admin viewing the wiki
- **WHEN** the admin opens the document space management area
- **THEN** all team spaces are listed, with membership management and a create-team-space action available

#### Scenario: Ordinary user does not see the management area

- **GIVEN** a non-admin user viewing the wiki
- **WHEN** the wiki navigation is rendered
- **THEN** the document space management area is not shown

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

# Spec Delta

## ADDED Requirements

### Requirement: Space write access is checked separately from visibility

The system SHALL keep existing space visibility for reads and SHALL authorize mutations using write access. Every logged-in user SHALL have write access to the public space. The personal-space owner and the platform admin SHALL have write access to that personal space. A team-space admin or editor member and the platform admin SHALL have write access to that team space. A team viewer SHALL retain visibility but SHALL NOT have write access. Deleted spaces SHALL grant neither access.

#### Scenario: Ordinary user writes public content
- **GIVEN** an ordinary logged-in user and an active public space
- **WHEN** the user requests a Wiki content mutation in that space
- **THEN** the mutation is permitted regardless of the content creator

#### Scenario: Team viewer cannot write
- **GIVEN** a user whose team membership is view-only
- **WHEN** the user requests a mutation to that team's content
- **THEN** the mutation is rejected while normal reads remain available

#### Scenario: Other user's personal space
- **GIVEN** a user who does not own another user's personal space and is not the platform admin
- **WHEN** the user requests either read or write access to that space
- **THEN** access is rejected

## MODIFIED Requirements

### Requirement: Member exit does not remove documents

The system SHALL keep documents in the space where they were created when their author leaves the space, whether removed by the platform admin or by voluntary exit. Remaining members retain visibility according to their team membership and may operate on those documents only when they hold space write access.

#### Scenario: Documents remain after author exits
- **GIVEN** a team space containing documents authored by a member
- **WHEN** that member is removed by the platform admin or exits voluntarily
- **THEN** the documents stay in the team space and remain visible to its remaining members
- **AND** remaining admin or editor members may continue to operate on them

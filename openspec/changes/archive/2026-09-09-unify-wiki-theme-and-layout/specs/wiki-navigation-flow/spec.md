## ADDED Requirements

### Requirement: Space navigation tree root structure

The system SHALL mount public-type Wiki spaces (type = 2) directly at the root level of the navigation tree, alongside the `团队文档` (type = 1) and `个人文档` (type = 0) group wrappers. The system SHALL NOT introduce a separate `公开文档` group wrapper around public spaces.

#### Scenario: Public spaces appear at root

- **WHEN** a user with at least one public space opens `/documentWiki`
- **THEN** the navigation tree renders public spaces as root-level nodes with their folders nested underneath
- **AND** the tree does not wrap public spaces inside a `公开文档` group

#### Scenario: Team and personal groups remain

- **WHEN** a user with team or personal spaces opens `/documentWiki`
- **THEN** the navigation tree still renders `团队文档` and `个人文档` group wrappers for those space types

#### Scenario: Selecting a root public space

- **WHEN** a user clicks a public space node rendered at the root level
- **THEN** the Wiki workspace selects the space and lists its documents

### Requirement: Wiki workspace uses equal-height three columns

The system SHALL render the Wiki workspace as three columns (space navigation / document content / outline) that share the same height. Each column SHALL scroll independently. The system SHALL keep the columns equal-height on desktop viewports.

#### Scenario: Three columns share one height

- **WHEN** a user opens `/documentWiki` on a desktop viewport
- **THEN** the three columns have the same outer height
- **AND** each column scrolls independently when its content exceeds the column height

#### Scenario: Single column on narrow viewport

- **WHEN** a user opens `/documentWiki` on a viewport narrower than 900px
- **THEN** the layout collapses to a single column with no fixed height

### Requirement: Wiki search match-mode radio order

The system SHALL render the `匹配模式` radio group on the Wiki document page with the options in the order `标题`, `正文`, `标题或正文` from left to right.

#### Scenario: Radio group order

- **WHEN** a user opens `/documentWiki` and views the search bar
- **THEN** the radio buttons appear in the order `标题`, `正文`, `标题或正文`
- **AND** the default selection remains `标题或正文`

## REMOVED Requirements

_None._
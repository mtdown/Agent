# batch-document-import Specification

## Purpose
Lets authenticated users collect multiple webpage URLs and local files into a selected Wiki destination, producing traceable Markdown documents while showing the outcome for every submitted item.

## Requirements

### Requirement: User can batch import webpage URLs
The system SHALL allow an authenticated user to submit a newline-separated list of HTTP or HTTPS webpage URLs to a writable Wiki space and optional folder.

#### Scenario: Multiple URLs import successfully
- **WHEN** an authenticated user submits multiple valid webpage URLs with a writable Wiki destination
- **THEN** the system creates one Markdown Wiki document for each successfully processed URL
- **AND** each created document retains its source URL
- **AND** each document is placed in the selected space and optional folder

#### Scenario: URL batch has mixed outcomes
- **WHEN** one or more submitted URLs cannot be downloaded, cleaned, or converted into non-empty Markdown
- **THEN** the system reports a separate failure result for every failed URL
- **AND** the system continues processing unrelated URLs
- **AND** successfully imported URLs remain available as Wiki documents

### Requirement: URL content is cleaned into Markdown
The system SHALL convert downloaded webpage HTML into readable Markdown before creating a Wiki document.

#### Scenario: Page contains non-content elements
- **WHEN** a submitted webpage contains scripts, hidden elements, overlays, sharing controls, or short UI-only page chrome
- **THEN** the imported Markdown excludes those non-content elements
- **AND** retains the selected readable article content

#### Scenario: Page has no known article container
- **WHEN** a submitted webpage has no recognized article container but has a readable generic content area
- **THEN** the system creates Markdown from the best available readable content area
- **AND** repeated navigation-like short lines are removed only when they are not part of a recognized article container

### Requirement: URL imports protect the application and network
The system SHALL accept only safe external HTTP or HTTPS targets and SHALL apply configured request and response limits to every imported URL.

#### Scenario: URL targets a private or local address
- **WHEN** a user submits a URL that resolves to a loopback, private, link-local, multicast, unspecified, or other local-network address
- **THEN** the system rejects that URL without retrieving its content
- **AND** the result identifies the URL as unsafe

#### Scenario: URL exceeds configured limits
- **WHEN** a URL exceeds configured timeout, redirect, or response-size limits
- **THEN** the system marks that URL as failed with an actionable limit message
- **AND** processing continues for other submitted items

### Requirement: User can batch upload supported local documents
The system SHALL allow an authenticated user to submit multiple supported local documents to a writable Wiki space and optional folder.

#### Scenario: Multiple local files import successfully
- **WHEN** an authenticated user submits multiple valid supported files with a writable Wiki destination
- **THEN** the system creates one Markdown Wiki document for each successfully imported file
- **AND** the result identifies every created document

#### Scenario: Batch includes valid and invalid local files
- **WHEN** a user submits a mixture of valid files and unsupported, empty, or unreadable files
- **THEN** the system reports a separate result for every submitted file
- **AND** valid files are imported even when other files fail

### Requirement: HTML inputs use a consistent cleaning policy
The system SHALL apply the same readable-content cleanup policy to webpage HTML and locally uploaded `.html` or `.htm` files before storing Markdown.

#### Scenario: User uploads local HTML
- **WHEN** a user uploads a non-empty `.html` or `.htm` file through the batch-document page
- **THEN** the system removes non-content elements using the same cleanup policy as URL imports
- **AND** stores the resulting document as Markdown

### Requirement: Batch import results are traceable
The system SHALL show a stable per-item result after a URL or local-file submission and retain source information for every created document.

#### Scenario: User reviews a completed batch
- **WHEN** a batch URL or file submission finishes
- **THEN** the page displays the input label, status, result message, and created document reference when available for every item
- **AND** each created document records its import source and import metadata

# Spec Delta

## ADDED Requirements

### Requirement: New article imports extract optional publication metadata

The system SHALL extract and save the publication time, source-site name, source URL, and original author when importing a new article from a URL, HTML file, or Markdown file with recognized leading metadata front matter. It SHALL recognize standard article metadata, common Open Graph/meta aliases, JSON-LD article metadata, and recognized front matter fields. Recognized leading front matter SHALL be removed from the stored article body and its article title SHALL be used when no explicit title or page title is available. If a page has no recognizable site name but has a valid HTTP or HTTPS source URL, the system SHALL use the URL hostname as the source-site label. Missing author or publication-date values SHALL remain absent; the authenticated Wiki creator SHALL continue to be recorded separately. This requirement applies to new imports only and SHALL NOT trigger a backfill of previously imported documents.

#### Scenario: Import a webpage with article metadata
- **WHEN** a user imports a webpage whose HTML contains publication time, source-site name, and original-author metadata
- **THEN** the created document stores those available values separately from the cleaned article body
- **AND** the created document retains the canonical source URL
- **AND** the authenticated Wiki creator remains recorded as the document creator

#### Scenario: Import a webpage without original-author metadata
- **WHEN** a user imports a webpage with no recognizable original-author metadata
- **THEN** the document is still imported successfully when its article body is valid
- **AND** the original-author value remains absent

#### Scenario: Import a webpage without a publication date or site name
- **WHEN** a user imports a webpage missing publication-date or site-name metadata
- **THEN** the document is still imported successfully when its article body is valid
- **AND** publication date remains absent
- **AND** source site falls back to the valid source URL hostname when one is available

#### Scenario: Existing article is not backfilled
- **GIVEN** an article was imported before this change
- **WHEN** the new metadata extraction capability is deployed
- **THEN** the existing article is not re-fetched, rewritten, or assigned inferred author or publication metadata

#### Scenario: Import an article with leading metadata front matter
- **WHEN** a user imports a Markdown or HTML-derived Markdown article that begins with a recognized metadata front matter block
- **THEN** available title, publication date, source URL/site, and author values are saved as article metadata
- **AND** the front matter block is omitted from the stored article body
- **AND** the front matter title is used only when an explicit title and page title are unavailable

# wiki-document-list Specification

## Purpose
Defines that paginated wiki document queries resolve their authors in a single batch, matching the approach already used by the picture module, instead of issuing one author query per returned row.

## Requirements

### Requirement: Document list loads authors in one batch

The system SHALL resolve the authors of a page of documents with a single batched query and map them onto the page, keeping the total query count constant with respect to page size.

#### Scenario: Page of twenty documents by several authors

- **GIVEN** a page request returning twenty documents authored by three distinct users
- **WHEN** the page is assembled
- **THEN** the author lookup SHALL issue at most one additional query
- **AND** every row SHALL carry its author

#### Scenario: Page of twenty documents by the same author

- **GIVEN** a page request returning twenty documents all authored by one user
- **WHEN** the page is assembled
- **THEN** the author lookup SHALL issue one query, not twenty

#### Scenario: Empty page

- **GIVEN** a query that matches no documents
- **WHEN** the page is assembled
- **THEN** no author query SHALL be issued

### Requirement: Authorless and deleted authors degrade gracefully

The system SHALL produce a page successfully when a document has no author id or references a user row that no longer exists.

#### Scenario: Missing author row

- **GIVEN** a document whose `userId` does not match any user row
- **WHEN** the page is assembled
- **THEN** the row SHALL be returned with a null `user` field
- **AND** the request SHALL NOT fail

### Requirement: 浏览态文档列表每页固定 15 条

系统 SHALL 在 Wiki 文档页浏览态（单空间、文件夹或公开文档聚合三种选中方式）下按每页 15 条对文档列表分页；右侧文档导航列表与中栏列表共用同一份分页数据，同步受每页 15 条约束。

#### Scenario: 浏览列表按每页 15 条分页

- **GIVEN** 当前选中位置包含超过 15 篇文档
- **WHEN** 用户浏览文档列表
- **THEN** 中栏列表一次最多展示 15 条，分页器可切换页码
- **AND** 右侧文档导航列表同样最多展示当前页的 15 条

#### Scenario: 切换选中位置后分页重置

- **GIVEN** 用户浏览到某页码大于 1 的列表
- **WHEN** 切换空间或文件夹选中位置
- **THEN** 分页回到第 1 页，新位置列表按每页 15 条展示

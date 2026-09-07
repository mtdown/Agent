# wiki-workspace-theme Specification

## Purpose

Defines the shared warm visual theme applied to the Wiki workspace and the cross-cutting surfaces used by the Wiki, gallery, and admin pages so the application presents a single coherent palette.

## ADDED Requirements

### Requirement: Wiki workspace uses a shared warm theme

The system SHALL render the Wiki document page (`/documentWiki`) and the targeted gallery and admin pages on a single warm palette declared as CSS variables (`--wiki-bg`, `--wiki-panel`, `--wiki-panel-head`, `--wiki-border`, `--wiki-muted`, `--wiki-text`, `--wiki-text-muted`, `--wiki-accent`, `--wiki-accent-soft`, `--wiki-accent-hover`).

#### Scenario: Wiki document page loads with warm theme

- **WHEN** a user opens `/documentWiki`
- **THEN** the page background, all three column panels, panel headers, borders, and scrollbar thumbs use the warm palette tokens

#### Scenario: Gallery page loads with warm theme

- **WHEN** a user opens the gallery (`/gallery`), image detail, space detail, or `我的空间` page
- **THEN** the page background, picture cards, and antd surfaces inherit the warm palette

#### Scenario: Admin pages load with warm theme

- **WHEN** an administrator opens 图片管理, 图片空间管理, 用户管理, or 文档空间管理
- **THEN** the page background, tables, cards, pagination, and form controls inherit the warm palette

### Requirement: Ant Design Vue surfaces follow the warm palette

The system SHALL override the default Ant Design Vue surfaces (cards, card headers, tables and their thead/tbody, pagination items, list, empty, select selector, input, tree node content wrappers and selected nodes, radio button wrappers, modal content) on the targeted pages so they no longer render pure white and no longer use the default antd blue selection.

#### Scenario: Cards and tables on warm pages

- **WHEN** a user opens any page whose page wrapper sets the warm background
- **THEN** antd cards and tables render with warm panel surfaces and warm selection / hover tints

#### Scenario: Top navigation remains unchanged

- **WHEN** a user opens the application
- **THEN** the black top navigation header does NOT inherit warm overrides; only content pages below the header do

### Requirement: Warm theme is delivered via a single shared stylesheet

The system SHALL define the warm palette and component overrides in a single shared CSS file imported once from the application entry (`main.ts`).

#### Scenario: Single source of theme

- **WHEN** the frontend bundle is built
- **THEN** exactly one shared stylesheet declares the warm palette variables and antd overrides, imported after `ant-design-vue/dist/reset.css`
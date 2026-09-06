## Purpose

Defines the frontend type-safety quality gate for the Vue application so production builds fail only on real unresolved defects, not on ambiguous local typing contracts left over from earlier JavaScript-style implementation.

## ADDED Requirements

### Requirement: Full frontend build passes

The frontend SHALL pass the complete production build command, including both TypeScript/Vue type checking and Vite bundling.

#### Scenario: Production build

- **WHEN** `npm run build` is executed in `cloud_front`
- **THEN** the command SHALL complete successfully
- **AND** the type-check stage SHALL report zero TypeScript errors
- **AND** the Vite build stage SHALL produce the production assets

#### Scenario: Build-only command remains valid

- **WHEN** `npm run build-only` is executed in `cloud_front`
- **THEN** the command SHALL complete successfully
- **AND** the production asset split introduced by the wiki work SHALL remain intact

### Requirement: API response typing matches backend contracts

The frontend SHALL treat backend responses according to the generated OpenAPI response models and the shared `BaseResponse` shape.

#### Scenario: Component reads response status

- **WHEN** a component calls a generated API method
- **THEN** TypeScript SHALL allow access to the response code, data, and message only through a typed response contract
- **AND** the component SHALL NOT require unbounded `any` to inspect normal success or failure responses

#### Scenario: Optional response data

- **WHEN** an API response model marks data or nested output as optional
- **THEN** the caller SHALL handle the missing-data path before reading nested fields
- **AND** user-visible error handling SHALL remain present

### Requirement: Entity ids preserve backend Long precision

The frontend SHALL preserve backend Long/Snowflake identifier precision across route parameters, props, state, and API calls.

#### Scenario: Route id passed to API

- **WHEN** a page receives an entity id from a route path or query parameter
- **THEN** the id SHALL be normalized into the frontend entity-id contract
- **AND** the id SHALL NOT be blindly coerced with `Number(...)`

#### Scenario: Component receives an id prop

- **WHEN** a component receives a user, space, picture, document, or folder id
- **THEN** the prop type SHALL accept the project entity-id contract
- **AND** downstream API calls SHALL keep the id compatible with generated OpenAPI typings

### Requirement: Vue state and UI callbacks are explicitly typed

The frontend SHALL give collection state, pagination state, table callbacks, and UI event callbacks enough type information for `vue-tsc` to validate them.

#### Scenario: List state assignment

- **WHEN** a page assigns API records into a list ref
- **THEN** TypeScript SHALL know the element type of the list
- **AND** the assignment SHALL NOT rely on `never[]` or implicit `any`

#### Scenario: UI callback parameters

- **WHEN** a table, pagination, upload, menu, or click callback receives a framework-provided parameter
- **THEN** the parameter SHALL have an explicit local or framework type
- **AND** the fix SHALL preserve the current user-visible behavior

### Requirement: Legacy picture and space workflows remain unchanged

The typecheck remediation SHALL NOT change the functional behavior of existing picture, space, and admin workflows.

#### Scenario: Picture workflow regression

- **WHEN** a logged-in user browses pictures, uploads a picture, and opens picture detail
- **THEN** those operations SHALL continue to work as before

#### Scenario: Space workflow regression

- **WHEN** a logged-in user opens a space detail page and loads its picture list
- **THEN** the page SHALL continue to load the same data and permission-controlled actions as before

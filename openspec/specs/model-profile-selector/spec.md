# model-profile-selector Specification
## Purpose

Gives local developers one persistent place to view and select the model profiles configured for this project (local Ollama and cloud providers), lets them add and remove their own profiles from the UI, keeps the deployed cloud interface unchanged, and supplies a single source of truth that a later evaluation run can read instead of maintaining a second model inventory. Credentials are represented only by variable-name references, never by values stored in the browser.

## Requirements
### Requirement: Local-only selector visibility

The system SHALL render the model profile selector in the AI assistant chat composer's input bar only in local development mode, or when the explicitly documented local opt-in build variable is set to true. The system SHALL NOT render the selector in a standard production build, and SHALL NOT require any untracked configuration file in order to build for deployment.

#### Scenario: Local development mode
- **WHEN** a developer opens the application through the local development server
- **THEN** the chat composer input bar shows the selector with the currently selected profile

#### Scenario: Standard cloud build
- **WHEN** the frontend is built without the local opt-in variable and served from the production build
- **THEN** the chat composer input bar does not render the selector and no selector state is injected into the page

#### Scenario: Local opt-in outside development mode
- **WHEN** the local opt-in variable is set to true in the gitignored local environment file
- **THEN** the selector is rendered even outside development mode
- **AND** a cloud build still succeeds without that file

### Requirement: Compact composer-integrated presentation

The selector SHALL appear as a borderless text trigger inside the composer's bottom toolbar, showing the selected model name, a local/cloud hint, and a dropdown chevron. Its dropdown SHALL open upward so that it is not clipped at the bottom of the viewport. When the selector is not rendered, the composer SHALL keep its original single-row input-and-send layout.

#### Scenario: Compact trigger in the bottom toolbar
- **WHEN** a developer views the chat composer in local development mode
- **THEN** the bottom toolbar shows a borderless text trigger with the selected model name and a chevron
- **AND** activating the trigger opens the profile menu upward

#### Scenario: Cloud build keeps the original composer layout
- **WHEN** the selector is not rendered in a standard production build
- **THEN** the text input and the send/stop button remain on the same row as before the change
- **AND** the send/stop button behaviour is unchanged

### Requirement: Model profile inventory and grouping

The selector SHALL present the configured chat/generation model profiles grouped by location and provider, each showing its model name and a short note about its current role. Every selectable profile SHALL carry a base URL and a model identifier. Embedding and rerank models SHALL be listed as read-only, non-selectable entries. User-defined profiles SHALL appear in a separate custom group and built-in profiles SHALL remain read-only.

#### Scenario: Grouped inventory
- **WHEN** a user opens the selector dropdown
- **THEN** selectable profiles appear under local and cloud groups, and embedding/rerank models appear in a disabled group

#### Scenario: Non-chat model cannot be selected
- **WHEN** a user attempts to choose a disabled embedding or rerank entry
- **THEN** the selected profile remains unchanged

### Requirement: User-defined model profiles

The system SHALL provide an "add / manage models" entry in the selector menu that opens a dialog where a user can add a custom profile by supplying a display name, model identifier, base URL, location, provider, an optional note, and an optional credential variable name. The system SHALL validate the required fields and require an `http://` or `https://` base URL, SHALL reject invalid input without creating a profile, and SHALL add the new profile to the custom group and select it. Custom profiles SHALL be removable individually, and built-in profiles SHALL NOT be removable. Custom profiles SHALL be persisted separately from the selected profile identifier in browser local storage, and SHALL be validated on read so that malformed, non-array, or conflicting entries are discarded without error. The system SHALL state in the UI that custom profiles exist only in the local browser and are not readable by the Python evaluation runner.

#### Scenario: Add a custom profile
- **WHEN** a user fills in a display name, model identifier, and a valid `http(s)` base URL and submits the dialog
- **THEN** the profile appears in the custom group and becomes the selected profile
- **AND** it remains after a page reload

#### Scenario: Invalid input is rejected
- **WHEN** a user submits the dialog with a missing required field or a base URL that is not a valid `http(s)` URL
- **THEN** no profile is created and the user sees a validation message

#### Scenario: Remove a custom profile
- **WHEN** a user confirms removal of a custom profile
- **THEN** that profile disappears from the menu and from stored custom profiles
- **AND** if it was selected, the selection falls back to the default remote `deepseek-flash` profile (`cloud-deepseek-flash`)
- **AND** built-in profiles remain unchanged and cannot be removed

#### Scenario: Malformed stored custom profiles
- **WHEN** browser local storage contains malformed entries, a non-array value, or entries with invalid fields
- **THEN** invalid entries are discarded, valid entries are kept, and the selector renders without error

#### Scenario: Custom profiles are local-only
- **WHEN** a user views the selector menu or the management dialog
- **THEN** the UI states that custom profiles are stored only in this browser and are not readable by the Python evaluation runner

### Requirement: Credentials are references, never values

A model profile SHALL record credentials only as an optional `apiKeyEnv` field holding the name of an allowed environment variable. The system SHALL NOT store any API key value in browser local storage, in the profile inventory, or in any front-end artifact. The allowed names SHALL be the model API key names registered in the deployment environment example. When custom profiles are read from local storage, the system SHALL discard any legacy plaintext credential fields and write back the cleaned list. The system SHALL reject a custom profile whose `apiKeyEnv` is not empty and not one of the allowed names.

#### Scenario: Built-in cloud profile carries a reference
- **WHEN** a built-in cloud profile is displayed in the management dialog
- **THEN** it shows only a variable name such as `RAG_LLM_API_KEY` and no key value

#### Scenario: Plaintext in stored data is rejected
- **WHEN** stored custom profiles contain a plaintext-looking `apiKeyEnv` or a legacy literal credential field
- **THEN** the invalid profile is discarded or the legacy field is removed from memory and from local storage

#### Scenario: Reference is validated on read
- **WHEN** a stored custom profile has an `apiKeyEnv` that is not in the allowed name list
- **THEN** that profile is filtered out and the selector still renders without error

### Requirement: Local API key entry in the management dialog

In local development mode the management dialog SHALL offer a local secrets section that lists the allowed model API key names with a configured/not-configured boolean state, and SHALL allow the user to submit a key value which is written to the project-root local secrets file. The dialog SHALL state that the state reflects only that file, that the value is not kept in the browser or shipped in the front-end artifact, and that the backend and agent must be restarted before the new value takes effect. When the development-only write endpoint is unavailable, including when the startup script explicitly enables a tunnel, the dialog SHALL disable this section instead of pretending the value was saved. If a user supplies a key while adding a custom profile and the write fails, the system SHALL NOT create that profile.

#### Scenario: Save a key from the dialog
- **WHEN** a developer selects an allowed variable name, enters a key value, and confirms saving
- **THEN** the value is written to the project-root local secrets file and the configured state is refreshed
- **AND** the dialog shows the restart notice

#### Scenario: Endpoint unavailable outside development mode
- **WHEN** the selector is displayed outside development mode and no local write endpoint exists
- **THEN** the local secrets section is disabled and explains that cloud builds do not provide this capability

#### Scenario: Failed write does not create a dangling profile
- **WHEN** a user supplies a key while adding a custom profile and the write endpoint rejects it
- **THEN** no custom profile is created and the user sees an error message

### Requirement: Persistent selection as a single source of truth

The system SHALL persist the selected profile identifier in browser local storage under one documented key and SHALL restore it on reload. An invalid or unavailable stored value SHALL fall back to the default remote `deepseek-flash` profile without error. Any consumer, including a later evaluation entry point, SHALL be able to read the complete selected profile (base URL, model identifier, provider, location, credential variable name) from that single source rather than maintaining a second inventory.

#### Scenario: Selection survives reload
- **WHEN** a user selects a profile and reloads the page
- **THEN** the selector shows the same selected profile

#### Scenario: Invalid stored value
- **WHEN** the stored identifier matches no profile, or storage access fails
- **THEN** the selector falls back to the default remote `deepseek-flash` profile (`cloud-deepseek-flash`) without error

#### Scenario: Evaluation consumer reads the same profile
- **WHEN** a later evaluation entry point reads the current selection
- **THEN** it obtains the same profile including its base URL, model identifier, and credential variable name

### Requirement: Selection does not change the backend model

The selector SHALL only record a local preference and SHALL NOT alter any backend request, model endpoint, or response contract. The system SHALL state in the selector UI that the backend model remains determined by startup environment variables. The default selection SHALL be the remote `deepseek-flash` profile (`cloud-deepseek-flash`).

#### Scenario: Switching profile leaves the backend unchanged
- **WHEN** a user switches the selected profile
- **THEN** no backend request field changes and the backend keeps its startup-configured model
- **AND** the selector displays the notice that the backend has not been switched

#### Scenario: Default profile
- **WHEN** no valid stored selection exists
- **THEN** the selected profile is the remote `deepseek-flash` profile (`cloud-deepseek-flash`)

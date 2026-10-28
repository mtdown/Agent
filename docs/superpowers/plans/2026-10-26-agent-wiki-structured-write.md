# Agent Wiki Structured Write Implementation Plan

> **For agentic workers:** Implement this plan task by task in the existing `feature/agent-wiki-write开发` checkout. The project forbids new worktrees and Mockito. The OpenSpec change `add-agent-wiki-write` is the source of truth.

**Goal:** Save Wiki content through Agent with a built-in workflow, clickable destination choices, and display-safe results that never show raw Wiki IDs.

**Architecture:** Cloud owns login identity, pending choices, permission checks, and persisted display projections. Agent owns the model/tool loop and reports structured Wiki tool events to Cloud. The browser receives only display labels and opaque choice tokens. Tool JSON retains real IDs internally.

**Tech Stack:** Spring Boot, MyBatis/MySQL, Java 11 source level, Vue 3/TypeScript, SSE, JUnit 5, OpenSpec.

---

## Task 1: Built-in Wiki write Skill and loop budget

**Files:** `cloud/src/main/java/com/et/cloud/controller/AgentSkillCatalog.java`, `cloud/src/main/java/com/et/cloud/controller/AgentLibraryStore.java`, `cloud/src/main/java/com/et/cloud/controller/AgentSkillDefinitionValidator.java`, `cloud_agent/src/main/java/com/et/agent/AgentLoop.java`, `cloud_agent/src/main/resources/application.yml`, corresponding Cloud/Agent tests.

- [ ] Write tests proving the fixed built-in Skill appears in every user's catalog and `get_skill`, cannot be overridden by user records, and validates its steps. Run the targeted tests and confirm a feature-related failure.
- [ ] Implement the reserved Skill definition and catalog/lookup integration; include `get_skill → list_writable_wiki_locations → choice or create_wiki_folder → create_wiki_document → verify` and require content before a folder is created.
- [ ] Write a scripted loop test for five tool/model turns; confirm the old four-turn setting fails for the intended reason. Set the default to six and run the targeted tests green.

## Task 2: Persisted pending destination choice

**Files:** `cloud/src/main/resources/agent-conversation.sql`, `cloud/src/main/java/com/et/cloud/model/entity/AgentMessage.java`, `cloud/src/main/java/com/et/cloud/mapper/AgentMessageMapper.java`, `cloud/src/main/java/com/et/cloud/controller/AgentConversationStore.java`, a focused `AgentWikiChoiceService.java`, and local JUnit integration tests.

- [ ] Write a database-backed test that creates a pending choice, reloads display-only options, and proves another user, another conversation, an expired token, and a replay cannot consume it. Verify red.
- [ ] Add an idempotent schema migration for an internal interaction JSON field on `agent_message`; persist a 30-minute random token association with owner, conversation, original turn, and real space ID. Keep the internal JSON out of conversation API projections.
- [ ] Consume the token atomically, recheck current write permission, and invalidate older choices on a newer turn. Verify green and add a permission-revocation assertion.

## Task 3: Structured Agent events and Cloud projection

**Files:** `cloud_agent/src/main/java/com/et/agent/AgentToolRegistry.java`, `cloud_agent/src/main/java/com/et/agent/AgentLoop.java`, `cloud_agent/src/main/java/com/et/agent/AgentController.java`, `cloud/src/main/java/com/et/cloud/controller/AgentPreviewProxy.java`, `cloud/src/main/java/com/et/cloud/controller/AgentPreviewController.java`, `cloud/src/main/java/com/et/cloud/controller/AgentWritableWikiLocations.java`, tests.

- [ ] Write a scripted model test proving a Wiki choice request stops before any creation tool and emits a structured choice event without model prose. Verify red.
- [ ] Add a choice-request tool/event and a request-scoped Wiki operation trace. Buffer model text for Wiki write turns; only verified structured choice/result data may reach the browser or stored answer.
- [ ] Write tests for unique named team writes, selected-space continuation, folder-only partial failure, document success, and a model attempting to echo `spaceId` or a legacy personal name. Verify red, then implement Cloud projection and run green.
- [ ] Generate display labels by type (`个人空间`, `公开空间`, team name), keep actual folder/document names, and expose a clickable document action backed by reauthorized preview. No name mapping table.

## Task 4: Selection endpoint and browser UI

**Files:** `cloud/src/main/java/com/et/cloud/controller/AgentPreviewController.java`, `cloud_front/src/api/ragController.ts`, `cloud_front/src/pages/aiAssistant/AiAssistantPage.vue`, related frontend tests.

- [ ] Write a Cloud test proving choice submission sends only the opaque token, atomically consumes it, resumes the original request with the selected space, and refuses stale/replayed/foreign tokens. Verify red, implement, and run green.
- [ ] Write a frontend test for recommended personal choice, option click, structured success/partial failure, and history reload with no visible IDs. Verify red, implement, and run green.
- [ ] Preserve the existing right-side document preview for the structured open action. The UI must not auto-click the recommended choice.

## Task 5: Verification and handoff

**Files:** OpenSpec `tasks.md`, `IssueLog.xlsx` when an issue is encountered, and tests touched above.

- [ ] Run Cloud/Agent targeted tests locally without Mockito, frontend type/build checks, module packages, and `openspec validate add-agent-wiki-write --strict`.
- [ ] Manually mutate two or three key choice/permission assertions and confirm the targeted tests turn red; restore and rerun green.
- [ ] Review external signatures, implicit assumptions, database migration, null/legacy data, cache and authorization paths; record defects in `IssueLog.xlsx`.
- [ ] Report the automated results before any repair loop. Once clean, use `stop-dev.ps1` then `start-dev.ps1`, and give the owner browser test cases and expected outcomes. Do not perform default browser hand testing or merge to `main`.

**Rollback:** Disable structured Wiki events and choice submission on the task branch, retain existing Wiki data and created documents, and leave the additive interaction column unused. Preserve the prior endpoint contract for unrelated Agent chat.

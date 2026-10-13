# Spec Delta

## Purpose

Provides a bounded, observable, streaming Agent execution entry that can accept registered tools in later increments while safely completing, failing, or cancelling each request.

## ADDED Requirements

### Requirement: Bounded model and tool loop
The system SHALL process a model turn, execute only registered tool calls, return their results to the model, and repeat until the model produces a final answer or a configured limit is reached.

#### Scenario: Final answer without tool use
- **WHEN** the model returns answer text without a tool call
- **THEN** the system streams that text and ends the request with one completion event

#### Scenario: Registered tool call
- **WHEN** the model requests a registered tool with valid arguments
- **THEN** the system executes it once, returns its result to the model, and continues the loop

#### Scenario: Invalid tool call
- **WHEN** the model requests an unknown tool or invalid arguments
- **THEN** the system does not execute the request and terminates with a readable error

#### Scenario: Loop limit
- **WHEN** the model continues requesting tools beyond the configured turn limit
- **THEN** the system stops and reports a limit error without executing another tool

### Requirement: Streaming and cancellation
The system SHALL stream answer text as it arrives and SHALL release upstream work when the caller disconnects or cancels.

#### Scenario: Incremental answer
- **WHEN** the model emits multiple answer text deltas
- **THEN** the caller receives them in the same order before completion

#### Scenario: Client cancellation
- **WHEN** the caller disconnects during generation
- **THEN** the system stops forwarding deltas and cancels the in-flight model request

### Requirement: Observable usage and latency
The system SHALL report provider-supplied token usage, time to first answer text, and elapsed time to completion for each successful request.

#### Scenario: Provider returns usage
- **WHEN** every model turn reports input and output tokens
- **THEN** the completion event reports their sums and total tokens

#### Scenario: Usage unavailable
- **WHEN** at least one model turn lacks provider token usage
- **THEN** the completion event marks aggregate usage unavailable rather than reporting zero or a partial sum as complete

#### Scenario: First answer text timing
- **WHEN** reasoning or tool-call deltas precede answer text
- **THEN** first-answer-text latency is measured to the first answer-body delta, not to the first upstream event

### Requirement: Service boundary
The system SHALL accept Agent execution only from the authenticated cloud backend; the browser SHALL not directly call the Agent service.

#### Scenario: Direct unauthenticated call
- **WHEN** a request reaches the Agent service without valid service credentials
- **THEN** the service rejects it before invoking the model

# ADR 0005: Optional, provider-neutral AI post-processing

- Status: Accepted
- Date: 2026-10-04

## Context

AI cleanup, naming, labeling and categorization are useful but must never be required, must not
cost anything when unused, and must support local or self-hosted models.

## Decision

Post-processing is off by default. When enabled it uses one structured-output call per note through an
`LlmClient` interface with implementations for the Anthropic Messages API (official Java SDK) and any
OpenAI-compatible endpoint (OpenAI, LiteLLM, Ollama, a personal gateway). Custom HTTP hooks cover
everything else.

## Consequences

API keys are stored encrypted with the Android Keystore and never exported. Processing never blocks
saving; failures are logged on the note.

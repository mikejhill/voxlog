# voxlog-engine:llm

Optional AI post-processing and custom hooks. Provider-neutral `LlmClient` with Anthropic (official Java SDK) and OpenAI-compatible implementations; `NotePostProcessor` runs every enabled task in one structured-output call; `HttpHookClient` implements the hook contract.

**May depend on:** `core/model`. Pure JVM module.

## Entry points

- `LlmClient`, `AnthropicLlmClient`, `OpenAiCompatibleLlmClient`
- `NotePostProcessor`
- `HttpHookClient`, `HookPayload`, `HookPatch`

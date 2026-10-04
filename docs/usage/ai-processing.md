# AI processing and hooks

AI processing is **optional and off by default**. Without it, VoxLog is a complete note-taking app.

## Providers

Settings → AI processing:

| Provider | You need | Notes |
|---|---|---|
| Anthropic (Claude) | An API key from console.anthropic.com | The model list loads from your account. |
| OpenAI | An API key | |
| Custom OpenAI-compatible endpoint | A base URL (e.g. `http://192.168.1.10:4000/v1`) and, if required, a key | Works with LiteLLM, Ollama, vLLM and personal gateways. |

API keys are encrypted with the Android Keystore, never backed up and never exported.

## Tasks

All enabled tasks run in a single request per note, after transcription:

| Task | What it does | Safety rule |
|---|---|---|
| Clean up transcripts | Fixes punctuation, capitalization and filler words. | Voice notes only, and only if you haven't edited the text. The original transcript is always kept. |
| Auto-name | Replaces the timestamp title with a short summary. | Never replaces a title you typed. |
| Auto-label | Suggests labels, preferring existing ones. | Adds labels; never removes yours. |
| Auto-categorize | Moves a note to the best category. | Only for notes still in Uncategorized. |

Turn tasks on globally, or per category (Settings → Categories). **Cleanup instructions** let you add
guidance such as "use British spelling" or "format lists as bullet points".

Use **Re-run AI processing** from a note's menu to process it again.

## Custom hooks

Settings → Custom hooks → Add hook. Each hook receives a JSON copy of every new note (optionally with
the audio file) and can return changes. See [hooks.md](../hooks.md) for the exact contract.

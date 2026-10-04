# VoxLog

**Talk, tap stop, done.** VoxLog is an Android app for capturing voice and text notes with as little
friction as possible, and keeping them useful afterwards: every note gets metadata, a category,
labels, full-text and semantic search, and optional AI cleanup.

- **Instant capture.** Launcher shortcuts (including one per category) open the microphone directly.
  Recording only ends when you press Stop. The note is saved the moment you stop; transcription
  happens in the background.
- **Private by default.** Speech-to-text (whisper.cpp) and semantic search (MiniLM via ONNX Runtime) run
  on the device. No account, no server, no tracking.
- **Your storage.** Notes live on the device and can be mirrored to any folder you choose (Nextcloud,
  Syncthing, OneDrive or Google Drive apps) as Markdown files. Everything exports to JSON/CSV plus audio.
- **Optional AI.** Bring your own Anthropic or OpenAI key, or point it at any OpenAI-compatible endpoint
  (LiteLLM, Ollama, your own gateway) to clean up transcripts, name, label and categorize notes. Custom
  HTTP hooks let you plug in anything else. With nothing configured, VoxLog is purely a note taker.

## Quick start (development)

Requirements: JDK 21, Android SDK (platform 37, NDK 29.0.14206865, CMake 4.1.2). Clone with submodules:

```bash
git clone --recurse-submodules https://github.com/mikejhill/voxlog.git
```

```bash
./gradlew :voxlog-app:installDebug
```

```bash
./gradlew check
```

## Documentation

| Topic | Document |
|---|---|
| How the code is organized and why | [docs/architecture.md](docs/architecture.md) |
| Data model, processing pipeline, search ranking, sync format | [docs/software-design.md](docs/software-design.md) |
| Visual design system and UX principles | [docs/ux/design-system.md](docs/ux/design-system.md) |
| User flows | [docs/ux/user-flows.md](docs/ux/user-flows.md) |
| Using the app | [docs/usage/](docs/usage/README.md) |
| HTTP hook contract | [docs/hooks.md](docs/hooks.md) |
| Permissions and why each is needed | [docs/PERMISSIONS.md](docs/PERMISSIONS.md) |
| Tests, quality gates and performance budgets | [docs/testing.md](docs/testing.md) |
| Releasing (GitHub, self-hosted F-Droid repo, F-Droid main repo) | [docs/release-runbook.md](docs/release-runbook.md) |
| Architecture decision records | [docs/adr/](docs/adr/) |

## Modules

```
voxlog-app                   Application, navigation, launcher shortcuts
voxlog-core/model            Pure-Kotlin domain model
voxlog-core/data             Room database, repositories, processing pipeline, search, export, sync
voxlog-core/datastore        Settings (DataStore) and Keystore-encrypted secrets
voxlog-core/designsystem     Material 3 theme, tokens and shared composables
voxlog-core/testing          Fakes, fixtures and test utilities
voxlog-feature/capture       Recording service, record screen, text composer
voxlog-feature/notes         Notes list and detail (adaptive list-detail)
voxlog-feature/search        Hybrid search
voxlog-feature/settings      Settings, categories, labels, hooks, shortcuts
voxlog-engine/whisper        whisper.cpp (source submodule) + JNI
voxlog-engine/embeddings     ONNX sentence embeddings + WordPiece tokenizer
voxlog-engine/llm            Anthropic / OpenAI-compatible clients, post-processing, HTTP hooks
voxlog-benchmark             Macrobenchmarks and baseline profile generator
voxlog-architecture-tests    Konsist architecture rules
```

Each module has its own README describing what it does and what it may depend on.

## License

Apache License 2.0. See [LICENSE](LICENSE). Third-party components keep their own licenses
(whisper.cpp: MIT; ONNX Runtime: MIT; model weights: see their model cards).

# voxlog-core:testing

Test utilities shared by all modules: in-memory fake repositories, fakes for every side effect, a Room-backed `TestDataGraph`, `MainDispatcherRule` and fixed test time.

**May depend on:** `core/*`, `engine/llm` (test code only).

## Entry points

- `FakeNoteRepository`, `FakeCategoryRepository`, `FakeLabelRepository`
- `FakeNoteProcessingScheduler`, `FakeLlmClient`, `FakeSettingsRepository`
- `TestDataGraph`, `TestDatabase`, `TestTime`, `MainDispatcherRule`

# Testing and quality gates

Every gate below runs in CI (`.github/workflows/ci.yml`) and must pass before merging.

## Quick reference

| Command | What it checks |
|---|---|
| `./gradlew spotlessCheck` | Formatting (ktlint, `android_studio` style, 140 columns). `spotlessApply` fixes it. |
| `./gradlew detekt` | Static analysis, complexity, naming, KDoc coverage, Compose rules. |
| `./gradlew lintDebug` | Android Lint with warnings as errors. |
| `./gradlew testDebugUnitTest test` | Unit, Robolectric, ViewModel and Room tests. |
| `./gradlew :voxlog-architecture-tests:test` | Konsist architecture rules. |
| `./gradlew verifyRoborazziDebug` | Screenshot regression tests for every screen (light and dark). |
| `./gradlew koverVerify` | Aggregated line coverage gate. |
| `./gradlew assertModuleGraph` | Module dependency direction. |
| `./gradlew :voxlog-app:pixel8api34DebugAndroidTest` | Instrumented E2E tests on a Gradle Managed Device. |
| `./gradlew :voxlog-benchmark:pixel8api34BenchmarkReleaseAndroidTest` | Macrobenchmarks. |

## Test pyramid

| Level | Where | Tools |
|---|---|---|
| Pure unit | `core/model`, `engine/*` | JUnit 4, Truth, coroutines-test, MockWebServer |
| Data | `core/data` | Robolectric, in-memory Room, real repositories, fakes for side effects |
| ViewModel | `feature/*` | In-memory fake repositories from `core/testing`, `MainDispatcherRule` |
| Screenshot | `voxlog-app/src/test` | Roborazzi + Robolectric native graphics, fixed `LocalClock` |
| Architecture | `voxlog-architecture-tests` | Konsist |
| Instrumented E2E | `voxlog-app/src/androidTest` | UI Automator across activities; real whisper.cpp on a speech fixture |
| Performance | `voxlog-benchmark` | Macrobenchmark, custom trace sections |

Fakes are preferred over mocks: `FakeNoteRepository`, `FakeCategoryRepository`, `FakeLabelRepository`,
`FakeNoteProcessingScheduler`, `FakeLlmClient`, `FakeSettingsRepository`, `FakeSecretStore`,
`FakeLocationCapture` and the Room-backed `TestDataGraph` live in `voxlog-core/testing`.

## Static analysis thresholds

Configured in `config/detekt/detekt.yml`:

| Rule | Threshold |
|---|---|
| Cyclomatic complexity per function | ≤ 10 |
| Cognitive complexity per function | ≤ 15 |
| Function length | ≤ 60 lines (composables exempt) |
| Parameters | ≤ 6 per function, 8 per constructor |
| Nesting depth | ≤ 4 |
| Return statements | ≤ 2 (guard clauses exempt) |
| Public classes and functions | must have KDoc |
| Boolean properties | must read as predicates (`is`, `has`, `should`, …) |

## Architecture rules (Konsist)

- `core.model` has no Android, AndroidX or Dagger imports.
- Feature code never imports the database package.
- Engines only import `core.model` from the project.
- ViewModels end in `ViewModel`, live in features and never take DAOs or the database.
- Room DAOs end in `Dao` and live in `core.data.database.dao`.
- Workers end in `Worker`; `*UiState` types have no `var` properties.
- No `*Helper`, `*Manager` or `*Util(s)` classes.
- Every public class and interface has KDoc.
- Every `*Screen` composable is covered by a screenshot test.

## Coverage

Kover aggregates all modules at the root. Generated code (Hilt, Room, Compose singletons, previews)
and device-only glue (activities, services, the recorder, native JNI wrappers) are excluded; the
device-only glue is exercised by the instrumented E2E tests instead. The gates are in `build.gradle.kts`:
60% overall and 70% each for `core/data`, `core/model` and `engine/llm`. Module gates aggregate debug
and JVM tests, including feature tests that exercise real repositories, and retain the same exclusions.

## Performance budgets

Measured with Macrobenchmark through trace sections in the production code:

| Scenario | Metric | Budget (median) |
|---|---|---|
| Voice shortcut, cold start → microphone recording | `VoxLog.recordingStart` + `timeToInitialDisplayMs` | ≤ 500 ms |
| Stop → note persisted | `VoxLog.recordingStop` | ≤ 150 ms |
| Text shortcut, cold start → composer | `timeToInitialDisplayMs` | ≤ 400 ms |
| Notes list scroll | janky frame ratio | < 1% |

Latest local run (2026-10-04, API 36 x86_64 emulator, software rendering, generated baseline profiles;
not representative of phones), medians over ten iterations:

| Metric | Emulator median |
|---|---|
| Microphone start trace | 115.1 ms |
| Stop audio finalization trace | 64.8 ms |
| Voice shortcut cold-start initial display | 1,180.1 ms |
| Text shortcut cold-start initial display | 982.8 ms |

Every stop iteration emitted exactly one positive-duration trace and reached `Saved`. The stop trace
measures audio finalization; the benchmark also waits for persistence to finish and the Saved screen.
Notes list scrolling was skipped with JUnit Assume because ranchu software rendering lacks frame
timeline data. Both startup display medians exceed the current emulator limits (1,000/800 ms), so
`scripts/check_benchmarks.py` fails this run. Startup numbers must be validated on a physical arm64 device
(`./gradlew :voxlog-benchmark:connectedBenchmarkReleaseAndroidTest` with a phone attached) before
tightening `EMULATOR_TOLERANCE` in `scripts/check_benchmarks.py`.

Design rules that keep these budgets: the microphone opens before any database write; transcription,
AI processing, embeddings and sync never run on the capture path; baseline profiles cover the capture
paths. Emulator numbers in CI catch regressions; absolute budgets are validated on a physical device.

## Updating screenshots

```bash
./gradlew :voxlog-app:recordRoborazziDebug
```

Review the changed PNGs in `voxlog-app/src/test/screenshots/` before committing.
`./gradlew :voxlog-app:generateStoreScreenshots` also refreshes the store listing images.

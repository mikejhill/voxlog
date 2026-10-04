# Contributing

## Setup

1. JDK 21 and the Android SDK with platform 37, NDK `29.0.14206865` and CMake `4.1.2`
   (versions live in `gradle/libs.versions.toml`).
2. Clone with submodules: `git clone --recurse-submodules …` (whisper.cpp is a submodule).
3. `./gradlew :voxlog-app:installDebug`.

## Before you push

```bash
./gradlew spotlessApply
```

```bash
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest :voxlog-architecture-tests:test verifyRoborazziDebug koverVerify
```

CI runs the same gates plus instrumented tests, benchmarks and a reproducibility check. See
[docs/testing.md](docs/testing.md).

## Conventions

- Follow the layering in [docs/architecture.md](docs/architecture.md); Konsist and the module-graph
  plugin will tell you when you don't.
- Every public class and function has a short KDoc that says what it is for, not how it works.
- Names are specific: no `Helper`, `Manager` or `Util` classes; booleans read as predicates.
- Composables are stateless screens (`*Screen`) driven by an immutable `*UiState`; every screen has a
  screenshot test in `voxlog-app/src/test`.
- Prefer fakes from `voxlog-core/testing` over mocks.
- User-visible strings go in `res/values/strings.xml`.
- Keep commits focused; describe the user-visible effect in the message.

## Documentation

Update the relevant document in `docs/` with every behavior change. Record significant decisions as a
new ADR in `docs/adr/`.

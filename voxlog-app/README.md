# voxlog-app

Application shell: `VoxLogApplication` (Hilt, WorkManager configuration, crash recovery, first-run model downloads), `MainActivity` with the navigation graph, static and dynamic launcher shortcuts, release/benchmark build types and the screenshot tests that also generate the store listing images.

**May depend on:** All `feature`, `core` and `engine` modules.

## Entry points

- `MainActivity`, `VoxLogApplication`
- `res/xml/shortcuts.xml` (static shortcuts)
- `ShortcutPublisher` (dynamic per-category shortcuts)
- `src/test/.../screenshots` (Roborazzi), `src/androidTest` (E2E)

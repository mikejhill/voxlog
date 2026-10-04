# voxlog-feature:capture

Capturing notes: `CaptureActivity` (entry point for every shortcut), the microphone foreground `RecordingService`, the record screen and the text composer.

**May depend on:** `core/*`, `engine/whisper` (audio format constants).

## Entry points

- `CaptureActivity`
- `RecordingService`, `RecordingStateHolder`
- `RecordScreen`, `TextComposerScreen`

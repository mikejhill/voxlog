# voxlog-engine:whisper

On-device speech-to-text. Builds whisper.cpp from the pinned git submodule with CMake and exposes it through a small JNI bridge. Audio is decoded in 5-minute chunks so recording length is unbounded.

**May depend on:** `core/model`.

## Entry points

- `SpeechTranscriber`, `WhisperSpeechTranscriber`
- `WavChunkReader`, `WhisperAudioFormat`, `TranscriptCleaner`
- `src/main/cpp/` (CMake + JNI)

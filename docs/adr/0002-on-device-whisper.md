# ADR 0002: On-device speech-to-text with whisper.cpp

- Status: Accepted
- Date: 2026-10-04

## Context

Every voice note must produce text, with or without network or an API key. Recording must only stop
when the user presses Stop. Android's SpeechRecognizer stops on silence and cannot record the audio
at the same time.

## Decision

Record audio to a WAV file, then transcribe it on the device with whisper.cpp, built from a pinned
source submodule through JNI. Models are downloaded once from Hugging Face at a pinned revision and
verified by SHA-256.

## Consequences

Transcription happens after recording (not live) and runs in a background foreground-worker. Model
downloads of 32–190 MB are needed once. Accuracy and speed are user-selectable via model size.

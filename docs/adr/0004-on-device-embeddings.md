# ADR 0004: On-device semantic search with ONNX Runtime

- Status: Accepted
- Date: 2026-10-04

## Context

Search must combine exact text matches with similarity search, at no cost and offline.

## Decision

Embed notes with all-MiniLM-L6-v2 (int8 ONNX, 23 MB) on ONNX Runtime with a Kotlin WordPiece
tokenizer, store vectors in Room and rank by brute-force cosine similarity after FTS4/BM25 matches.

## Consequences

ONNX Runtime adds about 33 MB per ABI, so release builds ship arm64-v8a only. If F-Droid requires
ONNX Runtime to be built from source, the fallback is embeddings through llama.cpp/ggml built from
source alongside whisper.cpp.

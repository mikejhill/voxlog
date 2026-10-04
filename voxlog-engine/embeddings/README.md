# voxlog-engine:embeddings

On-device sentence embeddings for semantic search: all-MiniLM-L6-v2 on ONNX Runtime with a pure-Kotlin BERT WordPiece tokenizer, plus vector helpers.

**May depend on:** `core/model`.

## Entry points

- `TextEmbedder`, `OnnxTextEmbedder`
- `WordPieceTokenizer`
- `VectorMath`

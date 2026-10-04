package com.mikejhill.voxlog.core.model

/**
 * A downloadable on-device model file, pinned to an immutable revision and verified by SHA-256.
 *
 * @property fileName name of the file on disk inside the app's model directory.
 */
data class ModelFile(val fileName: String, val url: String, val sha256: String, val sizeBytes: Long)

/** A speech-to-text model the user can choose in Settings. */
data class SpeechModel(val id: String, val displayName: String, val isEnglishOnly: Boolean, val file: ModelFile)

/** The files making up the sentence-embedding model used for semantic search. */
data class EmbeddingModel(val id: String, val dimensions: Int, val modelFile: ModelFile, val vocabularyFile: ModelFile)

/** The fixed set of models VoxLog knows how to download. */
object ModelCatalog {
    private const val WHISPER_BASE_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1"
    private const val MINILM_BASE_URL =
        "https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/751bff37182d3f1213fa05d7196b954e230abad9"

    /** Selectable Whisper models, smallest first. */
    val speechModels: List<SpeechModel> =
        listOf(
            SpeechModel(
                id = "tiny.en",
                displayName = "Tiny (English, fastest)",
                isEnglishOnly = true,
                file =
                    whisperFile(
                        "ggml-tiny.en-q5_1.bin",
                        "c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b",
                        32_166_155,
                    ),
            ),
            SpeechModel(
                id = "base.en",
                displayName = "Base (English, recommended)",
                isEnglishOnly = true,
                file =
                    whisperFile(
                        "ggml-base.en-q5_1.bin",
                        "4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f",
                        59_721_011,
                    ),
            ),
            SpeechModel(
                id = "base",
                displayName = "Base (multilingual)",
                isEnglishOnly = false,
                file =
                    whisperFile(
                        "ggml-base-q5_1.bin",
                        "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
                        59_707_625,
                    ),
            ),
            SpeechModel(
                id = "small.en",
                displayName = "Small (English, most accurate)",
                isEnglishOnly = true,
                file =
                    whisperFile(
                        "ggml-small.en-q5_1.bin",
                        "bfdff4894dcb76bbf647d56263ea2a96645423f1669176f4844a1bf8e478ad30",
                        190_098_681,
                    ),
            ),
        )

    /** all-MiniLM-L6-v2, int8-quantized ONNX export. */
    val embeddingModel: EmbeddingModel =
        EmbeddingModel(
            id = "all-MiniLM-L6-v2-int8",
            dimensions = 384,
            modelFile =
                ModelFile(
                    fileName = "minilm-l6-v2-int8.onnx",
                    url = "$MINILM_BASE_URL/onnx/model_int8.onnx",
                    sha256 = "afdb6f1a0e45b715d0bb9b11772f032c399babd23bfc31fed1c170afc848bdb1",
                    sizeBytes = 22_972_370,
                ),
            vocabularyFile =
                ModelFile(
                    fileName = "minilm-l6-v2-vocab.txt",
                    url = "$MINILM_BASE_URL/vocab.txt",
                    sha256 = "07eced375cec144d27c900241f3e339478dec958f92fddbc551f295c992038a3",
                    sizeBytes = 231_508,
                ),
        )

    /** Returns the speech model with [id], falling back to the recommended default. */
    fun speechModel(id: String): SpeechModel = speechModels.firstOrNull { it.id == id } ?: speechModels[1]

    private fun whisperFile(fileName: String, sha256: String, sizeBytes: Long) =
        ModelFile(fileName, "$WHISPER_BASE_URL/$fileName", sha256, sizeBytes)
}

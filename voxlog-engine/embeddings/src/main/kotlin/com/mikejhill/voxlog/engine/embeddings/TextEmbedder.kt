package com.mikejhill.voxlog.engine.embeddings

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.LongBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Maps text to a fixed-length, unit-normalized semantic vector. */
interface TextEmbedder {
    /** Returns the embedding of [text]. */
    suspend fun embed(text: String): FloatArray
}

/**
 * Sentence-transformer embedder running an ONNX export of all-MiniLM-L6-v2 on ONNX Runtime.
 * Applies attention-masked mean pooling followed by L2 normalization, matching the reference model.
 * The session is created lazily on first use and reused afterwards.
 */
class OnnxTextEmbedder(private val modelFile: File, private val vocabularyFile: File) :
    TextEmbedder,
    AutoCloseable {
    private val mutex = Mutex()
    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var session: OrtSession? = null
    private var tokenizer: WordPieceTokenizer? = null

    override suspend fun embed(text: String): FloatArray = mutex.withLock {
        withContext(Dispatchers.Default) {
            val activeTokenizer =
                tokenizer ?: WordPieceTokenizer
                    .fromVocabularyLines(vocabularyFile.readLines())
                    .also { tokenizer = it }
            val activeSession =
                session ?: environment
                    .createSession(modelFile.absolutePath, OrtSession.SessionOptions())
                    .also { session = it }
            runModel(activeSession, activeTokenizer.encode(text))
        }
    }

    private fun runModel(session: OrtSession, tokenIds: LongArray): FloatArray {
        val shape = longArrayOf(1, tokenIds.size.toLong())
        val attentionMask = LongArray(tokenIds.size) { 1L }
        val tokenTypes = LongArray(tokenIds.size)
        OnnxTensor.createTensor(environment, LongBuffer.wrap(tokenIds), shape).use { ids ->
            OnnxTensor.createTensor(environment, LongBuffer.wrap(attentionMask), shape).use { mask ->
                OnnxTensor.createTensor(environment, LongBuffer.wrap(tokenTypes), shape).use { types ->
                    val inputs = mapOf("input_ids" to ids, "attention_mask" to mask, "token_type_ids" to types)
                    session.run(inputs).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val hiddenStates = (result[0].value as Array<Array<FloatArray>>)[0]
                        return VectorMath.normalize(meanPool(hiddenStates))
                    }
                }
            }
        }
    }

    private fun meanPool(tokenVectors: Array<FloatArray>): FloatArray {
        val pooled = FloatArray(tokenVectors.first().size)
        tokenVectors.forEach { vector -> vector.indices.forEach { pooled[it] += vector[it] } }
        pooled.indices.forEach { pooled[it] /= tokenVectors.size }
        return pooled
    }

    override fun close() {
        session?.close()
        session = null
    }
}

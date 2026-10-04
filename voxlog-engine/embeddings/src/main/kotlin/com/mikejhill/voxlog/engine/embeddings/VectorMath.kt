package com.mikejhill.voxlog.engine.embeddings

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Vector helpers for embedding storage and similarity ranking. */
object VectorMath {
    /** Cosine similarity in [-1, 1]; returns 0 for zero-length or mismatched vectors. */
    fun cosineSimilarity(first: FloatArray, second: FloatArray): Float {
        if (first.size != second.size || first.isEmpty()) return 0f
        var dot = 0f
        var firstNorm = 0f
        var secondNorm = 0f
        for (index in first.indices) {
            dot += first[index] * second[index]
            firstNorm += first[index] * first[index]
            secondNorm += second[index] * second[index]
        }
        val denominator = sqrt(firstNorm) * sqrt(secondNorm)
        return if (denominator == 0f) 0f else dot / denominator
    }

    /** Scales [vector] to unit length in place and returns it. */
    fun normalize(vector: FloatArray): FloatArray {
        val norm = sqrt(vector.fold(0f) { sum, value -> sum + value * value })
        if (norm > 0f) vector.indices.forEach { vector[it] /= norm }
        return vector
    }

    /** Encodes floats as little-endian float32 bytes. */
    fun toBytes(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    /** Decodes bytes produced by [toBytes]. */
    fun fromBytes(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()) { buffer.get(it) }
    }
}

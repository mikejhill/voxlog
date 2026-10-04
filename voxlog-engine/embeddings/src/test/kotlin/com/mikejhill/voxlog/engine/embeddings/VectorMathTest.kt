package com.mikejhill.voxlog.engine.embeddings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VectorMathTest {
    @Test
    fun `cosine similarity is 1 for parallel, 0 for orthogonal and -1 for opposite vectors`() {
        assertThat(VectorMath.cosineSimilarity(floatArrayOf(1f, 2f), floatArrayOf(2f, 4f))).isWithin(1e-6f).of(1f)
        assertThat(VectorMath.cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f))).isWithin(1e-6f).of(0f)
        assertThat(VectorMath.cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f))).isWithin(1e-6f).of(-1f)
    }

    @Test
    fun `cosine similarity of mismatched or zero vectors is 0`() {
        assertThat(VectorMath.cosineSimilarity(floatArrayOf(1f), floatArrayOf(1f, 2f))).isEqualTo(0f)
        assertThat(VectorMath.cosineSimilarity(floatArrayOf(0f, 0f), floatArrayOf(1f, 2f))).isEqualTo(0f)
    }

    @Test
    fun `normalize produces a unit vector`() {
        val vector = VectorMath.normalize(floatArrayOf(3f, 4f))

        assertThat(vector[0]).isWithin(1e-6f).of(0.6f)
        assertThat(vector[1]).isWithin(1e-6f).of(0.8f)
    }

    @Test
    fun `byte encoding round-trips`() {
        val original = floatArrayOf(0.1f, -2.5f, 1e-7f, 384f)

        assertThat(VectorMath.fromBytes(VectorMath.toBytes(original)).toList()).isEqualTo(original.toList())
    }
}

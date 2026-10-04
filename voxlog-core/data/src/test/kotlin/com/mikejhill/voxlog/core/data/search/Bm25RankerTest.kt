package com.mikejhill.voxlog.core.data.search

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln
import org.junit.Test

class Bm25RankerTest {
    @Test
    fun `title body and transcript weights match the BM25 formula`() {
        val idf = ln((100.0 - 5.0 + 0.5) / (5.0 + 0.5) + 1.0)
        assertThat(Bm25Ranker.score(blob(hits = listOf(1, 0, 0)))).isWithin(0.000001).of(2 * idf)
        assertThat(Bm25Ranker.score(blob(hits = listOf(0, 1, 0)))).isWithin(0.000001).of(idf)
        assertThat(Bm25Ranker.score(blob(hits = listOf(0, 0, 1)))).isWithin(0.000001).of(0.5 * idf)
        assertThat(Bm25Ranker.score(blob(hits = listOf(0, 0, 0)))).isEqualTo(0.0)
    }

    @Test
    fun `rarer terms and shorter documents rank higher`() {
        val normal = Bm25Ranker.score(blob())
        assertThat(Bm25Ranker.score(blob(rowsWithHit = 1))).isGreaterThan(normal)
        assertThat(Bm25Ranker.score(blob(length = 50))).isLessThan(normal)
        assertThat(Bm25Ranker.score(blob(hits = listOf(2, 0, 0)))).isGreaterThan(normal)
    }

    @Test
    fun `zero average lengths are safe and additional columns get unit weight`() {
        val score = Bm25Ranker.score(blob(hits = listOf(0, 0, 0, 1), averageLength = 0, length = 0))
        assertThat(score.isFinite()).isTrue()
        assertThat(score).isGreaterThan(0.0)
    }

    @Test
    fun `multiple phrases add independent contributions`() {
        val single = blob()
        val values = ByteBuffer.wrap(single).order(ByteOrder.nativeOrder()).asIntBuffer()
        val header = IntArray(9) { values[it] }.also { it[0] = 2 }
        val hits = IntArray(9) { values[it + 9] }
        assertThat(Bm25Ranker.score(bytes(header + hits + hits))).isWithin(0.000001).of(2 * Bm25Ranker.score(single))
    }

    private fun blob(hits: List<Int> = listOf(1, 0, 0), rowsWithHit: Int = 5, averageLength: Int = 10, length: Int = 10): ByteArray = bytes(
        intArrayOf(1, hits.size, 100) + IntArray(hits.size) { averageLength } +
            IntArray(hits.size) { length } + hits.flatMap { listOf(it, rowsWithHit, rowsWithHit) }.toIntArray(),
    )

    private fun bytes(values: IntArray): ByteArray = ByteBuffer.allocate(values.size * Int.SIZE_BYTES)
        .order(ByteOrder.nativeOrder()).apply { values.forEach { putInt(it) } }.array()
}

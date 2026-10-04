package com.mikejhill.voxlog.core.data.search

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln

/**
 * Okapi BM25 over SQLite FTS4 `matchinfo(table, 'pcnalx')` blobs, which FTS4 provides but does
 * not rank itself. Column weights favor title matches over body and raw-transcript matches.
 */
internal object Bm25Ranker {
    private const val K1 = 1.2
    private const val B = 0.75
    private const val FIELDS_PER_HIT = 3

    /** Weights for FTS columns in declaration order: title, text, raw transcript. */
    private val COLUMN_WEIGHTS = doubleArrayOf(2.0, 1.0, 0.5)

    /** Scores one row; higher is more relevant. */
    fun score(matchInfo: ByteArray): Double {
        val values = ByteBuffer.wrap(matchInfo).order(ByteOrder.nativeOrder()).asIntBuffer()
        val phraseCount = values[0]
        val columnCount = values[1]
        val totalRows = values[2].toDouble()
        val averageLengthsOffset = FIELDS_PER_HIT
        val rowLengthsOffset = averageLengthsOffset + columnCount
        val hitsOffset = rowLengthsOffset + columnCount
        var score = 0.0
        for (phrase in 0 until phraseCount) {
            for (column in 0 until columnCount) {
                val base = hitsOffset + FIELDS_PER_HIT * (phrase * columnCount + column)
                val hitsInRow = values[base].toDouble()
                if (hitsInRow == 0.0) continue
                val rowsWithHit = values[base + 2].toDouble()
                val averageLength = values[averageLengthsOffset + column].toDouble().coerceAtLeast(1.0)
                val rowLength = values[rowLengthsOffset + column].toDouble()
                val idf = ln((totalRows - rowsWithHit + 0.5) / (rowsWithHit + 0.5) + 1.0)
                val termFrequency = hitsInRow * (K1 + 1) / (hitsInRow + K1 * (1 - B + B * rowLength / averageLength))
                score += COLUMN_WEIGHTS.getOrElse(column) { 1.0 } * idf * termFrequency
            }
        }
        return score
    }

    /**
     * Turns free text into a safe FTS4 MATCH expression: every word becomes a prefix term and all
     * terms must match. Returns null when the query has no searchable words.
     */
    fun toMatchExpression(query: String): String? = query.split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { "${it.lowercase()}*" }
        .ifBlank { null }
}

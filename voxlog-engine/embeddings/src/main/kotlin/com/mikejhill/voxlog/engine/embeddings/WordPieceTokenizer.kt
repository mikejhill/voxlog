package com.mikejhill.voxlog.engine.embeddings

import java.text.Normalizer

/**
 * BERT "uncased" tokenizer: lower-cases, strips accents, splits on whitespace and punctuation,
 * then applies greedy longest-match-first WordPiece using the model's `vocab.txt`.
 */
class WordPieceTokenizer(private val vocabulary: Map<String, Int>, private val maxSequenceLength: Int = DEFAULT_MAX_LENGTH) {
    private val clsId = vocabulary.getValue(CLS_TOKEN)
    private val sepId = vocabulary.getValue(SEP_TOKEN)
    private val unknownId = vocabulary.getValue(UNKNOWN_TOKEN)

    /** Returns token ids wrapped in `[CLS] … [SEP]`, truncated to the maximum sequence length. */
    fun encode(text: String): LongArray {
        val pieceIds = basicTokens(text).flatMap { wordPieces(it) }.take(maxSequenceLength - 2)
        return (listOf(clsId) + pieceIds + sepId).map { it.toLong() }.toLongArray()
    }

    private fun basicTokens(text: String): List<String> {
        val normalized =
            Normalizer
                .normalize(text.lowercase(), Normalizer.Form.NFD)
                .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        val tokens = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) tokens += current.toString()
            current.clear()
        }
        for (char in normalized) {
            when {
                char.isWhitespace() || char.isISOControl() -> {
                    flush()
                }

                isPunctuation(char) -> {
                    flush()
                    tokens += char.toString()
                }

                else -> {
                    current.append(char)
                }
            }
        }
        flush()
        return tokens
    }

    private fun wordPieces(word: String): List<Int> {
        if (word.length > MAX_WORD_CHARS) return listOf(unknownId)
        val pieces = mutableListOf<Int>()
        var start = 0
        while (start < word.length) {
            val match = longestPieceAt(word, start) ?: return listOf(unknownId)
            pieces += match.first
            start = match.second
        }
        return pieces
    }

    private fun longestPieceAt(word: String, start: Int): Pair<Int, Int>? {
        var end = word.length
        while (end > start) {
            val candidate = (if (start > 0) CONTINUATION_PREFIX else "") + word.substring(start, end)
            vocabulary[candidate]?.let { return it to end }
            end--
        }
        return null
    }

    private fun isPunctuation(char: Char): Boolean {
        val type = Character.getType(char).toByte()
        return char.code in ASCII_PUNCTUATION_RANGES || type in PUNCTUATION_TYPES
    }

    /** Vocabulary loading and special tokens. */
    companion object {
        /** all-MiniLM-L6-v2 was trained with 256-token inputs. */
        const val DEFAULT_MAX_LENGTH: Int = 256

        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"
        private const val UNKNOWN_TOKEN = "[UNK]"
        private const val CONTINUATION_PREFIX = "##"
        private const val MAX_WORD_CHARS = 100

        private val ASCII_PUNCTUATION_RANGES = (33..47) + (58..64) + (91..96) + (123..126)
        private val PUNCTUATION_TYPES =
            setOf(
                Character.CONNECTOR_PUNCTUATION,
                Character.DASH_PUNCTUATION,
                Character.START_PUNCTUATION,
                Character.END_PUNCTUATION,
                Character.INITIAL_QUOTE_PUNCTUATION,
                Character.FINAL_QUOTE_PUNCTUATION,
                Character.OTHER_PUNCTUATION,
            )

        /** Builds a tokenizer from `vocab.txt` lines, where the line index is the token id. */
        fun fromVocabularyLines(lines: List<String>): WordPieceTokenizer =
            WordPieceTokenizer(lines.withIndex().associate { (index, token) -> token to index })
    }
}

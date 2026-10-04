package com.mikejhill.voxlog.engine.embeddings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WordPieceTokenizerTest {
    private val vocabulary = listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "hello", "world", "!", "play", "##ing", "cafe", ",")
    private val tokenizer = WordPieceTokenizer.fromVocabularyLines(vocabulary)

    private fun idsOf(vararg tokens: String): List<Long> = tokens.map { vocabulary.indexOf(it).toLong() }

    @Test
    fun `wraps tokens in CLS and SEP and lower-cases input`() {
        assertThat(tokenizer.encode("Hello WORLD").toList()).isEqualTo(idsOf("[CLS]", "hello", "world", "[SEP]"))
    }

    @Test
    fun `splits punctuation into separate tokens`() {
        assertThat(tokenizer.encode("hello, world!").toList()).isEqualTo(idsOf("[CLS]", "hello", ",", "world", "!", "[SEP]"))
    }

    @Test
    fun `applies greedy longest-match word pieces with continuation prefix`() {
        assertThat(tokenizer.encode("playing").toList()).isEqualTo(idsOf("[CLS]", "play", "##ing", "[SEP]"))
    }

    @Test
    fun `strips accents before lookup`() {
        assertThat(tokenizer.encode("Café").toList()).isEqualTo(idsOf("[CLS]", "cafe", "[SEP]"))
    }

    @Test
    fun `maps unknown words to UNK`() {
        assertThat(tokenizer.encode("zebra").toList()).isEqualTo(idsOf("[CLS]", "[UNK]", "[SEP]"))
    }

    @Test
    fun `truncates to the maximum sequence length including special tokens`() {
        val short = WordPieceTokenizer(vocabulary.withIndex().associate { (index, token) -> token to index }, maxSequenceLength = 4)

        val ids = short.encode("hello hello hello hello")

        assertThat(ids).hasLength(4)
        assertThat(ids.last()).isEqualTo(vocabulary.indexOf("[SEP]").toLong())
    }
}

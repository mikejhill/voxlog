package com.mikejhill.voxlog.core.data.search

import com.mikejhill.voxlog.core.data.database.dao.NoteDao
import com.mikejhill.voxlog.core.data.database.dao.SearchDao
import com.mikejhill.voxlog.core.data.database.toDomain
import com.mikejhill.voxlog.core.data.embedding.EmbeddingService
import com.mikejhill.voxlog.core.datastore.SettingsRepository
import com.mikejhill.voxlog.core.model.Note
import com.mikejhill.voxlog.core.model.NoteFilter
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.engine.embeddings.VectorMath
import javax.inject.Inject
import javax.inject.Singleton

/** Finds notes by text. */
interface SearchRepository {
    /** Returns matching notes, full-text matches first, then semantic matches. */
    suspend fun search(query: String, filter: NoteFilter): List<SearchResult>
}

/** One search hit. */
data class SearchResult(val note: Note, val matchType: MatchType, val score: Double)

/** Why a note matched. */
enum class MatchType {
    /** The note contains the query words. */
    FULL_TEXT,

    /** The note is semantically similar to the query. */
    SEMANTIC,
}

/**
 * Hybrid search: FTS4 matches ranked by BM25, followed by embedding cosine-similarity matches
 * above [SIMILARITY_THRESHOLD] that full-text search did not already return.
 */
@Singleton
class HybridSearchRepository @Inject constructor(
    private val searchDao: SearchDao,
    private val noteDao: NoteDao,
    private val embeddingService: EmbeddingService,
    private val settingsRepository: SettingsRepository,
) : SearchRepository {
    override suspend fun search(query: String, filter: NoteFilter): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val fullText = fullTextResults(query, filter)
        val alreadyMatched = fullText.map { it.note.id }.toSet()
        val semantic = if (settingsRepository.current().isSemanticSearchEnabled) {
            semanticResults(query, filter, alreadyMatched)
        } else {
            emptyList()
        }
        return fullText + semantic
    }

    private suspend fun fullTextResults(query: String, filter: NoteFilter): List<SearchResult> {
        val expression = Bm25Ranker.toMatchExpression(query) ?: return emptyList()
        val scores = searchDao.matchFullText(expression).associate { it.noteId to Bm25Ranker.score(it.matchInfo) }
        return loadNotes(scores.keys, filter)
            .map { SearchResult(it, MatchType.FULL_TEXT, scores.getValue(it.id.value)) }
            .sortedByDescending { it.score }
    }

    private suspend fun semanticResults(query: String, filter: NoteFilter, exclude: Set<NoteId>): List<SearchResult> {
        val queryVector = embeddingService.embedQuery(query) ?: return emptyList()
        val scores = searchDao.getEmbeddings(embeddingService.modelId)
            .filter { NoteId(it.noteId) !in exclude }
            .map { it.noteId to VectorMath.cosineSimilarity(queryVector, VectorMath.fromBytes(it.vector)).toDouble() }
            .filter { (_, similarity) -> similarity >= SIMILARITY_THRESHOLD }
            .sortedByDescending { (_, similarity) -> similarity }
            .take(MAX_SEMANTIC_RESULTS)
            .toMap()
        return loadNotes(scores.keys, filter)
            .map { SearchResult(it, MatchType.SEMANTIC, scores.getValue(it.id.value)) }
            .sortedByDescending { it.score }
    }

    private suspend fun loadNotes(ids: Collection<String>, filter: NoteFilter): List<Note> = ids.chunked(SQLITE_MAX_VARIABLES)
        .flatMap { noteDao.getNotes(it) }
        .map { it.toDomain() }
        .filter(filter::matches)

    private companion object {
        /** Empirically good cut-off for all-MiniLM-L6-v2: below this, results are mostly noise. */
        const val SIMILARITY_THRESHOLD = 0.35
        const val MAX_SEMANTIC_RESULTS = 25
        const val SQLITE_MAX_VARIABLES = 900
    }
}

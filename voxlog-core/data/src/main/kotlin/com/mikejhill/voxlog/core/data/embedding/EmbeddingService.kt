package com.mikejhill.voxlog.core.data.embedding

import com.mikejhill.voxlog.core.data.database.dao.NoteDao
import com.mikejhill.voxlog.core.data.database.dao.SearchDao
import com.mikejhill.voxlog.core.data.database.entity.NoteEmbeddingEntity
import com.mikejhill.voxlog.core.data.model.ModelRepository
import com.mikejhill.voxlog.core.model.ModelCatalog
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.engine.embeddings.OnnxTextEmbedder
import com.mikejhill.voxlog.engine.embeddings.TextEmbedder
import com.mikejhill.voxlog.engine.embeddings.VectorMath
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes and stores note embeddings with the on-device model. Every method is a no-op returning
 * null when the embedding model has not been downloaded, so semantic search degrades gracefully.
 */
@Singleton
class EmbeddingService @Inject constructor(
    private val modelRepository: ModelRepository,
    private val noteDao: NoteDao,
    private val searchDao: SearchDao,
) {
    private val model = ModelCatalog.embeddingModel
    private var embedder: TextEmbedder? = null

    /** Identifier stored alongside vectors so a model change triggers re-embedding. */
    val modelId: String
        get() = model.id

    /** Whether the model files are present. */
    val isModelAvailable: Boolean
        get() = modelRepository.isAvailable(model.modelFile) && modelRepository.isAvailable(model.vocabularyFile)

    /** Embeds a search query, or returns null if the model is unavailable. */
    suspend fun embedQuery(query: String): FloatArray? = activeEmbedder()?.embed(query)

    /** Embeds a note's title and text if they changed since the last embedding. Returns true if work was done. */
    suspend fun embedNote(noteId: NoteId): Boolean {
        val activeEmbedder = activeEmbedder() ?: return false
        val note = noteDao.getNote(noteId.value)?.note ?: return false
        val content = "${note.title}\n${note.text}".trim()
        if (content.isEmpty()) return false
        val hash = sha256(content)
        val existing = searchDao.getEmbedding(noteId.value)
        if (existing?.textHash == hash && existing.modelId == model.id) return false
        val vector = activeEmbedder.embed(content)
        searchDao.upsertEmbedding(NoteEmbeddingEntity(noteId.value, model.id, hash, VectorMath.toBytes(vector)))
        return true
    }

    private fun activeEmbedder(): TextEmbedder? {
        if (!isModelAvailable) return null
        return embedder ?: OnnxTextEmbedder(
            modelRepository.fileFor(model.modelFile),
            modelRepository.fileFor(model.vocabularyFile),
        ).also { embedder = it }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).joinToString("") { "%02x".format(it) }
}

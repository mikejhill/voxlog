package com.mikejhill.voxlog.core.data.embedding

import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.data.model.ModelRepository
import com.mikejhill.voxlog.core.data.repository.TextNoteDraft
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.ModelCatalog
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.core.testing.TestDataGraph
import java.io.RandomAccessFile
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmbeddingServiceTest {
    private val scope = TestScope()
    private val graph = TestDataGraph(scope)
    private val models = ModelRepository(graph.audioFileStore)
    private val service = EmbeddingService(models, graph.database.noteDao(), graph.database.searchDao())

    @After
    fun tearDown() = graph.close()

    @Test
    fun `missing model leaves queries and notes usable without embeddings`() = scope.runTest {
        val id = graph.notes.createTextNote(TextNoteDraft(CategoryId.UNCATEGORIZED, "Title", "Body"))
        assertThat(service.modelId).isEqualTo(ModelCatalog.embeddingModel.id)
        assertThat(service.isModelAvailable).isFalse()
        assertThat(service.embedQuery("Body")).isNull()
        assertThat(service.embedNote(id)).isFalse()
        assertThat(service.embedNote(NoteId("missing"))).isFalse()
        assertThat(graph.database.searchDao().getEmbedding(id.value)).isNull()
        assertThat(graph.notes.getNote(id)!!.text).isEqualTo("Body")
    }

    @Test
    fun `availability requires both complete model and vocabulary files`() {
        val model = ModelCatalog.embeddingModel
        RandomAccessFile(models.fileFor(model.modelFile), "rw").use { it.setLength(model.modelFile.sizeBytes) }
        assertThat(service.isModelAvailable).isFalse()
        RandomAccessFile(models.fileFor(model.vocabularyFile), "rw").use { it.setLength(model.vocabularyFile.sizeBytes - 1) }
        assertThat(service.isModelAvailable).isFalse()
        RandomAccessFile(models.fileFor(model.vocabularyFile), "rw").use { it.setLength(model.vocabularyFile.sizeBytes) }
        assertThat(service.isModelAvailable).isTrue()
        models.delete(model.modelFile)
        assertThat(service.isModelAvailable).isFalse()
    }
}

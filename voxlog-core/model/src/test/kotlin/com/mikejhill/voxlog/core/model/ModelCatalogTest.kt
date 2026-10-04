package com.mikejhill.voxlog.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun `unknown speech model ids fall back to the recommended base english model`() {
        assertThat(ModelCatalog.speechModel("does-not-exist").id).isEqualTo("base.en")
    }

    @Test
    fun `every model file is pinned to an immutable revision with a sha256`() {
        val files = ModelCatalog.speechModels.map { it.file } +
            listOf(ModelCatalog.embeddingModel.modelFile, ModelCatalog.embeddingModel.vocabularyFile)

        files.forEach { file ->
            assertThat(file.url).containsMatch("/resolve/[0-9a-f]{40}/")
            assertThat(file.sha256).matches("[0-9a-f]{64}")
            assertThat(file.sizeBytes).isGreaterThan(0)
        }
        assertThat(files.map { it.fileName }.toSet()).hasSize(files.size)
    }
}

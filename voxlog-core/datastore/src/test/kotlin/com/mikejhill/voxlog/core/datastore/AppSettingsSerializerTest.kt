package com.mikejhill.voxlog.core.datastore

import androidx.datastore.core.CorruptionException
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class AppSettingsSerializerTest {
    private suspend fun roundTrip(settings: AppSettings): AppSettings {
        val output = ByteArrayOutputStream()
        AppSettingsSerializer.writeTo(settings, output)
        return AppSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
    }

    @Test
    fun `round-trips every field`() = runTest {
        val settings = AppSettings(
            defaultCategoryId = "journal",
            isLocationCaptureEnabled = true,
            themeMode = ThemeMode.DARK,
            postProcessing = PostProcessingSettings(provider = LlmProviderType.OPENAI_COMPATIBLE, customBaseUrl = "http://local:4000/v1"),
            hooks = listOf(HookSettings("h", "Hook", "https://example.com", mapOf("X" to "y"), categoryIds = setOf("journal"))),
        )

        assertThat(roundTrip(settings)).isEqualTo(settings)
    }

    @Test
    fun `missing fields take defaults and unknown fields are ignored`() = runTest {
        val json = """{"defaultCategoryId":"ideas","someFutureField":42}"""

        val settings = AppSettingsSerializer.readFrom(ByteArrayInputStream(json.toByteArray()))

        assertThat(settings.defaultCategory.value).isEqualTo("ideas")
        assertThat(settings.postProcessing.provider).isEqualTo(LlmProviderType.NONE)
        assertThat(settings.speechModelId).isEqualTo(AppSettings.DEFAULT_SPEECH_MODEL_ID)
    }

    @Test
    fun `corrupt files raise CorruptionException so DataStore can recover`() {
        assertThrows(CorruptionException::class.java) {
            runBlocking { AppSettingsSerializer.readFrom(ByteArrayInputStream("{not json".toByteArray())) }
        }
    }

    @Test
    fun `global hooks apply to every category and scoped hooks only to theirs`() {
        val global = HookSettings("g", "Global", "https://a")
        val scoped = HookSettings("s", "Scoped", "https://b", categoryIds = setOf("journal"))
        val disabled = global.copy(isEnabled = false)

        assertThat(global.appliesTo("anything")).isTrue()
        assertThat(scoped.appliesTo("journal")).isTrue()
        assertThat(scoped.appliesTo("ideas")).isFalse()
        assertThat(disabled.appliesTo("anything")).isFalse()
    }
}

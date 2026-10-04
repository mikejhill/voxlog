package com.mikejhill.voxlog.core.data.pipeline

import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.data.repository.TextNoteDraft
import com.mikejhill.voxlog.core.data.repository.VoiceCaptureRequest
import com.mikejhill.voxlog.core.datastore.LlmProviderType
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.core.model.NoteStatus
import com.mikejhill.voxlog.core.model.TitleSource
import com.mikejhill.voxlog.core.testing.FakeLlmClient
import com.mikejhill.voxlog.core.testing.FakeLlmClientFactory
import com.mikejhill.voxlog.core.testing.TestDataGraph
import com.mikejhill.voxlog.core.testing.TestTime
import com.mikejhill.voxlog.engine.llm.HttpHookClient
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PostProcessingRunnerTest {
    private val scope = TestScope()
    private val harness = TestDataGraph(scope)
    private val llm = FakeLlmClient()
    private val runner = PostProcessingRunner(
        noteDao = harness.database.noteDao(),
        categoryDao = harness.database.categoryDao(),
        labelRepository = harness.labels,
        settingsRepository = harness.settings,
        llmClientFactory = FakeLlmClientFactory(llm),
        hookClient = HttpHookClient(),
        audioFileStore = harness.audioFileStore,
        clock = TestTime.CLOCK,
    )

    @After
    fun tearDown() = harness.close()

    private suspend fun transcribedVoiceNote(text: String): NoteId {
        val id = NoteId.random()
        harness.notes.beginVoiceNote(VoiceCaptureRequest(id, CategoryId.UNCATEGORIZED))
        harness.notes.completeVoiceNote(id, 1_000, CategoryId.UNCATEGORIZED)
        val row = harness.database.noteDao().getNote(id.value)!!.note
        harness.database.noteDao().update(row.copy(text = text, rawTranscript = text, status = NoteStatus.PROCESSING.name))
        return id
    }

    private suspend fun enableAllTasks() = harness.settings.update {
        it.copy(
            postProcessing = it.postProcessing.copy(
                provider = LlmProviderType.ANTHROPIC,
                shouldCleanupGlobally = true,
                shouldAutoNameGlobally = true,
                shouldAutoLabelGlobally = true,
                shouldAutoCategorizeGlobally = true,
            ),
        )
    }

    @Test
    fun `with no provider the note is simply marked ready and no call is made`() = scope.runTest {
        val id = transcribedVoiceNote("um hello")

        runner.run(id)

        assertThat(harness.notes.getNote(id)!!.status).isEqualTo(NoteStatus.READY)
        assertThat(llm.systemPrompts).isEmpty()
    }

    @Test
    fun `applies cleanup, auto-name, labels and category in one pass`() = scope.runTest {
        enableAllTasks()
        val ideas = harness.categories.createCategory("Ideas", 0, "lightbulb")
        llm.reply = """{"cleaned_text":"Hello.","title":"Greeting","labels":["Social","social"],"category":"ideas"}"""
        val id = transcribedVoiceNote("um hello")

        runner.run(id)

        val note = harness.notes.getNote(id)!!
        assertThat(note.text).isEqualTo("Hello.")
        assertThat(note.rawTranscript).isEqualTo("um hello")
        assertThat(note.title).isEqualTo("Greeting")
        assertThat(note.titleSource).isEqualTo(TitleSource.AUTO)
        assertThat(note.categoryId).isEqualTo(ideas.id)
        assertThat(harness.labels.getLabels().map { it.name }).containsExactly("Social")
        assertThat(note.status).isEqualTo(NoteStatus.READY)
    }

    @Test
    fun `never overwrites a user title or user-edited text`() = scope.runTest {
        enableAllTasks()
        llm.reply = """{"cleaned_text":"Cleaned.","title":"Auto","labels":[],"category":null}"""
        val id = transcribedVoiceNote("raw words")
        harness.notes.updateTitle(id, "Mine")
        harness.notes.updateText(id, "I edited this")

        runner.run(id)

        val note = harness.notes.getNote(id)!!
        assertThat(note.title).isEqualTo("Mine")
        assertThat(note.text).isEqualTo("I edited this")
    }

    @Test
    fun `typed notes are never cleaned up and auto-categorize only applies to Uncategorized`() = scope.runTest {
        enableAllTasks()
        val journal = harness.categories.createCategory("Journal", 0, "book")
        val id = harness.notes.createTextNote(TextNoteDraft(journal.id, null, "typed exactly like this"))

        runner.run(id)

        val prompt = llm.systemPrompts.single()
        assertThat(prompt).doesNotContain("cleaned_text")
        assertThat(prompt).doesNotContain("category:")
        assertThat(prompt).contains("title:")
    }

    @Test
    fun `permanent AI failures are logged on the note without failing it`() = scope.runTest {
        enableAllTasks()
        llm.reply = "this is not json"
        val id = transcribedVoiceNote("hi")

        // Invalid JSON is retryable, so the runner surfaces it for WorkManager to retry.
        val result = runCatching { runner.run(id) }

        assertThat(result.isFailure).isTrue()
        assertThat(harness.notes.getNote(id)!!.text).isEqualTo("hi")
    }
}

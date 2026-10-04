package com.mikejhill.voxlog.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAnnotationNamed
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * Layering, naming and documentation rules that detekt and the module-graph plugin cannot express.
 * See docs/architecture.md for the rationale behind each rule.
 */
class ArchitectureTest {
    private val production = Konsist.scopeFromProduction().files
        .filterNot { it.path.contains("/cpp/") || it.path.contains("\\cpp\\") || it.path.contains("build-logic") }

    private val classes = production.flatMap { it.classes() }

    @Test
    fun `domain model is pure Kotlin with no Android or framework imports`() {
        production.filter { it.packagee?.name?.startsWith("com.mikejhill.voxlog.core.model") == true }
            .assertFalse { file ->
                file.imports.any {
                    it.name.startsWith("android") || it.name.startsWith("androidx") ||
                        it.name.startsWith("dagger")
                }
            }
    }

    @Test
    fun `UI and feature code never touches the database directly`() {
        production.filter { it.packagee?.name?.startsWith("com.mikejhill.voxlog.feature") == true }
            .assertFalse { file -> file.imports.any { it.name.contains(".core.data.database") } }
    }

    @Test
    fun `engines depend only on the domain model`() {
        production.filter { it.packagee?.name?.startsWith("com.mikejhill.voxlog.engine") == true }
            .assertFalse { file ->
                file.imports.any {
                    it.name.startsWith("com.mikejhill.voxlog") && !it.name.startsWith("com.mikejhill.voxlog.engine") &&
                        !it.name.startsWith("com.mikejhill.voxlog.core.model")
                }
            }
    }

    @Test
    fun `view models are named ViewModel and live in feature modules`() {
        classes.filter { klass -> klass.parents().any { it.name == "ViewModel" } }
            .assertTrue { it.name.endsWith("ViewModel") && it.resideInPackage("com.mikejhill.voxlog.feature..") }
    }

    @Test
    fun `view models depend on repositories and services, never on DAOs or the database`() {
        classes.withNameEndingWith("ViewModel")
            .assertFalse { klass ->
                klass.primaryConstructor?.parameters?.any {
                    it.type.name.endsWith("Dao") ||
                        it.type.name.endsWith("Database")
                } ==
                    true
            }
    }

    @Test
    fun `room DAOs are named Dao and live in the database package`() {
        production.flatMap { it.interfaces() }.withAnnotationNamed("Dao")
            .assertTrue { it.name.endsWith("Dao") && it.resideInPackage("..core.data.database.dao..") }
    }

    @Test
    fun `workers are named Worker`() {
        classes.filter { klass -> klass.parents().any { it.name == "CoroutineWorker" } }
            .assertTrue { it.name.endsWith("Worker") }
    }

    @Test
    fun `ui state types are immutable data holders`() {
        classes.withNameEndingWith("UiState")
            .assertTrue { klass -> klass.properties().none { it.isVar } }
    }

    @Test
    fun `no grab-bag helper or manager class names`() {
        classes.assertFalse { klass -> listOf("Helper", "Manager", "Util", "Utils").any { klass.name.endsWith(it) } }
    }

    @Test
    fun `every public class and interface is documented`() {
        val publicTypes =
            classes.filter { it.hasPublicOrDefaultModifier } +
                production.flatMap { it.interfaces() }.filter { it.hasPublicOrDefaultModifier }
        publicTypes.filterNot { it.name.endsWith("Test") }
            .assertTrue { it.hasKDoc }
    }

    @Test
    fun `every screen composable is covered by a screenshot test`() {
        val screenshotTest = Konsist.scopeFromTest().files.single { it.name == "ScreenScreenshotTest" }.text
        production.flatMap { it.functions() }
            .filter { function -> function.name.endsWith("Screen") && function.hasAnnotationWithName("Composable") }
            .assertTrue { function -> screenshotTest.contains("${function.name}(") }
    }
}

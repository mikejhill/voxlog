plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.module.graph.assert)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
}

// Enforces the layering described in docs/architecture.md:
// app -> feature -> core/engine; features never depend on each other; core:model depends on nothing.
moduleGraphAssert {
    maxHeight = 5
    allowed =
        arrayOf(
            ":voxlog-app -> :voxlog-(feature|core|engine):.*",
            ":voxlog-feature:.* -> :voxlog-(core|engine):.*",
            ":voxlog-engine:.* -> :voxlog-core:model",
            ":voxlog-core:.* -> :voxlog-core:.*",
            ":voxlog-core:data -> :voxlog-engine:.*",
            ":voxlog-core:testing -> :voxlog-(core|engine):.*",
            ":voxlog-benchmark -> :voxlog-app",
        )
    restricted =
        arrayOf(
            ":voxlog-feature:.* -X> :voxlog-feature:.*",
            ":voxlog-core:model -X> .*",
            ":voxlog-engine:.* -X> :voxlog-feature:.*",
        )
    configurations = setOf("api", "implementation")
}

dependencies {
    // Aggregate coverage across every module that applies Kover.
    subprojects.filter { it.path.count { char -> char == ':' } > 1 || it.path == ":voxlog-app" }
        .forEach { kover(it) }
    subprojects.filter { it.path.startsWith(":voxlog-core:") || it.path.startsWith(":voxlog-engine:") || it.path.startsWith(":voxlog-feature:") }
        .forEach { dokka(it) }
}

kover {
    // Aggregate debug/JVM tests so module gates count real repository use in feature tests too.
    currentProject {
        listOf("data", "model", "llm").forEach { module ->
            createVariant(module) {}
        }
    }
    reports {
        filters {
            excludes {
                // Generated code and DI plumbing carry no logic worth measuring.
                classes(
                    "*_Factory*",
                    "*_HiltModules*",
                    "*Hilt_*",
                    "*_Impl*",
                    "*ComposableSingletons*",
                    "*.BuildConfig",
                    "dagger.hilt.internal.*",
                    "hilt_aggregated_deps.*",
                    // Device-only glue: needs real audio hardware, native libraries, the Keystore or
                    // the system location stack. Covered by instrumented E2E tests and benchmarks instead.
                    "*.CaptureActivity*",
                    "*.MainActivity*",
                    "*.RecordingService*",
                    "*.WavRecorder*",
                    "*.AacTranscoder*",
                    "*.WhisperNative*",
                    "*.WhisperSpeechTranscriber*",
                    "*.OnnxTextEmbedder*",
                    "*.PlatformLocationCapture*",
                    "*.KeystoreSecretStore*",
                    "*.AnthropicLlmClient*",
                    "*.VoxLogApplication*",
                    "*.ShortcutPublisher*",
                    "*.ShortcutPinner*",
                )
                annotatedBy("androidx.compose.ui.tooling.preview.Preview", "javax.annotation.processing.Generated")
            }
        }
        verify {
            rule("Overall line coverage") {
                minBound(60)
            }
        }
        mapOf("data" to "core.data", "model" to "core.model", "llm" to "engine.llm").forEach { (module, packageName) ->
            variant(module) {
                filtersAppend {
                    includes { packages("com.mikejhill.voxlog.$packageName", "com.mikejhill.voxlog.$packageName.*") }
                }
                verify {
                    rule("$module line coverage") { minBound(70) }
                }
            }
        }
    }
}

tasks.named("koverVerify") {
    dependsOn("koverVerifyData", "koverVerifyModel", "koverVerifyLlm")
}

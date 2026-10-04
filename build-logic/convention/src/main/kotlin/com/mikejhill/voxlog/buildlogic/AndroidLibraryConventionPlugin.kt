package com.mikejhill.voxlog.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Base convention for every Android library module (`voxlog.android.library`). */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
                namespace = namespaceFor(path)
                testOptions.targetSdk = libs.versionOf("targetSdk").toInt()
                lint.targetSdk = libs.versionOf("targetSdk").toInt()
            }
            dependencies {
                add("testImplementation", libs.libraryOf("robolectric"))
                add("testImplementation", libs.libraryOf("androidx-test-core"))
            }
            configureQuality()
        }
    }

    private companion object {
        /** Maps a Gradle path such as `:voxlog-core:data` to `com.mikejhill.voxlog.core.data`. */
        fun namespaceFor(projectPath: String): String =
            "com.mikejhill.voxlog." +
                projectPath
                    .removePrefix(":voxlog-")
                    .replace(":", ".")
                    .replace("-", ".")
    }
}

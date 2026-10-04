package com.mikejhill.voxlog.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** Enables Jetpack Compose with the shared BOM, tooling and test dependencies (`voxlog.android.compose`). */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.getByType<CommonExtension>().buildFeatures.compose = true
            dependencies {
                val bom = platform(libs.libraryOf("androidx-compose-bom"))
                add("implementation", bom)
                add("testImplementation", bom)
                add("androidTestImplementation", bom)
                add("implementation", libs.libraryOf("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", libs.libraryOf("androidx-compose-ui-tooling"))
                add("debugImplementation", libs.libraryOf("androidx-compose-ui-test-manifest"))
                add("testImplementation", libs.libraryOf("androidx-compose-ui-test-junit4"))
                add("androidTestImplementation", libs.libraryOf("androidx-compose-ui-test-junit4"))
            }
        }
    }
}

package com.mikejhill.voxlog.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention for feature modules under `voxlog-feature`: an Android library with Compose, Hilt,
 * the design system and the UDF/ViewModel stack. Features never depend on each other.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("voxlog.android.library")
            pluginManager.apply("voxlog.android.compose")
            pluginManager.apply("voxlog.hilt")
            pluginManager.apply("io.github.takahirom.roborazzi")
            dependencies {
                add("implementation", project(":voxlog-core:model"))
                add("implementation", project(":voxlog-core:designsystem"))
                add("implementation", libs.libraryOf("androidx-compose-material3"))
                add("implementation", libs.libraryOf("androidx-compose-material-icons-extended"))
                add("implementation", libs.libraryOf("androidx-hilt-navigation-compose"))
                add("implementation", libs.libraryOf("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.libraryOf("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.libraryOf("androidx-navigation-compose"))
                add("testImplementation", project(":voxlog-core:testing"))
                add("testImplementation", libs.libraryOf("roborazzi"))
                add("testImplementation", libs.libraryOf("roborazzi-compose"))
                add("testImplementation", libs.libraryOf("roborazzi-junit-rule"))
            }
        }
    }
}

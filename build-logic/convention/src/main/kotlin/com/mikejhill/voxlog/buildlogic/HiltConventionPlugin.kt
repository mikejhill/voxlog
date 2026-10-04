package com.mikejhill.voxlog.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Adds Hilt dependency injection through KSP (`voxlog.hilt`). */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")
            dependencies {
                add("implementation", libs.libraryOf("hilt-android"))
                add("ksp", libs.libraryOf("hilt-compiler"))
            }
        }
    }
}

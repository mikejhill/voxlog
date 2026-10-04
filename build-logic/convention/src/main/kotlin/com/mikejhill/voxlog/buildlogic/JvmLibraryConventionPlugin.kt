package com.mikejhill.voxlog.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Convention for pure-Kotlin JVM modules with no Android dependency (`voxlog.jvm.library`). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            extensions.configure<JavaPluginExtension> {
                val javaVersion = JavaVersion.toVersion(libs.versionOf("javaTarget"))
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
            }
            configureKotlin()
            dependencies {
                add("testImplementation", libs.libraryOf("junit"))
                add("testImplementation", libs.libraryOf("truth"))
                add("testImplementation", libs.libraryOf("kotlinx-coroutines-test"))
                add("testImplementation", libs.libraryOf("turbine"))
            }
            configureQuality()
        }
    }
}

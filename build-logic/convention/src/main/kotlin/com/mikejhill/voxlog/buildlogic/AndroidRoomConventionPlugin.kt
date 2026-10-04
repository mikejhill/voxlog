package com.mikejhill.voxlog.buildlogic

import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Adds Room with KSP and exported schemas for migration testing (`voxlog.android.room`). */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room")
            pluginManager.apply("com.google.devtools.ksp")
            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }
            dependencies {
                add("implementation", libs.libraryOf("androidx-room-runtime"))
                add("implementation", libs.libraryOf("androidx-room-ktx"))
                add("ksp", libs.libraryOf("androidx-room-compiler"))
                add("testImplementation", libs.libraryOf("androidx-room-testing"))
                add("androidTestImplementation", libs.libraryOf("androidx-room-testing"))
            }
        }
    }
}

package com.mikejhill.voxlog.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Applies the static-analysis and coverage stack every module must pass:
 * Spotless (ktlint), detekt (with Compose rules), Kover and Dokka.
 */
internal fun Project.configureQuality() {
    pluginManager.apply("com.diffplug.spotless")
    pluginManager.apply("dev.detekt")
    pluginManager.apply("org.jetbrains.kotlinx.kover")
    pluginManager.apply("org.jetbrains.dokka")

    val ktlintVersion = libs.versionOf("ktlint")
    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**", "src/main/cpp/**")
            ktlint(ktlintVersion)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion)
        }
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        parallel.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        basePath.set(rootProject.layout.projectDirectory)
    }
    dependencies {
        add("detektPlugins", libs.libraryOf("compose-rules-detekt"))
    }
}

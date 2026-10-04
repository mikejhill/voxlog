package com.mikejhill.voxlog.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Convention for the single application module (`voxlog.android.application`). */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig.targetSdk = libs.versionOf("targetSdk").toInt()
                // Reproducible builds for F-Droid: keep the signed dependency-metadata blob out of the APK.
                dependenciesInfo.includeInApk = false
                dependenciesInfo.includeInBundle = false
            }
            configureQuality()
        }
    }
}

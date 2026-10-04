package com.mikejhill.voxlog.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Applies SDK levels, Java compatibility, lint and test options shared by all Android modules. */
internal fun Project.configureAndroidCommon(android: CommonExtension) {
    val javaVersion = JavaVersion.toVersion(libs.versionOf("javaTarget"))
    android.apply {
        compileSdk = libs.versionOf("compileSdk").toInt()
        defaultConfig.minSdk = libs.versionOf("minSdk").toInt()
        defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        compileOptions.sourceCompatibility = javaVersion
        compileOptions.targetCompatibility = javaVersion
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.unitTests.isReturnDefaultValues = true
        testOptions.animationsDisabled = true
        testOptions.unitTests.all { test ->
            // Robolectric's shared-memory shim reflects into JDK internals on recent JDKs.
            test.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            test.maxHeapSize = "2g"
        }
        lint.apply {
            warningsAsErrors = true
            abortOnError = true
            // Dependency freshness is Renovate's job; it must not fail builds.
            disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
            // Only fires on the untracked, machine-generated local.properties (and misfires on Windows paths).
            disable += "PropertyEscape"
        }
        packaging.resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
    }
    configureKotlin()
    dependencies {
        add("testImplementation", libs.libraryOf("junit"))
        add("testImplementation", libs.libraryOf("truth"))
        add("testImplementation", libs.libraryOf("kotlinx-coroutines-test"))
        add("testImplementation", libs.libraryOf("turbine"))
        add("testImplementation", libs.libraryOf("robolectric"))
        add("testImplementation", libs.libraryOf("androidx-test-core"))
        add("androidTestImplementation", libs.libraryOf("androidx-test-ext-junit"))
        add("androidTestImplementation", libs.libraryOf("androidx-test-runner"))
        add("androidTestImplementation", libs.libraryOf("truth"))
    }
}

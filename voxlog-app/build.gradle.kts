plugins {
    alias(libs.plugins.voxlog.android.application)
    alias(libs.plugins.voxlog.android.compose)
    alias(libs.plugins.voxlog.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.mikejhill.voxlog"

    defaultConfig {
        applicationId = "com.mikejhill.voxlog"
        versionCode =
            providers
                .gradleProperty("voxlog.versionCode")
                .orElse("1")
                .get()
                .toInt()
        versionName = providers.gradleProperty("voxlog.versionName").orElse("0.1.0").get()
    }

    signingConfigs {
        create("release") {
            // Supplied by CI (release.yml) or ~/.gradle/gradle.properties; never committed.
            val keystorePath = providers.gradleProperty("voxlog.signing.storeFile").orNull
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = providers.gradleProperty("voxlog.signing.storePassword").get()
                keyAlias = providers.gradleProperty("voxlog.signing.keyAlias").get()
                keyPassword = providers.gradleProperty("voxlog.signing.keyPassword").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (providers.gradleProperty("voxlog.signing.storeFile").isPresent) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.voxlogCore.model)
    implementation(projects.voxlogCore.data)
    implementation(projects.voxlogCore.datastore)
    implementation(projects.voxlogCore.designsystem)
    implementation(projects.voxlogFeature.capture)
    implementation(projects.voxlogFeature.notes)
    implementation(projects.voxlogFeature.search)
    implementation(projects.voxlogFeature.settings)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.voxlogCore.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    androidTestImplementation(projects.voxlogCore.testing)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.test.rules)
}

// Copies the light-theme screenshot baselines into the F-Droid/fastlane listing.
// Run after `recordRoborazziDebug`; CI's metadata-guard job fails if the two drift apart.
val generateStoreScreenshots by tasks.registering(Copy::class) {
    group = "publishing"
    description = "Renders key screens and copies them into fastlane/metadata as store screenshots."
    dependsOn("recordRoborazziDebug")
    from(layout.projectDirectory.dir("src/test/screenshots")) {
        include("*_light.png")
        exclude("x_*")
    }
    into(rootProject.layout.projectDirectory.dir("fastlane/metadata/android/en-US/images/phoneScreenshots"))
    rename { it.removeSuffix("_light.png") + ".png" }
}

plugins {
    alias(libs.plugins.voxlog.android.application)
    alias(libs.plugins.voxlog.android.compose)
    alias(libs.plugins.voxlog.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.mikejhill.voxlog"

    defaultConfig {
        applicationId = "com.mikejhill.voxlog"
        // Literal values on purpose: F-Droid's update checker reads them from this file at each tag.
        // Bump with `python scripts/bump_version.py <major.minor.patch>`; never edit by hand.
        versionCode = 100
        versionName = "0.1.0"
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
            // Emulators (x86_64) for development and CI.
            ndk.abiFilters += setOf("arm64-v8a", "x86_64")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 64-bit ARM phones only by default: keeps the APK small (ONNX Runtime ships ~35 MB per ABI).
            // Emulator benchmark runs add x86_64 with -Pvoxlog.releaseAbis=arm64-v8a,x86_64.
            ndk.abiFilters += providers.gradleProperty("voxlog.releaseAbis").orElse("arm64-v8a").get().split(",")
            if (providers.gradleProperty("voxlog.signing.storeFile").isPresent) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        // Release APKs are deliberately arm64-only to keep size down (see docs/adr/0004).
        disable += "ChromeOsAbiSupport"
    }

    testOptions.managedDevices.localDevices.create("pixel8api34") {
        device = "Pixel 8"
        apiLevel = 34
        systemImageSource = "aosp-atd"
    }
}

// Benchmark/profile variants are local-only and never distributed, so they can use the debug key.
android.buildTypes.matching { it.name.startsWith("benchmark") || it.name.startsWith("nonMinified") }.configureEach {
    signingConfig = android.signingConfigs.getByName("debug")
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
    implementation(libs.androidx.tracing)
    baselineProfile(projects.voxlogBenchmark)
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
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(projects.voxlogEngine.whisper)
}

// Copies the light-theme screenshot baselines and store graphics into the F-Droid/fastlane listing.
// CI's metadata-guard job (scripts/check_store_metadata.py) fails if they drift apart.
val generateStoreScreenshots by tasks.registering(Sync::class) {
    group = "publishing"
    description = "Renders key screens and store graphics and copies them into fastlane/metadata."
    dependsOn("recordRoborazziDebug")
    val screenshots = layout.projectDirectory.dir("src/test/screenshots")
    from(screenshots) {
        include("*_light.png")
        exclude("x_*")
        rename { it.removeSuffix("_light.png") + ".png" }
        into("phoneScreenshots")
    }
    from(screenshots) {
        include("store_icon.png", "store_feature_graphic.png")
        rename { if (it == "store_icon.png") "icon.png" else "featureGraphic.png" }
    }
    into(rootProject.layout.projectDirectory.dir("fastlane/metadata/android/en-US/images"))
}

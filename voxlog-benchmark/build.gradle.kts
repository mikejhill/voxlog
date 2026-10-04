import com.android.build.api.dsl.TestExtension

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

extensions.configure<TestExtension> {
    namespace = "com.mikejhill.voxlog.benchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // CI runs on emulators; absolute numbers there are indicative, regressions are what matter.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":voxlog-app"
    testOptions.managedDevices.localDevices.create("pixel8api34") {
        device = "Pixel 8"
        apiLevel = 34
        systemImageSource = "aosp-atd"
    }
}

baselineProfile {
    managedDevices += "pixel8api34"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}

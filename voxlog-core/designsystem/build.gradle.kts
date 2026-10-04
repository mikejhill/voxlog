plugins {
    alias(libs.plugins.voxlog.android.library)
    alias(libs.plugins.voxlog.android.compose)
    alias(libs.plugins.roborazzi)
}

dependencies {
    api(projects.voxlogCore.model)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.ui)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}

plugins {
    alias(libs.plugins.voxlog.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.voxlogCore.data)
    implementation(libs.kotlinx.serialization.json)
}

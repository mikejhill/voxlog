plugins {
    alias(libs.plugins.voxlog.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.voxlogCore.data)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.kotlinx.serialization.json)
}

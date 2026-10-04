plugins {
    alias(libs.plugins.voxlog.android.library)
    alias(libs.plugins.voxlog.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.voxlogCore.model)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}

plugins {
    alias(libs.plugins.voxlog.android.feature)
}

dependencies {
    implementation(projects.voxlogCore.data)
    implementation(projects.voxlogCore.datastore)
    implementation(projects.voxlogEngine.whisper)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.tracing)
}

plugins {
    alias(libs.plugins.voxlog.android.library)
}

dependencies {
    implementation(projects.voxlogCore.model)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.onnxruntime.android)
}

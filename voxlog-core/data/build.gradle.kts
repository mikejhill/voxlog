plugins {
    alias(libs.plugins.voxlog.android.library)
    alias(libs.plugins.voxlog.android.room)
    alias(libs.plugins.voxlog.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.voxlogCore.model)
    implementation(projects.voxlogCore.datastore)
    implementation(projects.voxlogEngine.whisper)
    implementation(projects.voxlogEngine.embeddings)
    implementation(projects.voxlogEngine.llm)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(projects.voxlogCore.testing)
    testImplementation(libs.androidx.work.testing)
}

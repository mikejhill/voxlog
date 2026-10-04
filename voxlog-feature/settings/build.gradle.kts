plugins {
    alias(libs.plugins.voxlog.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.voxlogCore.data)
    implementation(projects.voxlogCore.datastore)
    implementation(projects.voxlogEngine.llm)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
}

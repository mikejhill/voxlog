plugins {
    alias(libs.plugins.voxlog.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.voxlogCore.model)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.anthropic.java)
    testImplementation(libs.okhttp.mockwebserver)
}

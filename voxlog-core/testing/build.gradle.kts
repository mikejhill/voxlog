plugins {
    alias(libs.plugins.voxlog.android.library)
}

dependencies {
    api(projects.voxlogCore.model)
    api(projects.voxlogCore.data)
    api(projects.voxlogCore.datastore)
    api(projects.voxlogEngine.llm)
    api(libs.kotlinx.coroutines.test)
    api(libs.kotlinx.serialization.json)
    api(libs.androidx.room.runtime)
    api(libs.junit)
    api(libs.androidx.test.core)
}

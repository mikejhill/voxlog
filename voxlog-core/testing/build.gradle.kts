plugins {
    alias(libs.plugins.voxlog.android.library)
}

dependencies {
    api(projects.voxlogCore.model)
    api(projects.voxlogCore.data)
    api(libs.kotlinx.coroutines.test)
    api(libs.junit)
}

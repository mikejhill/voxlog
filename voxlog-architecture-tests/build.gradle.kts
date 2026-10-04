plugins {
    alias(libs.plugins.voxlog.jvm.library)
}

dependencies {
    testImplementation(libs.konsist)
}

tasks.test {
    // Konsist scans sources, so the task must rerun whenever any module's sources change.
    inputs.files(fileTree(rootDir) { include("voxlog-*/**/src/**/*.kt") })
}

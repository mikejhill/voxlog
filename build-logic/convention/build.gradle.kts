plugins {
    `kotlin-dsl`
}

group = "com.mikejhill.voxlog.buildlogic"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
    compileOnly(libs.dokka.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "voxlog.android.application"
            implementationClass = "com.mikejhill.voxlog.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "voxlog.android.library"
            implementationClass = "com.mikejhill.voxlog.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "voxlog.android.compose"
            implementationClass = "com.mikejhill.voxlog.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "voxlog.android.feature"
            implementationClass = "com.mikejhill.voxlog.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = "voxlog.android.room"
            implementationClass = "com.mikejhill.voxlog.buildlogic.AndroidRoomConventionPlugin"
        }
        register("hilt") {
            id = "voxlog.hilt"
            implementationClass = "com.mikejhill.voxlog.buildlogic.HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "voxlog.jvm.library"
            implementationClass = "com.mikejhill.voxlog.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}

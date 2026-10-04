plugins {
    alias(libs.plugins.voxlog.android.library)
}

android {
    defaultConfig {
        ndk {
            // arm64 for phones, x86_64 for emulators and CI. 32-bit devices are not supported.
            abiFilters += setOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static", "-DCMAKE_BUILD_TYPE=Release")
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }
}

dependencies {
    implementation(projects.voxlogCore.model)
    implementation(libs.kotlinx.coroutines.android)
}

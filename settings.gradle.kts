pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "voxlog"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":voxlog-app")
include(":voxlog-core:model")
include(":voxlog-core:data")
include(":voxlog-core:datastore")
include(":voxlog-core:designsystem")
include(":voxlog-core:testing")
include(":voxlog-feature:capture")
include(":voxlog-feature:notes")
include(":voxlog-feature:search")
include(":voxlog-feature:settings")
include(":voxlog-engine:whisper")
include(":voxlog-engine:embeddings")
include(":voxlog-engine:llm")
include(":voxlog-benchmark")
include(":voxlog-architecture-tests")

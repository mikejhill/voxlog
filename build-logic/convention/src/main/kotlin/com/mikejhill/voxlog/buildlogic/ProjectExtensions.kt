package com.mikejhill.voxlog.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** The `libs` version catalog shared by every module. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Returns the required version string for [alias] from the catalog. */
internal fun VersionCatalog.versionOf(alias: String): String = findVersion(alias).get().requiredVersion

/** Returns the library dependency for [alias] from the catalog. */
internal fun VersionCatalog.libraryOf(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).get()

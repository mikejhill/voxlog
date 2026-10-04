package com.mikejhill.voxlog.feature.settings.ui

/** Navigation out of the settings screen. */
data class SettingsNavigation(
    val onBack: () -> Unit,
    val onManageCategories: () -> Unit,
    val onManageLabels: () -> Unit,
    val onRequestLocationPermission: (isPrecise: Boolean) -> Unit,
    val appVersion: String,
)

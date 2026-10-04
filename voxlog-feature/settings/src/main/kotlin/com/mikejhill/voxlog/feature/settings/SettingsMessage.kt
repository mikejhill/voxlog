package com.mikejhill.voxlog.feature.settings

/**
 * One-off feedback shown as a snackbar. The ViewModel emits these; the screen turns them into
 * localized text, so no user-visible string lives in the ViewModel.
 *
 * @property detail optional technical detail (for example an error message) appended to the text.
 */
data class SettingsMessage(val kind: Kind, val detail: String? = null) {
    /** What happened. */
    enum class Kind {
        /** Model listing needs credentials first. */
        PROVIDER_NOT_CONFIGURED,

        /** The provider's model list could not be loaded. */
        MODEL_LIST_FAILED,

        /** A sync folder was chosen. */
        SYNC_FOLDER_SET,

        /** A manual sync was queued. */
        SYNC_STARTED,

        /** Export finished. */
        EXPORT_COMPLETE,

        /** Export failed. */
        EXPORT_FAILED,
    }
}

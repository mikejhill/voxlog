package com.mikejhill.voxlog.core.model

/**
 * The public intent contract for starting a capture. Launcher shortcuts, the Quick Settings tile
 * and in-app buttons all use these actions (with the app's package set), so no module needs a
 * compile-time reference to the capture activity.
 */
object CaptureIntents {
    /** Start recording a voice note immediately. */
    const val ACTION_RECORD_VOICE: String = "com.mikejhill.voxlog.action.RECORD_VOICE"

    /** Open the text composer. */
    const val ACTION_WRITE_TEXT: String = "com.mikejhill.voxlog.action.WRITE_TEXT"

    /** Open a note: `voxlog://note/{id}`. */
    const val NOTE_URI_PREFIX: String = "voxlog://note/"

    /** Optional [CategoryId] value; the default category is used when absent. */
    const val EXTRA_CATEGORY_ID: String = "com.mikejhill.voxlog.extra.CATEGORY_ID"

    /** Optional id of the launcher shortcut that started the capture, stored on the note. */
    const val EXTRA_SHORTCUT_ID: String = "com.mikejhill.voxlog.extra.SHORTCUT_ID"
}

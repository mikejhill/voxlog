# Permissions

Every permission in the merged manifest must be listed here; CI's `permissions-guard` job fails if one
is added without documentation. The same text is used in the store listing.

| Permission | Required? | Why |
|---|---|---|
| `RECORD_AUDIO` | Yes, for voice notes | Records voice notes. Only used while a recording is in progress, which always shows a notification. |
| `FOREGROUND_SERVICE` | Yes | Lets recording and long transcriptions continue with the screen off or the app closed. |
| `FOREGROUND_SERVICE_MICROPHONE` | Yes | Required by Android 14+ for the recording foreground service. |
| `FOREGROUND_SERVICE_DATA_SYNC` | Yes | Required by Android 14+ for background transcription of long recordings. |
| `POST_NOTIFICATIONS` | Recommended | Shows the recording notification with its Stop button and transcription progress. |
| `INTERNET` | Yes | Downloads the on-device speech and search models once (from Hugging Face, checksum-verified). Optional AI processing and hooks also use it, only when configured. |
| `ACCESS_NETWORK_STATE` | Yes | Lets model downloads wait for a connection. |
| `ACCESS_COARSE_LOCATION` | Optional | Only requested when **Save location** is turned on; tags notes with approximate location. |
| `ACCESS_FINE_LOCATION` | Optional | Only requested when **Precise location** is turned on. |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | Library | Added by WorkManager so background processing survives Doze and reboots. |
| `com.mikejhill.voxlog.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Library | Added by AndroidX to protect internal broadcast receivers. Not visible to users. |

## What VoxLog does not do

- No analytics, crash reporting, ads or tracking libraries.
- No Google Play services.
- Nothing leaves the device unless you configure an AI provider, a hook or a sync folder.

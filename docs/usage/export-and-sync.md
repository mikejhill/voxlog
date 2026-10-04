# Export, backup and sync

## Export everything

Settings → Storage & sync → **Export everything** creates a ZIP:

| File | Contents |
|---|---|
| `notes.json` | Every note with all metadata (`schema_version: 1`). |
| `notes.csv` | The same data as a spreadsheet; labels are separated by `;`. |
| `audio/` | Retained recordings (`.m4a`). |

Both `notes.json` and `notes.csv` have an `audio_file` column with the path of the note's recording
inside the ZIP, when there is one.

Columns: `id, title, text, raw_transcript, category, labels, capture_method, created_at, updated_at,
duration_millis, audio_file, latitude, longitude, place_name, language`. Times are ISO-8601 UTC.

## Sync folder

Settings → Storage & sync → **Sync folder** lets you pick any folder the system file picker can
show. Point it at a folder your sync app manages:

- **Nextcloud** or **Syncthing**: pick a synced folder.
- **OneDrive / Google Drive**: pick a folder in their app's storage location (availability depends
  on the app).

VoxLog then keeps the folder up to date after every change:

```
index.json
notes/2026/10/2026-10-04-morning-walk-by-the-river-1a2b3c4d.md
audio/1a2b3c4d-....m4a
```

Each Markdown file starts with YAML front matter (title, category, labels, times, location) followed by
the note text, so the folder works as an Obsidian vault.

Sync is one-way: edits made in the folder are not read back into VoxLog. Files VoxLog didn't create are
never touched.

## Android backup

Notes, settings and audio are included in Android's device backup and device-to-device transfer.
API keys and downloaded models are not.

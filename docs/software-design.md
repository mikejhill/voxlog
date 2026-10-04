# Software design

## Data model

| Entity | Notes |
|---|---|
| `Note` | `captureMethod` (VOICE_RECORDING / TEXT_ENTRY, immutable), `title` + `titleSource` (DEFAULT / USER / AUTO), editable `text`, unmodified `rawTranscript`, UTC `createdAt`/`updatedAt`, `durationMillis`, retained `audioFileName`, optional location, `status`. |
| `Category` | Exactly one per note. `Uncategorized` (id `uncategorized`) is a system row that cannot be renamed or deleted. Per-category settings: save audio, default labels, AI toggles. |
| `Label` | Many-to-many with notes; names unique ignoring case. |
| `NoteEmbedding` | 384-dim float32 vector, model id and a hash of the embedded text. |

### Time

All stored and exported timestamps are UTC epoch milliseconds / ISO-8601 `Z` strings. The single
exception is the default title, which is the capture time rendered as ISO-8601 **with the device's
UTC offset at capture time** (e.g. `2026-10-04T08:15:00-05:00`), so a title records local wall time
even after travel or daylight-saving changes.

### Capture-time settings

"Save audio" and default labels are resolved from the category **at capture time** and snapshotted
on the note (`should_retain_audio`). If the user switches category on the record screen before
stopping, the final category's settings apply. Moving a note later never re-applies them.

## Processing pipeline

| Step | Worker | Runs when | Failure behavior |
|---|---|---|---|
| Transcribe | `TranscriptionWorker` (foreground) | After every voice capture; one at a time via a shared queue | Network/IO errors retry (model download); decode errors mark the note FAILED with a log. |
| Post-process | `PostProcessingWorker` | After transcription / text capture / on demand | Retryable errors retry up to 5 times; then the note is marked READY with a log line. |
| Embed | `EmbeddingWorker` | After capture and (debounced) after every edit | Skipped silently until the model is downloaded. |
| Sync | `FolderSyncWorker` | After any change (debounced) | Retries on IO errors. |

Transcription processes audio in 5-minute chunks (bounded memory) and feeds the tail of the previous
chunk as Whisper's initial prompt to keep sentences coherent. Partial text is saved after every chunk.

### AI post-processing

All enabled tasks (cleanup, auto-name, auto-label, auto-categorize) run in **one** request that returns
a JSON object constrained by a JSON schema containing only the requested fields. Safety rules:

- Cleanup only applies to voice notes and only if the user hasn't edited the text since transcription.
- Auto-name only replaces `DEFAULT` titles, never `USER` ones.
- Auto-categorize only moves notes that are still in Uncategorized.
- `rawTranscript` is never modified.

## Search

1. The query is tokenized into words; each becomes an FTS4 prefix term (`word*`) and all must match.
   This also neutralizes FTS operators typed by the user.
2. Matches are ranked with Okapi BM25 computed from `matchinfo('pcnalx')`, weighting title 2.0, text
   1.0 and raw transcript 0.5.
3. If semantic search is enabled and the model is present, the query is embedded and compared with
   every note vector by cosine similarity (brute force — fast enough for tens of thousands of notes).
   Hits ≥ 0.35 that full-text search did not already return are appended, best first.

Full-text matches therefore always rank above semantic matches.

## Storage layout

```
files/recordings/<id>.wav   raw 16 kHz mono PCM, deleted after transcription
files/audio/<id>.m4a        retained audio (AAC-LC 64 kbps)
files/models/               downloaded models (SHA-256 verified)
files/datastore/            settings JSON
no_backup/secrets/          Keystore-encrypted API keys (never backed up or exported)
databases/voxlog.db         Room database
```

## Export format

A ZIP containing `notes.json` (versioned, `schema_version: 1`), `notes.csv` and `audio/`. Both JSON and
CSV include an `audio_file` field with the relative path of the note's audio, if any. See
[usage/export-and-sync.md](usage/export-and-sync.md).

## Folder sync format

One-way mirror (device → folder):

```
index.json                                        same document as notes.json
notes/YYYY/MM/<date>-<slug>-<id8>.md              Markdown with YAML front matter
audio/<id>.m4a
```

Only files VoxLog created (recognized by the `-<8 hex>.md` suffix) are ever deleted from the folder.

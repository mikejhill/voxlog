# voxlog-core:data

Everything that stores or processes notes: the Room database (with FTS4), repositories, the WorkManager processing pipeline (transcribe → post-process → embed → sync), hybrid search, model downloads, audio recording/transcoding, export and SAF folder sync.

**May depend on:** `core/model`, `core/datastore`, `engine/*`.

## Entry points

- `NoteRepository`, `CategoryRepository`, `LabelRepository`
- `SearchRepository`
- `NoteProcessingScheduler`, `CaptureRecovery`
- `WavRecorder`, `UserFilesRepository`, `ModelRepository`

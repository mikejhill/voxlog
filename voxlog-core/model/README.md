# voxlog-core:model

Pure-Kotlin domain model shared by every module: notes, categories, labels, identifiers, filters, the default-title rule and the catalog of downloadable models.

**May depend on:** Nothing. No Android imports (enforced by Konsist).

## Entry points

- `Note`, `Category`, `Label`, `NoteFilter`
- `DefaultNoteTitle`
- `ModelCatalog`
- `CaptureIntents` (public intent contract)

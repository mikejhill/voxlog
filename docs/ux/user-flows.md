# User flows

## Quick voice note from the home screen shortcut

```mermaid
flowchart LR
    A[Long-press VoxLog icon] --> B[Tap 'Voice note' or a category shortcut]
    B --> C{Mic permission?}
    C -- first time --> D[Grant] --> E
    C -- granted --> E[Recording starts immediately]
    E --> F[Talk for as long as needed<br/>screen can be off]
    F --> G[Tap Stop<br/>in app or notification]
    G --> H[Saved ✓ auto-closes]
    H --> I[Transcript appears on the note in the background]
```

## Typed note

```mermaid
flowchart LR
    A[Tap 'Text note' shortcut or the write FAB] --> B[Composer opens with keyboard up]
    B --> C[Optionally edit the timestamp title or category]
    C --> D[Type] --> E[Save] --> F[Back where you were]
```

## Review and organize

```mermaid
flowchart TD
    A[Open VoxLog] --> B[Filter by category chip and/or label chips]
    B --> C[Tap a note]
    C --> D[Edit title or text — saved automatically]
    C --> E[Change category]
    C --> F[Add or remove labels]
    C --> G[Play or delete audio]
    C --> H[Re-run AI processing]
```

## Find something

```mermaid
flowchart LR
    A[Search icon] --> B[Type words]
    B --> C[Matches: notes containing the words, best first]
    B --> D[Related notes: similar meaning, if semantic search is on]
    C --> E[Open note]
    D --> E
```

## Set up a category with its own shortcut

```mermaid
flowchart LR
    A[Settings → Categories] --> B[New category]
    B --> C[Pick color and icon]
    C --> D[Choose: save audio? default labels? AI tasks?]
    D --> E[Add voice shortcut → launcher asks to place it]
```

## Optional AI processing

```mermaid
flowchart LR
    A[Settings → AI processing] --> B[Choose provider]
    B --> C[Enter API key or base URL]
    C --> D[Pick a model from the provider list]
    D --> E[Enable cleanup / auto-name / auto-label / auto-categorize]
    E --> F[New notes are processed after transcription]
```

## Back up and sync

```mermaid
flowchart LR
    A[Settings → Sync folder] --> B[Pick a folder<br/>Nextcloud, Syncthing, Drive, OneDrive…]
    B --> C[Notes mirrored as Markdown + audio after every change]
    A2[Settings → Export everything] --> D[ZIP: notes.json, notes.csv, audio/]
```

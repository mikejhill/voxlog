# HTTP hooks

Hooks let you send each new note to your own endpoint (Home Assistant, n8n, a personal API, a
self-hosted transcription service) and optionally change the note from the response.

Configure them in **Settings → Custom hooks**. A hook applies to every category unless you select
specific categories.

## When hooks run

After a note is saved, transcribed (voice notes) and processed by the optional built-in AI tasks.
Hooks run in name order. A failing hook is logged on the note and never blocks the save.

## Request

`POST <your URL>` with your custom headers.

- Without audio: `Content-Type: application/json`, body is the payload below.
- With "Send audio file": `multipart/form-data` with a `payload` part (the JSON below) and an
  `audio` part (`audio/mp4`, AAC in an `.m4a` container), only if the note has retained audio.

```json
{
  "schema_version": 1,
  "event": "note.created",
  "note": {
    "id": "0d8a3b6e-5f0c-4d1e-9a51-6f7c1d1f2b3a",
    "title": "2026-10-04T08:15:00-05:00",
    "text": "Blood pressure 118 over 76.",
    "raw_transcript": "blood pressure 118 over 76",
    "category": "Health log",
    "labels": ["blood-pressure"],
    "capture_method": "VOICE_RECORDING",
    "created_at": "2026-10-04T13:15:00Z",
    "duration_millis": 4200,
    "latitude": null,
    "longitude": null
  }
}
```

Timestamps are ISO-8601 UTC. `raw_transcript` is null for typed notes.

## Response

Any 2xx status. The body is optional; an empty body changes nothing. To modify the note, return:

```json
{
  "text": "replacement body",
  "title": "replacement title",
  "add_labels": ["from-hook"],
  "category": "Name of an existing category"
}
```

All fields are optional. Unknown fields are ignored. Labels are created if needed. A category is
matched by name, ignoring case.

## Errors and retries

- Non-2xx or unreachable endpoints are logged on the note ("Hook "name" failed: …").
- Hooks are not retried automatically; use **Re-run AI processing** on a note to call them again.

## Compatibility

`schema_version` increases only on incompatible changes. Additive fields can appear at any time.

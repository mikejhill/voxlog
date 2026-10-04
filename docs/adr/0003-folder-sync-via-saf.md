# ADR 0003: Cloud storage through folder sync (SAF) first

- Status: Accepted
- Date: 2026-10-04

## Context

The user prefers their own cloud provider over a VoxLog-hosted backend, and wants to host nothing.
Native Google Drive and OneDrive integrations need OAuth clients, app verification and per-provider code.

## Decision

Version 1 mirrors notes one way into a folder the user picks with the Storage Access Framework,
as Markdown with YAML front matter plus audio files. Any provider that exposes a folder works
(Nextcloud, Syncthing, the OneDrive or Google Drive apps). Native provider APIs are deferred.

## Consequences

Sync is one-way (device → folder). Two-way sync and conflict handling are future work. The Markdown
layout makes notes readable in any editor and compatible with Obsidian.

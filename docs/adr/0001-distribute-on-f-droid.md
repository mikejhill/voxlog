# ADR 0001: Distribute on F-Droid with FOSS-only dependencies

- Status: Accepted
- Date: 2026-10-04

## Context

VoxLog has one primary user and needs a free, low-friction distribution channel. Google Play now
requires a paid account and a 14-day closed test with 12 testers for new personal accounts, and
favors Play Services-based libraries.

## Decision

Target F-Droid. Use only FOSS dependencies (no Play Services, Firebase or ML Kit). Ship reproducible
builds so F-Droid publishes our signed APK, and run a self-hosted F-Droid repo on GitHub Pages for
immediate availability.

## Consequences

No proprietary SDKs (e.g. fused location, ML Kit speech). Releases must be byte-for-byte
reproducible, which constrains native builds and baseline profiles (see release runbook).

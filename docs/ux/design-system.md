# Design system and UX principles

## Principles

1. **Capture first.** The fastest path in the app is "start recording". It is one tap from the
   launcher (static and per-category shortcuts) and the most prominent control on the home screen.
2. **Nothing blocks the user.** Saving is instant; slow work (transcription, AI, sync) happens in the
   background and shows progress on the note itself.
3. **Undo over confirm.** Reversible actions (deleting a note from the list, moving) use undo
   snackbars. Only irreversible actions (deleting audio, deleting from the detail screen) confirm.
4. **Every state is designed.** Loading, empty, filtered-empty, partial (transcribing) and error states
   all have explicit UI.
5. **Accessible by default.** 48 dp touch targets, content descriptions on icons, text that scales to
   200%, and color is never the only signal (category badges pair color with an icon and name).

## Material 3

- Material 3 components and tokens throughout (`androidx.compose.material3`).
- **Dynamic color** (Material You) on Android 12+, with a branded fallback palette (deep indigo
  primary, warm coral tertiary) in `voxlog-core/designsystem/.../theme/Theme.kt`.
- Light, dark and follow-system themes; edge-to-edge with correct insets; predictive back enabled.
- Shapes: 6/10/16/24/32 dp corner scale. Spacing: 4 dp scale (`Spacing`).
- The recording timer uses a monospaced style so digits don't jitter.

## Adaptive layout

The notes home uses `NavigableListDetailPaneScaffold`: list and detail side by side on tablets and
unfolded foldables, single pane with navigation on phones.

## Shared components (`voxlog-core/designsystem`)

| Component | Use |
|---|---|
| `CategoryBadge` | Icon + name of a category in metadata rows. |
| `CategoryChip` | Tappable category selector on capture screens. |
| `CategoryPickerSheet` | Modal bottom sheet listing categories. |
| `LabelTag` | Non-interactive `#label` tag on note cards. |
| `EmptyState` | Centered icon, title, message and optional action. |
| `ScreenRoot` | Activity root; exposes test tags to UI Automator. |
| `LocalClock` | Clock for relative times, so previews and screenshots are deterministic. |

## Screens

| Screen | Key patterns |
|---|---|
| Notes list | Large "Record" extended FAB plus a small "write" FAB; category and label filter chips; swipe to delete with undo; live "Transcribing n%" progress on cards. |
| Record | One huge Stop button, a big elapsed timer, a scrolling level meter, the target category (changeable mid-recording), a low-storage warning, haptic feedback. After Stop: "Saved" confirmation that auto-closes. |
| Text composer | Keyboard up immediately, timestamp title prefilled and editable, category chip, single Save action. |
| Note detail | Inline-editable title and body (auto-saved), audio player, labels, full metadata. |
| Search | Focused query field, category chips, "Matches" then "Related notes". |
| Settings | Grouped list: capture, speech model, search, AI (optional), hooks, storage and sync, appearance. |

Screenshots of every screen in light and dark are kept in `voxlog-app/src/test/screenshots/` and
verified in CI.

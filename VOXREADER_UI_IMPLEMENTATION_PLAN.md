# Vox Reader UI/UX implementation plan

**Owner:** Luna (implementation)  
**Reviewer:** Codex (milestone gates)  
**Inputs:** `VOXREADER_DEEP_UI_UX_AUDIT.md`, `VOXREADER_ELEVENREADER_STANDARD_AUDIT.md`, `docs/design/voxreader-ui-concept.png`  
**Rule:** one milestone at a time; do not start the next milestone until the current gate passes.

## Delivery rules

1. Preserve user data and current playback behavior. UI work must not silently change parsing, progress, bookmarks, highlights, imported files, or generated audio.
2. Reuse the existing Compose/Material stack and bundled fonts. Add no dependency unless the milestone explicitly requires it and the existing stack cannot cover the behavior cleanly.
3. Keep diffs bounded. No package-wide rename, architecture rewrite, speculative component framework, or formatting of unrelated files.
4. Use Material components for semantics where possible. Custom components must expose name, role, state/value, focus indication, and a 48 dp minimum target.
5. Every destructive action names the target and consequence. Long-press selects; it never deletes immediately.
6. All visible new copy goes through string/plural resources.
7. Every milestone includes compact, medium/portrait, and expanded/landscape validation plus 200% font-scale smoke testing where its screens are affected.
8. Before handoff, run the milestone checks and report changed files, tests, known limitations, and screenshots.
9. Do not commit, push, publish, rename the application ID, or change signing without reviewer approval.

## Review gates

At each major gate Codex will inspect:

- `git diff --check` and the complete scoped diff;
- accidental changes outside the milestone write set;
- Compose state ownership and process/configuration restoration;
- accessibility semantics and keyboard/Back behavior;
- destructive and error paths;
- unit/Compose tests and debug build;
- screenshots on the connected device at affected widths;
- regression of import, playback, progress, bookmarks, and deletion.

A gate fails if tests are skipped, copy overpromises behavior, accessibility requires a gesture-only action, user data can be lost silently, or the implementation introduces an unnecessary abstraction/dependency.

## Milestone 0 — establish a reproducible baseline

**Purpose:** make every later visual diff measurable.

**Write scope:** tests/screenshots only if a missing baseline check must be added; otherwise read-only.

**Work:**

- Record current build/test/lint status and known pre-existing lint failures.
- Capture Library, Search, Bookmarks, Import, Book details, Reader, Voice, Settings, Stats, and About at compact and expanded widths.
- Confirm the connected device display/rotation/font settings are restored after captures.
- Confirm the two audit documents and concept image remain untouched.

**Gate 0:** baseline recorded; no app behavior changed.

## Milestone 1 — typography, contrast, semantics foundation

**Purpose:** make the existing app visually coherent and accessible before rearranging screens.

**Primary files:**

- `app/src/main/java/com/voxleaf/reader/ui/theme/Type.kt`
- `app/src/main/java/com/voxleaf/reader/ui/theme/Color.kt`
- `app/src/main/java/com/voxleaf/reader/feature/reader/ReaderScreen.kt`
- `app/src/main/java/com/voxleaf/reader/feature/settings/SettingsScreen.kt`
- `app/src/main/java/com/voxleaf/reader/core/ui/VoxLeafApp.kt`
- `app/src/main/java/com/voxleaf/reader/core/navigation/NavGraph.kt`
- `app/src/main/res/values/strings.xml`
- focused tests only

**Work:**

1. Define and use bundled Inter for app UI roles.
2. Keep Newsreader for editorial/display roles, Source Serif for Reader body, and JetBrains Mono for timers/counters.
3. Map Reader `Book`, `Clean`, and `Mono` choices to bundled Source Serif, Inter, and JetBrains Mono rather than Android generic families.
4. Use dark content on cyan surfaces and raise tertiary text contrast to AA.
5. Remove hard-coded 28/38 sp top-level titles where a typography role exists.
6. Fix Stats route title.
7. Remove the notification permission prompt from activity startup; do not invent a replacement prompt yet.
8. Add missing roles/selected states/progress semantics to shared controls touched by this milestone.

**Tests:** typography family mapping, route title, primary-button contrast token assertion where practical, Compose semantics for one representative tab/card/progress control.

**Gate 1:** debug build and unit tests pass; affected screens remain usable at 200%; no generic font family remains in Reader choices; no white-on-cyan labels.

## Milestone 2 — Library quality and safe multi-select

**Purpose:** deliver the most-requested library-management interaction and make covers useful.

**Primary files:** Library contract/view model/screen, shared book card/cover components, string resources, focused tests.

**Work:**

1. Replace blank cover placeholders with deterministic title/initial fallback art using existing `SpineColor`.
2. Show title, author, and progress with bounded lines under/in every library card.
3. Add sort: Recent, Title, Author, Progress, Date added.
4. Add filters: All, In progress, Unread, Finished, Favorites, then real metadata categories.
5. Long-press enters selection mode and selects one book.
6. Tap toggles additional books while selection mode is active.
7. Context bar shows exact count, Close, Select all, and Delete.
8. Card overflow exposes `Select` as the accessible/keyboard alternative.
9. Back/Escape exits selection before navigating away.
10. Confirmation names exact count and all removed data. Never delete on long-press.
11. Keep current hard-delete semantics; do not display a fake Undo.

**Tests:** selection reducer/state, select all, deselect, Back behavior, exact deletion IDs, plural confirmation copy, TalkBack labels, rotation/recomposition restoration.

**Gate 2:** delete only affects selected IDs; no accidental deletion path; selection works by touch, TalkBack, keyboard, and mouse; compact and expanded screenshots approved.

## Milestone 3 — adaptive Library and app shell

**Purpose:** stop stretching phone layouts on tablets.

**Primary files:** app shell, Library screen/components, Book details screen, focused navigation/layout tests.

**Work:**

- Derive compact/medium/expanded behavior from window width, not device identity.
- Compact: bottom navigation and one-pane Library.
- Medium: rail plus bounded content.
- Expanded: rail plus Library list/grid and selected-book supporting pane.
- Keep controls/text fields bounded; no tablet-wide buttons.
- Preserve selected destination and selected book through resize/rotation.
- Use the existing Compose stack first. Add Material adaptive navigation only if it reduces code and is version-compatible.

**Gate 3:** feature parity across orientations and split-screen; no empty two-thirds canvas; no stretched dialogs/fields/buttons; state survives resizing.

## Milestone 4 — truthful Search and consistent Bookmarks

**Purpose:** make discovery accurate and saved content coherent.

**Search:** rename to `Search library`; accurately describe metadata fields; remove static genre chips; use informative result rows, count, sorting, and real filters. Do not imply page/full-text search.

**Bookmarks:** standardize `Bookmarks` and `Highlights`; use proper tabs; improve row metadata; add search/sort and export scope.

**Gate 4:** result relevance is explainable; terminology is consistent across nav/Reader/details; tabs announce selected state; no full-text promise before Room FTS exists.

## Milestone 5 — import resilience and Android ingestion

**Purpose:** make importing recoverable and native to Android.

**Work:**

- Convert scan pages from in-memory bitmaps to persisted temporary image references and a recoverable session manifest.
- Remove the eight-page memory-driven ceiling; preflight storage and show practical limits.
- Add page reorder/remove/rotate/crop/retake and OCR review.
- Add stage-based progress, cancel, retry, and specific errors.
- Handle password/encrypted/unsupported/malformed files explicitly.
- Add `ACTION_SEND`, `ACTION_SEND_MULTIPLE`, `ACTION_VIEW`, and optional `PROCESS_TEXT` entry points that all converge on the existing validated importer.
- Add Paste text and URL only as separate bounded follow-ups if their parsing paths are production-ready.

**Gate 5:** process-death camera test passes; temporary files are bounded/cleaned; shared inputs cannot bypass validation; cancellation cannot leave a half-imported library item.

## Milestone 6 — Reader and playback hierarchy

**Purpose:** make listening calm and ergonomic without changing the TTS engine.

**Work:**

- Primary controls: Back 10, Play/Pause, Forward 10.
- Move sentence/section navigation to secondary controls.
- Add tap-to-show/hide chrome without moving text.
- Put text appearance controls in Reader with live preview.
- Show `Voice name · On device/Online`, not engine implementation status.
- Provide visible and accessibility-equivalent highlight actions.
- Make Contents and Sleep surfaces bounded/adaptive and show current state.
- Contents search and current-section marker.

**Gate 6:** playback behavior and progress remain unchanged; media keys/TalkBack/keyboard work; player uses materially less landscape height; text position does not jump when chrome changes.

## Milestone 7 — document-structure review

**Purpose:** solve the core trust problem exposed by malformed chapter detection.

**Dependency:** parser/storage design must be reviewed before UI coding begins.

**Work:**

- Persist section source, confidence/reason, stable text anchor, and manual-edit status.
- Build a list + source-preview editor, not a graphical page editor.
- Support rename, demote/ignore, merge, split, and reorder.
- Preview re-analysis differences before applying.
- Preserve or explicitly remap progress, bookmarks, and highlights.
- Never silently replace user-corrected structure.

**Gate 7:** the live `THOMAS HORN` document can be corrected without re-import; re-analysis preserves manual edits and user state; transactional tests cover interruption/failure.

## Milestone 8 — Voice, Settings, Stats, About, privacy

**Work:** voice preview/filter/recent/favorite and dynamic counts; categorized Settings with expanded list-detail; simplified reader themes and preview; bounded accessible Stats; user-facing About; font/open-source licenses; optional Hide content in Recents; Storage management; local export/restore.

**Gate 8:** no developer stack on About; every setting announces state; private/offline consequences are explicit; voice list remains responsive with hundreds of rows.

## Milestone 9 — release verification

- Full build, unit tests, instrumentation/migration tests, lint with every release-impacting error resolved.
- Compact/medium/expanded screenshots at 100%, 130%, and 200% font scale.
- TalkBack, Switch Access, keyboard/D-pad, mouse, RTL/pseudolocale, reduced motion, light/dark/sepia/black.
- Import matrix for supported formats, OCR, malformed/encrypted, low storage, interruption, and process death.
- Playback matrix for calls, alarms, audio focus, headset removal, Bluetooth, lock screen, background, swipe-away, offline/network loss.
- Single/bulk deletion and local export/restore tests.
- Final application ID, versioning, signing, font/model/native licenses, privacy policy, Play data-safety declaration, icon, screenshots, and store copy.

**Release gate:** no P0 issue open; no user-data-loss defect; no actionable NAF node in core flows; crash-free beta and chapter-accuracy targets met.

## Mandatory milestone report template

Luna must finish each milestone with:

1. Outcome and user-visible behavior.
2. Exact files changed.
3. Tests run with pass/fail counts.
4. Screenshots/device configurations checked.
5. Accessibility checks completed.
6. Known limitations or deferred work.
7. Confirmation that no commit/push was performed.

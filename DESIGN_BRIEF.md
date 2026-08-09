# Vox Reader — Design Brief

Paste this into Claude (or any design tool) to generate UI concepts that match what actually ships.

---

Design a mobile app UI for **Vox Reader** — an offline, privacy-first audiobook reader for Android.

## What it does

Users import their own documents (PDF, EPUB, DOCX, scanned pages via OCR) and listen to them read aloud by a bundled on-device TTS voice. Nothing leaves the phone — no accounts, no cloud, no tracking.

Positioning line: **"Read it. Hear it. A private library with an offline voice."**

## Who uses it

People converting personal documents — research papers, work policy docs, ebooks they own — into listening material during commutes, chores, workouts. They are document-hoarders, not casual readers. They value control and privacy over social features.

## Screens to design

1. **Library** (home) — hero header, search field, "Now listening" resume cards with progress, filter chips (All / Favorites / Fiction / Classic / Mystery), scrollable book list, import action, bottom nav (Library / Bookmarks / Search / Settings)
2. **Book details** — cover, title, author, play CTA, chapter list with listening time per chapter, bookmark + delete actions
3. **Reader / player** — active sentence highlighting, play/pause/skip, speed and voice controls, sleep timer, chapter scrubber

## Existing brand system (honor it, do not replace)

| Role | Value |
|---|---|
| Background | `#111312` near-black |
| Surface layers | step up to `#1B1E1C` → `#23272A` → `#2C3130` |
| Accent | Signal Orange `#FF6B35` — playback state, active nav, primary actions only |
| Secondary | Leaf green `#16624A` / pale `#C4EAD8` — progress, secondary emphasis |
| Type | Inter variable, weights 400–700 |
| Serif | wordmark and hero line only |

Dark theme is primary. Material 3 structure applies (native Android): navigation bar, FAB, chips, snackbars, tonal elevation.

## Design constraints

- **Most imported documents have no cover art.** Solve the empty-cover problem beautifully — generated covers, typographic treatments, whatever reads as designed rather than as a missing asset.
- **Titles are frequently long, ugly, ALL-CAPS filenames.** The layout must survive a 20-word title without breaking.
- **Progress state matters everywhere** — users return mid-document constantly.
- 48dp minimum touch targets, WCAG AA contrast on all text.

## What I want from you

Do not give me generic Material dark theme with an accent color swapped in. I want a distinct visual identity — the app should be recognizable as Vox Reader, not "an Android app." Push on hierarchy, spacing rhythm, and how covers and progress are expressed.

Show the **Library** screen first, and explain the one idea holding the design together.

---

## Optional modifiers

Append either line to change the output:

- **Bolder swing:** "The current build is too safe — take a real position."
- **Explore first:** "Give me 3 distinct directions before refining one."

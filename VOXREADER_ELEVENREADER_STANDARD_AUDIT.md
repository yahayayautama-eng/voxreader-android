# Vox Reader: ElevenReader-standard product and engineering audit

**Audit date:** 18 August 2026  
**Audited build:** `com.aistudio.voxreader.xyz.debug`, commit `fbaf718`  
**Evidence:** source review, live Android tablet inspection, UI accessibility tree, 92 unit tests, Android lint, and current official product documentation.

For the screen-by-screen redesign specification, accessibility criteria, UX copy, and Luna delivery phases, see `VOXREADER_DEEP_UI_UX_AUDIT.md`.

## Executive verdict

Vox Reader has a credible beta foundation and one valuable advantage that ElevenReader cannot easily copy: a private, account-free reader with a genuinely on-device voice. It is not yet ready to claim ElevenReader-level quality.

The gap is not mainly the number of voices or AI features. The gap is trust. In the live test book, Vox Reader exposed **163 chapters** and displayed the repeating page header **“46 THOMAS HORN”** as a chapter. A polished interface cannot compensate for confidently presenting the wrong book structure.

The correct product strategy is:

> **Make Vox Reader the most trustworthy private/offline document listener on Android.**

Do not try to clone ElevenReader's catalog, voice marketplace, cloud sync, publishing system, or GenFM yet. First make every imported document structurally correct, every listening session dependable, and every screen accessible.

### Current maturity

| Area | State | Verdict |
|---|---|---|
| Local privacy | Strong | Real differentiator; preserve it |
| File-format breadth | Strong beta | Broader local format support than the benchmark, but uneven parsing quality |
| Chapter accuracy | Release blocker | Real PDF produces false chapters from running headers |
| Offline narration | Promising | Eight English presets; quality and battery use need measured benchmarks |
| Online narration | Functional but risky | Edge endpoint is unofficial and should not be a release dependency |
| Playback | Feature-rich beta | Needs modern media-session behavior and interruption testing |
| Reader UI | Visually distinctive | Needs hierarchy, adaptive layout, semantic polish, and less control density |
| Accessibility | Below release bar | Missing semantics, contrast failures, and nearly all copy is hard-coded |
| Test/build health | Mixed | 92 unit tests pass; lint fails with 3 compatibility errors and 51 warnings |
| Release readiness | Not ready | Licensing, CI, Play policy, ABI, lint, and signed release gates remain |

## What Vox Reader already does well

- Imports TXT, EPUB, DOCX, text/scanned PDF, MOBI, AZW/AZW3, FB2, and camera scans.
- Copies imported files into private app storage and disables Android backup.
- Provides eight bundled English narrator presets through Sherpa ONNX.
- Offers optional online Edge voices with an explicit privacy warning and offline fallback.
- Supports sentence-follow highlighting, seek-by-sentence, chapter navigation, sleep timer, bookmarks, highlights, notes, listening statistics, search, reading themes, font family, font size, and line spacing.
- Preserves local reading progress and uses Room migrations rather than destructive fallback.
- Has meaningful parser and reader tests; the current 92 unit tests all pass.
- Already uses adaptive bottom navigation/navigation rail behavior.

Those are solid foundations. The next release should improve them rather than replace them.

## Benchmark: what “ElevenReader standard” means in 2026

ElevenReader currently advertises file and link import, camera scan, pasted text, 1,000+ voices in 30+ languages, synchronized highlighting, speed control, offline downloads, pronunciation rules, bookmarks with notes, and device sync. Its official help center describes four ingestion paths: write/paste, camera scan, URL, and EPUB/PDF/TXT upload. It also now spans Android, iOS, and web/desktop. Sources: [Google Play listing](https://play.google.com/store/apps/details?id=io.elevenlabs.readerapp), [adding content](https://help.elevenlabs.io/hc/en-us/articles/26197616307985-How-do-I-add-content-to-ElevenReader), and [ElevenReader help center](https://help.elevenlabs.io/hc/en-us/sections/26165356474897-ElevenReader).

| Capability | ElevenReader | Vox Reader now | Product decision |
|---|---|---|---|
| Upload documents | EPUB, PDF, TXT; listing also advertises docs | Seven local document families | Keep Vox's breadth; improve correctness |
| Camera scan | Yes | Yes, multi-page | Upgrade crop/deskew, correction, and structure preview |
| Paste text | Yes | No | Add in parity phase |
| Web URL/article | Yes, plus Chrome extension | No | Add URL/share-sheet import after core parsing is reliable |
| Narration | 1,000+ cloud voices, 30+ languages | 8 English offline presets + 400+ online Edge voices | Do not chase voice count; ship fewer, excellent, licensed local voices |
| Offline use | Pre-generated paid downloads | Core voice is always local | Vox advantage; add battery/storage controls and reliable caching |
| Speed | 0.25x–4x advertised | 0.5x–2x local | Expand only where measured speech quality remains acceptable |
| Pronunciation dictionary | Yes | No | Add; high-value listening feature |
| Synchronized highlighting | Yes | Yes, sentence-level | Keep; improve accessibility and user-scroll behavior |
| Bookmarks and notes | Yes | Yes, plus colored highlights | Keep and add export |
| Cross-device sync | Yes | No account/local only | Optional encrypted sync later; not a launch requirement |
| Audiobook catalog | 200,000 premium titles announced | User-owned documents only | Do not build; it changes the business and licensing model |
| AI podcasts / GenFM | Yes | No | Defer until the reader is trusted and users request it |
| Voice design | Yes | No | Defer; conflicts with simple, private positioning |

ElevenReader's offline mode still pre-generates cloud audio and limits downloads by plan, while Vox can generate speech locally without a network. That is a stronger privacy and availability story if local quality is good. Source: [ElevenReader offline downloads](https://help.elevenlabs.io/hc/en-us/articles/36191320658833-How-do-offline-downloads-work).

For a broader reading-product benchmark, Readwise Reader demonstrates valuable power-reader behaviors: share-sheet ingestion, offline full-text search, annotations, export, and stopping automatic TTS-follow when the user manually scrolls. These are better references for Vox Reader than a social catalog. Sources: [Readwise Reader](https://readwise.io/read/) and [Readwise TTS behavior](https://docs.readwise.io/reader/docs/faqs/text-to-speech).

## P0: release blockers

### 1. Make chapter structure trustworthy

**Observed failure:** the real PDF currently reports 163 chapters and treats a running page header as a chapter title. The existing stripper compares exact first/last lines, so a header whose page number changes—such as `46 THOMAS HORN`, `47 THOMAS HORN`—escapes removal. The chapter detector then trusts isolated all-caps lines.

**Ultimate fix:** structure detection must return a confidence level and must be allowed to say “continuous document.” A single honest continuous document is better than 163 invented chapters.

Implement in this order:

1. Prefer authoritative structure: EPUB nav/NCX, FB2 sections, DOCX heading styles, MOBI/KF8 structure, and PDF outline.
2. Canonicalize PDF top/bottom lines before repetition analysis: case-fold, normalize whitespace, remove page counters and Roman numerals, and compare the remaining signature.
3. Reject header-like candidates when the signature repeats near page boundaries, increments once per page, or is dominated by author/title tokens.
4. Score inferred headings using multiple signals: explicit `CHAPTER/PART/BOOK`, surrounding whitespace, title case, length, distance from adjacent headings, and body length. Do not trust all-caps alone.
5. Add document-level sanity checks: reject near-one-heading-per-page results, repeated title signatures, tiny sections, and implausible chapter counts.
6. If confidence is low, create one **Continuous document** section or readable parts, clearly labeled as generated—not fake chapters.
7. Show a non-destructive scan preview: source, chapter count, confidence, and first few titles. Let the user accept, rename, split, merge, or keep the old structure.
8. Replace sections in one database transaction only after acceptance. Remap progress, bookmarks, highlights, and notes by stable text anchor; never silently delete them.
9. Store camera source images or normalized OCR text so camera books can be reprocessed.

**Acceptance gates:**

- The observed test PDF no longer exposes `46 THOMAS HORN` or any equivalent running header as a chapter.
- Every source character belongs to exactly one section; no first sentence or trailing paragraph is lost.
- A low-confidence document falls back honestly instead of inventing structure.
- Re-scan preserves the previous result until the new result is accepted.
- Reading position, bookmarks, highlights, and notes survive accepted re-scans.
- A real-file corpus includes at least 5 representative documents per supported format, including malformed, scanned, hybrid, multi-column, outline-free, and long-title cases.

The existing [chapter fix plan](./CHAPTER_SCAN_ULTIMATE_FIX_LUNA.md) remains useful, but the confidence/fallback, preview, and data-preservation gates above must be treated as mandatory.

### 2. Fix compatibility crashes and make lint a gate

Android lint currently fails on three API-35-only list calls while the app supports API 26:

- `EpubParser.kt`: `removeLast()`
- `PdfBookParser.kt`: `removeFirst()`
- `PdfBookParser.kt`: `removeLast()`

Replace them with API-26-safe indexed removals, add one regression test, then require `testDebugUnitTest` and `lintDebug` in CI. Do not create a lint baseline that hides these errors.

There are also 51 warnings. Most dependency-update notices are not urgent and should not trigger a risky bulk upgrade. Resolve release-impacting warnings first: launcher icon shape, ARM64-only support decision, model/native licensing, and security findings.

### 3. Make PDF/OCR import complete and recoverable

Current PDF OCR runs only when **every page** lacks digital text. Hybrid PDFs therefore lose image-only pages. Camera scans are flattened into one section per page and the source is not retained for re-scan.

Implement:

- OCR per blank/low-text page, not all-or-nothing per document.
- Use the existing bundled ML Kit model offline; expose supported scripts honestly.
- Camera crop, rotation, perspective correction, blur warning, and retake.
- OCR review/edit before import; retain normalized source images or extracted text.
- Detect columns, page headers/footers, hyphenated line wraps, footnotes, and page numbers before TTS.
- Import progress with page count, cancel, and actionable failure details.

Google notes that OCR accuracy depends strongly on resolution and focus and documents may need roughly 720×1280 input; this supports adding capture-quality feedback rather than trying to repair every bad result afterward. Source: [ML Kit text recognition guidance](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).

### 4. Make playback dependable under real Android conditions

The current foreground service and legacy platform `MediaSession` are a useful start, but a premium reader must survive lock screen, app switching, calls, headset controls, Bluetooth changes, process recreation, and network loss.

Implement:

- Migrate the player/session boundary to Media3 `MediaSessionService` while reusing the existing TTS manager.
- Correct audio-focus pause/duck/resume behavior.
- Persist the active book/chapter/sentence outside the reader ViewModel.
- Put sleep-timer state in the service so it survives navigation and process recreation.
- Support notification, lock-screen, Bluetooth, and headset media controls consistently.
- Separate pause from stop; never lose the resume anchor.
- Cache generated sentence/chapter audio with a bounded LRU and explicit storage controls.
- Test calls, alarms, headphone disconnect, Bluetooth handoff, screen lock, rotation, app swipe-away, low storage, offline start, and network loss.

Android's current guidance is to keep the player and media session in a `MediaSessionService` for background playback. Source: [Android background playback guidance](https://developer.android.com/media/media3/session/background-playback).

### 5. Resolve voice, privacy, and licensing risk

- The code and project documentation disagree: current code ships Sherpa ONNX/Piper-LibriTTS, while `PROJECT_STATUS.md` still claims Kokoro/C++. Correct the handoff before more work starts.
- Record the exact source, version, license, and redistribution terms for the model, speaker data, espeak-ng data, Sherpa AAR, fonts, and icons.
- The online Edge engine uses an unofficial WebSocket endpoint, not a documented supported API. It can change without notice. For release, either use a formal provider agreement/API or label the feature experimental and make the offline engine fully sufficient.
- Keep the current explicit disclosure that online speech sends read text to Microsoft. Add per-engine consent, delete cached online audio, and a plain privacy policy.
- Add open-source notices inside the app and repository.

## P1: product quality and parity

### Import and library

- Add Android share-sheet ingestion for files, selected text, and URLs.
- Add paste/write text.
- Add URL/article extraction only after the file pipeline is stable.
- Add sort/filter by recent, title, author, format, progress, finished, and unread.
- Add user collections/tags; do not infer fake genres from metadata.
- Detect duplicate imports by content hash and offer replace/keep-both.
- Add batch import and clear per-item status.
- Generate restrained typographic covers for books without artwork. Never show the current blank white rectangle.
- Allow export/backup of highlights and notes as Markdown or JSON.

### Listening quality

- Add a per-book pronunciation dictionary with simple word replacement first; phoneme editing can wait.
- Add pronunciation preview and one-tap “fix this word” from the current sentence.
- Add language detection per section and warn when the selected voice does not match.
- Curate 2–4 excellent offline voices before adding more. Voice count is not quality.
- Build a fixed listening benchmark covering names, numbers, acronyms, dialogue, quotations, citations, dates, Nigerian names, and long sentences.
- Measure time-to-first-audio, sentence gap, real-time factor, memory, battery, thermal behavior, and crashes on low/mid/high devices.
- Add optional citation/footnote skipping for academic PDFs.

### Reader behavior

- Preserve auto-follow while listening, but suspend it as soon as the user manually scrolls; show a “Return to spoken text” affordance.
- Allow tap-to-start without forcing a permanent position change until playback succeeds.
- Add chapter-level elapsed/remaining time and whole-book remaining time.
- Make highlighting selection-based where possible; keep sentence long-press as a shortcut, not the only path.
- Provide undo after bookmark/highlight/delete actions.
- Add finished state, replay, and optional archive.

## UI/UX direction: “quiet editorial instrument”

The strongest visual idea is already present: a dark private library opening into a paper-like reading surface. Keep that. Remove the visual competition around it.

### System-level changes

1. **Choose one brand system.** The design brief specifies orange/green, but shipping code aliases `SignalOrange`, `Leaf`, `Denim`, and `Azure` to the same cyan/blue palette. Keep the current sky/cobalt direction or restore orange/green—do not maintain fake aliases for both.
2. **Reduce decorative stickers.** Use the book/microphone motif on splash, onboarding, and empty states. On content-heavy screens it adds low-contrast noise and makes the app feel less premium.
3. **Use one UI family and one reading family.** Sans serif for controls/metadata; serif only for book titles and reading content. The current large serif on almost every control weakens hierarchy.
4. **Make state visible in words.** Replace implementation copy such as “Edge TTS ready” with the chosen voice and connection state, for example “Aria · Online” or “Ada · On device.”
5. **Use motion sparingly.** Animate playback state, import completion, and navigation continuity; respect reduced-motion settings.

### Library redesign

The live tablet screen leaves a large unused right side while stacking narrow “Now listening” cards on the left and stretching search across almost the full window.

- Phone: compact masthead, one continue card, filter/sort row, 2-column cover grid, anchored import FAB.
- Tablet/landscape: navigation rail + two-pane content. Left pane is “Continue listening” and filters; right pane is the responsive library grid. Search belongs in the content column with a maximum width.
- Show no more than one prominent resume card; additional recent books belong in a horizontal row.
- Book cards need title/author/progress semantics and a reliable generated cover.
- Replace custom category boxes with semantic filter chips and visible selected state.

### Book details redesign

The live tablet screen centers a small blank cover above a title spanning almost the entire 2560-pixel width, then gives Bookmark, Delete, Favorite, Edit, and Re-scan nearly equal visual weight.

- Use a two-column hero on wide screens: 240–300dp cover left; title, author, progress, and primary play CTA right.
- Cap the metadata column around 720dp; allow a long title up to three lines.
- Primary action: Continue/Start listening.
- Secondary actions: bookmark/favorite and edit.
- Put Delete and Re-scan under an overflow menu; both need confirmation and consequence copy.
- Follow with a searchable chapter list and show whether structure came from the book, was detected, or is continuous.

### Reader redesign

The live landscape reader is readable, but the player consumes roughly the bottom third and presents seven peer-level transport controls.

- Keep the text column between roughly 680–820dp equivalent for comfortable line length.
- On phone, use a compact bottom player that expands for voice, speed, and sleep options.
- On tablet landscape, use a compact bottom strip or right-side player panel rather than a deep full-width block.
- Primary controls: back 10 seconds, play/pause, forward 10 seconds.
- Secondary controls: previous/next chapter. Sentence stepping can live in the expanded panel.
- Shorten the top bar to book title plus chapter; do not repeat the chapter as a large body heading unless it is genuine document content.
- Show a visible “follow narration” state and return-to-current-sentence button.

### Import redesign

Use one import hub with four clear cards:

1. **Choose files** — existing document picker.
2. **Scan pages** — camera with crop/quality review.
3. **Paste text** — title plus editable content.
4. **Add web link** — later parity feature.

After extraction, show a single review screen: title, author, page/word count, detected language, structure source, chapter count, warnings, and a short text preview. The final button should say **Add to library**, not merely “Finish scan.”

## Accessibility audit

The app does many touch targets well, but it does not yet meet a WCAG 2.1 AA / premium Android accessibility bar.

### Confirmed issues

- The live accessibility tree marks book-cover click targets as `NAF` (not accessibility friendly) with no useful name.
- Progress indicators expose no readable percentage or position.
- Custom category chips do not expose selected state or role.
- Long-press-to-highlight is undiscoverable and has no equivalent accessibility action.
- Nearly all user-facing copy is hard-coded in Compose; `strings.xml` has only 11 basic strings. This blocks proper localization and makes accessibility copy inconsistent.
- `TextTertiary` (`#6B7280`) on the dark background (`#090D16`) is about **4.02:1**, below 4.5:1 for normal text.
- White text on the cyan primary (`#38BDF8`) is about **2.14:1**. Selected chips using this pairing fail; dark text on cyan is about **9.07:1** and should be used instead.
- Several custom clickable containers have no explicit role, state, or merged semantics.

### Required remediation

- Give every book card one merged label: title, author, progress, and action.
- Add progress semantics, selected/toggle state, heading semantics, and custom actions where needed.
- Use Material components when they already provide correct semantics instead of rebuilding buttons/chips from clickable boxes.
- Move all visible and spoken copy into string resources with placeholders/plurals.
- Test 200% font size, display scaling, TalkBack traversal, switch access, keyboard/D-pad, RTL, high contrast, reduced motion, and grayscale.
- Add semantic Compose UI tests for the library, importer, reader controls, bookmarks, and destructive confirmations.

Android describes semantics as the source of name, role, value, state, and actions for accessibility services and tests. Source: [Compose semantics guidance](https://developer.android.com/develop/ui/compose/accessibility/semantics). Android's current quality guidance also requires consistent behavior across app switching, sleep/lock, interruptions, connectivity changes, and adaptive windows. Sources: [core app quality](https://developer.android.com/develop/adaptive-apps/quality-guidelines/core-app-quality) and [adaptive app quality](https://developer.android.com/develop/adaptive-apps/quality-guidelines/adaptive-app-quality).

## Engineering and release audit

### Keep

- Existing MVVM/UDF shape, Room, DataStore, Hilt, parser separation, private file storage, and transaction-based persistence.
- Existing safety limits for document/archive size.
- Existing tests as the base of a real-format corpus.
- Existing offline-first default and explicit online-engine disclosure.

### Improve before public release

- Add CI for unit tests, lint, release compilation, and dependency/license checks.
- Add connected tests for Room migrations and five critical device journeys; the two instrumentation source files are not part of the current unit-test pass.
- Add baseline-profile/macrobenchmark coverage for cold start, library load, opening a long book, and first audio.
- Profile the 89.2MB debug APK and 22.5MB model; verify Play Asset Delivery behavior and first-run failure states.
- Decide and document supported ABIs. The current native build is ARM64-only and lint flags ChromeOS x86_64 support.
- Remove stale Kokoro/C++ claims and dead third-party/native material that is no longer part of the shipping path after confirming it is unused.
- Add structured local diagnostics that users can export voluntarily; privacy-first does not mean undiagnosable.
- Add release signing, versioning, privacy policy, data-safety declaration, app icon validation, screenshots, and store copy.
- Threat-model file imports, ZIP bombs, malformed XML/HTML, path traversal, oversized images, and untrusted metadata. Existing size guards are a good start.

### Do not do yet

- Do not bulk-upgrade all 51 dependency warnings in one change.
- Do not add accounts merely to copy synchronization.
- Do not build an audiobook marketplace, publishing platform, social feed, or creator payouts.
- Do not add GenFM/podcast generation before chapter structure and playback are dependable.
- Do not add dozens of local voices before the best two or four pass quality, size, thermal, and licensing gates.
- Do not hide low-confidence parsing behind more confident UI.

## Luna implementation plan

### Phase 0 — restore a trustworthy baseline

**Goal:** green build and accurate documentation.

- Fix the three API compatibility errors.
- Make unit tests and lint required CI checks.
- Update README, project status, engine naming, architecture, and release limitations.
- Record third-party/model licenses and decide the Edge engine release policy.
- Add the observed false-header PDF as a private regression fixture or a legally safe synthetic equivalent.

**Done when:** 92+ tests pass, lint has zero errors, CI is green, and docs match shipping code.

### Phase 1 — document structure and OCR trust

**Goal:** no fabricated chapters and no destructive recovery path.

- Implement canonical running-header/footer detection.
- Add candidate scoring, confidence, document sanity checks, and continuous-document fallback.
- OCR hybrid PDF pages individually.
- Add structure preview and atomic accepted replacement.
- Preserve progress/bookmarks/highlights/notes through re-scan.
- Add manual rename/split/merge and OCR text correction.
- Build the cross-format real-document regression corpus.

**Done when:** all P0 chapter/OCR acceptance gates pass on device.

### Phase 2 — playback reliability and listening quality

**Goal:** audiobook-grade sessions.

- Move session ownership to Media3 service.
- Implement audio focus, lock-screen/headset controls, durable sleep timer, and process-safe resume.
- Add bounded pre-generation/cache and storage controls.
- Add pronunciation replacement, preview, and language mismatch warning.
- Run the device interruption matrix and voice performance benchmark.

**Done when:** a 60-minute locked-screen session survives interruptions without losing place, leaking audio, or stalling.

### Phase 3 — UI system and accessibility

**Goal:** coherent, adaptive, accessible product UI.

- Consolidate brand tokens and typography.
- Implement the library, details, reader, and import layouts specified above.
- Generate useful missing covers.
- Reduce control density and move destructive actions to overflow.
- Resource all copy, correct contrast, and repair semantics.
- Add phone/tablet/foldable snapshots and TalkBack test journeys.

**Done when:** critical journeys work at 200% text, with TalkBack, in portrait/landscape, and at compact/medium/expanded widths.

### Phase 4 — high-value parity

**Goal:** make getting content into Vox Reader effortless.

- Share sheet, paste text, and URL import.
- Duplicate detection, batch import, sort/filter, collections/tags.
- Export highlights/notes and local backup/restore.
- Optional language/model packs only after demand and license review.

**Done when:** a user can send an article or document from another app to audible playback with no confusing intermediate state.

### Phase 5 — release candidate

**Goal:** Play-ready build, not a debug demo.

- Signed AAB, release shrinking test, asset-pack validation, supported-device matrix.
- Privacy policy, data-safety form, license notices, store assets, support channel.
- Zero lint errors, zero known data-loss bugs, all migration/device journeys green.
- Closed beta with structured feedback on chapter accuracy, first-audio latency, voice quality, battery, and accessibility.

**Done when:** release gates are evidence-based and reproducible from a clean checkout.

## Success metrics

Track locally or through explicit opt-in diagnostics:

- Import success rate by format.
- Percentage of documents using authored, inferred-high-confidence, and continuous structure.
- User rejection/re-edit rate for detected chapters.
- Time from import tap to readable preview.
- Time from play tap to first audio.
- P50/P95 gap between spoken sentences.
- Playback failures per listening hour.
- Resume-position accuracy after interruption/process death.
- Battery drain and thermal throttling per listening hour.
- TalkBack completion rate for import → play → bookmark.
- Crash-free sessions and migration success.

## Final priority order

1. Chapter/structure correctness and safe re-scan.
2. API compatibility, lint, CI, licensing, and accurate handoff docs.
3. Hybrid OCR and editable extraction.
4. Playback/session reliability and pronunciation control.
5. Adaptive UI and accessibility remediation.
6. Share/paste/link ingestion and library organization.
7. Additional local voices/languages.
8. Optional sync or AI features only after user evidence.

That order is the shortest route to an app users trust. Feature parity without these gates would make Vox Reader larger, not better.

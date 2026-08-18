# VoxLeaf Engineering Handoff

Updated: 2026-08-18  
Repository: `C:\Users\Yayis\Desktop\CODEX\Android studio\voxleaf`  
Branch: `main`  
Package: `com.aistudio.voxreader.xyz.debug`  
Connected Test Device: `b6f2151b` (Android Device)

---

## 1. Product Direction & Architecture Overview

VoxLeaf is an offline-first, high-fidelity neural audiobook and e-book reader built for Android. The architecture encompasses:

1. **Multi-Format Book Ingestion & Parsing**: High-accuracy chapter detection and structural segmentation for **EPUB (v2/v3)**, **PDF**, **MOBI/AZW3**, **FB2**, **DOCX**, and **TXT** files, with on-device ML Kit OCR fallback for scanned documents.
2. **Audiobook Pre-Generation & Continuous Playback**: Neural synthesis via offline on-device Kokoro / ONNX Runtime into chapter-level M4A files and sentence-level cue metadata, backed by Edge TTS online engine fallback.
3. **Robust Media Playback & Foreground Service**: Continuous background playback via `PlaybackService` / `TtsManager`, supporting sentence highlighting, speed adjustment, and persistent reading progress.
4. **Stable Jetpack Compose UI**: Discrete speed controls, dynamic reader themes (Denim Blue / Sky Cyan), adaptive covers, and responsive reader controls.

---

## 2. Recent Major Milestones & Implemented Systems

### A. Chapter Detection & Book Importing Overhaul (Completed: 2026-08-18)
Exhaustive overhaul addressing 9 critical failure points identified in book parsing and matching industry-standard techniques from top e-book/audiobook readers (ElevenReader, Voice Dream, Moon+ Reader, ReadEra):

- **Core Chapter Detector (`ChapterDetector.kt`)**:
  - *Subtitle Bug Fixed*: Slicing now strictly consumes `candidate.linesConsumed` tracked during candidate detection instead of re-evaluating `title.contains(": ")`. The first sentence of chapter bodies is no longer discarded.
  - *Roman Numeral Precision*: `STANDALONE_ROMAN` strictly matches whole-line numerals (`^\s*([IVXLCDM]{1,8})\s*[.:—–-]?\s*$`) with `IGNORE_CASE`, preventing regular prose starting with words like *"Il"*, *"Did"*, or *"Civil"* from being misdetected as chapters. Added `ROMAN_WITH_SUBTITLE` for inline title patterns (e.g., `"IV: The Beginning"`).
  - *Alternating Running Header Suppression*: Frequency-based deduplication eliminates alternating odd/even running headers (titles appearing $\ge 3$ times and representing $> 40\%$ of candidates are stripped).
  - *Table of Contents (TOC) Loop Prevention*: `skipTocPreamble` identifies dense heading clusters where all titles repeat in later body chapters, stripping the raw TOC preamble without generating false opening chapters or breaking the body flow.
  - *Extended Multi-lingual & Number Words*: `NUMBER_WORDS` extended with ordinals through *twentieth* and hyphenated compounds (e.g. `twenty-first`). Expanded `isChapterLabel()` to recognize numbered and standalone Roman headings.
  - *Scene Break Fallback*: Added `splitBySceneBreaks()` supporting `***`, `* * *`, `---`, `###`, `~~~`, `⁂`, and `• • •` as a structural fallback before paragraph chunking.
- **EPUB 3 Navigation & Anchor Slicing (`EpubParser.kt`)**:
  - *EPUB 3 `<nav>` Support*: Added `parseNavDocument()` to parse `<nav epub:type="toc">` elements for chapter titles and anchor references, prioritized over legacy EPUB 2 `toc.ncx`.
  - *Anchor-Based Splitting*: Added `splitXhtmlByAnchors()` and `parseXhtmlWithIds()` to cleanly slice monolithic single-file EPUBs at `#fragment` ID boundaries into distinct chapters.
  - *Multi-File Chapter Merging*: Spine items without TOC entries or heading tags are automatically merged into the preceding chapter.
- **PDF Recursive Outlines & Header Stripping (`PdfBookParser.kt`)**:
  - *Recursive Outline Traversal*: Replaced flat bookmark reading with recursive `firstChild`/`nextSibling` traversal to extract nested chapter bookmarks (e.g., `"Book Title" → [Chapter 1, Chapter 2, ...]`).
  - *Header & Footer Suppression*: Added `stripRunningHeaders()` to eliminate recurring header/footer lines across pages and filter out standalone page numbers.
- **Importer Orchestration (`TextBookImporterImpl.kt`)**:
  - Lowered intra-spine omnibus sub-split threshold to 3,000 characters with an average-section size safeguard ($\ge 500$ chars) to prevent over-fragmenting legitimate chapters.
  - Integrated scene-break splitting fallback into the hierarchy for TXT, PDF, and EPUB files.

### B. Speed Controller Stability & Layout Polish (Completed: 2026-08-16)
- **Discrete Speed Selector Chips**: Replaced continuous sliders app-wide (Reader and Voice Selection screens) with discrete speed selector chips (`0.75x`, `1.0x`, `1.25x`, `1.5x`, `1.75x`, `2.0x`) using fixed-dimension `Surface` chips with constant borders to eliminate visual layout shifts and buffer thrashing.
- **Buffer Stability Guard**: Added epsilon-based change detection (`0.02f`) to `TtsManager.setSpeechRate` to prevent redundant buffer clearing and audio stuttering when tapping `1.0x`.

### C. Audiobook Pre-Generation & Worker Isolation (Completed: 2026-08-14)
- **WorkManager Hilt Worker (`GenerateAudiobookWorker.kt`)**: Foreground data-sync service with proper notification management on Android 14+ / HyperOS.
- **Runtime File Isolation**: Dedicated `files/audiobook-runtime/` directory separated from `files/kokoro-runtime/` to prevent file deletion collisions between background generation and live playback.
- **Tokenizer Punctuation Normalization**: `KokoroNativeEngine` punctuation normalization and asset validation.

---

## 3. What Has Been Verified

### A. Desktop Automated Unit Tests
Executed all 17 test suites:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```
**Latest Result**: `BUILD SUCCESSFUL`  
- **Total Tests Completed**: 90  
- **Total Failures / Errors**: 0 (100% pass rate)  
- **Suites Verified**:
  - `ChapterDetectorTest` (20 tests covering all 9 bug fixes, Roman numerals, CJK, multi-lingual, TOC loops, scene breaks)
  - `BookDaoTest` (3 tests)
  - `NavigationTest` (3 tests)
  - `DocxParserTest` (3 tests)
  - `EpubParserTest` (2 tests)
  - `HtmlTextExtractorTest` (8 tests)
  - `MobiParserTest` (17 tests)
  - `PdfOutlineSectionsTest` (3 tests)
  - `RealAzw3ScratchTest` (1 test)
  - `TextImportStreamsTest` (3 tests)
  - `HighlightsMarkdownTest` (3 tests)
  - `ReaderViewModelTest` (5 tests)
  - `ListeningStatsTest` (7 tests)
  - `EdgeTtsEngineTest` (9 tests)
  - `TtsChapterQueueTest` (1 test)
  - `ExampleRobolectricTest` & `ExampleUnitTest` (2 tests)

### B. Device Installation & Deployment
- **Target Device**: `b6f2151b`
- **Application ID**: `com.aistudio.voxreader.xyz.debug`
- **Main Activity**: `com.voxleaf.reader.MainActivity`
- **Status**: Installed and running live on device.

---

## 4. Key Files & Responsibilities

| File | Path | Responsibility |
| :--- | :--- | :--- |
| `ChapterDetector.kt` | `app/src/main/java/.../data/repository/` | Heuristic engine for regex, Roman numerals, multi-lingual prefixes, running header suppression, TOC loops, and scene breaks. |
| `EpubParser.kt` | `app/src/main/java/.../data/repository/` | EPUB 2/3 archive unpacker, OPF manifest reader, `nav.xhtml` / `toc.ncx` parser, and anchor-based DOM chapter splitter. |
| `PdfBookParser.kt` | `app/src/main/java/.../data/repository/` | PDFBox text extractor, recursive outline reader, spatial header/footer stripper, and on-device OCR fallback. |
| `TextBookImporterImpl.kt` | `app/src/main/java/.../data/repository/` | Orchestrates import pipeline across all file types, Room database insertion, chunking, and re-scan support. |
| `TtsManager.kt` | `app/src/main/java/.../tts/` | High-level TTS playback manager, speed control, epsilon buffer guarding, and queue synchronization. |
| `GenerateAudiobookWorker.kt` | `app/src/main/java/.../audiobook/` | Background WorkManager worker generating offline M4A audiobook files. |
| `KokoroNativeEngine.kt` | `app/src/main/java/.../tts/` | ONNX Runtime offline neural TTS synthesis and asset management. |
| `ReaderScreen.kt` | `app/src/main/java/.../feature/reader/` | Compose reader UI, fixed-dimension speed chips, paragraph rendering, and sentence highlighting. |

---

## 5. Remaining Roadmap & Next Milestones

### P0 — Continuous Background Playback & Queue Architecture
- Ensure `TtsManager` / `PlaybackService` independently own chapter advancement across screen lifecycle destruction and device sleep.
- Persist queue state in Room (book ID, active chapter ID, cue index, speed).

### P1 — Audiobook Pre-Generation Resumability & Progress
- Implement granular sentence/chunk checkpointing in `GenerateAudiobookWorker` so long chapters (e.g. 200+ sentences) resume from the last completed chunk on interruption rather than restarting the entire chapter.
- Expose real-time chunk progress percentage to the UI.

### P1 — Large-File & Archive Stress Testing
- Test import throughput on 50MB+ and 100MB+ omnibus EPUBs, large graphic-heavy PDFs, and scanned documents.
- Validate memory consumption on lower-RAM devices during native Kokoro ONNX inference.

---

## 6. Verification Commands Quick Reference

```powershell
# Set Java Home
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Run Unit Tests
.\gradlew.bat :app:testDebugUnitTest --no-daemon

# Build & Install Debug APK
.\gradlew.bat :app:installDebug --no-daemon

# Launch App on Device
adb shell am start -n com.aistudio.voxreader.xyz.debug/com.voxleaf.reader.MainActivity

# Stream Device Logs
adb logcat -v time -s VoxLeaf:V TtsManager:V ChapterDetector:V EpubParser:V PdfBookParser:V
```

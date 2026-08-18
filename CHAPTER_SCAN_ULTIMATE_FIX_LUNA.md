# Vox Reader Chapter Scan — Root-Cause Fix Plan for Luna

## Objective

Make chapter detection reliable across TXT, EPUB, MOBI/AZW3, FB2, DOCX, text PDFs,
scanned PDFs, and camera scans without losing body text or silently replacing a good
chapter map with a worse one.

The target is structure-first detection with deterministic text fallback, a quality gate,
and a preview before destructive re-scan. Do not add an LLM, cloud service, or new parsing
dependency.

## Repository rules

- The product and user-facing app name is **Vox Reader**. Its debug application ID is
  `com.aistudio.voxreader.xyz.debug`.
- Work in the active `com.voxleaf.reader` source tree only.
- The worktree contains a large in-progress `com.example` to `com.voxleaf` package move.
  Preserve it. Do not reset, restore, or recreate the deleted `com.example` tree.
- Implement one phase at a time and run its gate before continuing.
- Do not work on playback, TTS, visual redesign, or unrelated handoff items.
- Keep the existing supported file-size and archive-safety limits.

## Evidence and root cause

The engineering handoff says the chapter overhaul is complete, and the current 90 desktop
tests pass. Those tests prove the listed synthetic cases, but not the real import and re-scan
flow.

1. `TextBookImporterImpl.invoke(pages, title)` stores every camera image as `Page N`.
   It never calls `ChapterDetector`, and it stores `sourceFilePath = null`. Camera-scanned
   books therefore cannot receive real chapter detection and cannot be re-scanned later.
2. `ChapterDetector.detectHeadingAt` treats almost any short line after `Chapter 1` as a
   subtitle. For input such as `Chapter 1\nThis is the first sentence.\nMore text`, the first
   sentence is consumed into the title and removed from the body. The existing regression
   test has a blank line after its heading, so it does not exercise this failure.
3. Re-scan calls the same `parseByExtension` path used during import, then immediately deletes
   and replaces all sections. It has no alternate detection mode, confidence result, preview,
   old-versus-new comparison, or content-conservation check.
4. Parser evidence is either discarded or trusted too broadly:
   - PDF OCR runs only when every extracted page is blank, so mixed digital/scanned PDFs lose
     the scanned pages.
   - PDF outline traversal flattens parent, chapter, and subsection bookmarks into one list;
     any two entries are accepted as chapters.
   - EPUB combines NCX and EPUB 3 navigation targets by file instead of choosing one coherent
     authoritative navigation source. Duplicate and nested targets can produce false boundaries.
   - DOCX recognizes only style IDs beginning with `Heading`; custom or localized styles with
     an outline level are missed.
5. Re-scan resets reading progress but does not reconcile bookmarks/highlights or invalidate
   generated chapter audio rows/files. A changed chapter map can therefore leave dependent data
   pointing at the wrong chapter.
6. The test suite has only generated miniature parser fixtures. It has no end-to-end fixtures
   for camera OCR, hybrid PDFs, nested PDF outlines, EPUB 3 anchors, false numbered-list
   headings, or re-scan persistence.

The root cause is not a missing regular expression. Structural evidence is lost before the
fallback detector runs, and there is no validation layer before a proposed chapter map is saved.

## Target behavior

Use this priority order for every format:

1. Valid format-native boundaries: EPUB navigation/anchors, FB2 sections, DOCX outline styles,
   MOBI structure, or one coherent PDF outline level.
2. Explicit heading boundaries from preserved blocks/lines.
3. Plain-text heading heuristics.
4. Low-confidence navigation parts only when no real chapter structure exists.

Every result must include:

- sections with title and body;
- source: `NATIVE`, `HEADING`, or `FALLBACK`;
- confidence: `HIGH`, `MEDIUM`, or `LOW`;
- warnings suitable for the re-scan preview;
- a content-conservation result.

The content-conservation rule is mandatory: after removing accepted heading lines and normalized
running headers, all remaining non-blank input text must occur in the output bodies in the same
order. A candidate that loses body text is rejected.

## Phase 0 — Lock down real failures

### Changes

- Add focused regressions to the existing parser test files; do not create a new test framework.
- Add the exact failing user documents as local, non-copyrighted/minimized fixtures when they are
  available. Reduce each to the smallest excerpt that still fails.
- Add tests for:
  - a chapter label followed immediately by a complete first sentence;
  - a true two-line chapter title followed by body text;
  - numbered lists that must not become chapters;
  - alternating running headers;
  - TOC entries with dot leaders/page numbers;
  - a nested PDF outline with parent, chapter, and subsection levels;
  - an EPUB 3 nav file with multiple anchors in one XHTML file;
  - a hybrid PDF with digital and image-only pages;
  - camera OCR text containing two chapter headings;
  - re-scan preserving the old database state when a proposed result is rejected.
- Add one shared test assertion that verifies body-text conservation.

### Gate

The new tests must fail for the current implementation for the expected reason. Existing tests
must remain green.

## Phase 1 — Make `ChapterDetector` conservative and lossless

### Files

- `app/src/main/java/com/voxleaf/reader/data/repository/ChapterDetector.kt`
- `app/src/test/java/com/voxleaf/reader/data/repository/ChapterDetectorTest.kt`

### Changes

- Reuse `ChapterDetector`; do not add a second heuristic detector.
- Add small result metadata types beside `Section`: source, confidence, and warnings.
- Tighten subtitle consumption. A following line is a subtitle only when all are true:
  - it is short;
  - it does not end like a sentence (`.`, `?`, `!`, or ellipsis);
  - it is followed by a blank line or explicit block boundary;
  - it is not another heading or known noise.
  When ambiguous, keep it in the body.
- Require isolation for generic numbered and Roman headings. Do not treat numbered prose/list
  items as headings merely because they begin with a capital letter.
- Normalize whitespace, case, page digits, and punctuation only for running-header comparison;
  retain the original body text.
- Keep TOC suppression, but compare cleaned candidate titles rather than raw source lines and
  support dot leaders/page suffixes.
- Run content conservation before returning a detected result. Return no detected chapters when
  the candidate loses text.
- Keep scene-break/paragraph grouping as an explicitly low-confidence fallback. Do not present
  scene breaks as confidently detected chapters.

### Gate

- All detector regressions pass.
- Existing detector tests pass.
- A first sentence can never disappear into a title.

## Phase 2 — Preserve and validate format-native structure

### EPUB

Files: `EpubParser.kt`, `EpubParserTest.kt`, `TextBookImporterImpl.kt`.

- If EPUB 3 nav targets are valid, use them; otherwise use NCX. Do not merge both target sets.
- Resolve and normalize relative/percent-encoded hrefs, then deduplicate by normalized
  `(path, fragment)` while retaining navigation order.
- Split at anchors only when anchors resolve monotonically in document order.
- Reject a native split with duplicate boundaries, empty middle sections, or lost text and fall
  through to heading detection.
- Preserve untitled spine content by merging it only when doing so conserves reading order.

### DOCX

Files: `DocxParser.kt`, `DocxParserTest.kt`, `TextBookImporterImpl.kt`.

- Continue using paragraph styles first.
- Parse `word/styles.xml` only as needed to recognize paragraph styles carrying `w:outlineLvl`,
  including custom/localized style IDs.
- Keep list paragraphs as body content; they must not become chapter headings through the text
  fallback.
- Validate the styled split before accepting it.

### PDF and scanned PDF

Files: `PdfBookParser.kt`, `PdfOutlineSectionsTest.kt`, `OcrScanner.kt`,
`TextBookImporterImpl.kt`.

- Preserve outline depth. Select one coherent level with at least two unique, increasing page
  starts; do not flatten parent, chapter, and subsection bookmarks together.
- Reject outline levels dominated by duplicate starts or tiny/empty sections.
- OCR each blank or near-empty page in a mixed PDF instead of requiring the entire PDF to be
  image-only.
- Keep page indices stable after OCR so outline destinations remain correct.
- Strip only normalized lines repeated in top/bottom page positions. Never delete matching text
  from the middle of a page.
- Validate outline and heading candidates through the same content-conservation rule.

### MOBI/AZW3 and FB2

Files: `MobiParser.kt`, `Fb2Parser.kt`, `TextBookImporterImpl.kt`, existing parser tests.

- Keep their native chapter structures when valid.
- Route invalid/single-blob results through the same heading detector and validation path.
- Do not introduce format-specific fallback thresholds outside the shared selection function.

### Gate

Each parser fixture returns the expected ordered titles and bodies, and every accepted result
passes content conservation.

## Phase 3 — Put every import through one selection path

### Files

- `TextBookImporterImpl.kt`
- `OcrScanner.kt`
- existing importer/parser tests

### Changes

- Add one private `chooseSections(native, text, pages)` function in `TextBookImporterImpl`.
  It should validate candidates in priority order and return the first valid result. Do not add a
  registry, provider interface, or scoring framework.
- Make TXT, EPUB, DOCX, MOBI/AZW3, FB2, PDF, and re-scan call that function.
- For camera scans:
  - OCR all pages;
  - retain page boundaries;
  - strip repeated top/bottom lines;
  - concatenate the cleaned OCR text and run the shared selection path;
  - use `Page N`/`Part N` only as a low-confidence fallback;
  - persist the cleaned OCR text under `files/imports` as the book source so later re-scan works.
- Keep original display names separately from UUID storage names when generating fallback titles
  and diagnostics.
- Log only format, candidate source, confidence, proposed count, rejected reason, and elapsed time.
  Never log book text.

### Gate

- Import and re-scan produce the same chapter map for the same source and app version.
- A camera scan with recognizable chapter headings produces chapters rather than one chapter per
  page.
- A camera scan with no reliable headings produces clearly labelled fallback parts and remains
  re-scannable.

## Phase 4 — Make re-scan previewed and atomic

### Files

- `domain/usecase/ImportTextBookUseCase.kt`
- `data/repository/TextBookImporterImpl.kt`
- `data/local/dao/BookDao.kt`
- `data/local/dao/BookmarkDao.kt`
- `data/local/dao/HighlightDao.kt`
- `feature/bookdetails/BookDetailsContract.kt`
- `feature/bookdetails/BookDetailsViewModel.kt`
- `feature/bookdetails/BookDetailsScreen.kt`
- relevant ViewModel and Room tests

### Changes

- Change re-scan from one destructive call into preview then confirm/apply.
- Preview shows old count, proposed count, proposed titles, source, confidence, and warnings.
- Do not apply a candidate that fails conservation. Require explicit confirmation for a
  low-confidence result or a large chapter-count change.
- On confirmation, perform one Room transaction that:
  - replaces sections/chunks;
  - updates total chapters;
  - resets reading progress;
  - remaps bookmarks and highlights by normalized exact sentence text when the match is unique;
  - reports ambiguous/unmatched annotations instead of silently deleting them;
  - deletes stale `chapter_audio`, `audio_cues`, and generation rows for the book if present.
- Delete stale generated-audio files only after the database transaction succeeds.
- If parsing, validation, or the transaction fails, leave the original chapter map untouched and
  surface the real error category. Do not collapse all failures into “Could not re-scan”.

### Gate

- Cancelled/rejected preview changes nothing.
- Forced parser and database failures leave old chapters intact.
- Successful apply cannot retain audio mapped to old chapter indices.
- Bookmark/highlight handling is explicit and tested.

## Phase 5 — End-to-end verification

Run:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon
```

Then verify on a connected device with at least:

- one standard EPUB with one XHTML file per chapter;
- one EPUB 3 with multiple chapter anchors in one XHTML file;
- one DOCX with custom heading styles and numbered lists;
- one PDF with nested bookmarks;
- one hybrid/scanned PDF;
- one camera-scanned document;
- one plain TXT with two-line titles and no blank after some chapter labels.

For every fixture record expected titles/count, actual titles/count, confidence, warnings, import
time, re-scan result, and whether normalized body text was conserved. The release gate is zero
lost body text, exact expected boundaries for the regression corpus, and no destructive re-scan
without confirmation.

The handoff device `b6f2151b` is connected and ADB-authorized. The device gates remain mandatory
before calling the fix complete.

## Luna execution prompt

```text
Read CHAPTER_SCAN_ULTIMATE_FIX_LUNA.md completely, then inspect every file named in Phase 0 and
Phase 1 before editing.

Preserve the existing dirty worktree and the com.example -> com.voxleaf package migration. Never
reset or restore user changes. Work only on chapter detection/import/re-scan files.

Implement Phase 0 first. Run the new regression tests and show that they fail for the expected
current defects. Then implement Phase 1 only, run :app:testDebugUnitTest, and report changed files,
test results, and any remaining failing fixture. Stop after the Phase 1 gate; do not start later
phases until I review the result.

Use the existing Kotlin/Android stack. Add no LLM, cloud API, parsing library, dependency injection
layer, provider registry, or speculative abstraction. The non-negotiable invariant is that chapter
detection must never lose body text.
```

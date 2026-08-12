# Vox Reader Neural Audiobook Overhaul

## Execution brief for Luna

This document is the authoritative implementation plan for replacing Vox Reader's live, sentence-by-sentence TTS playback with automatic, durable, on-device neural audiobook generation.

Luna must work phase by phase. Do not perform the final removal phase until generated-audio playback, migration, recovery, and user-data preservation have passed their gates.

## Repository context

- Repository: `C:\Users\Yayis\Desktop\CODEX\Android studio\voxleaf`
- Application module: `app`
- Offline voice asset pack: `offlinevoices`
- Android package: `com.aistudio.voxreader.xyz`
- Debug package: `com.aistudio.voxreader.xyz.debug`
- Minimum Android version: API 26
- Current database version: 6
- UI: Jetpack Compose
- Persistence: Room and DataStore
- Dependency injection: Hilt
- Native neural speech: Babylon.cpp, ONNX Runtime, kittenTTS bridge
- Current playback: singleton `TtsManager`, `MediaPlayer`, foreground `PlaybackService`

### Current worktree warning

The repository contains an existing uncommitted change set covering the durable live-TTS chapter queue, offline asset-pack split, tests, and launcher icon. Before this overhaul:

1. Run `git status --short`.
2. Review the existing changes.
3. Compile and test them.
4. Commit them as a recoverable baseline.
5. Do not discard or overwrite unrelated user changes.

## Product decision

An imported document becomes a generated audiobook. Normal playback reads persistent chapter audio files; it never synthesizes the next sentence while the user is listening.

The first production version is fully offline. Remove Edge TTS only after the new path is verified. Do not send full books to an unofficial remote TTS endpoint.

### Required user flow

1. User imports or scans a document.
2. Existing parsers extract metadata, chapters, text, and cover.
3. The book appears in the library immediately with a conversion state.
4. A unique durable background job converts every chapter with the selected neural voice.
5. Completed chapters are encoded and stored permanently.
6. A book becomes `READY` after every playable chapter is valid.
7. The player loads stored chapter files as a playlist.
8. Playback survives navigation, process recreation, screen lock, Bluetooth controls, and chapter boundaries.
9. Failed conversion resumes from the first incomplete chapter.

## Non-negotiable constraints

- Preserve existing books, chapters, bookmarks, highlights, notes, listening statistics, covers, and reading progress.
- Use an explicit Room migration. Never enable destructive migration.
- Conversion must survive app process death and device reboot.
- One unique conversion job per book; duplicate taps must not duplicate work.
- Every retry must be idempotent.
- Write audio to a temporary file and atomically rename only after validation.
- Never mark a chapter complete before its final file exists and is playable.
- Deleting a book must cancel conversion and delete its owned audio directory.
- Check free storage before generation and during long conversions.
- Keep generated audio in app-private storage.
- Do not delete the live TTS path during the expand and migrate phases.
- Do not add cloud accounts, sync, voice cloning, DRM, or a backend in this overhaul.

## Target architecture

```text
Document picker / Scanner
        |
        v
Existing import and parsing pipeline
        |
        +--> Room: book, sections, text chunks
        |
        +--> AudiobookGenerationCoordinator.enqueue(bookId)
                    |
                    v
             WorkManager unique work
                    |
                    v
          GenerateAudiobookWorker
                    |
          +---------+----------+
          |                    |
          v                    v
 NeuralAudioGenerator     AudioFileStore
          |                    |
          +--> chapter temp PCM/WAV
                               |
                               v
                         AAC/M4A encoder
                               |
                               v
                  files/audiobooks/{bookId}/
                               |
                               v
                    ChapterAudio rows + cues
                               |
                               v
                  Media3 PlaybackService
                               |
                 PlaybackController / UI state
```

## Data ownership

### Source-of-truth rules

- Room owns generation status, chapter-audio metadata, cue metadata, and playback progress.
- WorkManager owns durable execution and retry scheduling.
- `AudioFileStore` owns filesystem layout, atomic file writes, validation, and deletion.
- The neural generator owns synthesis only. It must not update UI or playback state.
- The playback service owns the active media playlist and transport state.
- Screen ViewModels observe state; they do not own conversion or playback queues.

### Filesystem layout

```text
files/audiobooks/{bookId}/
  manifest.json
  chapter-000.m4a
  chapter-001.m4a
  chapter-002.m4a
  temp/
```

Use zero-padded chapter numbers. Delete `temp/` after success and on safe recovery. Never delete a valid chapter file merely because a later chapter failed.

## Room schema expansion

Increase the database from version 6 to version 7 during the expand phase.

### `audiobook_generations`

```text
bookId TEXT PRIMARY KEY, FOREIGN KEY books(id) ON DELETE CASCADE
status TEXT NOT NULL
completedChapters INTEGER NOT NULL DEFAULT 0
totalChapters INTEGER NOT NULL DEFAULT 0
progressPercent INTEGER NOT NULL DEFAULT 0
voiceId TEXT NOT NULL
modelVersion TEXT NOT NULL
generationSpeed REAL NOT NULL DEFAULT 1.0
estimatedBytes INTEGER NOT NULL DEFAULT 0
generatedBytes INTEGER NOT NULL DEFAULT 0
errorCode TEXT
errorMessage TEXT
createdAt INTEGER NOT NULL
updatedAt INTEGER NOT NULL
```

Allowed statuses:

```text
QUEUED
PREPARING
CONVERTING
PAUSED
READY
FAILED
CANCELLED
```

Do not use `READY` unless all non-empty chapters have valid final files.

### `chapter_audio`

```text
bookId TEXT NOT NULL, FOREIGN KEY books(id) ON DELETE CASCADE
chapterIndex INTEGER NOT NULL
status TEXT NOT NULL
filePath TEXT
durationMs INTEGER NOT NULL DEFAULT 0
fileSizeBytes INTEGER NOT NULL DEFAULT 0
checksum TEXT
segmentCount INTEGER NOT NULL DEFAULT 0
updatedAt INTEGER NOT NULL
PRIMARY KEY(bookId, chapterIndex)
```

Allowed statuses: `PENDING`, `GENERATING`, `READY`, `FAILED`, `SKIPPED`.

### `audio_cues`

```text
bookId TEXT NOT NULL
chapterIndex INTEGER NOT NULL
sentenceIndex INTEGER NOT NULL
startMs INTEGER NOT NULL
endMs INTEGER NOT NULL
PRIMARY KEY(bookId, chapterIndex, sentenceIndex)
FOREIGN KEY(bookId, chapterIndex) REFERENCES chapter_audio(bookId, chapterIndex) ON DELETE CASCADE
```

Audio cues preserve text highlighting, sentence bookmarks, and seek-to-text behavior.

### Playback progress

Expand `reading_progress` with:

```text
audioPositionMs INTEGER NOT NULL DEFAULT 0
```

Retain `currentPosition` as the sentence index during the compatibility window. Update both values while both playback paths exist.

### Required DAO operations

- Observe generation by book ID.
- List pending or failed chapter audio in order.
- Upsert a generation row.
- Atomically mark a chapter ready and update generation totals.
- Reset one failed chapter for retry.
- Mark a generation ready only after a completeness query succeeds.
- Read ordered ready chapter audio for playback.
- Replace cues for one chapter in a transaction.
- Update audio playback position.
- Clear generation metadata without deleting the book.

## Audio format and storage policy

### Production output

- Container: M4A
- Codec: AAC-LC
- Channels: mono
- Sample rate: 24 kHz, unless the native model requires another rate
- Default bitrate: 48 kbps
- High quality option: 64 kbps
- One final file per chapter

At 48 kbps, budget approximately 21.6 MB per hour. Add a safety margin for container overhead, temporary PCM, and interrupted work.

Before starting, require:

```text
freeBytes >= estimatedFinalBytes + largestEstimatedChapterTemporaryBytes + safetyMargin
```

Use at least a 250 MB safety margin. If that is excessive on a low-storage device, use the larger of 250 MB or 20% of the estimated final output.

### Generation segmentation

The native model may still synthesize bounded text segments internally. That is an implementation detail of conversion, not playback.

- Reuse the current sentence parsing behavior.
- Preserve sentence order.
- Record each segment's resulting duration.
- Build sentence cue ranges while appending audio.
- Reject empty or zero-duration output.
- Normalize only enough to prevent clipping; do not introduce a large DSP framework in version one.

## Module disposition matrix

Status meanings:

- `KEEP`: preserve behavior unless integration requires a small change.
- `MODIFY`: remains, but its responsibility changes.
- `ADD`: new production module or file.
- `REMOVE LATER`: delete only in the contract phase after all gates pass.

### Build and packaging

| Module/file | Status | Required work |
|---|---|---|
| `settings.gradle.kts` | MODIFY | Keep `:app` and `:offlinevoices`; no new Gradle module is required initially. |
| `gradle/libs.versions.toml` | MODIFY | Add WorkManager, Hilt WorkManager integration, Media3 ExoPlayer, Media3 Session, and Media3 UI only if used. |
| `app/build.gradle.kts` | MODIFY | Apply required KSP/Hilt worker setup and Media3 dependencies. Preserve debug access to offline assets and release asset-pack configuration. |
| `offlinevoices/` | KEEP | Retain neural model, phonemizer, dictionary, and voice files. |
| ProGuard/R8 rules | MODIFY | Preserve worker constructors, Room schema, Media3 session, JNI bridge, and native entry points where required. |

Use currently stable dependency versions compatible with the repository's Android Gradle Plugin and Kotlin versions. Do not upgrade unrelated dependencies in the same change.

### Application and manifest

| Module/file | Status | Required work |
|---|---|---|
| `VoxLeafApplication.kt` | MODIFY | Initialize WorkManager/Hilt integration if required by the chosen worker factory. |
| `AndroidManifest.xml` | MODIFY | Declare Media3 playback service and required foreground service types. Keep wake lock and notifications. Remove internet/network permissions only in the final Edge removal phase. |
| `MainActivity.kt` | KEEP | No conversion or playback ownership. |

### Data and persistence

| Module/file | Status | Required work |
|---|---|---|
| `Entities.kt` | MODIFY | Add generation, chapter-audio, cue entities and audio timestamp. Do not overload `TextChunkEntity.audioFilePath`. |
| `AppDatabase.kt` | MODIFY | Register new entities and set version 7. |
| `DatabaseModule.kt` | MODIFY | Add tested migration 6→7; never use destructive migration. |
| `BookDao.kt` | MODIFY | Add generation/audio operations or introduce a focused `AudiobookDao`. Prefer `AudiobookDao` to prevent `BookDao` becoming a grab bag. |
| `BookWithDetails.kt` | MODIFY | Expose conversion summary only where library/detail screens need it. Avoid loading every cue in library queries. |
| `AppSettingsManager.kt` | MODIFY | Store default generation voice, generation speed, quality, charging policy, and storage policy. Preserve old TTS settings until contraction. |
| `RoomBookRepository.kt` | MODIFY | Cancel generation and delete files when deleting a book. Keep book/highlight/bookmark behavior. |
| `AudiobookDao.kt` | ADD | Own generation, chapter audio, cue, and resume queries. |
| `AudiobookRepository.kt` | ADD | Provide domain-safe observation and commands for generation state. |
| `AudioFileStore.kt` | ADD | Own paths, free-space checks, temp files, atomic commit, validation, checksum, and recursive per-book cleanup. |

### Import, parsing, OCR, and chapter detection

| Module/file | Status | Required work |
|---|---|---|
| `TextBookImporterImpl.kt` | MODIFY | Finish import transaction first, then enqueue unique conversion. Import success means the book exists; expose conversion state separately. |
| `ImportTextBookUseCase.kt` | MODIFY | Keep parse progress. Return the book ID and conversion-enqueued state without blocking until a full audiobook is generated. |
| `ImportViewModel.kt` | MODIFY | Observe parse state and then generation summary. Never run synthesis in `viewModelScope`. |
| `ImportScreen.kt` | MODIFY | Show parsing separately from queued conversion and navigate safely after import. |
| `TextImportStreams.kt` | KEEP | Preserve the 100 MiB boundary and bounded copy behavior. |
| `ChapterDetector.kt` | KEEP | Remains the chapter source for plain text and fallback parsing. |
| `EpubParser.kt` | KEEP | Preserve parsing tests. |
| `PdfBookParser.kt` | KEEP | Preserve outlines and text extraction. |
| `DocxParser.kt` | KEEP | Preserve parsing tests. |
| `MobiParser.kt` | KEEP | Preserve MOBI/AZW parsing tests. |
| `Fb2Parser.kt` | KEEP | Preserve FB2 parsing. |
| `HtmlTextExtractor.kt` | KEEP | Preserve text extraction. |
| `OcrScanner.kt` | KEEP | Scanned books enter the same conversion queue after OCR import. |
| Re-scan chapters feature | MODIFY | Invalidate old generated audio only after confirmation, cancel current work, replace chapters, then enqueue regeneration. |

### Neural generation

| Module/file | Status | Required work |
|---|---|---|
| `KokoroNativeEngine.kt` | MODIFY | Rename later to `NeuralAudioGenerator`; remove playback concerns and stale sentence-file sweeping. Expose deterministic segment generation. |
| `kokoro_bridge.cpp` | MODIFY | Keep initialization and synthesis; expose sample-rate/output metadata if Kotlin cannot reliably infer it. |
| `CMakeLists.txt` | KEEP/MODIFY | Preserve native libraries; add only encoder/native changes proven necessary. Prefer Android `MediaCodec`/`MediaMuxer` over another large native codec dependency. |
| `TtsTextParser.kt` | MODIFY | Rename later to `GenerationTextSegmenter`; retain bounded segments and add stable cue mapping. |
| `TtsEngine.kt` | REMOVE LATER | Its live-engine abstraction is unnecessary when the product has one offline generation pipeline. Keep temporarily for compatibility. |
| `EdgeTtsEngine.kt` | REMOVE LATER | Delete after offline generation and migration gates pass. |
| `GenerateAudiobookWorker.kt` | ADD | Durable, foreground, unique, resumable per-book generation worker. |
| `AudiobookGenerationCoordinator.kt` | ADD | Enqueue, pause/cancel, retry, and unique-work naming. |
| `ChapterAudioAssembler.kt` | ADD | Append segment PCM, encode/mux chapter output, calculate cues, validate duration. |
| `StorageEstimator.kt` | ADD | Estimate final and temporary bytes from text/estimated duration and selected quality. |

### WorkManager behavior

Use unique work name:

```text
audiobook-generation-{bookId}
```

Default policy:

- Initial enqueue: `KEEP`
- Explicit regenerate after confirmation: cancel old work, clear only owned generated output, then enqueue new work
- Retry failed generation: resume incomplete chapters

Worker algorithm:

1. Load book and generation record.
2. Exit success if generation is already complete and files validate.
3. Mark `PREPARING`.
4. Check voice assets and storage.
5. Query chapters without valid `READY` audio.
6. For each chapter in order:
   - stop cleanly if cancelled;
   - mark chapter `GENERATING`;
   - synthesize bounded segments;
   - assemble cues and encode a temporary chapter file;
   - validate duration and file size;
   - checksum and atomically commit;
   - transactionally mark chapter `READY` and update progress;
   - release temporary PCM before the next chapter.
7. Run a database/filesystem completeness check.
8. Mark generation `READY`.
9. Return success.

Retry only transient failures. Invalid source text, unsupported model assets, and insufficient storage must become actionable failures rather than infinite retries.

### Playback

| Module/file | Status | Required work |
|---|---|---|
| `PlaybackService.kt` | MODIFY | Convert to a Media3 `MediaSessionService`; own ExoPlayer, media session, queue, and notification. |
| `TtsManager.kt` | REMOVE LATER | During migration, keep as fallback. Generated books must use the new controller. Delete after the fallback gate closes. |
| `AudiobookPlaybackController.kt` | ADD | Build ordered MediaItems from ready chapter files and expose stable playback state. |
| `PlaybackState.kt` | ADD | Include book, chapter, duration, position, buffering, playing, speed, error, and queue availability. |
| `ListeningTracker.kt` | MODIFY | Read Media3 playing state and elapsed time instead of TTS state. Preserve existing statistics. |

Media item identity must include `bookId` and `chapterIndex`. Restore the active book, playlist, chapter, and position after service/process recreation.

Do not store the active playlist in a Reader ViewModel.

### Player UI

| Module/file | Status | Required work |
|---|---|---|
| `GlobalPlayerBar.kt` | MODIFY | Bind to generated-audio playback. Keep play/pause, previous/next chapter, seek, sleep timer, bookmark, and permanent expand control. Remove engine and voice selection. |
| `VoxLeafApp.kt` | MODIFY | Bind the persistent player without overlapping bottom navigation. Preserve navigation ownership. |
| Playback notification | MODIFY | Show book/chapter title, real position, and Media3 transport controls. |

Voice choice belongs in generation settings, not in the active player. Playback speed remains a runtime player control and must not regenerate audio.

### Reader

| Module/file | Status | Required work |
|---|---|---|
| `ReaderViewModel.kt` | MODIFY | Load generated playback by book ID and seek using cues. Remove synthesis calls only during contraction. |
| `ReaderContract.kt` | MODIFY | Replace TTS engine/preparing fields with conversion readiness and media position. |
| `ReaderScreen.kt` | MODIFY | Show conversion state if audio is not ready; bind highlighted sentence through cues. Remove Edge/offline engine labels. |
| `domain/model/tts/TtsModels.kt` | RENAME/REMOVE LATER | Move reusable text chunk concepts out of the TTS namespace. |

Reader text navigation rules:

- Tapping a sentence seeks to its `AudioCue.startMs`.
- Current highlighting chooses the cue containing the current media timestamp.
- Missing cues must not crash playback; fall back to chapter-level progress.
- Existing sentence-index bookmarks remain valid.

### Library and book details

| Module/file | Status | Required work |
|---|---|---|
| `LibraryContract.kt` | MODIFY | Add lightweight conversion summary per book. |
| `LibraryViewModel.kt` | MODIFY | Combine books with generation summaries without N+1 database queries. |
| `LibraryScreen.kt` | MODIFY | Show `Queued`, `Converting 38%`, `Ready`, or `Failed`. |
| `BookDetailsContract.kt` | MODIFY | Add generation state, size, selected voice, retry/cancel/regenerate commands. |
| `BookDetailsViewModel.kt` | MODIFY | Coordinate user actions through repositories/coordinator. |
| `BookDetailsScreen.kt` | MODIFY | Show chapter conversion status and storage usage. Confirm destructive regeneration. |

### Voice selection and settings

| Module/file | Status | Required work |
|---|---|---|
| `VoiceSelectionScreen.kt` | MODIFY | Present installed offline voices as generation profiles and allow a short sample. Remove Edge engine selection after contraction. |
| `VoiceSelectionViewModel.kt` | MODIFY | Update default generation settings; never mutate active audiobook voice. |
| `SettingsScreen.kt` | MODIFY | Add quality, charging, battery, storage, auto-convert, and generated-audio cleanup settings. Update privacy copy. |
| `AboutScreen.kt` | MODIFY | Describe automatic offline neural audiobook generation and stored playback. |

Changing the default voice affects future conversions only. Regenerating an existing book requires an explicit confirmation because it deletes and replaces generated audio.

### Search, bookmarks, highlights, and statistics

| Module/file | Status | Required work |
|---|---|---|
| `SearchScreen.kt` | KEEP | Text search remains independent of audio. |
| `BookmarksScreen.kt` | MODIFY SMALL | Opening a bookmark seeks through cue metadata if audio is ready; otherwise open text only. |
| Highlight/note repositories and screens | KEEP | Preserve all data and exports. |
| `ListeningStats.kt` and `StatsScreen.kt` | KEEP/MODIFY SMALL | Preserve aggregation; change only the event source to Media3 playback. |

### Navigation and theme

| Module/file | Status | Required work |
|---|---|---|
| `NavGraph.kt` / `Screen.kt` | MODIFY SMALL | Add a conversion detail route only if inline library/detail UI becomes crowded. Do not create a screen without a demonstrated need. |
| Theme, colors, shapes, typography | KEEP | This is an architecture overhaul, not another visual redesign. |
| Launcher resources | KEEP | Do not mix icon redesign into this migration. |

### Tests

| Area | Required checks |
|---|---|
| Room migration | Migrate a populated v6 database to v7 and prove books, progress, bookmarks, highlights, notes, covers, and stats remain. |
| Worker idempotency | Running the same work twice does not duplicate or overwrite valid audio. |
| Worker recovery | Kill/recreate between chapters and resume at the first incomplete chapter. |
| Atomic output | A cancelled or failed chapter leaves no file marked ready. |
| Storage | Refuse safely before corruption; preserve completed chapters. |
| Deletion | Cancels work and deletes only `files/audiobooks/{bookId}` plus related rows. |
| Regeneration | Requires confirmation, replaces owned audio, preserves text annotations. |
| Playback | Continuous chapter transitions with Reader destination destroyed. |
| Restoration | Recreate process and restore book, chapter, and timestamp. |
| Cues | Text tap seeks to audio; playback timestamp highlights correct sentence. |
| Controls | Notification, lockscreen, Bluetooth, mini-player, sleep timer, and audio focus. |
| Imports | 1 MiB, 10 MiB, 50 MiB, and 100 MiB valid documents. |
| Stress | 1, 100, and 1,000 chapters; long chapter; empty chapters; malformed text. |
| Device | HyperOS install and on-device playback/conversion with screen locked. |

## Migration phases and gates

### Phase 0 — Baseline

Tasks:

- Review and commit current uncommitted work.
- Run the full JVM test suite.
- Build debug and release.
- Install debug on the connected phone.
- Record current database schema and sample user-data counts.

Gate: clean recoverable commit and passing baseline. No overhaul code before this gate.

### Phase 1 — Expand persistence

Tasks:

- Add entities, DAO, repository contracts, database v7, and migration 6→7.
- Add migration and DAO tests.
- Add `AudioFileStore` and storage estimator tests.
- Do not change playback yet.

Gate: populated v6 fixture migrates with zero user-data loss; old live playback still works.

### Phase 2 — Generate one chapter

Tasks:

- Refactor native synthesis behind `NeuralAudioGenerator` while keeping compatibility.
- Implement chapter assembly, AAC/M4A output, validation, checksum, and cues.
- Add a developer-only command or test fixture for one short chapter.

Gate: a generated chapter is playable after app restart, has correct duration, and has no leaked temporary files.

### Phase 3 — Durable whole-book conversion

Tasks:

- Add WorkManager dependencies and Hilt worker integration.
- Implement coordinator and foreground worker.
- Connect all import paths to unique work.
- Add progress, cancel, retry, storage, and resume behavior.

Gate: whole-book conversion resumes after forced process death and device reboot without regenerating completed chapters.

### Phase 4 — Generated-audio playback

Tasks:

- Introduce Media3 playback controller and service.
- Route ready books through stored chapter playlists.
- Persist timestamp and restore queue.
- Connect mini-player, notification, audio focus, sleep timer, and listening tracker.

Gate: a ready audiobook plays continuously with the Reader destination destroyed and restores after process recreation.

### Phase 5 — UI and synchronization

Tasks:

- Update import, library, details, reader, voice, and settings UI.
- Add cue-based text synchronization and seek-to-sentence.
- Add cleanup/regeneration confirmation.

Gate: all conversion states are actionable and accessibility labels/tests pass.

### Phase 6 — Contract old architecture

Only after every previous gate passes:

- Remove `TtsManager` live synthesis and sentence buffer.
- Remove `TtsEngine` if it has no remaining useful abstraction.
- Remove `EdgeTtsEngine` and Edge tests.
- Remove Edge UI, settings keys after a compatibility read, WebSocket dependency, internet/network permissions if no other feature uses them.
- Rename TTS-domain models to audiobook/playback names.
- Remove compatibility columns or fields only in a later database version, not v7.
- Delete obsolete temporary-audio cleanup code.

Gate: repository search contains no live synthesis call from playback or Reader; full tests and device audit pass.

## Rollback plan

- Phase 1 is additive. Rolling the app code back must not destroy v7 user data; therefore do not distribute a v7 build publicly until rollback compatibility has been evaluated.
- During phases 2–5, retain live TTS as a guarded fallback for books without ready generated audio.
- A failed generated path must never mutate or delete source text, annotations, or existing progress.
- Generated files are derived data and may be deleted/rebuilt; user-authored data is not derived and must never be discarded.
- Do not downgrade the database on-device. Roll forward with fixes.
- Before contraction, tag or commit the last known-good compatibility build.

## Observability and error codes

Persist stable error codes so UI and diagnostics do not parse messages:

```text
SOURCE_MISSING
NO_PLAYABLE_TEXT
VOICE_ASSET_MISSING
MODEL_INITIALIZATION_FAILED
INSUFFICIENT_STORAGE
SYNTHESIS_FAILED
ENCODE_FAILED
OUTPUT_VALIDATION_FAILED
CANCELLED_BY_USER
UNKNOWN
```

Log book ID, chapter index, phase, attempt, elapsed time, and error code. Never log complete book text.

## Performance and thermal policy

- Convert sequentially by default; do not synthesize multiple chapters concurrently on a phone.
- Release segment PCM as soon as it is encoded.
- Update progress at chapter boundaries and coarse segment intervals, not every sample.
- Foreground conversion must show a cancellable notification.
- Default long conversion to charging-only if device testing shows unacceptable battery or thermal impact.
- Pause cleanly under critically low storage.
- Do not hold the entire book or chapter PCM in memory when streaming to an encoder is possible.

## Luna execution rules

For each phase Luna must:

1. Re-read this document and inspect the current files before editing.
2. Run `git status --short` and preserve unrelated changes.
3. State the current phase and gate.
4. Trace all callers before replacing shared behavior.
5. Make the smallest phase-complete change.
6. Add the smallest test that proves every non-trivial branch introduced.
7. Run focused tests, then the full JVM suite for cross-module changes.
8. Run `git diff --check`.
9. Report modified files, test evidence, risks, and whether the phase gate passed.
10. Stop at the gate. Never jump to contraction automatically.

Luna must not:

- Rewrite the app from scratch.
- Replace working parsers.
- delete user data or use destructive Room migration.
- generate audio in a screen ViewModel.
- store a playback queue in Compose state.
- mark partial files ready.
- add speculative interfaces, factories, modules, or a cloud backend.
- mix unrelated UI redesign, branding, or dependency upgrades into the migration.
- remove live TTS before generated playback has passed every required gate.

## Completion definition

The overhaul is complete only when all statements are true:

- Imported documents automatically enqueue full neural audiobook conversion.
- Normal playback performs zero neural synthesis.
- Every playable chapter has durable validated audio.
- Conversion survives process death and reboot.
- Retry resumes rather than restarts completed work.
- Playback crosses chapters without a Reader ViewModel.
- Playback restores the exact book, chapter, and timestamp.
- Text highlighting and sentence bookmarks map through persisted cues.
- Book deletion cancels work and removes only owned derived audio.
- Voice regeneration preserves books, notes, highlights, bookmarks, and statistics.
- Existing v6 user data migrates without loss.
- The 100 MiB import boundary remains enforced.
- The app builds release, passes the full JVM suite, passes AndroidJUnit tests, and passes a real HyperOS audit.

## Recommended first Luna task

Do Phase 0 only: inspect the dirty worktree, validate the existing changes, commit a recoverable baseline, and report the baseline test/build/device evidence. Do not start the database migration in the same task.

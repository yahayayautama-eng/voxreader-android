# VoxLeaf Engineering Handoff

Updated: 2026-08-12  
Repository: `C:\Users\Yayis\Desktop\CODEX\Android studio\voxleaf`  
Branch: `main`  
Package: `com.aistudio.voxreader.xyz.debug`

## Product direction

VoxLeaf is being changed from “read text with TTS as the user goes” to:

1. import a book;
2. detect chapters and sentences;
3. let the user choose voice, speed, and tone in Settings;
4. generate the complete offline neural audiobook before playback;
5. store chapter-level M4A files and cue metadata;
6. play those files continuously, including when the Reader screen is destroyed.

The current implementation is an incremental migration toward that design. It is not yet a production-ready audiobook renderer.

## Baseline commits

The current baseline is commit `bf73cc3` (`fix: show audiobook conversion state instead of tts preparation`). Earlier commits added the audiobook-generation pipeline:

- `444d6a2` — conversion controls and M4A output;
- `58c053a` — generated playback/audio cues;
- `d056c42` — playback restore after process restart;
- `30b0d81` — gate playback on completed conversion.

## Current uncommitted changes

Do not discard these changes. They are intentional and currently uncommitted:

- `app/build.gradle.kts`
  - adds Hilt WorkManager compiler via KSP;
- `app/src/main/AndroidManifest.xml`
  - disables default WorkManager initialization;
  - declares foreground data-sync service permission/type;
- `AudiobookGenerationCoordinator.kt`
  - repairs stale QUEUED/CONVERTING jobs when WorkManager lost the active request;
- `GenerateAudiobookWorker.kt`
  - uses Hilt worker creation;
  - promotes to foreground safely on HyperOS;
  - splits imported paragraph chunks through `TtsTextParser`;
  - retries failed sentences;
  - never silently drops missing audio;
  - uses a dedicated audiobook synthesis output API;
- `KokoroNativeEngine.kt`
  - repairs truncated copied model assets;
  - validates native WAV output;
  - normalizes punctuation for the native tokenizer;
  - separates audiobook output files from live playback cache;
- `TtsTextParser.kt`
  - caps native input at 160 characters;
- `ReaderViewModel.kt`
  - schedules stale audiobook jobs while a book is opened;
- `GlobalPlayerBar.kt`
  - removes voice/engine/speed controls from the player;
  - keeps the persistent expand control and playback controls;
  - removes sliding player visibility behavior.

`.idea/` is untracked local IDE state. Do not commit it.

## What has been verified

### Desktop build

The following command passes:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Latest result: `BUILD SUCCESSFUL`; 57 tasks, all unit tests passed.

### Phone / HyperOS

Device used: Xiaomi 2406APNFAG / HyperOS, connected over wireless ADB.

Verified previously:

- debug APK installs with `adb install -r`;
- Hilt worker creation works;
- no `InvalidForegroundServiceTypeException`;
- foreground notification `Creating audiobook` appears;
- the bundled phonemizer/model assets repair themselves when an older truncated copy exists;
- chapters 0–6 were generated as valid M4A files;
- the large preface is chapter index 7 and contains 240 imported chunks.

The phone conversion is slow because native inference is CPU-heavy. Do not call a job stuck merely because chapter 7 takes a long time.

## Important failure discovered

The first audiobook worker wrote synthesized files into `files/kokoro-runtime`. Live playback also uses that directory and deletes files as playback advances. The worker eventually failed with:

```text
/data/user/0/com.aistudio.voxreader.xyz.debug/files/kokoro-runtime/audio-....wav:
open failed: ENOENT (No such file or directory)
```

The root cause is shared temporary-file ownership, not a missing model. The current patch adds `synthesizeForAudiobook()` and writes audiobook segments to a separate `files/audiobook-runtime` directory. This build passed desktop tests and was installed on the phone. A retry was started, but the final chapter commit still needs to be observed on-device.

## Current phone state at handoff

The last observed book is:

- title: `Covert Wars and Breakaway Civilizations`;
- generation row: previously `FAILED`, chapter 7 incomplete;
- chapters 0–6: `READY`;
- chapter 7: old `.m4a.tmp`/`.wav.tmp` files remain from the failed attempt;
- the new build was installed and the UI Retry action was tapped;
- WorkManager started `GenerateAudiobookWorker` again;
- no new native error was visible in the short follow-up window.

Next phone check:

```powershell
$adb='C:\Users\Yayis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$device=((& $adb devices) | Select-String '\sdevice$' | Select-Object -First 1).ToString().Split("`t")[0]
& $adb -s $device shell run-as com.aistudio.voxreader.xyz.debug ls -l files/audiobook-runtime
& $adb -s $device shell run-as com.aistudio.voxreader.xyz.debug ls -l files/audiobooks/84bc302d-25c0-4996-927c-e66aa2c0bcc8
& $adb -s $device logcat -d -v threadtime -s GenerateAudiobookWorker:V KokoroNativeEngine:V onnxruntime:V WM-WorkerWrapper:V
```

Do not delete the old temporary chapter files until the retry result is known.

## Remaining functional gaps

### P0 — audiobook generation reliability

- Verify the dedicated `audiobook-runtime` output survives concurrent live playback and reaches the final `.m4a` commit.
- Confirm all 240 chapter-7 segments are represented by cues and no sentence is omitted.
- Add cleanup for abandoned `audiobook-runtime` WAV files after success/failure/retry.
- Update progress during a chapter, not only after the whole chapter finishes. Current progress can sit at 25% for a long time.
- Avoid retrying an entire 240-segment chapter when only one sentence failed. Persist sentence progress if generation time becomes unacceptable.
- Add a visible error message with a retry action that includes the actual failure reason.

### P0 — continuous playback architecture

The durable playback queue should belong to `TtsManager` or a foreground `PlaybackService`, not `ReaderViewModel`. The queue must persist:

- book ID;
- ordered chapter IDs;
- current chapter and cue index;
- generated-audio identity/checksum;
- selected voice/model/speed version.

Reader screens should observe this state and never own chapter-completion decisions. This is required for background playback and screen/process recreation.

### P1 — player UX

- Confirm the global Play button cold-starts from a stopped/error state.
- Confirm the mini-player cannot intercept bottom-navigation taps.
- Keep voice, engine, rate, and tone controls in Settings only.
- Keep the expand control permanently visible; do not reintroduce sliding open/close behavior.
- Verify accessibility labels and touch targets after the player redesign.

### P1 — import and parsing

- The requested minimum upload size is 100 MB. Current raw text stream handling has a 100 MB ceiling, but several archive/parser decompression ceilings remain 50 MB. Decide whether the product requirement means input file size, decompressed text size, or both, then align all parsers.
- Add a single import policy shared by EPUB/MOBI/FB2/PDF/DOCX/OCR paths.
- Treat malformed Unicode and replacement characters deliberately; do not corrupt source text silently.
- Add parser fixtures for large books, scanned pages, malformed archives, and chapter headings.

### P1 — resource limits

Native inference can consume hundreds of MB of native memory and high CPU. Add:

- a user-visible storage estimate;
- a minimum free-space check before generation;
- cancellation handling that deletes only owned temporary files;
- thermal/battery/background policy;
- a maximum chapter/segment policy or resumable segment checkpointing.

### P2 — visual/product quality

- Replace the disproportionate app icon with a balanced adaptive icon and verify launcher mask rendering on Xiaomi/Pixel.
- Add a compact generation details screen: chapter, segment progress, elapsed time, remaining estimate, retry/cancel.
- Distinguish `QUEUED`, `CONVERTING`, `READY`, `FAILED`, and `CANCELLED` consistently across Library, Details, notification, and Player.

## What to remove or overhaul

### Remove

- Per-sentence live TTS as the primary audiobook path once generated audio is available.
- Voice/engine/rate controls from the player surface.
- Any screen-owned automatic chapter progression logic.
- Shared use of `kokoro-runtime/audio-*.wav` by audiobook generation and live playback.
- Silent `mapNotNull` behavior that creates an audiobook with missing sentences.
- “Preparing offline voice” wording for a book that is actually being converted.

### Overhaul

- `GenerateAudiobookWorker`: convert it into a resumable, checkpointed job with bounded memory and chapter/segment progress.
- `KokoroNativeEngine`: expose explicit output ownership or a lower-level synthesis API; keep model initialization shared but never share generated audio files.
- `TtsManager`: make it the single playback queue owner and persist the queue/progress.
- `AudiobookGenerationCoordinator`: make scheduling idempotent and reconcile DB state, files, and WorkManager state on app startup.
- Room schema: add generation version, segment progress, failure category, retry count, and audio checksum/version.
- Player: make it a pure controller/observer of the persistent playback state.

## Recommended execution order

1. Finish phone validation of the dedicated audiobook output directory.
2. Commit the current reliability patch as one checkpoint.
3. Add a small worker test for “one missing sentence never produces READY audio”.
4. Add resumable segment checkpoints and per-chapter progress.
5. Move continuous playback queue ownership into `TtsManager`/service.
6. Run parser/import stress tests with 100 MB+ fixtures.
7. Fix icon and final player accessibility/touch overlap.
8. Only then remove legacy live-TTS audiobook fallback.

## Do not claim yet

The app is not yet proven to generate a complete 27-chapter audiobook end-to-end on-device. The desktop build is green, the foreground worker path is repaired, and chapters 0–6 were previously generated, but final completion after the isolated-output fix still requires phone evidence.

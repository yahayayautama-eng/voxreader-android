# VoxLeaf — Rebuild Plan: TTS Replacement, Audio Pipeline, Playback

Drafted: 2026-08-12
Baseline commit: `57c3d06` (`fix: audiobook generation reliability and player redesign`)
Companion document: `VOXLEAF_ENGINEERING_HANDOFF.md`

---

## Context

The engineering handoff lists the remaining functional gaps. This plan covers how to close
them, in what order, and which model should be assigned to each task.

Two problems are structural rather than incidental, and they set the order of everything
below:

1. **The TTS stack is hand-maintained.** The app vendors a native bridge
   (`app/src/main/cpp/kokoro_bridge.cpp`), a CMake build, and the full ONNX Runtime source
   tree as a git submodule under `app/src/main/cpp/third_party/babylon/submodules/onnxruntime`
   (141 tracked files under `cpp/`). It ships two ONNX models as APK assets in the
   `:offlinevoices` module — `kitten-tts.onnx` (74.6 MB) and `open-phonemizer.onnx`
   (58.7 MB) — plus a JSON dictionary and 8 voice `.bin` files. That is ~133 MB of model
   assets and an entire inference runtime that the team owns, builds, and debugs itself.
   The handoff's "phone conversion is slow because native inference is CPU-heavy" and the
   truncated-asset repair code in `KokoroNativeEngine.copyAsset` are both consequences of
   this.

2. **Generation and playback each have more state than they can represent.** Covered in
   detail in the handoff; the fixes are phases 3 and 4 below.

Fixing (1) first is deliberate: it removes the slowest component, deletes the largest
amount of code, and makes phases 2–4 cheaper to build and test.

---

## Recommendation: replace the TTS stack with sherpa-onnx + Piper

### What to adopt

**Runtime: [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)** (k2-fsa, Apache-2.0).
It publishes prebuilt Android AARs with native libraries for `arm64-v8a`, `armeabi-v7a`,
`x86`, and `x86_64`, and exposes a Kotlin API (`OfflineTts`, `OfflineTtsConfig`,
`GeneratedAudio`). It wraps ONNX Runtime and runs VITS / Piper / Kokoro / Matcha models
behind one interface, fully offline.

**Voice model: Piper** (VITS-based), medium or low quality tier, as the default.
Optionally offer Kokoro-82M as a "higher quality, slower" alternative in Settings.

### Why this over the current stack

| Concern | Today | With sherpa-onnx + Piper |
|---|---|---|
| Inference runtime | Vendored ONNX Runtime submodule + custom `kokoro_bridge.cpp` + CMake, built in-tree | Prebuilt AAR dependency |
| Phonemization | `open-phonemizer.onnx`, 58.7 MB asset | Built into sherpa-onnx (lexicon / text normalization); asset deleted |
| Acoustic model | `kitten-tts.onnx`, 74.6 MB | Piper medium voice ≈ 63 MB; low tier ≈ 20 MB |
| Speed | "CPU-heavy", chapter 7 (240 chunks) takes very long | Piper reported at RTF 0.20 on a Raspberry Pi 4 — comfortably faster than realtime on any modern phone. Kokoro-82M reported at RTF 0.45 on an Android G99 |
| Voice count | 8 bundled `.bin` voices, English only | 30+ languages available; voices are downloadable model files, not baked into the APK |
| Maintenance | Team owns the C++, the JNI, the asset-repair logic, the WAV validation | Upstream owns it |
| Repo size | `.git` is 134 MB, largely these assets and the vendored runtime | Models move out of git; the `cpp/` tree is deleted |

The speed change is the one that matters most for the product. Generation time is the
reason the handoff cannot yet claim an end-to-end 27-chapter audiobook.

### What this deletes

- `app/src/main/cpp/` in full (bridge, CMake, `third_party` submodule tree)
- `externalNativeBuild` configuration in `app/build.gradle.kts`
- `KokoroNativeEngine`'s asset-copy, truncation-repair, WAV-header validation, and stale-sweep
  logic — sherpa-onnx returns samples in memory
- `open-phonemizer.onnx` and its dictionary
- The `normalizeForNative` punctuation workaround and the 160-character input cap in
  `TtsTextParser` (both exist to work around the current native tokenizer)

### Risks to handle explicitly

- **Voice identity changes.** Existing generated audiobooks were produced by kittenTTS
  voices. Bumping `KokoroNativeEngine.MODEL_VERSION` (or its replacement) must invalidate
  and offer to regenerate existing audiobooks rather than mixing voices within one book.
- **Model distribution.** Moving voices out of the APK means either (a) bundling one
  default voice and downloading the rest, or (b) keeping one bundled voice only. Option (a)
  conflicts with the app's "fully offline, nothing leaves the device" positioning unless the
  download is clearly a one-time, user-initiated action. **Decide this before starting
  phase 1.** Recommended: bundle one Piper low/medium English voice (~20–63 MB, still far
  below today's 133 MB), make additional voices an explicit opt-in download.
- **Dependency coordinate.** Published coordinates for the Android artifact differ between
  sources (Maven Central, JitPack, and third-party mirrors). Verify the current official
  coordinate and version against the sherpa-onnx releases page at implementation time
  rather than copying a version string from this document.
- **Asset compression.** Android's `aapt` compresses assets by default, which can corrupt
  binary model alignment. `noCompress` must cover `.onnx` and any other binary model files.

### Audio conversion tooling

Keep `MediaCodec` + `MediaMuxer` (`AacChapterEncoder`). It is the platform-native encoder,
already works, and needs no dependency. **But delete the WAV intermediate.**

Today the worker synthesizes every sentence to a separate WAV, concatenates them into one
full-chapter WAV (`WavChapterAssembler`), then encodes that to M4A. At 24 kHz / 16-bit mono
(~48 KB/s), a one-hour chapter is a ~172 MB temporary file on top of the per-sentence files.

With sherpa-onnx returning PCM samples in memory, the correct pipeline is:

```
for each sentence:
    samples = tts.generate(sentence)          # in memory, no file
    cue.startMs = encoderPositionMs           # cue offsets fall out of the encoder
    feed samples -> MediaCodec AAC encoder -> MediaMuxer
commit .m4a.tmp -> .m4a
```

This removes the per-sentence WAV files, the full-chapter WAV, `WavChapterAssembler`
entirely, and the disk-space failure mode — while producing the same cue table.

---

## Model assignment

The assignment rule used throughout:

- **Opus 5** — cross-cutting architecture, concurrency and lifecycle state machines, native/build-system
  surgery, anything where a wrong decision is expensive to unwind later.
- **Sonnet 5** — implementation against a spec that is already settled: schema changes, DAO
  and repository code, UI wiring, test suites, migrations.
- **Haiku 4.5** — mechanical and repetitive work with an unambiguous definition of done:
  deletions, renames, string and resource updates, dependency bumps, asset cleanup.

Where a task says "Opus 5 → Sonnet 5", the intent is that Opus designs and lands the
skeleton, then Sonnet fills in the repetitive remainder.

---

## Phase 0 — Baseline and safety net

| # | Task | Model | Notes |
|---|---|---|---|
| 0.1 | Confirm the current build generates a complete audiobook end-to-end on device, with the `57c3d06` fixes applied | Opus 5 | The handoff's open "do not claim yet" item. Needed as a before/after reference for the engine swap. |
| 0.2 | Add a `FakeTtsEngine` behind the existing `TtsEngine` interface that emits short real PCM/WAV | Sonnet 5 | Unblocks JVM testing of the whole generation pipeline. Highest leverage item in the plan. |
| 0.3 | Worker test suite: resume-after-kill is byte-identical; one permanently failing sentence never yields `READY`; blank section yields `SKIPPED` and the book still completes; re-running a finished job is a no-op | Sonnet 5 | Written against `FakeTtsEngine`; must pass before and after the engine swap. |
| 0.4 | Stop tracking `.idea/`; add to `.gitignore` | Haiku 4.5 | Currently untracked and noisy in every `git status`. |

Gate: 0.2 and 0.3 must be green before phase 1 begins. They are what make the engine swap
verifiable rather than hopeful.

---

## Phase 1 — TTS engine replacement

| # | Task | Model | Notes |
|---|---|---|---|
| 1.1 | Decide voice distribution: bundled-only vs bundled default + opt-in download | Opus 5 | Product decision with a privacy dimension. Blocks 1.3. |
| 1.2 | Verify the current official sherpa-onnx Android artifact coordinate and version; add the dependency; add `noCompress` for `.onnx` | Opus 5 | Do not trust a version string copied from a blog or from this document. |
| 1.3 | Implement `SherpaTtsEngine : TtsEngine` (`OfflineTts` init, `generate`, sample-rate handling, speed/voice selection) | Opus 5 | New primary engine. Must satisfy the same interface the rest of the app already uses, so nothing downstream changes. |
| 1.4 | Wire voice list, Settings picker, and persisted voice/engine selection to the new engine | Sonnet 5 | `AppSettingsManager`, `SettingsScreen`, `VoiceSelectionScreen`. |
| 1.5 | Bump model version; invalidate and offer regeneration for audiobooks produced by the old engine | Sonnet 5 | Must not mix voices inside one book. |
| 1.6 | Delete `app/src/main/cpp/`, the `externalNativeBuild` block, `kokoro_bridge.cpp`, the ONNX Runtime submodule, and the obsolete assets | Haiku 4.5 | Only after 1.3 is verified on device. Large mechanical deletion. |
| 1.7 | Delete `normalizeForNative`, the 160-char cap in `TtsTextParser`, and the asset-truncation repair path | Haiku 4.5 | These exist solely to work around the old native tokenizer. |
| 1.8 | Re-run 0.3 suite + on-device generation; record generation time vs the 0.1 baseline | Sonnet 5 | The headline number this phase exists to produce. |

---

## Phase 2 — Audio pipeline: remove the WAV intermediate

| # | Task | Model | Notes |
|---|---|---|---|
| 2.1 | Stream PCM from the engine directly into `MediaCodec`; derive cue offsets from encoder position | Opus 5 | Real-time encoder feeding with correct presentation timestamps and EOS handling. Easy to get subtly wrong. |
| 2.2 | Delete `WavChapterAssembler` and the temp-WAV paths in `AudioFileStore` | Haiku 4.5 | Straight deletion once 2.1 lands. |
| 2.3 | Free-space precheck before generation using the existing `StorageEstimator`; surface a clear message when there is not enough room | Sonnet 5 | `StorageEstimator.hasRoom` already exists and is unused at the call site. |
| 2.4 | Update `WavChapterAssemblerTest` / add cue-offset tests against the new encoder path | Sonnet 5 | Cue correctness is what sentence highlighting depends on. |

---

## Phase 3 — Resumable generation

| # | Task | Model | Notes |
|---|---|---|---|
| 3.1 | Add `audio_segments` table (`bookId`, `chapterIndex`, `sentenceIndex`, `text`, `status`) + Room migration | Sonnet 5 | Schema is already settled by the design; mechanical against the existing migration pattern in `DatabaseModule`. |
| 3.2 | Populate segments at enqueue time from `TtsTextParser.sentences(...)` | Sonnet 5 | |
| 3.3 | Rewrite the worker as a DB-driven loop over incomplete segments; per-segment commit | Opus 5 | This is the change that makes generation resumable by construction. Interacts with cancellation, retry, and foreground promotion. |
| 3.4 | Per-segment progress reporting (percent = ready segments / total) | Sonnet 5 | Fixes the "stuck at 25% for an hour" complaint. |
| 3.5 | `AudiobookRepository` as sole owner of DB + files + WorkManager, exposing one sealed state; add `reconcile()` run at startup | Opus 5 | Collapses the three ad-hoc reconciliation sites into one. |
| 3.6 | Point `ReaderViewModel`, `BookDetailsViewModel`, `LibraryViewModel`, and `PlaybackService` at the repository; delete `ensureScheduled` and the duplicated queue reconstruction | Sonnet 5 | Mechanical once 3.5 exists. |
| 3.7 | `reconcile()` tests: missing file, orphan file, row without file, half-written `.tmp` | Sonnet 5 | |
| 3.8 | Fix `generatedBytes` double-counting on chapter retry | Haiku 4.5 | One-line DAO query change. |

---

## Phase 4 — Playback unification

| # | Task | Model | Notes |
|---|---|---|---|
| 4.1 | Add media3; implement `MediaSessionService` with an ExoPlayer playlist of chapter M4A files | Opus 5 | Replaces hand-rolled MediaPlayer lifecycle, audio focus, notification, and transport controls. |
| 4.2 | Delete the live per-sentence TTS playback path, the buffer map, `playbackGeneration`, and the `generatedMode` branches from `TtsManager` | Opus 5 | The single largest bug-surface reduction in the plan. Must be done with 4.1, not before. |
| 4.3 | Drive sentence highlighting from player position via the existing cue binary search | Sonnet 5 | `cueIndex` already exists and is correct; only its input changes. |
| 4.4 | Correct MediaSession semantics: next/previous move by chapter, not ±10 s | Haiku 4.5 | Currently `onSkipToNext` seeks 10 seconds, which is wrong for Bluetooth and lockscreen. |
| 4.5 | Delete `PlaybackService.restoreAfterProcessRestart` | Haiku 4.5 | media3 restores state; this becomes dead code. |
| 4.6 | Global player: verify cold start from stopped/error state; confirm the bar cannot intercept bottom-nav taps; accessibility labels and touch targets | Sonnet 5 | Open P1 items from the handoff. |

---

## Phase 5 — Product polish

| # | Task | Model | Notes |
|---|---|---|---|
| 5.1 | Single import policy shared across EPUB/MOBI/FB2/PDF/DOCX/OCR; align the 50 MB and 100 MB ceilings after deciding whether the requirement means input size or decompressed size | Opus 5 | Requires a product decision first; the ceilings currently disagree with each other. |
| 5.2 | Parser fixtures: large books, scanned pages, malformed archives, chapter headings, malformed Unicode | Sonnet 5 | |
| 5.3 | Generation details screen: chapter, segment progress, elapsed, remaining estimate, retry/cancel with the real failure reason | Sonnet 5 | Depends on phase 3 progress data existing. |
| 5.4 | Consistent status vocabulary (`QUEUED`/`CONVERTING`/`READY`/`FAILED`/`CANCELLED`/`SKIPPED`) across Library, Details, notification, Player | Haiku 4.5 | |
| 5.5 | Balanced adaptive launcher icon; verify mask rendering on Xiaomi and Pixel | Haiku 4.5 | |
| 5.6 | Thermal / battery / background policy for generation | Opus 5 | WorkManager constraints plus a user-facing setting. |

---

## Verification gate

No release claim without all of the following green:

1. Phase 0.3 worker suite passes.
2. Phase 2.4 cue tests pass.
3. Phase 3.7 reconcile tests pass.
4. `:app:testDebugUnitTest :app:assembleDebug` builds clean.
5. One on-device end-to-end run: import the 27-chapter book, generate fully, play through a
   chapter boundary with the screen off, kill the app mid-generation and confirm it resumes.

Item 5 is the one the handoff currently cannot claim. Everything above exists to make it
cheap to re-run.

---

## Suggested execution order

Phases run in order. Within a phase, tasks marked Haiku are safe to defer to the end of
that phase; tasks marked Opus generally block the Sonnet tasks beneath them.

Phase 0 → 1 delivers the largest single user-visible improvement (generation speed) and
the largest code deletion. Phases 3 and 4 deliver reliability. Phase 5 is release polish.

## Sources

- [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
- [sherpa-onnx releases](https://github.com/k2-fsa/sherpa-onnx/releases)
- [Build sherpa-onnx for Android](https://k2-fsa.github.io/sherpa/onnx/android/build-sherpa-onnx.html)
- [Running Neural Text-to-Speech On-Device with Piper and sherpa-onnx](https://medium.com/@patare.vivek/running-neural-text-to-speech-on-device-with-piper-and-sherpa-onnx-58f4eed29247)
- [Offline TTS vs Cloud: Best Local AI Models for 2026](https://www.freevoicereader.com/blog/offline-tts-local-ai-2026-comparison)
- [Best Offline TTS Tools in 2026](https://offlinetts.com/blog/best-offline-tts-tools-2026/)
- [VoxSherpa TTS discussion — Kokoro + Piper + VITS on Android](https://github.com/k2-fsa/sherpa-onnx/discussions/3383)
- [siva-sub/NekoSpeak](https://github.com/siva-sub/NekoSpeak)

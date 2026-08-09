# Vox Reader — Project Status

Last updated: 8 August 2026

## What Vox Reader is

Vox Reader is a private, offline Android reading app. A person imports a document,
reads it on their phone, and can have it spoken aloud by a bundled local voice.
It is designed so the core reading and speech experience works without a cloud
account, a network connection, or Android's Google/system text-to-speech voice.

## The product promise

- Documents stay on the device.
- Speech is generated locally from the bundled Kokoro voice model.
- The app does not send document text to an online AI service.
- The reader should feel like a calm book tool, not an AI chat app.

## What has been achieved

### Native Android foundation

- Kotlin application using Jetpack Compose and Material 3.
- MVVM/UDF feature structure with Hilt dependency injection.
- Navigation Compose with type-safe destinations.
- Room database for books, chapters, reading progress, and bookmarks.
- Coroutines and Flow for reactive UI and playback state.

### Local document library

- Import `.txt`, text-based `.pdf`, `.epub`, and `.docx` files.
- Imported files are copied into Vox Reader's private app storage.
- Library records persist across app launches.
- Edit book metadata, favourite books, remove books, and search the library.
- Text-based PDFs are supported; scanned/image-only PDFs show a clear failure
  instead of pretending that text was extracted.

### Offline local voice

- Kokoro ONNX voice assets are bundled with the Android app.
- A native C++ bridge runs the local inference path on supported arm64 phones.
- Vox Reader does not fall back to Google TTS or Android system TTS.
- Speech is generated in short buffered groups to reduce gaps between sentences.
- Long sentences are safely split before inference to avoid native crashes.
- Playback supports play, pause, resume, stop, previous/next sentence, skip,
  speed changes, and an offline voice status display.

### Reader and navigation

- Reading progress is saved by chapter and sentence and restored on return.
- A 5, 10, 15, or 30 minute sleep timer stops the local voice automatically.
- The player shows sentence progress within the current chapter.
- Tapping any displayed sentence makes it the saved reading position; playback
  starts from that sentence.
- Bookmarks save the exact chapter and sentence. Selecting a bookmark returns
  to that exact place.
- Reader Contents lists every imported chapter and lets the reader jump to one.
- The Contents list on a book-detail page also opens the selected chapter
  directly.

### Product and UI cleanup

- The library, reader, import, bookmark, settings, and onboarding screens have
  been restyled around a content-first, editorial reading experience.
- Misleading fake AI-summary UI and simulated AI output were removed from the
  reader.
- The bookmark action on book details now opens Saved Places instead of being a
  non-functional button.

## Important limitations today

- Only `arm64-v8a` native voice support has been validated. Other Android CPU
  types need their own native build before release.
- The bundled voice increases APK size substantially.
- The current local model runs on CPU/native inference. GPU acceleration is not
  implemented yet.
- EPUB chapter titles come from document headings; EPUB navigation is not yet a
  full standards-level NCX/nav-document table-of-contents parser.
- Scanned/image-only PDFs need offline OCR before their text can be read.
- HTML and other formats are not yet supported. DOCX is supported.
- The sleep timer lives in the reader ViewModel. It is not a background-service
  timer, so it is not guaranteed to survive process death.
- This is a debug build, not a Play Store release build.

## Recommended next milestones

1. Add offline OCR for scanned PDFs, with explicit language-pack and storage
   choices.
2. Add DOCX and HTML import, using the same safe private-file import flow.
3. Profile local Kokoro performance and evaluate an optional GPU/NNAPI path;
   preserve CPU fallback.
4. Add more locally bundled voices only after measuring APK size and memory use.
5. Add migration tests, reader/bookmark UI tests, and connected-device
   instrumentation coverage.
6. Release preparation: app icon, versioning, privacy policy, signing key kept
   outside Git, signed Android App Bundle, and Play Store listing materials.

## Build and verification

Use JDK 17.

```powershell
$env:JAVA_HOME = (Resolve-Path '.tooling\jdk-17.0.20+8').Path
.\gradlew.bat --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process' :app:packageDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

The current debug APK is produced at:

`app/build/outputs/apk/debug/app-debug.apk`

## Repository note

The repository currently contains uncommitted implementation work. Before a
public push, review the bundled model and native binary licensing, confirm that
no signing keys or personal files are included, then commit the work in focused
stages.

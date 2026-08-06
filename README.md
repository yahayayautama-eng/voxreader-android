# VoxLeaf

Stage 1 baseline for the VoxLeaf native Android application: an offline, local
library with plain-text (`.txt`) import.

## Architecture
- **Language**: Kotlin
- **Build System**: Gradle Kotlin DSL
- **UI Toolkit**: Jetpack Compose (Material 3)
- **Architecture**: MVVM with Unidirectional Data Flow
- **Dependency Injection**: Hilt
- **Navigation**: Navigation Compose with type-safe routing
- **Local Database**: Room (setup configured)
- **Async**: Coroutines and Flow

## Package Structure
- `app`: Application class and main setup.
- `core`: Common utilities, navigation graph, DI modules.
- `data`: Room database, repositories, DataStore preferences, and safe TXT import.
- `domain`: Core business logic and repository interfaces.
- `feature`: UI screens categorized by feature (e.g. splash, library, reader).
- `playback`: Future media playback logic.
- `tts`: Future text-to-speech configuration.

## Setup and Build
This project can be opened directly in Android Studio. Use JDK 17 for Gradle
and Robolectric tests.

**Commands**:
- Debug Build: `./gradlew.bat assembleDebug` (Windows) or `./gradlew assembleDebug`
- Run Tests: `./gradlew.bat testDebugUnitTest`
- Lint: `./gradlew.bat lintDebug`

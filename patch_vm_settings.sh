sed -i 's/private val playbackController: PlaybackController/private val playbackController: PlaybackController,\n    private val appSettingsManager: com.example.data.local.datastore.AppSettingsManager/g' app/src/main/java/com/example/feature/reader/ReaderViewModel.kt

sed -i '/observeTtsState()/a \        viewModelScope.launch {\n            appSettingsManager.ttsRateFlow.collect {\n                _uiState.update { state -> state.copy(ttsRate = it) }\n            }\n        }\n        viewModelScope.launch {\n            appSettingsManager.ttsVoiceFlow.collect {\n                _uiState.update { state -> state.copy(ttsVoice = it) }\n            }\n        }' app/src/main/java/com/example/feature/reader/ReaderViewModel.kt

sed -i -e '/is ReaderUiAction.OnChangeTtsRate -> {/,/}/c\
            is ReaderUiAction.OnChangeTtsRate -> {\
                playbackController.setSpeed(action.rate)\
                viewModelScope.launch {\
                    appSettingsManager.setTtsRate(action.rate)\
                }\
            }' app/src/main/java/com/example/feature/reader/ReaderViewModel.kt

sed -i 's/playbackController.play(book.id, state.currentChapterIndex, state.currentSentenceIndex, state.ttsRate)/playbackController.play(book.id, state.currentChapterIndex, state.currentSentenceIndex, state.ttsRate, state.ttsVoice)/g' app/src/main/java/com/example/feature/reader/ReaderViewModel.kt
sed -i 's/playbackController.play(state.book.id, state.currentChapterIndex, newIndex, state.ttsRate)/playbackController.play(state.book.id, state.currentChapterIndex, newIndex, state.ttsRate, state.ttsVoice)/g' app/src/main/java/com/example/feature/reader/ReaderViewModel.kt


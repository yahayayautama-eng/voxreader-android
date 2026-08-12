package com.example.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.tts.SherpaTtsEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class AppSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val THEME_KEY = stringPreferencesKey("theme")
        val TTS_RATE_KEY = floatPreferencesKey("tts_rate")
        val TTS_PITCH_KEY = floatPreferencesKey("tts_pitch")
        val TTS_VOICE_KEY = stringPreferencesKey("tts_voice")
        val TTS_ENGINE_KEY = stringPreferencesKey("tts_engine")
        val AUTO_PLAY_KEY = booleanPreferencesKey("auto_play_on_open")
        val HIGHLIGHT_KEY = booleanPreferencesKey("highlight_spoken_sentences")
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { it[THEME_KEY] ?: "SYSTEM" }
    val ttsRateFlow: Flow<Float> = context.dataStore.data.map { it[TTS_RATE_KEY] ?: 1.0f }
    val ttsPitchFlow: Flow<Float> = context.dataStore.data.map { it[TTS_PITCH_KEY] ?: 1.0f }
    val ttsVoiceFlow: Flow<String> = context.dataStore.data.map { it[TTS_VOICE_KEY] ?: SherpaTtsEngine.DEFAULT_VOICE }
    /** Raw storage key ("offline"/"edge") rather than the `tts` package's [com.example.tts.EngineId]
     *  enum, so this data-layer class doesn't depend on it — callers map with `EngineId.fromStorageKey`. */
    val ttsEngineFlow: Flow<String> = context.dataStore.data.map { it[TTS_ENGINE_KEY] ?: "offline" }
    val autoPlayFlow: Flow<Boolean> = context.dataStore.data.map { it[AUTO_PLAY_KEY] ?: false }
    val highlightSentencesFlow: Flow<Boolean> = context.dataStore.data.map { it[HIGHLIGHT_KEY] ?: true }

    suspend fun setAutoPlay(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_PLAY_KEY] = enabled }
    }

    suspend fun setHighlightSentences(enabled: Boolean) {
        context.dataStore.edit { it[HIGHLIGHT_KEY] = enabled }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[THEME_KEY] = theme }
    }

    suspend fun setTtsRate(rate: Float) {
        context.dataStore.edit { it[TTS_RATE_KEY] = rate }
    }
    
    suspend fun setTtsPitch(pitch: Float) {
        context.dataStore.edit { it[TTS_PITCH_KEY] = pitch }
    }
    
    suspend fun setTtsVoice(voice: String) {
        context.dataStore.edit { it[TTS_VOICE_KEY] = voice }
    }

    suspend fun setTtsEngine(storageKey: String) {
        context.dataStore.edit { it[TTS_ENGINE_KEY] = storageKey }
    }
}

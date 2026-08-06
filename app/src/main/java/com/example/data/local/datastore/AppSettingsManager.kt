package com.example.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
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
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { it[THEME_KEY] ?: "SYSTEM" }
    val ttsRateFlow: Flow<Float> = context.dataStore.data.map { it[TTS_RATE_KEY] ?: 1.0f }
    val ttsPitchFlow: Flow<Float> = context.dataStore.data.map { it[TTS_PITCH_KEY] ?: 1.0f }
    val ttsVoiceFlow: Flow<String> = context.dataStore.data.map { it[TTS_VOICE_KEY] ?: "Puck" }

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
}

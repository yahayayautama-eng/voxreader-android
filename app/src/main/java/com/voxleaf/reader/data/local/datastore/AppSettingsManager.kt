package com.voxleaf.reader.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.voxleaf.reader.tts.SherpaTtsEngine
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
        val READER_THEME_KEY = stringPreferencesKey("reader_theme")
        val READER_FONT_FAMILY_KEY = stringPreferencesKey("reader_font_family")
        val READER_FONT_SIZE_KEY = androidx.datastore.preferences.core.intPreferencesKey("reader_font_size")
        val READER_LINE_SPACING_KEY = floatPreferencesKey("reader_line_spacing")
        val TTS_RATE_KEY = floatPreferencesKey("tts_rate")
        val TTS_VOICE_KEY = stringPreferencesKey("tts_voice")
        val TTS_ENGINE_KEY = stringPreferencesKey("tts_engine")
        val AUTO_PLAY_KEY = booleanPreferencesKey("auto_play_on_open")
        val HIGHLIGHT_KEY = booleanPreferencesKey("highlight_spoken_sentences")
        val HIDE_RECENTS_KEY = booleanPreferencesKey("hide_content_in_recents")
        val VOICE_FAVORITES_KEY = stringSetPreferencesKey("favorite_voice_ids")
        val VOICE_RECENTS_KEY = stringPreferencesKey("recent_voice_ids")
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { it[THEME_KEY] ?: "SYSTEM" }
    val readerThemeFlow: Flow<String> = context.dataStore.data.map {
        when (it[READER_THEME_KEY] ?: "NIGHT") {
            "DARK" -> "NIGHT"
            "IVORY" -> "LIGHT"
            else -> it[READER_THEME_KEY] ?: "NIGHT"
        }
    }
    val readerFontFamilyFlow: Flow<String> = context.dataStore.data.map { it[READER_FONT_FAMILY_KEY] ?: "SERIF" }
    val readerFontSizeFlow: Flow<Int> = context.dataStore.data.map { it[READER_FONT_SIZE_KEY] ?: 18 }
    val readerLineSpacingFlow: Flow<Float> = context.dataStore.data.map { it[READER_LINE_SPACING_KEY] ?: 1.75f }
    val ttsRateFlow: Flow<Float> = context.dataStore.data.map { it[TTS_RATE_KEY] ?: 1.0f }
    val ttsVoiceFlow: Flow<String> = context.dataStore.data.map { it[TTS_VOICE_KEY] ?: SherpaTtsEngine.DEFAULT_VOICE }
    /** Raw storage key ("offline"/"edge") rather than the `tts` package's [com.voxleaf.reader.tts.EngineId]
     *  enum, so this data-layer class doesn't depend on it — callers map with `EngineId.fromStorageKey`. */
    val ttsEngineFlow: Flow<String> = context.dataStore.data.map { it[TTS_ENGINE_KEY] ?: "offline" }
    val autoPlayFlow: Flow<Boolean> = context.dataStore.data.map { it[AUTO_PLAY_KEY] ?: false }
    val highlightSentencesFlow: Flow<Boolean> = context.dataStore.data.map { it[HIGHLIGHT_KEY] ?: true }
    val hideContentInRecentsFlow: Flow<Boolean> = context.dataStore.data.map { it[HIDE_RECENTS_KEY] ?: false }
    val favoriteVoiceIdsFlow: Flow<Set<String>> = context.dataStore.data.map { it[VOICE_FAVORITES_KEY] ?: emptySet() }
    val recentVoiceIdsFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        preferences[VOICE_RECENTS_KEY].orEmpty().split('\n').filter(String::isNotBlank).take(8)
    }

    suspend fun setAutoPlay(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_PLAY_KEY] = enabled }
    }

    suspend fun setHighlightSentences(enabled: Boolean) {
        context.dataStore.edit { it[HIGHLIGHT_KEY] = enabled }
    }

    suspend fun setHideContentInRecents(enabled: Boolean) {
        context.dataStore.edit { it[HIDE_RECENTS_KEY] = enabled }
    }

    suspend fun toggleFavoriteVoice(voiceId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[VOICE_FAVORITES_KEY].orEmpty()
            preferences[VOICE_FAVORITES_KEY] = if (voiceId in current) current - voiceId else current + voiceId
        }
    }

    suspend fun markVoiceRecent(voiceId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[VOICE_RECENTS_KEY].orEmpty().split('\n').filter(String::isNotBlank)
            preferences[VOICE_RECENTS_KEY] = (listOf(voiceId) + current.filterNot { it == voiceId }).take(8).joinToString("\n")
        }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[THEME_KEY] = theme }
    }

    suspend fun setReaderTheme(theme: String) {
        context.dataStore.edit { it[READER_THEME_KEY] = theme }
    }

    suspend fun setReaderFontFamily(family: String) {
        context.dataStore.edit { it[READER_FONT_FAMILY_KEY] = family }
    }

    suspend fun setReaderFontSize(fontSize: Int) {
        context.dataStore.edit { it[READER_FONT_SIZE_KEY] = fontSize }
    }

    suspend fun setReaderLineSpacing(lineSpacing: Float) {
        context.dataStore.edit { it[READER_LINE_SPACING_KEY] = lineSpacing }
    }

    suspend fun setTtsRate(rate: Float) {
        context.dataStore.edit { it[TTS_RATE_KEY] = rate }
    }
    
    
    suspend fun setTtsVoice(voice: String) {
        context.dataStore.edit { it[TTS_VOICE_KEY] = voice }
    }

    suspend fun setTtsEngine(storageKey: String) {
        context.dataStore.edit { it[TTS_ENGINE_KEY] = storageKey }
    }
}

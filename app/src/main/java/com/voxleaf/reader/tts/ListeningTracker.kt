package com.voxleaf.reader.tts

import com.voxleaf.reader.BuildConfig
import com.voxleaf.reader.billing.ProStore
import com.voxleaf.reader.domain.repository.BookRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accumulates listening time while the offline voice is actually speaking.
 *
 * Lives at the application scope on purpose: playback continues in the foreground service after the
 * reader's ViewModel is gone, and a ViewModel-side timer would drop exactly the long background
 * sessions the stats screen exists to show. [TtsManager] knows when audio is playing but not which
 * book it belongs to, so the reader reports the book and this joins the two.
 *
 * ponytail: fixed [TICK_SECONDS] ticks, so up to one tick of the final partial interval is lost.
 * Move to timestamp deltas if per-second accuracy ever matters.
 *
 * Also enforces the free tier's daily listening limit ([BuildConfig.DAILY_LISTEN_LIMIT_SECONDS],
 * lifted by [ProStore]):
 * every route into playback (reader, player bar, notification, headset) ends in [TtsManager]
 * speaking, so pausing here covers them all without a check at each call site.
 * ponytail: the total comes from the local stats table, so clearing app data resets it. Move it
 * behind the store's purchase check if that ever matters.
 */
@Singleton
class ListeningTracker @Inject constructor(
    private val ttsManager: TtsManager,
    private val bookRepository: BookRepository,
    private val proStore: ProStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var currentBookId: String? = null

    private val _limitReached = MutableStateFlow(false)

    /** True after playback was stopped by the daily limit, until [dismissLimit]. */
    val limitReached: StateFlow<Boolean> = _limitReached.asStateFlow()

    init {
        scope.launch {
            while (true) {
                delay(TICK_SECONDS * 1000L)
                val bookId = currentBookId ?: continue
                if (ttsManager.state.value.isSpeaking) {
                    bookRepository.recordListening(bookId, TICK_SECONDS)
                    enforceLimit()
                }
            }
        }
        if (BuildConfig.DAILY_LISTEN_LIMIT_SECONDS > 0) {
            scope.launch {
                ttsManager.state.map { it.isSpeaking || it.isPreparing }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { enforceLimit() }
            }
        }
    }

    fun setCurrentBook(bookId: String?) {
        currentBookId = bookId
    }

    fun dismissLimit() {
        _limitReached.value = false
    }

    private suspend fun enforceLimit() {
        val limit = BuildConfig.DAILY_LISTEN_LIMIT_SECONDS
        if (limit <= 0 || proStore.isPro.first()) return
        if (bookRepository.getListeningSecondsSince(LocalDate.now()) < limit) return
        withContext(Dispatchers.Main) { ttsManager.pause() }
        _limitReached.value = true
    }

    private companion object {
        // Short enough that quitting mid-tick loses little, long enough to keep DB writes rare.
        const val TICK_SECONDS = 15
    }
}

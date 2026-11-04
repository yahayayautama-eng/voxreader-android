package com.voxleaf.reader.tts

import com.voxleaf.reader.BuildConfig
import com.voxleaf.reader.domain.repository.BookRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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
 * Also enforces the free tier's weekly listening limit ([BuildConfig.WEEKLY_LISTEN_LIMIT_SECONDS]):
 * every route into playback (reader, player bar, notification, headset) ends in [TtsManager]
 * speaking, so pausing here covers them all without a check at each call site.
 * ponytail: the total comes from the local stats table, so clearing app data resets it. Move it
 * behind the store's purchase check if that ever matters.
 */
@Singleton
class ListeningTracker @Inject constructor(
    private val ttsManager: TtsManager,
    private val bookRepository: BookRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var currentBookId: String? = null

    private val _weeklyLimitReached = MutableStateFlow(false)

    /** True after playback was stopped by the weekly limit, until [dismissWeeklyLimit]. */
    val weeklyLimitReached: StateFlow<Boolean> = _weeklyLimitReached.asStateFlow()

    init {
        scope.launch {
            while (true) {
                delay(TICK_SECONDS * 1000L)
                val bookId = currentBookId ?: continue
                if (ttsManager.state.value.isSpeaking) {
                    bookRepository.recordListening(bookId, TICK_SECONDS)
                    enforceWeeklyLimit()
                }
            }
        }
        if (BuildConfig.WEEKLY_LISTEN_LIMIT_SECONDS > 0) {
            scope.launch {
                ttsManager.state.map { it.isSpeaking || it.isPreparing }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { enforceWeeklyLimit() }
            }
        }
    }

    fun setCurrentBook(bookId: String?) {
        currentBookId = bookId
    }

    fun dismissWeeklyLimit() {
        _weeklyLimitReached.value = false
    }

    private suspend fun enforceWeeklyLimit() {
        val limit = BuildConfig.WEEKLY_LISTEN_LIMIT_SECONDS
        if (limit <= 0) return
        if (bookRepository.getListeningSecondsSince(weekStart(LocalDate.now())) < limit) return
        withContext(Dispatchers.Main) { ttsManager.pause() }
        _weeklyLimitReached.value = true
    }

    companion object {
        // Short enough that quitting mid-tick loses little, long enough to keep DB writes rare.
        private const val TICK_SECONDS = 15

        /** The free allowance resets every Monday, local time. */
        internal fun weekStart(today: LocalDate): LocalDate =
            today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}

package com.voxleaf.reader.tts

import com.voxleaf.reader.domain.repository.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 */
@Singleton
class ListeningTracker @Inject constructor(
    private val ttsManager: TtsManager,
    private val bookRepository: BookRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var currentBookId: String? = null

    init {
        scope.launch {
            while (true) {
                delay(TICK_SECONDS * 1000L)
                val bookId = currentBookId ?: continue
                if (ttsManager.state.value.isSpeaking) {
                    bookRepository.recordListening(bookId, TICK_SECONDS)
                }
            }
        }
    }

    fun setCurrentBook(bookId: String?) {
        currentBookId = bookId
    }

    private companion object {
        // Short enough that quitting mid-tick loses little, long enough to keep DB writes rare.
        const val TICK_SECONDS = 15
    }
}

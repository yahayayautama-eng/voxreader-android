package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import com.example.MainActivity
import com.example.R
import com.example.data.local.dao.AudiobookDao
import com.example.data.local.dao.BookDao
import com.example.domain.repository.BookRepository
import com.example.tts.GeneratedChapterAudio
import com.example.tts.NowPlaying
import com.example.tts.TtsManager
import com.example.tts.TtsState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Foreground service that keeps the bundled offline TTS voice audible while the app is
 * backgrounded or the screen is locked. Hosts a MediaStyle notification and a MediaSession
 * for lockscreen/Bluetooth transport controls. Owns no playback logic itself — everything
 * routes through the singleton [TtsManager].
 */
@AndroidEntryPoint
class PlaybackService : Service() {

    @Inject
    lateinit var ttsManager: TtsManager

    @Inject
    lateinit var bookDao: BookDao

    @Inject
    lateinit var audiobookDao: AudiobookDao

    @Inject
    lateinit var bookRepository: BookRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var restoring = false
    private lateinit var mediaSession: MediaSession
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW)
        )
        mediaSession = MediaSession(this, "VoxLeafPlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { ttsManager.resume() }
                override fun onPause() { ttsManager.pause() }
                override fun onStop() { ttsManager.stop() }
                override fun onSkipToNext() { ttsManager.skip(+1) }
                override fun onSkipToPrevious() { ttsManager.skip(-1) }
            })
            isActive = true
        }
        collectJob = ttsManager.state
            .onEach { state ->
                updateMediaSession(state)
                if (!restoring && !state.isSpeaking && !state.isPaused && !state.isPreparing) {
                    stopSelf()
                } else {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
            .launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(ttsManager.state.value))
        if (intent == null) restoreAfterProcessRestart()
        when (intent?.action) {
            ACTION_TOGGLE -> ttsManager.togglePlayback()
            ACTION_NEXT -> ttsManager.skip(+1)
            ACTION_PREV -> ttsManager.skip(-1)
            ACTION_STOP -> ttsManager.stop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        mediaSession.release()
        super.onDestroy()
    }

    private fun updateMediaSession(state: TtsState) {
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_STOP
                )
                .setState(
                    when {
                        state.isSpeaking -> PlaybackState.STATE_PLAYING
                        state.isPaused -> PlaybackState.STATE_PAUSED
                        state.isPreparing -> PlaybackState.STATE_BUFFERING
                        else -> PlaybackState.STATE_STOPPED
                    },
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1f
                )
                .build()
        )
    }

    private fun action(icon: Int, title: String, actionName: String) = Notification.Action.Builder(
        Icon.createWithResource(this, icon),
        title,
        PendingIntent.getService(
            this,
            actionName.hashCode(),
            Intent(this, PlaybackService::class.java).setAction(actionName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    ).build()

    private fun buildNotification(state: TtsState): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Vox Reader")
            .setContentText(
                if (state.totalSentences > 0) "Sentence ${state.currentSentenceIndex + 1} of ${state.totalSentences}"
                else "Preparing offline audio…"
            )
            .setContentIntent(contentIntent)
            .setOngoing(state.isSpeaking || state.isPreparing)
            .addAction(action(android.R.drawable.ic_media_previous, "Previous", ACTION_PREV))
            .addAction(
                if (state.isSpeaking || state.isPreparing) action(android.R.drawable.ic_media_pause, "Pause", ACTION_TOGGLE)
                else action(android.R.drawable.ic_media_play, "Play", ACTION_TOGGLE)
            )
            .addAction(action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT))
            .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", ACTION_STOP))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun restoreAfterProcessRestart() {
        if (restoring || ttsManager.state.value.isSpeaking || ttsManager.state.value.isPaused || ttsManager.state.value.isPreparing) return
        restoring = true
        scope.launch {
            val progress = bookDao.getLatestAudioProgress()
            val book = progress?.let { bookRepository.getBookById(it.bookId) }
            val generation = progress?.let { audiobookDao.getGeneration(it.bookId) }
            val ready = progress?.let { audiobookDao.getChapterAudio(it.bookId) }
                ?.filter { it.status == "READY" && it.filePath?.let { path -> File(path) }.isValidAudioFile() }
                ?.associateBy { it.chapterIndex }
            val cueStarts = if (progress != null && ready != null) {
                ready.keys.associateWith { index -> audiobookDao.getCues(progress.bookId, index).map { it.startMs } }
            } else emptyMap()
            val chapters = if (book != null && ready != null) {
                book.chapters.mapIndexedNotNull { index, chapter ->
                    ready[index]?.filePath?.let { path ->
                        GeneratedChapterAudio(
                            nowPlaying = NowPlaying(book.id, book.title, index, chapter.title),
                            filePath = path,
                            cueCount = ready[index]?.segmentCount ?: 0,
                            cueStartsMs = cueStarts[index].orEmpty()
                        )
                    }
                }
            } else emptyList()
            val complete = book != null && chapters.size == book.chapters.count { it.content.isNotBlank() }
            if (progress != null && generation?.status == "READY" && complete) {
                ttsManager.playGeneratedChapters(chapters, progress.currentChapterIndex, progress.audioPositionMs)
            }
            restoring = false
            if (ttsManager.state.value == TtsState()) stopSelf()
        }
    }

    private fun File?.isValidAudioFile(): Boolean = this?.isFile == true && length() > 44

    private companion object {
        const val CHANNEL_ID = "voxleaf_playback"
        const val NOTIFICATION_ID = 1
        const val ACTION_TOGGLE = "com.example.playback.action.TOGGLE"
        const val ACTION_NEXT = "com.example.playback.action.NEXT"
        const val ACTION_PREV = "com.example.playback.action.PREV"
        const val ACTION_STOP = "com.example.playback.action.STOP"
    }
}

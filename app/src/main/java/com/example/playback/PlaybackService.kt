package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import com.example.MainActivity
import com.example.R
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




    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private lateinit var mediaSession: MediaSession
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    // Decoding a cover file is a disk read; the notification rebuilds on every TtsState tick, so
    // caching by path avoids re-decoding the same bitmap on every sentence advance.
    private var lastCoverPath: String? = null
    private var lastCoverBitmap: android.graphics.Bitmap? = null

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
                if (!state.isSpeaking && !state.isPaused && !state.isPreparing) {
                    stopSelf()
                } else {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
            .launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(ttsManager.state.value))
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
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, state.nowPlaying?.chapterTitle ?: "Vox Reader")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, state.nowPlaying?.bookTitle ?: "")
                .apply {
                    coverBitmap(state.nowPlaying?.coverImagePath)?.let {
                        putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
                    }
                }
                .build()
        )
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

    private fun coverBitmap(path: String?): android.graphics.Bitmap? {
        if (path == null) return null
        if (path != lastCoverPath) {
            lastCoverBitmap = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
            lastCoverPath = path
        }
        return lastCoverBitmap
    }

    private fun buildNotification(state: TtsState): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .apply { coverBitmap(state.nowPlaying?.coverImagePath)?.let { setLargeIcon(it) } }
            .setContentTitle(state.nowPlaying?.bookTitle ?: "Vox Reader")
            .setContentText(
                when {
                    state.nowPlaying != null && state.totalSentences > 0 ->
                        "${state.nowPlaying.chapterTitle} · sentence ${state.currentSentenceIndex + 1} of ${state.totalSentences}"
                    state.nowPlaying != null -> state.nowPlaying.chapterTitle
                    else -> "Preparing offline audio…"
                }
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



    private companion object {
        const val CHANNEL_ID = "voxleaf_playback"
        const val NOTIFICATION_ID = 1
        const val ACTION_TOGGLE = "com.example.playback.action.TOGGLE"
        const val ACTION_NEXT = "com.example.playback.action.NEXT"
        const val ACTION_PREV = "com.example.playback.action.PREV"
        const val ACTION_STOP = "com.example.playback.action.STOP"
    }
}

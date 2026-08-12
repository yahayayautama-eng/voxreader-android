package com.example.audiobook

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.R

object GenerationNotification {
    private const val CHANNEL_ID = "audiobook_generation"

    fun create(context: Context): Notification {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Audiobook generation", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Creating audiobook")
            .setContentText("Neural audio is being generated")
            .setOngoing(true)
            .build()
    }
}

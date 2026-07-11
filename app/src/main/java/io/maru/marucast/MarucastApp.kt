package io.maru.marucast

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MarucastApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Marucast Stream",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active Marucast streaming status"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "marucast_stream_channel"
    }
}

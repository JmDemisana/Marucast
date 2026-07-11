package io.maru.marucast.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.util.Log
import java.io.ByteArrayOutputStream

object MediaSessionState {
    var title: String? = null
    var artist: String? = null
    var durationMs: Long = 0L
    var positionMs: Long = 0L
    var lastPositionUpdateTimeMs: Long = 0L
    var playbackSpeed: Float = 1.0f
    var isPlaying: Boolean = false
    var appLabel: String? = null
    var artworkBitmap: Bitmap? = null
    var activeController: MediaController? = null
    
    var onMetadataChanged: (() -> Unit)? = null

    fun triggerUpdate() {
        onMetadataChanged?.invoke()
    }

    fun getEstimatedPosition(): Long {
        if (!isPlaying || lastPositionUpdateTimeMs <= 0) {
            return positionMs
        }
        val elapsed = android.os.SystemClock.elapsedRealtime() - lastPositionUpdateTimeMs
        val extrapolated = positionMs + (elapsed * playbackSpeed).toLong()
        return if (durationMs > 0) extrapolated.coerceIn(0, durationMs) else extrapolated
    }
}

class MarucastNotificationListener : NotificationListenerService() {
    private val TAG = "MarucastNotifListener"
    private lateinit var mediaSessionManager: MediaSessionManager
    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMetadata(MediaSessionState.activeController)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlaybackState(state)
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        updateActiveSessions()
        
        mediaSessionManager.addOnActiveSessionsChangedListener(
            { controllers ->
                Log.d(TAG, "Active sessions changed, count: ${controllers?.size ?: 0}")
                updateActiveSessions()
            },
            ComponentName(this, MarucastNotificationListener::class.java)
        )
    }

    private fun updateActiveSessions() {
        try {
            val controllers = mediaSessionManager.getActiveSessions(
                ComponentName(this, MarucastNotificationListener::class.java)
            )
            val firstPlaying = controllers.find { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            val controller = firstPlaying ?: controllers.firstOrNull()
            
            if (controller != MediaSessionState.activeController) {
                MediaSessionState.activeController?.unregisterCallback(controllerCallback)
                MediaSessionState.activeController = controller
                controller?.registerCallback(controllerCallback)
                updateMetadata(controller)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Notification Access might have been revoked.", e)
        }
    }

    private fun updateMetadata(controller: MediaController?) {
        if (controller == null) {
            MediaSessionState.title = null
            MediaSessionState.artist = null
            MediaSessionState.durationMs = 0L
            MediaSessionState.positionMs = 0L
            MediaSessionState.isPlaying = false
            MediaSessionState.appLabel = null
            MediaSessionState.artworkBitmap = null
            MediaSessionState.triggerUpdate()
            return
        }

        val metadata = controller.metadata
        val playbackState = controller.playbackState

        MediaSessionState.title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        MediaSessionState.artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        MediaSessionState.durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        MediaSessionState.appLabel = getAppNameFromPackage(controller.packageName)

        // Try to fetch artwork bitmap
        var art: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        if (art == null) {
            art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        }
        MediaSessionState.artworkBitmap = art

        updatePlaybackState(playbackState)
    }

    private fun updatePlaybackState(state: PlaybackState?) {
        if (state == null) {
            MediaSessionState.isPlaying = false
            MediaSessionState.positionMs = 0L
            MediaSessionState.lastPositionUpdateTimeMs = 0L
            MediaSessionState.playbackSpeed = 0f
        } else {
            MediaSessionState.isPlaying = state.state == PlaybackState.STATE_PLAYING
            MediaSessionState.positionMs = state.position
            MediaSessionState.lastPositionUpdateTimeMs = state.lastPositionUpdateTime
            MediaSessionState.playbackSpeed = state.playbackSpeed
        }
        MediaSessionState.triggerUpdate()
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.split(".").lastOrNull() ?: packageName
        }
    }
}

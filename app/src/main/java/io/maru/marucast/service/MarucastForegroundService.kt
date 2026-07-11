package io.maru.marucast.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import io.maru.marucast.MainActivity
import io.maru.marucast.MarucastApp
import io.maru.marucast.media.MediaSessionState
import io.maru.marucast.network.MarucastApiClient

class MarucastForegroundService : Service() {
    private val TAG = "MarucastService"
    private var relayServer: MarucastRelayServer? = null
    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    private var isStreaming = false
    private var audioThread: Thread? = null
    private var projectionIntentData: Intent? = null
    
    private var token: String? = null
    private var ipAddress: String? = null
    private var lastCommandNonce = 0
    private val handler = Handler(Looper.getMainLooper())
    
    private val presenceRunnable = object : Runnable {
        override fun run() {
            sendPresenceHeartbeat()
            handler.postDelayed(this, 3000)
        }
    }

    private val commandPollRunnable = object : Runnable {
        override fun run() {
            pollCommands()
            handler.postDelayed(this, 1200)
        }
    }

    companion object {
        const val ACTION_START = "io.maru.marucast.action.START"
        const val ACTION_STOP = "io.maru.marucast.action.STOP"
        const val EXTRA_TOKEN = "extra_token"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"
        
        var isRunning = false
            private set
            
        var currentToken: String? = null
            private set

        var isMicMode = false
        var isKaraokeMode = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START) {
            val token = intent.getStringExtra(EXTRA_TOKEN)
            val projData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
            }
            if (token != null) {
                startStreamingService(token, projData)
            } else {
                stopSelf()
            }
        } else if (action == ACTION_STOP) {
            stopStreamingService()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startStreamingService(token: String, projectionData: Intent?) {
        this.token = token
        currentToken = token
        this.projectionIntentData = projectionData
        
        ipAddress = getLocalIpAddress(this) ?: "127.0.0.1"
        val streamUrl = "http://$ipAddress:48543/stream"
        
        Log.i(TAG, "Starting stream relay on: $streamUrl")
        
        // 1. Start HTTP Server
        relayServer = MarucastRelayServer(48543).apply {
            onControlCommand = { command ->
                handleRemoteCommand(command)
            }
            start()
        }
        
        // 2. Promote to Foreground (MUST happen before MediaProjection creation!)
        val notification = createNotification("Starting Marucast stream...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (projectionData != null) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(101, notification, serviceType)
        } else {
            startForeground(101, notification)
        }
        
        // 3. Start Audio Capture
        startAudioCapture()

        // 4. Report details to API Complete endpoint
        completeHandoff(streamUrl)

        // 5. Start Presence Heartbeat & Command Poller
        handler.post(presenceRunnable)
        handler.post(commandPollRunnable)

        // 6. Listen for local song metadata changes to push to receiver
        MediaSessionState.onMetadataChanged = {
            updateNotificationAndComplete(streamUrl)
        }
    }

    private fun startAudioCapture() {
        isStreaming = true
        audioThread = Thread {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

            try {
                if (!isMicMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && projectionIntentData != null) {
                    Log.i(TAG, "Configuring system audio capture via MediaProjection")
                    val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val proj = mediaProjectionManager.getMediaProjection(android.app.Activity.RESULT_OK, projectionIntentData!!)
                    if (proj != null) {
                        mediaProjection = proj
                        val config = AudioPlaybackCaptureConfiguration.Builder(proj)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .build()
                        val audioFormatObj = AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                        audioRecord = AudioRecord.Builder()
                            .setAudioFormat(audioFormatObj)
                            .setBufferSizeInBytes(bufferSize)
                            .setAudioPlaybackCaptureConfig(config)
                            .build()
                    }
                }

                if (audioRecord == null) {
                    Log.i(TAG, "Configuring microphone capture")
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )
                }

                if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord?.startRecording()
                    val buffer = ByteArray(bufferSize)
                    while (isStreaming) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                        if (read > 0) {
                            if (isKaraokeMode) {
                                applyKaraokeFilter(buffer, read)
                            }
                            relayServer?.broadcastAudio(buffer, read)
                        }
                    }
                } else {
                    Log.e(TAG, "AudioRecord failed to initialize")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied for audio recording: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Exception in audio recording thread: ${e.message}")
            }
        }.apply { start() }
    }

    private fun applyKaraokeFilter(buffer: ByteArray, bytesRead: Int) {
        for (i in 0 until (bytesRead - 3) step 4) {
            val left = ((buffer[i + 1].toInt() and 0xFF) shl 8) or (buffer[i].toInt() and 0xFF)
            val right = ((buffer[i + 3].toInt() and 0xFF) shl 8) or (buffer[i + 2].toInt() and 0xFF)
            
            val leftSigned = if (left > 32767) left - 65536 else left
            val rightSigned = if (right > 32767) right - 65536 else right
            
            val diff = (leftSigned - rightSigned) / 2
            val result = diff.coerceIn(-32768, 32767)
            
            buffer[i] = (result and 0xFF).toByte()
            buffer[i + 1] = ((result shr 8) and 0xFF).toByte()
            buffer[i + 2] = (result and 0xFF).toByte()
            buffer[i + 3] = ((result shr 8) and 0xFF).toByte()
        }
    }

    private fun completeHandoff(streamUrl: String) {
        val currentToken = this.token ?: return
        val deviceName = Build.MODEL ?: "Android Device"
        val payload = MarucastApiClient.CompleteRequest(
            token = currentToken,
            relayUrl = streamUrl,
            deviceName = deviceName,
            serviceName = "marucast-android",
            mediaAccessEnabled = true,
            mediaAppLabel = MediaSessionState.appLabel ?: "Android Player",
            mediaArtist = MediaSessionState.artist ?: "Unknown Artist",
            mediaTitle = MediaSessionState.title ?: "Unknown Track",
            relayMode = "lan",
            sampleRate = 44100,
            channelCount = 2,
            vocalProcessingKind = if (isKaraokeMode) "karaoke" else "none",
            vocalStemModelReady = false
        )

        MarucastApiClient.completeReceiver(payload, object : MarucastApiClient.Callback<Boolean> {
            override fun onSuccess(result: Boolean) {
                Log.d(TAG, "Complete handoff succeeded")
            }

            override fun onError(error: String) {
                Log.e(TAG, "Complete handoff failed: $error")
            }
        })
    }

    private fun sendPresenceHeartbeat() {
        val currentToken = this.token ?: return
        val payload = MarucastApiClient.PresenceRequest(
            token = currentToken,
            advancing = MediaSessionState.isPlaying,
            artist = MediaSessionState.artist,
            durationMs = MediaSessionState.durationMs,
            playbackSpeed = if (MediaSessionState.isPlaying) 1.0 else 0.0,
            positionCapturedAtMs = System.currentTimeMillis(),
            positionMs = MediaSessionState.positionMs,
            receiverId = "marucast-android-sender",
            receiverLabel = Build.MODEL ?: "Android Sender",
            title = MediaSessionState.title,
            trackKey = "${MediaSessionState.title}-${MediaSessionState.artist}"
        )

        MarucastApiClient.sendPresence(payload, object : MarucastApiClient.Callback<Boolean> {
            override fun onSuccess(result: Boolean) {}
            override fun onError(error: String) {
                Log.e(TAG, "Presence report failed: $error")
            }
        })
    }

    private fun pollCommands() {
        val currentToken = this.token ?: return
        MarucastApiClient.pollCommand(currentToken, lastCommandNonce, object : MarucastApiClient.Callback<MarucastApiClient.CommandResponse> {
            override fun onSuccess(result: MarucastApiClient.CommandResponse) {
                if (result.success) {
                    lastCommandNonce = result.commandNonce
                    if (result.command != null) {
                        handleRemoteCommand(result.command)
                    }
                }
            }

            override fun onError(error: String) {
                Log.e(TAG, "Command polling error: $error")
            }
        })
    }

    private fun handleRemoteCommand(command: String) {
        Log.i(TAG, "Received remote command: $command")
        if (command == "disconnect" || command == "stop") {
            stopStreamingService()
            stopSelf()
            return
        }
        val controller = MediaSessionState.activeController ?: return
        try {
            when (command) {
                "play" -> controller.transportControls.play()
                "pause" -> controller.transportControls.pause()
                "next" -> controller.transportControls.skipToNext()
                "previous" -> controller.transportControls.skipToPrevious()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing remote command: ${e.message}")
        }
    }

    private fun updateNotificationAndComplete(streamUrl: String) {
        val title = MediaSessionState.title ?: "Unknown Track"
        val artist = MediaSessionState.artist ?: "Unknown Artist"
        val app = MediaSessionState.appLabel ?: "Android Player"
        
        // Update notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(101, createNotification("Casting: $title - $artist ($app)"))

        // Resend complete data to sync metadata to browser
        completeHandoff(streamUrl)
    }

    private fun createNotification(content: String): Notification {
        val stopIntent = Intent(this, MarucastForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 
            0, 
            stopIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            mainIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MarucastApp.CHANNEL_ID)
            .setContentTitle("Marucast Audio Sender")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun stopStreamingService() {
        Log.i(TAG, "Stopping streaming service")
        isStreaming = false
        handler.removeCallbacks(presenceRunnable)
        handler.removeCallbacks(commandPollRunnable)
        
        relayServer?.stop()
        relayServer = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {}
        mediaProjection = null
        projectionIntentData = null
        
        MediaSessionState.onMetadataChanged = null
        currentToken = null
        isRunning = false
    }

    override fun onDestroy() {
        stopStreamingService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getLocalIpAddress(context: Context): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        val ip = address.hostAddress
                        // Check if it's on a typical local subnet
                        if (networkInterface.name.contains("wlan") || ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network interfaces: ${e.message}")
        }
        return null
    }
}

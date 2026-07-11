package io.maru.marucast.service

import android.graphics.Bitmap
import android.util.Log
import io.maru.marucast.media.MediaSessionState
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import com.google.gson.Gson
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class MarucastRelayServer(private val port: Int = 48543) {
    private val TAG = "MarucastRelayServer"
    private val gson = Gson()
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val executor = Executors.newCachedThreadPool()
    
    var onControlCommand: ((String) -> Unit)? = null
    
    // List of currently connected streaming clients
    private val streamingClients = CopyOnWriteArrayList<OutputStream>()

    fun start() {
        if (isRunning) return
        isRunning = true
        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                Log.i(TAG, "Server started on port $port")
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    executor.execute { handleConnection(socket) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server exception: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverSocket = null
        streamingClients.clear()
    }

    /**
     * Broadcasts raw PCM audio bytes to all active streaming clients
     */
    fun broadcastAudio(pcmData: ByteArray, size: Int) {
        if (streamingClients.isEmpty()) return
        for (client in streamingClients) {
            try {
                client.write(pcmData, 0, size)
                client.flush()
            } catch (e: Exception) {
                Log.d(TAG, "Removing dead client connection")
                streamingClients.remove(client)
            }
        }
    }

    private fun handleConnection(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val output = BufferedOutputStream(socket.getOutputStream())
            
            // Read basic HTTP request headers
            val requestHeader = readRequestHeader(input)
            if (requestHeader == null) {
                socket.close()
                return
            }

            val method = extractMethod(requestHeader)
            val path = extractPath(requestHeader)
            Log.d(TAG, "Request: $method $path")

            if (method == "OPTIONS") {
                handleOptionsRequest(output)
                socket.close()
                return
            }

            if (path == "/stream") {
                handleStreamRequest(output)
            } else if (path == "/artwork.jpg") {
                handleArtworkRequest(output)
            } else if (path == "/status") {
                handleStatusRequest(socket, output)
                socket.close()
            } else if (path == "/control") {
                handleControlRequest(requestHeader, output)
                socket.close()
            } else {
                sendNotFound(output)
                socket.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client connection: ${e.message}")
            try { socket.close() } catch (ex: Exception) {}
        }
    }

    private fun handleOptionsRequest(out: OutputStream) {
        val response = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Connection: close\r\n\r\n"
        try {
            out.write(response.toByteArray())
            out.flush()
        } catch (e: Exception) {}
    }

    private fun handleStatusRequest(socket: Socket, out: OutputStream) {
        val ip = socket.localAddress?.hostAddress ?: "127.0.0.1"
        val statusMap = mapOf(
            "activeLoopbackMicClients" to 0,
            "activeNetworkMicClients" to streamingClients.size,
            "artworkUrl" to "http://$ip:48543/artwork.jpg",
            "captureActive" to isRunning,
            "channelCount" to 2,
            "deviceName" to android.os.Build.MODEL,
            "karaokeEnabled" to false,
            "karaokeDelayMs" to 0,
            "lastError" to null,
            "liveStreamUrl" to "http://$ip:48543/stream",
            "mediaAccessEnabled" to true,
            "mediaAppLabel" to (MediaSessionState.appLabel ?: "Android Player"),
            "mediaArtist" to (MediaSessionState.artist ?: "Unknown Artist"),
            "mediaDurationMs" to MediaSessionState.durationMs,
            "mediaPlaying" to MediaSessionState.isPlaying,
            "mediaPlaybackSpeed" to (if (MediaSessionState.isPlaying) 1.0 else 0.0),
            "mediaPositionCapturedAtMs" to System.currentTimeMillis(),
            "mediaPositionMs" to MediaSessionState.getEstimatedPosition(),
            "mediaTitle" to (MediaSessionState.title ?: "Unknown Track"),
            "serviceName" to "marucast-android",
            "sampleRate" to 44100,
            "vocalProcessingKind" to "none",
            "vocalStemModelReady" to false,
            "delayManagementMode" to MarucastForegroundService.delayManagementMode
        )
        val json = gson.toJson(statusMap)
        val responseBytes = json.toByteArray(Charsets.UTF_8)
        
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${responseBytes.size}\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n"
                
        try {
            out.write(response.toByteArray())
            out.write(responseBytes)
            out.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send status: ${e.message}")
        }
    }

    private fun handleControlRequest(requestHeader: String, out: OutputStream) {
        val query = requestHeader.substringAfter("?").substringBefore(" ")
        val command = query.split("&")
            .firstOrNull { it.startsWith("command=") }
            ?.substringAfter("command=")
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            
        if (command != null) {
            onControlCommand?.invoke(command)
            val json = "{\"success\":true}"
            val responseBytes = json.toByteArray(Charsets.UTF_8)
            val response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${responseBytes.size}\r\n" +
                    "Connection: close\r\n" +
                    "Access-Control-Allow-Origin: *\r\n\r\n"
            out.write(response.toByteArray())
            out.write(responseBytes)
            out.flush()
        } else {
            val json = "{\"success\":false,\"message\":\"Command missing\"}"
            val responseBytes = json.toByteArray(Charsets.UTF_8)
            val response = "HTTP/1.1 400 Bad Request\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${responseBytes.size}\r\n" +
                    "Connection: close\r\n" +
                    "Access-Control-Allow-Origin: *\r\n\r\n"
            out.write(response.toByteArray())
            out.write(responseBytes)
            out.flush()
        }
    }

    private fun handleStreamRequest(out: OutputStream) {
        val sampleRate = 44100
        val channels = 2
        val bitsPerSample = 16

        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: audio/x-wav\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                "Pragma: no-cache\r\n" +
                "Expires: 0\r\n\r\n"
        
        try {
            out.write(response.toByteArray())
            // Write WAV Header
            val header = getWavHeader(sampleRate, channels, bitsPerSample)
            out.write(header)
            out.flush()
            
            // Add to active broadcast streams
            streamingClients.add(out)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio stream to client: ${e.message}")
        }
    }

    private fun handleArtworkRequest(out: OutputStream) {
        try {
            val bitmap = MediaSessionState.artworkBitmap
            if (bitmap != null) {
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                val bytes = baos.toByteArray()
                
                val response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: image/jpeg\r\n" +
                        "Content-Length: ${bytes.size}\r\n" +
                        "Connection: close\r\n" +
                        "Access-Control-Allow-Origin: *\r\n\r\n"
                out.write(response.toByteArray())
                out.write(bytes)
            } else {
                sendNotFound(out)
            }
            out.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Artwork streaming failed: ${e.message}")
        } finally {
            try { out.close() } catch (e: Exception) {}
        }
    }

    private fun sendNotFound(out: OutputStream) {
        val response = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Length: 9\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n" +
                "Not Found"
        try {
            out.write(response.toByteArray())
            out.flush()
        } catch (e: Exception) {}
    }

    private fun extractMethod(request: String): String {
        val firstLine = request.substringBefore("\r\n")
        val parts = firstLine.split(" ")
        if (parts.isNotEmpty()) {
            return parts[0].uppercase()
        }
        return "GET"
    }

    private fun readRequestHeader(input: InputStream): String? {
        val builder = StringBuilder()
        val buffer = ByteArray(1024)
        var read: Int
        try {
            // Simple HTTP header parser: read until double CRLF
            while (true) {
                read = input.read(buffer)
                if (read == -1) break
                builder.append(String(buffer, 0, read, Charsets.US_ASCII))
                if (builder.contains("\r\n\r\n") || builder.length > 8192) {
                    break
                }
            }
        } catch (e: Exception) {
            return null
        }
        return builder.toString()
    }

    private fun extractPath(request: String): String {
        val firstLine = request.substringBefore("\r\n")
        val parts = firstLine.split(" ")
        if (parts.size >= 2) {
            return parts[1].substringBefore("?")
        }
        return "/"
    }

    private fun getWavHeader(sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = 0xFF.toByte() // infinite length placeholder
        header[5] = 0xFF.toByte()
        header[6] = 0xFF.toByte()
        header[7] = 0x7F.toByte()
        header[8] = 'W'.code.toByte() // WAVE
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // fmt chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // Format: PCM linear
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        
        val byteRate = sampleRate * channels * bitsPerSample / 8
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        
        val blockAlign = channels * bitsPerSample / 8
        header[32] = blockAlign.toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte() // data chunk
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = 0xFF.toByte() // infinite length placeholder
        header[41] = 0xFF.toByte()
        header[42] = 0xFF.toByte()
        header[43] = 0x7F.toByte()
        return header
    }
}

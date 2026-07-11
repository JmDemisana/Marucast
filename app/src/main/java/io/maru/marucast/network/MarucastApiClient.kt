package io.maru.marucast.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object MarucastApiClient {
    private const val TAG = "MarucastApiClient"
    private const val BASE_URL = "https://maruchansquigle.vercel.app/api/auth"
    private val client = OkHttpClient()
    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    interface Callback<T> {
        fun onSuccess(result: T)
        fun onError(error: String)
    }

    data class LookupPinRequest(val pin: String)
    data class LookupPinResponse(val success: Boolean, val token: String?, val error: String?)

    fun lookupPin(pin: String, callback: Callback<String>) {
        val requestBody = gson.toJson(LookupPinRequest(pin)).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL/marucast/receiver-lookup-pin")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (!response.isSuccessful || bodyStr == null) {
                    callback.onError("Server returned code ${response.code}")
                    return
                }
                try {
                    val resp = gson.fromJson(bodyStr, LookupPinResponse::class.java)
                    if (resp.success && resp.token != null) {
                        callback.onSuccess(resp.token)
                    } else {
                        callback.onError(resp.error ?: "Failed to pair")
                    }
                } catch (e: Exception) {
                    callback.onError("Parsing error: ${e.message}")
                }
            }
        })
    }

    data class CompleteRequest(
        val token: String,
        val relayUrl: String?,
        val deviceName: String?,
        val serviceName: String?,
        val mediaAccessEnabled: Boolean,
        val mediaAppLabel: String?,
        val mediaArtist: String?,
        val mediaTitle: String?,
        val relayMode: String?,
        val sampleRate: Int?,
        val channelCount: Int?,
        val vocalProcessingKind: String?,
        val vocalStemModelReady: Boolean
    )

    data class CompleteResponse(val success: Boolean, val error: String?)

    fun completeReceiver(payload: CompleteRequest, callback: Callback<Boolean>) {
        val requestBody = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL/marucast/receiver-complete")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (!response.isSuccessful || bodyStr == null) {
                    callback.onError("Server returned code ${response.code}")
                    return
                }
                try {
                    val resp = gson.fromJson(bodyStr, CompleteResponse::class.java)
                    if (resp.success) {
                        callback.onSuccess(true)
                    } else {
                        callback.onError(resp.error ?: "Failed to report registration")
                    }
                } catch (e: Exception) {
                    callback.onError("Parsing error: ${e.message}")
                }
            }
        })
    }

    data class StatusResponse(
        val status: String,
        val deviceName: String?,
        val mediaTitle: String?,
        val mediaArtist: String?,
        val expiresAt: String?,
        val error: String?
    )

    fun checkStatus(token: String, callback: Callback<StatusResponse>) {
        val request = Request.Builder()
            .url("$BASE_URL/marucast/receiver-status?token=${HttpUrl.Builder().percentDecode(token, 0, token.length, true)}")
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (!response.isSuccessful || bodyStr == null) {
                    callback.onError("Server returned code ${response.code}")
                    return
                }
                try {
                    val resp = gson.fromJson(bodyStr, StatusResponse::class.java)
                    callback.onSuccess(resp)
                } catch (e: Exception) {
                    callback.onError("Parsing error: ${e.message}")
                }
            }
        })
    }

    data class CommandRequest(val token: String, val lastNonce: Int)
    data class CommandResponse(val command: String?, val commandNonce: Int, val success: Boolean, val error: String?)

    fun pollCommand(token: String, lastNonce: Int, callback: Callback<CommandResponse>) {
        val requestBody = gson.toJson(CommandRequest(token, lastNonce)).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL/marucast/receiver-command")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (!response.isSuccessful || bodyStr == null) {
                    callback.onError("Server returned code ${response.code}")
                    return
                }
                try {
                    val resp = gson.fromJson(bodyStr, CommandResponse::class.java)
                    callback.onSuccess(resp)
                } catch (e: Exception) {
                    callback.onError("Parsing error: ${e.message}")
                }
            }
        })
    }

    data class PresenceRequest(
        val token: String,
        val advancing: Boolean,
        val artist: String?,
        val durationMs: Long?,
        val playbackSpeed: Double?,
        val positionCapturedAtMs: Long?,
        val positionMs: Long?,
        val receiverId: String,
        val receiverLabel: String,
        val title: String?,
        val trackKey: String?
    )

    fun sendPresence(payload: PresenceRequest, callback: Callback<Boolean>) {
        val requestBody = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL/marucast/receiver-presence")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (!response.isSuccessful) {
                    callback.onError("Server returned code ${response.code}")
                    return
                }
                callback.onSuccess(true)
            }
        })
    }
}

package io.maru.marucast

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import io.maru.marucast.network.MarucastApiClient
import io.maru.marucast.service.MarucastForegroundService
import io.maru.marucast.ui.DeepBackground
import io.maru.marucast.ui.MarucastAppContent

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!recordAudioGranted) {
            Toast.makeText(this, "Audio recording permission is required to broadcast microphone audio.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DeepBackground
            ) {
                MarucastAppContent(
                    onStartStream = { pin, onResult ->
                        lookupPinAndStartStream(pin, onResult)
                    },
                    onStopStream = {
                        stopStreamingService()
                    }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val needsRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needsRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(needsRequest.toTypedArray())
        }
    }

    private fun lookupPinAndStartStream(pin: String, onResult: (Boolean, String?) -> Unit) {
        MarucastApiClient.lookupPin(pin, object : MarucastApiClient.Callback<String> {
            override fun onSuccess(token: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Paired successfully!", Toast.LENGTH_SHORT).show()
                    startStreamingService(token)
                    onResult(true, null)
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Pairing failed: $error", Toast.LENGTH_LONG).show()
                    onResult(false, error)
                }
            }
        })
    }

    private fun startStreamingService(token: String) {
        val serviceIntent = Intent(this, MarucastForegroundService::class.java).apply {
            action = MarucastForegroundService.ACTION_START
            putExtra(MarucastForegroundService.EXTRA_TOKEN, token)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopStreamingService() {
        val serviceIntent = Intent(this, MarucastForegroundService::class.java).apply {
            action = MarucastForegroundService.ACTION_STOP
        }
        startService(serviceIntent)
        Toast.makeText(this, "Disconnected from receiver.", Toast.LENGTH_SHORT).show()
    }
}

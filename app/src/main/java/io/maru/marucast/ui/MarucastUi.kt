package io.maru.marucast.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.maru.marucast.media.MediaSessionState
import io.maru.marucast.media.MarucastNotificationListener
import io.maru.marucast.service.MarucastForegroundService

// Color palette
val DeepBackground = Color(0xFF0A0E18)
val DarkNavyAlt = Color(0xFF101420)
val GlassBg = Color(0x1F2531)
val AccentBlue = Color(0xFF7EB8F7)
val AccentPurple = Color(0xFFA78BFA)
val TextLight = Color(0xFFF5F8FF)
val TextMuted = Color(0xFF8C95A5)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MarucastAppContent(
    modifier: Modifier = Modifier,
    onStartStream: (String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onStopStream: () -> Unit
) {
    val context = LocalContext.current
    var hasNotificationAccess by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var currentToken by remember { mutableStateOf(MarucastForegroundService.currentToken) }

    // Periodically check if permission was granted
    LaunchedEffect(Unit) {
        while (true) {
            hasNotificationAccess = isNotificationServiceEnabled(context)
            currentToken = MarucastForegroundService.currentToken
            kotlinx.coroutines.delay(1000)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepBackground, DarkNavyAlt)
                )
            )
    ) {
        // Decorative background glowing spots
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-100).dp)
                .background(Brush.radialGradient(listOf(AccentBlue.copy(alpha = 0.15f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-100).dp, y = 100.dp)
                .background(Brush.radialGradient(listOf(AccentPurple.copy(alpha = 0.15f), Color.Transparent)))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "MARUCAST",
                    color = AccentBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                
                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (currentToken != null) Color(0x334CAF50) else Color(0x33FF9800))
                        .border(
                            1.dp,
                            if (currentToken != null) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (currentToken != null) "Casting" else "Not Paired",
                        color = TextLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!hasNotificationAccess) {
                // Prompt to enable Notification listener
                PermissionPromptCard {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            } else {
                AnimatedContent(targetState = currentToken) { token ->
                    if (token == null) {
                        PairingScreen(
                            onPairCodeEntered = { pin, onResult ->
                                onStartStream(pin, onResult)
                            }
                        )
                    } else {
                        NowPlayingScreen(
                            onDisconnect = {
                                onStopStream()
                                currentToken = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionPromptCard(onGrantClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        colors = CardDefaults.cardColors(containerColor = DarkNavyAlt.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0x1FFFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Notification Access Required",
                color = TextLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Marucast needs Notification Access permission to sync your current playing music title, artist, and album artwork with the browser receiver.",
                color = TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Grant Permission", color = DeepBackground, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PairingScreen(onPairCodeEntered: (String, onResult: (Boolean, String?) -> Unit) -> Unit) {
    var pinText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(30.dp))
            Text(
                text = "Enter Pairing PIN",
                color = TextLight,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Look at the Marucast applet on the website and enter the 6-digit code shown there.",
                color = TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // Pin view
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 6) {
                    val char = pinText.getOrNull(i)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkNavyAlt)
                            .border(
                                1.dp,
                                if (char != null) AccentBlue else Color(0x2AFFFFFF),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char?.toString() ?: "",
                            color = AccentBlue,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (pinText.isNotEmpty() || isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isLoading) "Cancel" else "Clear All",
                    color = if (isLoading) Color(0xFFE57373) else AccentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            isLoading = false
                            pinText = ""
                            errorMessage = null
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = error, color = Color(0xFFE57373), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(24.dp))
            }
        }

        // Custom numerical keyboard
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("CLR", "0", "OK")
            )
            keys.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .padding(horizontal = 6.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x0AFFFFFF))
                                .clickable {
                                    if (isLoading) return@clickable
                                    errorMessage = null
                                    when (key) {
                                        "CLR" -> {
                                            if (pinText.isNotEmpty()) {
                                                pinText = pinText.dropLast(1)
                                            }
                                        }
                                        "OK" -> {
                                            if (pinText.length == 6) {
                                                isLoading = true
                                                errorMessage = null
                                                onPairCodeEntered(pinText) { success, error ->
                                                    isLoading = false
                                                    if (!success) {
                                                        errorMessage = error ?: "Failed to pair"
                                                    }
                                                }
                                            } else {
                                                errorMessage = "Enter exactly 6 digits."
                                            }
                                        }
                                        else -> {
                                            if (pinText.length < 6) {
                                                pinText += key
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                color = if (key == "OK") AccentBlue else TextLight,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NowPlayingScreen(onDisconnect: () -> Unit) {
    var metadataState by remember { mutableStateOf(MediaSessionState) }
    var title by remember { mutableStateOf(MediaSessionState.title) }
    var artist by remember { mutableStateOf(MediaSessionState.artist) }
    var appLabel by remember { mutableStateOf(MediaSessionState.appLabel) }
    var isPlaying by remember { mutableStateOf(MediaSessionState.isPlaying) }
    var artworkBitmap by remember { mutableStateOf(MediaSessionState.artworkBitmap) }

    LaunchedEffect(Unit) {
        MediaSessionState.onMetadataChanged = {
            title = MediaSessionState.title
            artist = MediaSessionState.artist
            appLabel = MediaSessionState.appLabel
            isPlaying = MediaSessionState.isPlaying
            artworkBitmap = MediaSessionState.artworkBitmap
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Frosted Now Playing card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavyAlt.copy(alpha = 0.8f)),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, Color(0x1CFFFFFF))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Album Art
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0x0DFFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    if (artworkBitmap != null) {
                        Image(
                            bitmap = artworkBitmap!!.asImageBitmap(),
                            contentDescription = "Album Artwork",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Default vinyl disc placeholder
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(AccentBlue, AccentPurple, AccentBlue)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(DeepBackground)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Track title & artist
                Text(
                    text = title ?: "Waiting for music...",
                    color = TextLight,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = artist ?: "Play a song on your phone",
                    color = TextMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                appLabel?.let { label ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x1AFFFFFF))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            color = AccentPurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Disconnect Button
        Button(
            onClick = onDisconnect,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(52.dp)
        ) {
            Text("Stop Broadcast", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

fun isNotificationServiceEnabled(context: Context): Boolean {
    val cn = ComponentName(context, MarucastNotificationListener::class.java)
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(cn.flattenToString())
}

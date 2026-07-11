package io.maru.marucast.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
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
    var audioSourceIsMic by remember { mutableStateOf(MarucastForegroundService.isMicMode) }

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

            Spacer(modifier = Modifier.height(20.dp))

            // Audio Source Selector
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkNavyAlt)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val modes = listOf(
                    false to "System Music",
                    true to "Microphone"
                )
                modes.forEach { (isMic, label) ->
                    val selected = audioSourceIsMic == isMic
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) AccentBlue else Color.Transparent)
                            .clickable {
                                audioSourceIsMic = isMic
                                MarucastForegroundService.isMicMode = isMic
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) DeepBackground else TextLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

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
fun AudioVisualizer(isPlaying: Boolean) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val barCount = 7
        val infiniteTransition = rememberInfiniteTransition(label = "audio_visualizer")
        
        for (i in 0 until barCount) {
            val duration = remember { (450..850).random() }
            val heightFactor by if (isPlaying) {
                infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(duration, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar_height_$i"
                )
            } else {
                remember { mutableStateOf(0.15f) }
            }
            
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(heightFactor)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(AccentPurple, AccentBlue)
                        )
                    )
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NowPlayingScreen(onDisconnect: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("marucast_prefs", Context.MODE_PRIVATE) }
    var delayMode by remember { mutableStateOf(prefs.getString("delay_mode", "lossless") ?: "lossless") }
    var lyricsDelayOffset by remember { mutableStateOf(prefs.getLong("lyrics_delay_offset", 0L)) }
    var metadataState by remember { mutableStateOf(MediaSessionState) }
    var title by remember { mutableStateOf(MediaSessionState.title) }
    var artist by remember { mutableStateOf(MediaSessionState.artist) }
    var appLabel by remember { mutableStateOf(MediaSessionState.appLabel) }
    var isPlaying by remember { mutableStateOf(MediaSessionState.isPlaying) }
    var artworkBitmap by remember { mutableStateOf(MediaSessionState.artworkBitmap) }
    var karaokeEnabled by remember { mutableStateOf(MarucastForegroundService.isKaraokeMode) }
    
    LaunchedEffect(lyricsDelayOffset) {
        MarucastForegroundService.lyricsDelayOffsetMs = lyricsDelayOffset
    }

    LaunchedEffect(Unit) {
        MediaSessionState.onMetadataChanged = {
            title = MediaSessionState.title
            artist = MediaSessionState.artist
            appLabel = MediaSessionState.appLabel
            isPlaying = MediaSessionState.isPlaying
            artworkBitmap = MediaSessionState.artworkBitmap
        }
    }

    // Infinite transitions for rotating glowing ring and pulsing effects
    val infiniteTransition = rememberInfiniteTransition(label = "now_playing_animations")
    
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_pulse"
    )
    
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora_rotate"
    )

    val currentGlowScale = if (isPlaying) glowScale else 1.0f
    val currentRotation = if (isPlaying) rotationAngle else 0.0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Frosted Now Playing card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavyAlt.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, Color(0x1CFFFFFF))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated Glowing Backdrop under Album Art
                Box(
                    modifier = Modifier
                        .size(210.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing Neon Aura behind the art
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .scale(currentGlowScale)
                            .rotate(currentRotation)
                            .blur(20.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    colors = listOf(AccentBlue, AccentPurple, AccentBlue)
                                )
                            )
                    )
                    
                    // Album Art Box
                    Box(
                        modifier = Modifier
                            .size(185.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(DeepBackground)
                            .border(1.5.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp)),
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
                            // Spinning vinyl record visual
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .rotate(currentRotation)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F111A))
                                    .border(3.dp, Color(0x1CFFFFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                // Groove rings
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .border(1.dp, Color(0x0DFFFFFF), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .border(1.dp, Color(0x12FFFFFF), CircleShape)
                                )
                                // Center sticker label
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(AccentBlue, AccentPurple)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(DeepBackground)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Realtime Audio Equalizer Visualizer
                AudioVisualizer(isPlaying = isPlaying)

                Spacer(modifier = Modifier.height(16.dp))

                // Track title & artist with Slide/Fade Transition on Track Changes
                AnimatedContent(
                    targetState = title to artist,
                    transitionSpec = {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    },
                    label = "track_details_transition"
                ) { (currentTitle, currentArtist) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = currentTitle ?: "Waiting for music...",
                            color = TextLight,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = currentArtist ?: "Play a song on your phone",
                            color = TextMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                // Player Source Label
                appLabel?.let { label ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x14A78BFA))
                            .border(1.dp, AccentPurple.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            color = AccentPurple,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Interactive Media Player Controls row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Button
                    IconButton(
                        onClick = {
                            MediaSessionState.activeController?.transportControls?.skipToPrevious()
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0x0AFFFFFF))
                            .border(1.dp, Color(0x14FFFFFF), CircleShape)
                    ) {
                        Text("⏮", color = TextLight, fontSize = 20.sp)
                    }

                    // Play/Pause button
                    IconButton(
                        onClick = {
                            val controller = MediaSessionState.activeController
                            if (isPlaying) {
                                controller?.transportControls?.pause()
                            } else {
                                controller?.transportControls?.play()
                            }
                        },
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(AccentBlue, AccentPurple)
                                )
                            )
                            .border(1.dp, Color(0x3DFFFFFF), CircleShape)
                    ) {
                        Text(
                            text = if (isPlaying) "⏸" else "▶",
                            color = DeepBackground,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.offset(x = if (isPlaying) 0.dp else 2.dp)
                        )
                    }

                    // Next Button
                    IconButton(
                        onClick = {
                            MediaSessionState.activeController?.transportControls?.skipToNext()
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0x0AFFFFFF))
                            .border(1.dp, Color(0x14FFFFFF), CircleShape)
                    ) {
                        Text("⏭", color = TextLight, fontSize = 20.sp)
                    }
                }
            }
        }

        // Karaoke Mode Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable {
                    karaokeEnabled = !karaokeEnabled
                    MarucastForegroundService.isKaraokeMode = karaokeEnabled
                },
            colors = CardDefaults.cardColors(
                containerColor = if (karaokeEnabled) Color(0x29A78BFA) else DarkNavyAlt.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, if (karaokeEnabled) AccentPurple.copy(alpha = 0.5f) else Color(0x14FFFFFF))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "🎤",
                        fontSize = 24.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = "Karaoke Mode",
                            color = TextLight,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Real-time vocal cancellation filter",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                
                Switch(
                    checked = karaokeEnabled,
                    onCheckedChange = { checked ->
                        karaokeEnabled = checked
                        MarucastForegroundService.isKaraokeMode = checked
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DeepBackground,
                        checkedTrackColor = AccentPurple,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0x1AFFFFFF)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Lyrics Sync Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavyAlt.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0x14FFFFFF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⏱️",
                        fontSize = 22.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = "Lyrics Sync",
                            color = TextLight,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Nudge lyrics backward or forward in real-time",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Adjustment Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Minus Button
                    Button(
                        onClick = {
                            lyricsDelayOffset = (lyricsDelayOffset - 250).coerceIn(-10000L, 10000L)
                            prefs.edit().putLong("lyrics_delay_offset", lyricsDelayOffset).apply()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x14FFFFFF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                    ) {
                        Text("-0.25s", color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    // Display Value
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val offsetSecs = lyricsDelayOffset / 1000.0
                        val text = if (lyricsDelayOffset == 0L) "Synced" else "${if (offsetSecs > 0) "+" else ""}${String.format("%.2fs", offsetSecs)}"
                        Text(
                            text = text,
                            color = if (lyricsDelayOffset == 0L) AccentBlue else AccentPurple,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    // Plus Button
                    Button(
                        onClick = {
                            lyricsDelayOffset = (lyricsDelayOffset + 250).coerceIn(-10000L, 10000L)
                            prefs.edit().putLong("lyrics_delay_offset", lyricsDelayOffset).apply()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x14FFFFFF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                    ) {
                        Text("+0.25s", color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Delay Management Card (Segmented Control Refactoring)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavyAlt.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0x14FFFFFF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚡",
                        fontSize = 22.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = "Delay Management",
                            color = TextLight,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Optimize streaming quality dynamically",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Premium Segmented Pill Selector Row
                val delayOptions = listOf(
                    "lossless" to "Lossless",
                    "automatic" to "Automatic",
                    "less_delay" to "Less Delay"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x0AFFFFFF))
                        .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    delayOptions.forEach { (mode, label) ->
                        val selected = delayMode == mode
                        val backgroundAlpha by animateFloatAsState(
                            targetValue = if (selected) 1f else 0f,
                            animationSpec = tween(250, easing = LinearOutSlowInEasing),
                            label = "segmented_tab_bg_$mode"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            AccentBlue.copy(alpha = backgroundAlpha * 0.15f),
                                            AccentPurple.copy(alpha = backgroundAlpha * 0.15f)
                                        )
                                    )
                                )
                                .border(
                                    1.dp,
                                    if (selected) AccentBlue.copy(alpha = 0.25f) else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    delayMode = mode
                                    MarucastForegroundService.delayManagementMode = mode
                                    prefs.edit().putString("delay_mode", mode).apply()
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) AccentBlue else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dynamic detailed description with subtle typography
                Text(
                    text = when (delayMode) {
                        "lossless" -> "• Lossless: Prioritizes best sound fidelity. Stream remains uncompressed."
                        "automatic" -> "• Automatic: Monitors network delay and reduces sample rate dynamically."
                        else -> "• Less Delay: Optimizes for lowest latency. Compresses stream for speed."
                    },
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Professional Gradient Outline Disconnect Button
        Button(
            onClick = onDisconnect,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(54.dp)
                .border(
                    BorderStroke(1.dp, Color(0x33FF5252)),
                    RoundedCornerShape(16.dp)
                )
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFE53935).copy(alpha = 0.15f),
                            Color(0xFFD81B60).copy(alpha = 0.15f)
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Stop Broadcast",
                    color = Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

fun isNotificationServiceEnabled(context: Context): Boolean {
    val cn = ComponentName(context, MarucastNotificationListener::class.java)
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(cn.flattenToString())
}

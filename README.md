# Marucast — Native Android Sender App

A lightweight, pure-Kotlin Android application to broadcast audio streams, now-playing track details, and album artwork from your Android phone directly to the Maru website's audio receiver at `/marucast`.

## Features

- **Pure Kotlin + Jetpack Compose:** Built natively with modern Jetpack Compose dark-theme UI matching the main website.
- **Background Cast Service:** Utilizes an Android Foreground Service to host a local audio server (`MarucastRelayServer`) on port `48543` and broadcast audio without being killed in the background.
- **Media Session Interceptor:** Monitors currently active player sessions (e.g. Spotify, YouTube, Apple Music) using `NotificationListenerService` and broadcasts:
  - Track Title & Artist.
  - Playing State (Play/Pause).
  - High-quality Album Artwork (served locally via `/artwork.jpg`).
- **Remote Control Integration:** Polls the `/marucast/receiver-command` API endpoint to accept transport controls (Play, Pause, Skip Next, Skip Previous) sent from the browser/TV receiver and maps them to the local media player.
- **Local LAN Relay:** Audio is streamed directly over the local WiFi connection, completely bypassing cloud relays or Vercel execution limits.

## Pairing Instructions

1. Connect your Android phone to the same WiFi network as your TV/Computer receiver.
2. Open the Maru website and navigate to `/marucast` (or open the TV App).
3. Note the 6-digit PIN code displayed on the screen.
4. Open the **Marucast** Android app, grant Notification Access (if prompted), and enter the PIN.
5. Hit **OK** to pair. The browser will automatically detect the stream, load the track details/artwork, and play.

# RTSP Stream

A simple Android app (Kotlin) for watching RTSP streams from IP cameras, NVRs, and media servers. It uses [AndroidX Media3 / ExoPlayer](https://developer.android.com/media/media3/exoplayer) and its built-in RTSP support.

## Features

- An address box for `rtsp://` / `rtsps://` URLs, plus optional username and password fields (Basic and Digest auth)
- A recent-streams list. Tap to play, long-press to edit, use the bin icon to delete
- Opens `rtsp://` links from other apps
- Full-screen player with mute, a snapshot button (saves to *Pictures/RtspStream*), and picture-in-picture

### Settings

| Section | Options |
|---|---|
| Network | Transport (auto UDP→TCP / force TCP), connection timeout, user agent, auto-reconnect with delay and max attempts |
| Latency & buffering | Presets from ultra-low (≈250 ms) to smooth (≈2.5 s), or custom min/max/start buffer |
| Video | Aspect ratio / scaling (fit, fill, zoom, fixed width/height), orientation lock, hardware vs software decoder, stats overlay (resolution, codec, fps, buffer, dropped frames) |
| Playback | Start muted, keep screen on, PiP when leaving, remember passwords |

## Building

- **Android Studio:** open the folder and run the `app` configuration.
- **Command line:** `./gradlew assembleDebug`. The APK is written to `app/build/outputs/apk/debug/`.
- **GitHub Actions:** every push builds a debug APK. Download it from the run's **Artifacts** section.

Requires Android 8.0 (API 26) or newer.

## Tips

- If video freezes or shows grey smearing, set **Transport → TCP**. Many cameras and most connections over the internet or a VPN need it.
- Common camera paths: Hikvision `/Streaming/Channels/101`, Dahua/Amcrest `/cam/realmonitor?channel=1&subtype=0`, Reolink `/h264Preview_01_main`, Tapo `/stream1`.
- Passwords in the recent list are stored unencrypted. You can turn that off in Settings.

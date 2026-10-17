# UniGrab Android App

A premium, client-side media downloader for Android devices powered by `yt-dlp` and `FFmpeg`.

## Features
- **100% Serverless**: All media link resolution and downloading are handled entirely on your device.
- **High-Fidelity UI**: Premium dark mode aesthetics with animated neon aura glow and glassmorphic cards.
- **Audio & Video Modes**: Segmented controls for downloading video resolutions (preset selection) or audio streams.
- **Format Conversion**: Convert audio streams to high-quality MP3 format.
- **Multi-threaded Downloading**: Integrated `aria2c` downloader to accelerate download speeds.
- **Task Console Logs**: View live terminal output logs from the running `yt-dlp` process for transparent operation.
- **Local Library**: Instantly play downloaded tracks with your device's default media player (VLC, MX Player, etc.) or chooser.

## Technology Stack
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM with Kotlin Flows
- **Database**: Room for Tasks, completed Library items, and Logs
- **Engine**: `youtubedl-android` (native yt-dlp & FFmpeg Android wrapper)

## Project Setup & Build
1. Clone the repository and navigate to the `UniGrabApp` folder.
2. Ensure you have Java 17 installed.
3. Build the project using the Gradle wrapper:
   ```bash
   ./gradlew assembleDebug
   ```
4. Install the resulting APK from:
   `app/build/outputs/apk/debug/app-debug.apk`

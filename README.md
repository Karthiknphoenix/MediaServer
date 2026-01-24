# Vortex Media Server

A high-performance, self-hosted media server written in Rust with a modern Android client.

## Features
- **High Performance:** Built with Rust, Axum, and SQLx (SQLite).
- **Modern UI:** Glassy, premium Android interface built with Jetpack Compose.
- **Folder Browsing:** Navigate your file system directly for libraries.
- **Metadata:** Automatic scanning and fetching of metadata from TMDB.
- **Playback:** HLS streaming and direct play support.
- **Cross-Platform:** Server runs on Windows, Linux, and macOS.

## Setup

### Server (Rust)
1. Install Rust (https://rustup.rs).
2. Create `.env` file (if not present):
   ```env
   DATABASE_URL=sqlite:media_server.db
   TMDB_API_KEY=your_key_here
   ```
3. Run the server:
   ```bash
   cargo run --release
   ```

### Client (Android)
1. Open `android_app` in Android Studio.
2. Build and Run on your device/emulator.

## Architecture
- **Backend:** Rust (Axum, SQLx, Tokio)
- **Database:** SQLite
- **Frontend:** Android (Jetpack Compose, Hilt, Coil, ExoPlayer)

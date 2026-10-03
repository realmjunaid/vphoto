# VPhoto

A fast, **100% offline** photo gallery for Android. Open the app and all your device photos are already there — organized folder-wise like Google Photos. No accounts, no uploads, no tracking.

<p>
  <a href="https://github.com/realmjunaid/vphoto/releases/latest">
    <img src="https://img.shields.io/badge/Download-vPhoto_v1.0.0.apk-7C3AED?style=for-the-badge&logo=android&logoColor=white" alt="Download APK">
  </a>
</p>

<p>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT">
  <img src="https://img.shields.io/badge/Offline-No%20tracking-22c55e" alt="Offline">
</p>

## ✨ Features

- **Auto gallery** — every photo on your device shows up instantly, grouped folder-wise (Camera, Download, Screenshots…) just like Google Photos. Nothing to pick, nothing to configure.
- **Continuous full view** — tap any photo to enter a Drive-style vertical feed: every photo edge-to-edge, full original resolution, zero compression.
- **Smart counter** — a tiny `23 / 100` indicator follows you as you scroll.
- **Middle-only playback** — GIFs and animated WebPs play only when centered on screen; the rest stay paused to save battery.
- **Pinch-to-zoom** — 1x–5x zoom with two fingers, pan in every direction while zoomed.
- **Immersive mode** — one tap hides the top bar, titles, status bar and navigation buttons for pure full-screen viewing.
- **Modern feel** — crossfade image loading, animated grids, real-photo album covers.
- **Private by design** — photos never leave your phone. No internet permission at all.

## 📥 Download & Install

1. Go to the [**latest release**](https://github.com/realmjunaid/vphoto/releases/latest).
2. Download **`vphoto_v1.0.0.apk`** (under *Assets*).
3. Open the file on your phone and tap **Install** (allow *Install unknown apps* if asked).
4. Open **VPhoto**, allow photo access — done, your gallery is ready.

> Requires Android 8.0 (API 26) or newer.

## 🔒 Privacy

- No account, no analytics, no ads, no crash reporting.
- The app **does not request the INTERNET permission** — it is physically incapable of sending anything anywhere.
- Photos are read from the on-device `MediaStore` only. Nothing is uploaded, ever.

## 🛠 Build from Source

Requirements: **JDK 17** and the **Android SDK** (compileSdk 36).

```bash
git clone https://github.com/realmjunaid/vphoto.git
cd vphoto
# point to your SDK, e.g. C:\Users\you\AppData\Local\Android\Sdk
echo "sdk.dir=C:\\Users\\you\\AppData\\Local\\Android\\Sdk" > local.properties
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/vphoto_v1.0.0.apk
```

Run the unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

## 🏗 Tech Stack

- **Kotlin + Jetpack Compose (Material 3)** — declarative UI
- **MediaStore** — instant, no-copy access to device photos
- **Coil 3** (+ `coil-gif`) — image loading with animated WebP/GIF support
- **Hilt** — dependency injection
- **DataStore** — lightweight settings persistence
- **JUnit + Robolectric + Kotest** — unit & property tests

## 🗂 Project Structure

```
app/src/main/java/com/vphoto/app/
├── data/gallery/      # MediaStore repository, album grouping
├── ui/gallery/        # Auto gallery: albums → grid → full view
├── ui/settings/       # Settings (display, playback, about)
├── ui/viewer/         # Legacy folder viewer components
├── navigation/        # Nav graph & routes
├── player/            # ExoPlayer pool (legacy viewer)
└── util/thumbnail/    # Video thumbnail pipeline
```

## 🤝 Contributing

Issues and pull requests are welcome! For big changes, please open an issue first to discuss what you'd like to change.

## 📄 License

MIT — see [LICENSE](LICENSE).

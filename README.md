# My Viewer 📁

A simple Android file viewer that lets you browse files, preview images & videos, and open any file with the right app — all built with free GitHub Actions builds.

## Features

- 📂 **File explorer** — browse folders with a familiar list view
- 👁 **Show/hide hidden files** — tap the eye icon to toggle `.hidden` files
- 🖼 **Auto previews** — images & videos get a small thumbnail in the list
- 🚀 **Open with other apps** — tap a file to launch it in the right viewer
- ❌ **X close button** — the red X in the top-right corner closes the app
- ⬆ **Up button** — go to the parent folder with one tap
- ↻ **Refresh** — reload the current directory

## Screenshots

*(Run the app on your phone to see it!)*

## Quick Start

### Build the APK

1. Push this folder to a **public** GitHub repo
2. Open the repo's **Actions** tab
3. Wait ~5-10 minutes for the build to finish
4. Download the `app-debug` artifact → unzip → `app-debug.apk`
5. Transfer the APK to your phone and tap to install

> 📖 For detailed setup, see **[docs/PROJECT.md](docs/PROJECT.md)**.

## How to Customize

- **Change colors** → edit `app/src/main/res/values/colors.xml`
- **Change app name** → edit `app/src/main/res/values/strings.xml`
- **Add new file types** → edit `FileUtils.getMimeType()` in `app/src/main/java/.../FileUtils.kt`
- **Change default folder** → edit the `currentDir` initializer in `MainActivity.kt`
- **Adjust preview size** → change `96, 96` in `MediaPreviewLoader.calculateInSampleSize()`

See **[docs/PROJECT.md](docs/PROJECT.md)** for a full guide.

## Project Structure

```
my-viewer/
├── .github/workflows/build.yml     # Auto-builds APK on push
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/myviewer/
│       │   ├── MainActivity.kt           # Main screen & logic
│       │   ├── FileAdapter.kt            # RecyclerView list adapter
│       │   ├── FileItem.kt               # Data class for a file row
│       │   ├── FileUtils.kt              # Helper functions
│       │   └── MediaPreviewLoader.kt     # Thumbnail loader
│       └── res/
│           ├── layout/                   # UI layouts
│           ├── drawable/                 # Icons (vector drawables)
│           ├── values/                   # Colors, strings, themes
│           ├── xml/file_paths.xml        # FileProvider config
│           └── mipmap-anydpi-v26/        # App launcher icon
├── build.gradle.kts
├── settings.gradle.kts
└── docs/
    └── PROJECT.md                        # Full development guide
```

## Tech Stack

- **Language:** Kotlin 1.9.24
- **UI:** AndroidX + Material Components
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 34 (Android 14)
- **Build:** Gradle 8.7 + Android Gradle Plugin 8.5.2

## License

This is a personal/learning project — feel free to copy, modify, and share. ✨

# My Viewer — Project Documentation 📖

> A complete follow-up guide for developing, customizing, and extending the **my-viewer** Android app.

This document is the main "developer notebook" for the project. It explains:
- How the code is organized
- How each feature is implemented
- How to modify the app for your own needs
- How to add new features
- Common problems and how to fix them

Read it once end-to-end, then keep it open while you code. ✨

---

## 📑 Table of Contents

1. [What this app does](#1-what-this-app-does)
2. [Quick start: get the APK running](#2-quick-start-get-the-apk-running)
3. [Project structure explained](#3-project-structure-explained)
4. [Code walkthrough](#4-code-walkthrough)
5. [How to customize](#5-how-to-customize)
6. [How to add new features](#6-how-to-add-new-features)
7. [Permissions explained](#7-permissions-explained)
8. [Troubleshooting](#8-troubleshooting)
9. [Ideas for future enhancements](#9-ideas-for-future-enhancements)
10. [Reference links](#10-reference-links)

---

## 1. What this app does

**My Viewer** is a single-screen Android file browser. It opens to the root of your phone's shared storage (`/storage/emulated/0/`) and lets you:

- **Browse folders** — tap a folder to enter it, tap the up arrow to go back
- **Preview media** — images and videos automatically show a small thumbnail in the list
- **Open files** — tapping a file hands it off to the right app (Gallery, Video Player, PDF reader, etc.)
- **Toggle hidden files** — the eye icon shows/hides files starting with a dot (`.nomedia`, `.thumbnails`, etc.)
- **Close cleanly** — the red X in the top-right corner finishes the app

It does **not** currently support: file deletion, rename, copy, multi-select, search, or cloud storage. These are great candidates for your first extensions — see [section 6](#6-how-to-add-new-features).

---

## 2. Quick start: get the APK running

### Option A: Build in the cloud (recommended)

1. Create a new **public** repository on GitHub (e.g. `my-viewer`)
2. Push this entire folder to it
3. Open the repo → **Actions** tab
4. Wait for the build to finish (~5-10 min)
5. Download the **`app-debug`** artifact from the workflow run
6. Unzip → you'll find `app-debug.apk`
7. Transfer to your phone and install (you may need to allow "Install from unknown sources")

> The first build is slower because Gradle has to download Android dependencies. Subsequent builds reuse them and finish in ~2-3 min.

### Option B: Build locally with Android Studio

1. Install **Android Studio Hedgehog** (or newer): https://developer.android.com/studio
2. Open this folder (`my-viewer`) as a project
3. Wait for Gradle sync to finish
4. Plug in your phone with USB debugging enabled
5. Click the green ▶ play button

### Option C: Build locally with command line

```bash
# Install Android SDK + JDK 17 first, then:
cd my-viewer
gradle assembleDebug
# APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

---

## 3. Project structure explained

```
my-viewer/
│
├── .github/workflows/build.yml    ← GitHub Actions: auto-builds APK on every push
│
├── build.gradle.kts               ← Root project config (plugin versions)
├── settings.gradle.kts            ← Project settings (modules, repos)
├── gradle.properties              ← JVM and AndroidX flags
│
├── docs/
│   └── PROJECT.md                 ← This file
│
└── app/                           ← The actual Android app
    ├── build.gradle.kts           ← App module config (SDK versions, dependencies)
    ├── proguard-rules.pro         ← Code-shrinking rules (only used for release)
    │
    └── src/main/
        ├── AndroidManifest.xml    ← App identity, permissions, components
        │
        ├── java/com/example/myviewer/
        │   ├── MainActivity.kt           ← The single screen
        │   ├── FileAdapter.kt            ← RecyclerView adapter
        │   ├── FileItem.kt               ← Data model
        │   ├── FileUtils.kt              ← File/mime helpers
        │   └── MediaPreviewLoader.kt     ← Thumbnail loader
        │
        └── res/
            ├── layout/
            │   ├── activity_main.xml      ← The screen layout
            │   └── item_file.xml          ← One row in the file list
            │
            ├── drawable/                  ← Vector icons (no PNG files needed!)
            │   ├── ic_folder.xml          ← Folder icon (orange)
            │   ├── ic_file.xml            ← Generic file icon (gray)
            │   ├── ic_image.xml           ← Image icon (blue)
            │   ├── ic_video.xml           ← Video icon (red)
            │   ├── ic_audio.xml           ← Audio icon (purple)
            │   ├── ic_arrow_up.xml        ← Up button icon
            │   ├── ic_refresh.xml         ← Refresh icon
            │   ├── ic_close.xml           ← X close icon
            │   ├── ic_visibility.xml      ← Show hidden icon
            │   ├── ic_visibility_off.xml  ← Hide hidden icon
            │   ├── ic_launcher_background.xml
            │   └── ic_launcher_foreground.xml
            │
            ├── mipmap-anydpi-v26/
            │   ├── ic_launcher.xml        ← Adaptive launcher icon
            │   └── ic_launcher_round.xml  ← Round launcher icon
            │
            ├── values/
            │   ├── strings.xml            ← All user-facing text
            │   ├── colors.xml             ← Color palette
            │   ├── colors_extra.xml       ← Extra colors (the red X)
            │   └── themes.xml             ← App theme
            │
            └── xml/
                └── file_paths.xml         ← FileProvider config
```

---

## 4. Code walkthrough

This section explains the *why* behind each Kotlin file, focusing on patterns you'll want to copy.

### 4.1 `FileItem.kt` — the data model

```kotlin
data class FileItem(
    val file: File,
    val isDirectory: Boolean,
    val isHidden: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String?
)
```

This is a plain Kotlin **data class** — just a bag of values. We use one `FileItem` per row in the list.

> 💡 **Why not just store `File` directly?**
> - Pre-computing `isDirectory` and `mimeType` once is much faster than asking every time the row is rendered.
> - Adding a "favorited" or "selected" field later is easy without touching the rest of the code.

### 4.2 `FileUtils.kt` — pure helpers

`FileUtils` is a Kotlin **`object`** (singleton). All its methods are `static`-like. Two reasons for putting these here:
- They're easy to test (no Android dependencies)
- They can be called from anywhere

The most important function is `listFiles(dir, showHidden)`:

```kotlin
fun listFiles(dir: File, showHidden: Boolean): List<FileItem> {
    val files = dir.listFiles() ?: return emptyList()
    return files
        .filter { showHidden || !it.name.startsWith(".") }
        .map { file -> FileItem(...) }
        .sortedWith(compareBy({ !it.isDirectory }, { ... }))  // dirs first, then alpha
}
```

The `sortedWith` comparator is the most interesting bit: folders always come before files, then everything is sorted alphabetically. Try changing the comparator to see the list re-order instantly after a rebuild.

### 4.3 `MediaPreviewLoader.kt` — async thumbnails

This is the most complex file. The pattern is worth understanding because it shows up in **every** Android app that loads images:

```kotlin
fun loadThumbnail(file: File, imageView: ImageView) {
    // 1. Check cache
    cache.get(key)?.let { imageView.setImageBitmap(it); return }

    // 2. Show a placeholder (instant)
    imageView.setImageResource(placeholderIcon)
    imageView.tag = key   // ← the "tag" trick

    // 3. Load in background
    executor.execute {
        val bitmap = decodeImage(file)
        mainHandler.post {
            if (imageView.tag == key) {       // ← only update if still the same row
                imageView.setImageBitmap(bitmap)
            }
        }
    }
}
```

The **`tag` trick** prevents a classic bug: as you scroll a list, RecyclerView recycles the same `ImageView` for different rows. If your background task is slow, you might set a thumbnail for row 5 on an `ImageView` that now belongs to row 12. The `tag` check prevents that.

For **image files**, we use `BitmapFactory` with a calculated `inSampleSize` so we don't load a 12-megapixel photo into 96×96 of memory. For **video files**, we use `MediaMetadataRetriever` to extract a single frame.

### 4.4 `FileAdapter.kt` — RecyclerView

This uses the modern `ListAdapter` + `DiffUtil` pattern:

```kotlin
class FileAdapter(...) : ListAdapter<FileItem, ViewHolder>(DiffCallback()) {
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        // bind the data
    }
}

class DiffCallback : DiffUtil.ItemCallback<FileItem>() {
    override fun areItemsTheSame(a: FileItem, b: FileItem) = a.file.absolutePath == b.file.absolutePath
    override fun areContentsTheSame(a: FileItem, b: FileItem) = a.file.lastModified() == b.file.lastModified() ...
}
```

When you call `adapter.submitList(newList)`, DiffUtil computes the minimum set of changes and animates them automatically. Try renaming a file in your head and `submitList` will animate just that one row.

### 4.5 `MainActivity.kt` — the screen

This file is organized into clearly-labeled sections (look for the `// -----` comments). The most important things to understand:

**a) View Binding** — instead of `findViewById(R.id.closeButton)`, we use `binding.closeButton`. This is enabled in `app/build.gradle.kts`:
```kotlin
buildFeatures { viewBinding = true }
```

**b) The toolbar buttons** — instead of using the system's menu system (which is more complex), we have a custom `LinearLayout` acting as a toolbar. Each button is wired with a direct `setOnClickListener`.

**c) The X close button** — just calls `finish()`. That's the entire "close the app" logic.

**d) Permission handling** — uses the modern `ActivityResultContracts.RequestMultiplePermissions`:
```kotlin
private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { loadFiles() }
```

**e) Opening files** — uses `FileProvider` to safely share files with other apps:
```kotlin
val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
startActivity(Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, mime)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
})
```

---

## 5. How to customize

### 5.1 Change the app name

Edit `app/src/main/res/values/strings.xml`:
```xml
<string name="app_name">Your New Name</string>
```

### 5.2 Change colors

Edit `app/src/main/res/values/colors.xml` for the main palette, and `colors_extra.xml` for the red X color.

Quick reference (the colors you'll touch most):
| What | File | Key |
|------|------|-----|
| Toolbar background | colors.xml | `purple_500` |
| Status bar | colors.xml | `purple_700` |
| X button tint | colors_extra.xml | `close_button_red` |
| Folder icon | drawable/ic_folder.xml | the `fillColor` |
| Image icon | drawable/ic_image.xml | the `fillColor` |

### 5.3 Change the default starting folder

Edit `MainActivity.kt`:
```kotlin
// Original: starts at the storage root
private var currentDir: File = Environment.getExternalStorageDirectory()

// Change to start in the user's Pictures folder:
private var currentDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

// Or start in the app's private files:
private var currentDir: File = getExternalFilesDir(null)!!
```

### 5.4 Add support for new file types

Edit `FileUtils.getMimeType()` in `FileUtils.kt`. For example, to add `.epub` and `.mobi` ebook files:
```kotlin
"epub" -> "application/epub+zip",
"mobi" -> "application/x-mobipocket-ebook",
```

Don't forget to also add a new icon if you want a custom thumbnail icon:
1. Add `ic_ebook.xml` in `res/drawable/`
2. Add a line in `FileUtils.getIconRes()`:
   ```kotlin
   mimeType == "application/epub+zip" -> R.drawable.ic_ebook
   ```

### 5.5 Change the X button

The X button is in `activity_main.xml` — look for `@id/closeButton`. To make it bigger, change `android:layout_width="48dp"` to e.g. `60dp`. To change the tint, change `app:tint="@color/close_button_red"`. To use a different icon, swap `@drawable/ic_close`.

The click handler is in `MainActivity.kt`:
```kotlin
binding.closeButton.setOnClickListener { finish() }
```

Want to show a confirmation dialog before closing? Replace it with:
```kotlin
binding.closeButton.setOnClickListener {
    AlertDialog.Builder(this)
        .setTitle("Close My Viewer?")
        .setPositiveButton("Yes") { _, _ -> finish() }
        .setNegativeButton("No", null)
        .show()
}
```

### 5.6 Make thumbnails bigger or smaller

In `MediaPreviewLoader.kt`, find:
```kotlin
val sampleSize = calculateInSampleSize(boundsOpts, 96, 96)
```

Change `96, 96` to e.g. `128, 128` for bigger thumbnails, or `64, 64` for smaller. Also update the icon size in `item_file.xml`:
```xml
<ImageView
    android:id="@+id/fileIcon"
    android:layout_width="48dp"     <!-- match this to the new preview size -->
    android:layout_height="48dp"
    ...
/>
```

### 5.7 Show files in a different order

In `FileUtils.kt`, change the `sortedWith` call:
```kotlin
// Newest first
.sortedWith(compareByDescending { it.file.lastModified() })

// Largest first
.sortedWith(compareByDescending { it.file.length() })

// By extension, then name
.sortedWith(compareBy({ it.file.extension }, { it.file.name }))
```

---

## 6. How to add new features

Each of these is a self-contained project you can try in an afternoon.

### 6.1 "Open with" chooser is ugly → use a bottom sheet

Replace the `Intent.createChooser(...)` call in `MainActivity.openFile()` with a custom bottom sheet that shows app icons. This requires a bit of PackageManager work:
```kotlin
val pm = packageManager
val activities = pm.queryIntentActivities(intent, 0)
// Build a list of (label, icon, packageName) and show in a BottomSheetDialog
```

### 6.2 Long-press for actions (delete, rename, share)

In `FileAdapter.kt`, add a long-click listener:
```kotlin
holder.itemView.setOnLongClickListener {
    onItemLongClick(item)
    true
}
```

Then in `MainActivity.kt`, show a popup menu or bottom sheet with options.

### 6.3 Multi-select with checkboxes

1. Add a `selected` field to `FileItem`
2. Add a `Boolean` array in `MainActivity` tracking which items are selected
3. Show a contextual action bar when ≥1 item is selected
4. Add a delete-all-selected button

### 6.4 Search

Add a `SearchView` to the toolbar. On text change, filter the current list:
```kotlin
binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
    override fun onQueryTextChange(newText: String?): Boolean {
        val filtered = allFiles.filter { it.file.name.contains(newText ?: "", ignoreCase = true) }
        adapter.submitList(filtered)
        return true
    }
})
```

### 6.5 Grid view

Add a toggle button that switches between `LinearLayoutManager(this)` (list) and `StaggeredGridLayoutManager(2, VERTICAL)` (grid). You'll also want a different `item_file_grid.xml` layout.

### 6.6 Show file size as a bar chart

Add a custom `View` subclass that draws a horizontal bar showing the file's size relative to the largest file in the folder.

### 6.7 Favorites / Bookmarks

Add a `SharedPreferences` key for each bookmarked path. Add a long-press → "Bookmark" action. On startup, show a "Bookmarks" entry at the top of the root folder.

### 6.8 Recent files

In `MainActivity.onPause()`, save the current path to `SharedPreferences`. On `onCreate`, if no current dir was given, jump to the recent one.

---

## 7. Permissions explained

The app declares four permissions in `AndroidManifest.xml`:

| Permission | Why | Required when |
|------------|-----|---------------|
| `READ_EXTERNAL_STORAGE` | Read files on shared storage | Android 6.0 → 12 (API 23-32) |
| `READ_MEDIA_IMAGES` | Read image files (granular) | Android 13+ (API 33+) |
| `READ_MEDIA_VIDEO` | Read video files (granular) | Android 13+ (API 33+) |
| `READ_MEDIA_AUDIO` | Read audio files (granular) | Android 13+ (API 33+) |
| `MANAGE_EXTERNAL_STORAGE` | Read **all** files | Android 11+ (API 30+) |

`MANAGE_EXTERNAL_STORAGE` is special — the user has to enable it in **Settings → Apps → Special access → All files access**. The app shows a dialog prompting the user to do this the first time the app runs.

> ⚠️ **Play Store warning:** apps that use `MANAGE_EXTERNAL_STORAGE` get extra review scrutiny. For personal/learning projects this is fine, but for a real published app consider using the **Storage Access Framework** instead.

---

## 8. Troubleshooting

### Build fails: "SDK location not found"
The CI workflow handles this automatically. Locally, set `ANDROID_HOME` in your environment or create a `local.properties` file with `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`.

### Build fails: "Plugin not found"
Run `gradle --refresh-dependencies`. Or delete the `.gradle/` folder and try again.

### App opens but shows an empty list
You probably haven't granted storage permission. Open **Settings → Apps → My Viewer → Permissions → Files and media** and grant access.

### Tapping a file does nothing
- The file type might not be supported by any installed app
- Try a different file (e.g., a `.jpg` should always open in Gallery)

### The X button doesn't close the app
- Check `MainActivity.kt` — make sure `binding.closeButton.setOnClickListener { finish() }` is there
- Try a clean rebuild: delete `app/build/` and re-run

### Thumbnails never appear for videos
- Some video formats aren't supported by `MediaMetadataRetriever` (rare)
- The video file might be corrupt
- Check logcat for `MediaMetadataRetriever` errors

### "App not installed" on phone
- Make sure you have enough storage (the APK is small but the cache for thumbnails uses some)
- Try uninstalling any previous version first

---

## 9. Ideas for future enhancements

Sorted roughly by "fun to implement":

- 🌙 **Dark mode** — already partially supported via `Theme.MaterialComponents.DayNight`, just polish the colors
- 📋 **Copy / cut / paste** — use `ClipData` to share files between folders
- ☁️ **Cloud support** — Google Drive, Dropbox (use their official SDKs)
- 🔍 **Search** — filter the current list with a SearchView
- 🗂 **Tabs** — let the user have multiple folders open at once (Fragment backstack)
- 📊 **Storage usage** — recursive size calculation per folder
- 🖼 **Full-screen image viewer** — pinch-to-zoom with PhotoView library
- 🎵 **Built-in audio player** — MediaPlayer + simple UI
- 🏷 **Tags** — let users tag files and filter by tag
- 📤 **Share sheet** — share files to other apps via SEND intent
- 🗑 **Trash** — soft-delete with restore
- 🔒 **Hidden folder lock** — PIN to view certain folders
- 📐 **Sorting options** — name, size, date, type
- 🎨 **Theme picker** — let the user pick a color scheme

---

## 10. Reference links

- **Kotlin docs:** https://kotlinlang.org/docs/home.html
- **Android developer guides:** https://developer.android.com/guide
- **Material Design (icons & colors):** https://m3.material.io
- **RecyclerView guide:** https://developer.android.com/guide/topics/ui/layout/recyclerview
- **FileProvider docs:** https://developer.android.com/reference/androidx/core/content/FileProvider
- **Storage Access Framework:** https://developer.android.com/guide/topics/providers/document-provider
- **GitHub Actions for Android:** https://github.com/actions/setup-java

---

**Happy hacking!** 🚀 When you add a feature, share it with the world by pushing to GitHub — a fresh APK will be built in minutes.

package com.example.myviewer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myviewer.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * The single screen of My Viewer.
 *
 * Responsibilities:
 *  - Set up the custom toolbar (path label + buttons + settings gear)
 *  - Request storage permissions
 *  - List files in the current directory
 *  - Open files via the remembered app, or show a picker, optionally
 *    forcing a category MIME so the list is narrowed (the
 *    "Open as Photo / Video / Audio / Document" long-press items).
 *  - Settings dialog (thumbnail size, clear remembered apps)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FileAdapter

    private var currentDir: File = Environment.getExternalStorageDirectory()
    private var showHidden = false

    private val previewLoader by lazy { MediaPreviewLoader(this) }

    private val requiredPermissions: Array<String>
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            loadFiles()
            maybePromptForAllFilesAccess()
        }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupToolbarButtons()
        requestPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        loadFiles()
    }

    @Deprecated("Use OnBackPressedDispatcher for new code")
    override fun onBackPressed() {
        val parent = currentDir.parentFile
        val root = Environment.getExternalStorageDirectory()
        if (parent != null && currentDir != root) {
            currentDir = parent
            loadFiles()
        } else {
            super.onBackPressed()
        }
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    private fun setupRecyclerView() {
        adapter = FileAdapter(
            previewLoader = previewLoader,
            onItemClick = ::handleItemClick,
            onItemLongClick = ::handleItemLongClick
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupToolbarButtons() {
        binding.upButton.setOnClickListener {
            val parent = currentDir.parentFile
            val root = Environment.getExternalStorageDirectory()
            if (parent != null && currentDir != root) {
                currentDir = parent
                loadFiles()
            }
        }
        binding.refreshButton.setOnClickListener { loadFiles() }
        binding.showHiddenButton.setOnClickListener {
            showHidden = !showHidden
            updateShowHiddenIcon()
            loadFiles()
        }
        binding.settingsButton.setOnClickListener { showSettingsDialog() }
        binding.closeButton.setOnClickListener { finish() }
    }

    private fun requestPermissionsIfNeeded() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            loadFiles()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun maybePromptForAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (Environment.isExternalStorageManager()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.grant_all_files_access)
            .setMessage(R.string.grant_all_files_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // -------------------------------------------------------------------------
    // File listing
    // -------------------------------------------------------------------------

    private fun loadFiles() {
        val items = try {
            FileUtils.listFiles(currentDir, showHidden)
        } catch (e: SecurityException) {
            emptyList()
        }

        adapter.submitList(items)

        binding.pathText.text = currentDir.absolutePath
        binding.fileCountText.text = getString(R.string.item_count_format, items.size)

        binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE

        val canGoUp = currentDir.parentFile != null &&
                currentDir != Environment.getExternalStorageDirectory()
        binding.upButton.isEnabled = canGoUp
        binding.upButton.alpha = if (canGoUp) 1.0f else 0.35f

        updateShowHiddenIcon()
    }

    private fun updateShowHiddenIcon() {
        binding.showHiddenButton.setImageResource(
            if (showHidden) R.drawable.ic_visibility_off
            else R.drawable.ic_visibility
        )
    }

    // -------------------------------------------------------------------------
    // Item clicks
    // -------------------------------------------------------------------------

    private fun handleItemClick(item: FileItem) {
        if (item.isDirectory) {
            currentDir = item.file
            loadFiles()
        } else {
            openFileWithSavedPreference(item)
        }
    }

    /**
     * Fired on long-press of any file row. Shows a popup with:
     *  - "Open as Photo/Video/Audio/Document" so the user can force a
     *    category MIME (handy when extension-based detection is wrong, or
     *    when an extensionless file is in the wrong category).
     *  - "Choose another app...": the classic wildcard picker.
     *  - "Reset remembered app for .xyz": only if a default is saved.
     *
     * [anchorView] is the touched row view, used as the popup's anchor so
     * the menu appears right next to where the user pressed.
     */
    private fun handleItemLongClick(anchorView: View, item: FileItem): Boolean {
        if (item.isDirectory) return false
        val popup = PopupMenu(this, anchorView)
        val ext = item.file.extension.lowercase()
        val hasPref = SettingsManager.getPreferredApp(this, ext) != null

        // Category-specific force-opens (these bypass any remembered app).
        popup.menu.add(0, MENU_OPEN_AS_PHOTO, 0, R.string.open_as_photo)
        popup.menu.add(0, MENU_OPEN_AS_VIDEO, 1, R.string.open_as_video)
        popup.menu.add(0, MENU_OPEN_AS_AUDIO, 2, R.string.open_as_audio)
        popup.menu.add(0, MENU_OPEN_AS_DOCUMENT, 3, R.string.open_as_document)
        // Classic wildcard picker.
        popup.menu.add(0, MENU_OPEN_WITH_OTHER, 4, R.string.open_with_other_app)

        if (hasPref) {
            popup.menu.add(
                0,
                MENU_RESET_PREFERRED,
                5,
                getString(R.string.reset_preferred_app_format, ext)
            )
        }

        popup.setOnMenuItemClickListener { mi: MenuItem ->
            return@setOnMenuItemClickListener when (mi.itemId) {
                MENU_OPEN_AS_PHOTO -> {
                    openFileInCategory(item, CATEGORY_PHOTO)
                    true
                }
                MENU_OPEN_AS_VIDEO -> {
                    openFileInCategory(item, CATEGORY_VIDEO)
                    true
                }
                MENU_OPEN_AS_AUDIO -> {
                    openFileInCategory(item, CATEGORY_AUDIO)
                    true
                }
                MENU_OPEN_AS_DOCUMENT -> {
                    openFileInCategory(item, CATEGORY_DOCUMENT)
                    true
                }
                MENU_OPEN_WITH_OTHER -> {
                    openFileWithPicker(item)
                    true
                }
                MENU_RESET_PREFERRED -> {
                    SettingsManager.clearPreferredApp(this, ext)
                    Toast.makeText(
                        this,
                        getString(R.string.preferred_app_cleared_format, ext),
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
        return true
    }

    // -------------------------------------------------------------------------
    // Opening files
    // -------------------------------------------------------------------------

    /**
     * Opens a file using a remembered app if one is set; otherwise shows
     * the picker dialog so the user can choose.
     */
    private fun openFileWithSavedPreference(item: FileItem) {
        val mime = item.mimeType ?: ANY_MIME
        val ext = item.file.extension.lowercase()
        val uri = createContentUri(item.file)

        val apps = queryAppsForMime(uri, mime)
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
            return
        }

        val savedPackage = SettingsManager.getPreferredApp(this, ext)
        val savedAppStillInstalled = apps.any { it.activityInfo.packageName == savedPackage }

        when {
            savedAppStillInstalled && savedPackage != null -> launchWithPackage(uri, mime, savedPackage)
            apps.size == 1 -> launchWithPackage(uri, mime, apps[0].activityInfo.packageName)
            else -> showAppPicker(item.file, uri, mime, apps, savedPackage)
        }
    }

    /** Called from the long-press menu: bypass any saved preference. */
    private fun openFileWithPicker(item: FileItem) {
        val mime = item.mimeType ?: ANY_MIME
        val uri = createContentUri(item.file)
        val apps = queryAppsForMime(uri, mime)
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
        } else {
            showAppPicker(item.file, uri, mime, apps, null)
        }
    }

    /**
     * Force the file to open under a specific category (image/video/audio/
     * document). Bypasses detection AND any remembered app, since some
     * remembered apps might not handle the forced category at all.
     *
     * If no apps are registered for the category we show a toast instead
     * of an empty dialog.
     */
    private fun openFileInCategory(item: FileItem, categoryMime: String) {
        val uri = createContentUri(item.file)
        val apps = queryAppsForMime(uri, categoryMime)
        if (apps.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.no_apps_in_category) + " (" + categoryMime + ")",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        when {
            apps.size == 1 -> launchWithPackage(uri, categoryMime, apps[0].activityInfo.packageName)
            else -> showAppPicker(item.file, uri, categoryMime, apps, null)
        }
    }

    /** Wraps PackageManager.queryIntentActivities. */
    private fun queryAppsForMime(uri: Uri, mime: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return packageManager.queryIntentActivities(intent, 0)
    }

    /**
     * Builds the FileProvider-backed content URI used to share files with
     * other apps safely (Android 7+ forbids raw file:// URIs across apps).
     */
    private fun createContentUri(file: File): Uri = try {
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    } catch (e: Exception) {
        Uri.fromFile(file)
    }

    /** Launches the chosen app; falls back gracefully if the app died. */
    private fun launchWithPackage(uri: Uri, mime: String, packageName: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            setPackage(packageName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.app_not_available, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Custom Material-styled "Open with..." dialog.
     * Marks the currently-remembered app with a "Default" badge if one is set.
     */
    private fun showAppPicker(
        file: File,
        uri: Uri,
        mime: String,
        apps: List<ResolveInfo>,
        preferredPackage: String?
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_app_picker, null)
        val titleText = view.findViewById<TextView>(R.id.titleText)
        val listView = view.findViewById<ListView>(R.id.appList)
        val alwaysSwitch = view.findViewById<SwitchCompat>(R.id.alwaysUseSwitch)

        titleText.text = getString(R.string.open_with_format, file.name)

        val ext = file.extension.lowercase()
        val savedPref = preferredPackage ?: SettingsManager.getPreferredApp(this, ext)
        alwaysSwitch.isChecked = savedPref != null

        listView.adapter = AppPickerAdapter(this, apps, savedPref)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            val packageName = apps[position].activityInfo.packageName
            if (alwaysSwitch.isChecked) {
                SettingsManager.setPreferredApp(this, ext, packageName)
            }
            launchWithPackage(uri, mime, packageName)
            dialog.dismiss()
        }

        dialog.show()
    }

    // -------------------------------------------------------------------------
    // Settings dialog
    // -------------------------------------------------------------------------

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val sizeGroup = view.findViewById<RadioGroup>(R.id.thumbnailSizeGroup)
        val countText = view.findViewById<TextView>(R.id.preferredAppsCountText)
        val clearButton = view.findViewById<Button>(R.id.clearPreferredAppsButton)

        when (SettingsManager.getThumbnailSize(this)) {
            ThumbnailSize.SMALL -> sizeGroup.check(R.id.sizeSmall)
            ThumbnailSize.LARGE -> sizeGroup.check(R.id.sizeLarge)
            else -> sizeGroup.check(R.id.sizeMedium)
        }

        updatePreferredAppsCount(countText)
        clearButton.isEnabled = SettingsManager.getPreferredAppCount(this) > 0

        // Show app version (read from PackageManager - no BuildConfig needed
        // because we don't enable the buildConfig feature flag).
        val versionText = view.findViewById<TextView>(R.id.versionInfoText)
        versionText.text = try {
            val info = packageManager.getPackageInfo(packageName, 0)
            val versionName = info.versionName
                ?: getString(R.string.version_info_unknown)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION") info.versionCode.toLong()
            }
            getString(R.string.version_info_format, versionName, versionCode)
        } catch (e: Exception) {
            getString(R.string.version_info_unknown)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings)
            .setView(view)
            .setPositiveButton(R.string.cancel, null)
            .create()

        sizeGroup.setOnCheckedChangeListener { _, checkedId ->
            val newSize = when (checkedId) {
                R.id.sizeSmall -> ThumbnailSize.SMALL
                R.id.sizeLarge -> ThumbnailSize.LARGE
                else -> ThumbnailSize.MEDIUM
            }
            if (newSize != SettingsManager.getThumbnailSize(this)) {
                SettingsManager.setThumbnailSize(this, newSize)
                loadFiles()
            }
        }

        clearButton.setOnClickListener {
            SettingsManager.clearAllPreferredApps(this)
            Toast.makeText(this, R.string.preferred_apps_cleared, Toast.LENGTH_SHORT).show()
            updatePreferredAppsCount(countText)
            clearButton.isEnabled = false
        }

        dialog.show()
    }

    private fun updatePreferredAppsCount(textView: TextView) {
        val count = SettingsManager.getPreferredAppCount(this)
        textView.text = if (count == 0) {
            getString(R.string.preferred_apps_none)
        } else {
            getString(R.string.preferred_apps_count_format, count)
        }
    }

    companion object {
        // Long-press popup menu IDs.
        private const val MENU_OPEN_AS_PHOTO = 0
        private const val MENU_OPEN_AS_VIDEO = 1
        private const val MENU_OPEN_AS_AUDIO = 2
        private const val MENU_OPEN_AS_DOCUMENT = 3
        private const val MENU_OPEN_WITH_OTHER = 4
        private const val MENU_RESET_PREFERRED = 5

        // Category wildcards used by the force-open menus.
        private const val CATEGORY_PHOTO = "image/*"
        private const val CATEGORY_VIDEO = "video/*"
        private const val CATEGORY_AUDIO = "audio/*"
        private const val CATEGORY_DOCUMENT = "application/*"

        // Constant for "any file" wildcard.
        private const val ANY_MIME = "*/*"
    }
}

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
import android.widget.CheckBox
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myviewer.databinding.ActivityMainBinding
import java.io.File

/**
 * The single screen of My Viewer.
 *
 * Responsibilities:
 *  - Set up the custom toolbar (path label + buttons + settings gear)
 *  - Request storage permissions
 *  - List files in the current directory
 *  - Open files: use saved preference if available, else show the
 *    "Open with" picker. Remember the user's choice when asked.
 *  - Settings dialog: change thumbnail size + clear remembered apps
 *  - Long-press a file to reset its remembered app
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FileAdapter

    private var currentDir: File = Environment.getExternalStorageDirectory()
    private var showHidden = false

    private val previewLoader by lazy { MediaPreviewLoader(this) }

    /** Permissions required to list files, varies by Android version. */
    private val requiredPermissions: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            loadFiles()
            maybePromptForAllFilesAccess()
        }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

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
        // Returning from Settings or an external app -> refresh.
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

    // -----------------------------------------------------------------------
    // Setup
    // -----------------------------------------------------------------------

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.grant_all_files_access)
                    .setMessage(R.string.grant_all_files_message)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                            )
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        } catch (e: Exception) {
                            startActivity(
                                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            )
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    // -----------------------------------------------------------------------
    // File listing
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Item clicks
    // -----------------------------------------------------------------------

    private fun handleItemClick(item: FileItem) {
        if (item.isDirectory) {
            currentDir = item.file
            loadFiles()
        } else {
            openFileWithSavedPreference(item)
        }
    }

    /**
     * Fired on long-press of any file row.
     * Shows a small popup that lets the user clear the remembered app
     * for this file's extension, or open the file with a different app.
     */
    private fun handleItemLongClick(item: FileItem): Boolean {
        if (item.isDirectory) return false
        // Use the row's actual location for the popup anchor.
        val anchor = binding.recyclerView.findViewById<View>(
            android.R.id.content
        ) ?: return false

        val popup = PopupMenu(this, anchor)
        val ext = item.file.extension.lowercase()
        val hasPref = SettingsManager.getPreferredApp(this, ext) != null

        // "Choose another app" is always present
        popup.menu.add(0, MENU_OPEN_WITH_OTHER, 0, R.string.open_with_other_app)

        // "Reset remembered app" only if there's actually one to reset
        if (hasPref) {
            popup.menu.add(
                0,
                MENU_RESET_PREFERRED,
                0,
                getString(R.string.reset_preferred_app_format, ext)
            )
        }

        popup.setOnMenuItemClickListener { mi: MenuItem ->
            when (mi.itemId) {
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

    // -----------------------------------------------------------------------
    // Opening files (Feature 1)
    // -----------------------------------------------------------------------

    /**
     * Opens a file using a remembered app if one is set; otherwise shows
     * the picker dialog so the user can choose.
     */
    private fun openFileWithSavedPreference(item: FileItem) {
        val mime = item.mimeType ?: "*/*"
        val ext = item.file.extension.lowercase()
        val uri = createContentUri(item.file)

        val resolveIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val apps = packageManager.queryIntentActivities(resolveIntent, 0)

        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
            return
        }

        val savedPackage = SettingsManager.getPreferredApp(this, ext)
        val savedAppStillInstalled = apps.any { it.activityInfo.packageName == savedPackage }

        when {
            // Remembered app exists and is installed -> go straight to it
            savedAppStillInstalled && savedPackage != null -> launchWithPackage(uri, mime, savedPackage)

            // Only one app on the phone can open this file -> use it directly
            apps.size == 1 -> launchWithPackage(uri, mime, apps[0].activityInfo.packageName)

            // Multiple options -> show picker
            else -> showAppPicker(item.file, uri, mime, apps)
        }
    }

    /** Called from the long-press menu: bypass any saved preference. */
    private fun openFileWithPicker(item: FileItem) {
        val mime = item.mimeType ?: "*/*"
        val uri = createContentUri(item.file)
        val resolveIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val apps = packageManager.queryIntentActivities(resolveIntent, 0)
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
        } else {
            showAppPicker(item.file, uri, mime, apps)
        }
    }

    /**
     * Builds the FileProvider-backed content URI used to share files with
     * other apps safely (Android 7+ forbids raw file:// URIs across apps).
     */
    private fun createContentUri(file: File): Uri = try {
        FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
    } catch (e: Exception) {
        Uri.fromFile(file)
    }

    /**
     * Launches the chosen app. If the app is somehow not available any
     * more (uninstalled, broken), we fall back to the picker dialog.
     */
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
     * Shows a custom dialog with the list of apps that can handle
     * this file. If the user ticks "Always use this app" and then
     * picks one, the choice is saved to [SettingsManager].
     */
    private fun showAppPicker(
        file: File,
        uri: Uri,
        mime: String,
        apps: List<ResolveInfo>
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_app_picker, null)
        val titleText = view.findViewById<TextView>(R.id.titleText)
        val listView = view.findViewById<ListView>(R.id.appList)
        val alwaysCheckbox = view.findViewById<CheckBox>(R.id.alwaysUseCheckbox)

        titleText.text = getString(R.string.open_with_format, file.name)
        listView.adapter = AppPickerAdapter(this, apps)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            val packageName = apps[position].activityInfo.packageName
            val ext = file.extension.lowercase()

            if (alwaysCheckbox.isChecked) {
                SettingsManager.setPreferredApp(this, ext, packageName)
            }
            launchWithPackage(uri, mime, packageName)
            dialog.dismiss()
        }

        dialog.show()
    }

    // -----------------------------------------------------------------------
    // Settings dialog (Feature 2)
    // -----------------------------------------------------------------------

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val sizeGroup = view.findViewById<RadioGroup>(R.id.thumbnailSizeGroup)
        val countText = view.findViewById<TextView>(R.id.preferredAppsCountText)
        val clearButton = view.findViewById<Button>(R.id.clearPreferredAppsButton)

        // Initial selection based on current preference
        when (SettingsManager.getThumbnailSize(this)) {
            ThumbnailSize.SMALL -> sizeGroup.check(R.id.sizeSmall)
            ThumbnailSize.LARGE -> sizeGroup.check(R.id.sizeLarge)
            else -> sizeGroup.check(R.id.sizeMedium)
        }

        updatePreferredAppsCount(countText)
        clearButton.isEnabled = SettingsManager.getPreferredAppCount(this) > 0

        val dialog = AlertDialog.Builder(this)
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
                // Re-bind the list so sizes update immediately
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
        private const val MENU_OPEN_WITH_OTHER = 1
        private const val MENU_RESET_PREFERRED = 2
    }
}

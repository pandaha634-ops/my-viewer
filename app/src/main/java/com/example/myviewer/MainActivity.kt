package com.example.myviewer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myviewer.databinding.ActivityMainBinding
import java.io.File

/**
 * The single screen of My Viewer.
 *
 * Responsibilities:
 *  - Set up the custom toolbar (path label + buttons)
 *  - Request storage permissions
 *  - List files in the current directory
 *  - Navigate into folders / open files with other apps
 *  - Toggle hidden files visibility
 *  - Close the app via the X button
 */
class MainActivity : AppCompatActivity() {

    // Auto-generated view binding class (from activity_main.xml)
    private lateinit var binding: ActivityMainBinding

    // The list adapter
    private lateinit var adapter: FileAdapter

    // The folder we're currently browsing
    private var currentDir: File = Environment.getExternalStorageDirectory()

    // Whether to include hidden files (those starting with ".")
    private var showHidden = false

    // The thumbnail loader - created once, shared with the adapter
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

    // Modern permission launcher (replaces the older onRequestPermissionsResult pattern)
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // After the user answers, reload regardless of result
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
        // If the user came back from Settings (e.g. granted a permission),
        // refresh the list so newly accessible files show up.
        loadFiles()
    }

    @Deprecated("Use OnBackPressedDispatcher for new code")
    override fun onBackPressed() {
        val parent = currentDir.parentFile
        val root = Environment.getExternalStorageDirectory()
        // Go up one level unless we're already at the storage root
        if (parent != null && currentDir != root) {
            currentDir = parent
            loadFiles()
        } else {
            super.onBackPressed()
        }
    }

    // -----------------------------------------------------------------------
    // Setup helpers
    // -----------------------------------------------------------------------

    private fun setupRecyclerView() {
        adapter = FileAdapter(
            previewLoader = previewLoader,
            onItemClick = ::handleItemClick
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupToolbarButtons() {
        // Up: go to parent folder
        binding.upButton.setOnClickListener {
            val parent = currentDir.parentFile
            val root = Environment.getExternalStorageDirectory()
            if (parent != null && currentDir != root) {
                currentDir = parent
                loadFiles()
            }
        }

        // Refresh: re-read the current directory
        binding.refreshButton.setOnClickListener { loadFiles() }

        // Show/hide hidden files
        binding.showHiddenButton.setOnClickListener {
            showHidden = !showHidden
            updateShowHiddenIcon()
            loadFiles()
        }

        // Close: finish the activity (X button)
        binding.closeButton.setOnClickListener { finish() }
    }

    // -----------------------------------------------------------------------
    // Permissions
    // -----------------------------------------------------------------------

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

    /**
     * On Android 11+, "All files access" must be granted via Settings.
     * We prompt the user only if they haven't been asked yet.
     */
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
                            // Fallback: open the general "All files access" screen
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
            // Happens when permission is denied
            emptyList()
        }

        adapter.submitList(items)

        // Update toolbar labels
        binding.pathText.text = currentDir.absolutePath
        binding.fileCountText.text = getString(R.string.item_count_format, items.size)

        // Show empty state if there are no items
        binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE

        // Up button: disable when at the storage root
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
            // Navigate into the folder
            currentDir = item.file
            loadFiles()
        } else {
            // Open the file with another app
            openFile(item)
        }
    }

    /**
     * Opens a file using an external app (gallery, video player, PDF reader, etc.)
     *
     * We use [FileProvider] to convert the file:// URI into a content:// URI,
     * which is the safe, modern way to share files with other apps on Android 7+.
     */
    private fun openFile(item: FileItem) {
        val mime = item.mimeType ?: "*/*"

        // Build the content:// URI safely.
        // We use [packageName] (the actual app id at runtime) instead of
        // BuildConfig.APPLICATION_ID — the latter requires enabling
        // `buildConfig = true` in build.gradle, which is off by default in AGP 8+.
        val uri: Uri = try {
            FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                item.file
            )
        } catch (e: Exception) {
            // Fall back to a raw file URI (works on older Android)
            Uri.fromFile(item.file)
        }

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            // createChooser shows a "Open with" dialog if multiple apps can handle it
            startActivity(Intent.createChooser(viewIntent, getString(R.string.open_with)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
        }
    }
}

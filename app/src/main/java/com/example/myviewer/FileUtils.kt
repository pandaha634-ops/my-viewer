package com.example.myviewer

import android.webkit.MimeTypeMap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper functions for working with files and folders.
 *
 * Everything in here is a "pure" utility — no Android UI dependencies,
 * which means it's easy to test and reuse.
 */
object FileUtils {

    /**
     * Returns all files/folders inside [dir], sorted with directories first
     * and then by name (case-insensitive).
     *
     * If [showHidden] is false, files whose name starts with "." are filtered out.
     */
    fun listFiles(dir: File, showHidden: Boolean): List<FileItem> {
        val files = dir.listFiles() ?: return emptyList()

        return files
            .filter { showHidden || !it.name.startsWith(".") }
            .map { file ->
                FileItem(
                    file = file,
                    isDirectory = file.isDirectory,
                    isHidden = file.name.startsWith("."),
                    size = if (file.isDirectory) 0L else file.length(),
                    lastModified = file.lastModified(),
                    mimeType = if (file.isDirectory) null else getMimeType(file)
                )
            }
            .sortedWith(
                compareBy(
                    { !it.isDirectory },                              // directories first
                    { it.file.name.lowercase(Locale.getDefault()) }  // then alphabetically
                )
            )
    }

    /**
     * Returns the MIME type for a file based on its extension.
     *
     * Common media types are mapped explicitly for reliability;
     * everything else falls back to Android's MimeTypeMap.
     */
    fun getMimeType(file: File): String? {
        if (file.isDirectory) return null
        val ext = file.extension.lowercase(Locale.getDefault())
        if (ext.isEmpty()) return null
        return when (ext) {
            // Images
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif" -> "image/*"
            // Videos
            "mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v" -> "video/*"
            // Audio
            "mp3", "wav", "ogg", "flac", "m4a", "aac", "opus" -> "audio/*"
            // Documents
            "pdf" -> "application/pdf"
            "txt", "log", "md" -> "text/plain"
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }
    }

    /** True if the file is an image, video, or audio file. */
    fun isMediaFile(mimeType: String?): Boolean {
        return mimeType != null && (
            mimeType.startsWith("image/") ||
            mimeType.startsWith("video/") ||
            mimeType.startsWith("audio/")
        )
    }

    /**
     * Returns the drawable resource id for the placeholder icon shown
     * before a real preview is loaded.
     */
    fun getIconRes(mimeType: String?, isDirectory: Boolean): Int {
        if (isDirectory) return R.drawable.ic_folder
        if (mimeType == null) return R.drawable.ic_file
        return when {
            mimeType.startsWith("image/") -> R.drawable.ic_image
            mimeType.startsWith("video/") -> R.drawable.ic_video
            mimeType.startsWith("audio/") -> R.drawable.ic_audio
            else -> R.drawable.ic_file
        }
    }

    /** Formats a byte count as "1.2 KB", "3.4 MB", "5.6 GB", etc. */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.1f GB".format(gb)
    }

    /** Formats a timestamp as "2026-08-16 19:24". */
    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

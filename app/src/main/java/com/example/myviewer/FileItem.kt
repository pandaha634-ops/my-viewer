package com.example.myviewer

import java.io.File

/**
 * Represents one row in the file list.
 *
 * @param file       The actual File object on disk
 * @param isDirectory Whether this is a folder (true) or regular file (false)
 * @param isHidden   Whether the file/folder name starts with "." (hidden on Linux/Android)
 * @param size       File size in bytes; 0 for directories
 * @param lastModified When the file/folder was last modified (epoch millis)
 * @param mimeType   Detected MIME type (e.g. "image/png") or null for directories
 */
data class FileItem(
    val file: File,
    val isDirectory: Boolean,
    val isHidden: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String?
)

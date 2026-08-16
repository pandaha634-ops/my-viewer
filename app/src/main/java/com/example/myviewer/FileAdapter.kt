package com.example.myviewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter that displays a list of [FileItem]s.
 *
 * It uses [ListAdapter] + [DiffUtil] so list updates animate smoothly
 * and only changed rows are re-bound (efficient for large folders).
 */
class FileAdapter(
    private val previewLoader: MediaPreviewLoader,
    private val onItemClick: (FileItem) -> Unit
) : ListAdapter<FileItem, FileAdapter.ViewHolder>(DiffCallback()) {

    /** View holder for a single row. */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.fileIcon)
        val name: TextView = view.findViewById(R.id.fileName)
        val info: TextView = view.findViewById(R.id.fileInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.name.text = item.file.name
        holder.info.text = formatInfo(item)

        // Load the thumbnail (or show a placeholder icon)
        previewLoader.loadThumbnail(item.file, holder.icon)

        // Click handler
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    /**
     * Builds the small "1.2 MB • 2026-08-15" line shown under the file name.
     * Folders just show the modification date.
     */
    private fun formatInfo(item: FileItem): String {
        return if (item.isDirectory) {
            FileUtils.formatDate(item.lastModified)
        } else {
            "${FileUtils.formatFileSize(item.size)} • ${FileUtils.formatDate(item.lastModified)}"
        }
    }

    /**
     * Tells DiffUtil how to detect changes between two lists.
     * This is what makes the list animations efficient.
     */
    class DiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.file.absolutePath == newItem.file.absolutePath
        }

        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.file.lastModified() == newItem.file.lastModified() &&
                   oldItem.file.length() == newItem.file.length() &&
                   oldItem.isDirectory == newItem.isDirectory
        }
    }
}

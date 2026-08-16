package com.example.myviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

/**
 * Loads small preview images for media files (images & videos) on a background
 * thread and applies them to an [ImageView] on the main thread.
 *
 * Includes:
 *  - An in-memory [LruCache] so scrolling back to a file is instant
 *  - Down-sampling for image files (avoids decoding huge photos)
 *  - Frame extraction for video files via [MediaMetadataRetriever]
 *  - "Tag" check on the ImageView to avoid setting stale bitmaps
 */
class MediaPreviewLoader(private val context: Context) {

    // Background executor for loading bitmaps off the UI thread
    private val executor = Executors.newFixedThreadPool(2)

    // Posts results back to the UI thread
    private val mainHandler = Handler(Looper.getMainLooper())

    // Holds up to 50 thumbnails (~ a few MB depending on image size)
    private val cache = LruCache<String, Bitmap>(50)

    /**
     * Loads a preview for [file] into [imageView].
     *
     * First, a placeholder icon is shown immediately. Then a background
     * task tries to load a real bitmap; if successful it's swapped in.
     */
    fun loadThumbnail(file: File, imageView: ImageView) {
        val key = file.absolutePath + "_" + file.lastModified()

        // 1. Try cache first - super fast path
        cache.get(key)?.let { cached ->
            imageView.setImageBitmap(cached)
            return
        }

        // 2. Set a placeholder so the UI looks responsive
        val mime = FileUtils.getMimeType(file)
        val placeholder = FileUtils.getIconRes(mime, file.isDirectory)
        imageView.setImageResource(placeholder)

        // The "tag" pattern: if the ImageView is recycled before our task
        // finishes, we shouldn't apply the late result to the wrong file.
        imageView.tag = key

        // 3. Load real bitmap in background
        executor.execute {
            val bitmap = loadBitmap(file, mime)
            mainHandler.post {
                // Only apply if this ImageView is still showing the same file
                if (imageView.tag == key) {
                    if (bitmap != null) {
                        cache.put(key, bitmap)
                        imageView.setImageBitmap(bitmap)
                    } else {
                        // Fallback to the placeholder
                        imageView.setImageResource(placeholder)
                    }
                }
            }
        }
    }

    /** Decides which loader to call based on the file's MIME type. */
    private fun loadBitmap(file: File, mime: String?): Bitmap? {
        if (mime == null) return null
        return when {
            mime.startsWith("image/") -> decodeImage(file)
            mime.startsWith("video/") -> extractVideoFrame(file)
            else -> null
        }
    }

    /**
     * Decodes a downsampled bitmap from the file.
     * Without down-sampling, decoding a 12 MP photo would eat a lot of memory.
     */
    private fun decodeImage(file: File): Bitmap? {
        return try {
            // First pass: read just the dimensions
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOpts)

            // Decide the sample size to roughly match a 96x96 preview
            val sampleSize = calculateInSampleSize(boundsOpts, 96, 96)

            // Second pass: actually decode, with the chosen sample size
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts a single frame from a video file using the system metadata retriever.
     * This is the same API used by Android's Gallery app.
     */
    private fun extractVideoFrame(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            // Grab the first key frame (fast)
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) { /* ignore */ }
        }
    }

    /**
     * Computes the largest power-of-two sample size that still produces
     * an image at least as big as the requested dimensions.
     *
     * Example: a 4000x3000 image with reqWidth=reqHeight=96 → sampleSize=32
     *          (final bitmap is 125x94)
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}

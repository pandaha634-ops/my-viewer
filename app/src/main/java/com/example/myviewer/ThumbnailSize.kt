package com.example.myviewer

/**
 * Three preset thumbnail sizes.
 *
 * Each value represents both:
 *   - the size of the [android.widget.ImageView] in the list row (in dp)
 *   - the size of the bitmap that gets decoded for it (in px)
 *
 * Larger sizes look nicer but use more memory and CPU.
 */
object ThumbnailSize {

    const val SMALL = 0
    const val MEDIUM = 1   // default
    const val LARGE = 2

    /** All the data that varies with the user's choice. */
    data class Config(
        val imageViewDp: Int,   // ImageView width/height in dp
        val bitmapPx: Int       // Source bitmap side length we aim for in px
    )

    /** Returns the [Config] for a given preset. Defaults to MEDIUM. */
    fun getConfig(size: Int): Config = when (size) {
        SMALL -> Config(imageViewDp = 40, bitmapPx = 56)
        MEDIUM -> Config(imageViewDp = 56, bitmapPx = 96)
        LARGE -> Config(imageViewDp = 88, bitmapPx = 160)
        else -> Config(imageViewDp = 56, bitmapPx = 96)
    }

    /** Display name shown in the Settings dialog. */
    fun getDisplayName(size: Int): String = when (size) {
        SMALL -> "Small (40dp)"
        MEDIUM -> "Medium (56dp)"
        LARGE -> "Large (88dp)"
        else -> "Medium (56dp)"
    }

    /** Should we re-decode the bitmap when the size changes? Used as a cache key. */
    fun cacheKey(filePath: String, lastModified: Long, size: Int): String {
        return "${filePath}_${lastModified}_${size}"
    }
}

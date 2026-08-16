package com.example.myviewer

import android.content.Context

/**
 * A thin wrapper around [android.content.SharedPreferences] that holds
 * all the user-tweakable settings in My Viewer.
 *
 * Two things are stored:
 *   1. Per-extension preferred apps — so the second time the user opens
 *      "cat.png" we can launch Gallery directly without showing a picker.
 *   2. The user's preferred thumbnail size (small / medium / large).
 *
 * The keys are namespaced so we can wipe just the preferences without
 * losing the thumbnail-size choice.
 */
object SettingsManager {

    private const val PREFS_NAME = "myviewer_prefs"

    /** All "preferred app for extension X" keys start with this prefix. */
    private const val PREFIX_PREFERRED_APP = "pref_app_"

    /** Single key for the thumbnail size preference. */
    private const val KEY_THUMBNAIL_SIZE = "thumbnail_size"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // -----------------------------------------------------------------------
    // Preferred app per file extension
    // -----------------------------------------------------------------------

    /** Remember that [packageName] should be used for files with extension [ext]. */
    fun setPreferredApp(context: Context, ext: String, packageName: String) {
        prefs(context).edit()
            .putString(PREFIX_PREFERRED_APP + ext.lowercase(), packageName)
            .apply()
    }

    /** Returns the saved package for [ext], or null if none was saved. */
    fun getPreferredApp(context: Context, ext: String): String? {
        return prefs(context).getString(PREFIX_PREFERRED_APP + ext.lowercase(), null)
    }

    /** Clear the saved preference for a single extension. */
    fun clearPreferredApp(context: Context, ext: String) {
        prefs(context).edit()
            .remove(PREFIX_PREFERRED_APP + ext.lowercase())
            .apply()
    }

    /** Wipe every "preferred app" entry, leaving other settings intact. */
    fun clearAllPreferredApps(context: Context) {
        val editor = prefs(context).edit()
        prefs(context).all.keys
            .filter { it.startsWith(PREFIX_PREFERRED_APP) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    /** How many extensions currently have a saved preferred app. */
    fun getPreferredAppCount(context: Context): Int {
        return prefs(context).all.keys.count { it.startsWith(PREFIX_PREFERRED_APP) }
    }

    /** All saved preferences, e.g. {"png" -> "com.gallery", "pdf" -> "com.reader"}. */
    fun getAllPreferredApps(context: Context): Map<String, String> {
        return prefs(context).all
            .filterKeys { it.startsWith(PREFIX_PREFERRED_APP) }
            .mapKeys { it.key.removePrefix(PREFIX_PREFERRED_APP) }
            .mapValues { it.value.toString() }
    }

    // -----------------------------------------------------------------------
    // Thumbnail size
    // -----------------------------------------------------------------------

    /**
     * @return one of [ThumbnailSize.SMALL], [ThumbnailSize.MEDIUM], [ThumbnailSize.LARGE].
     *         Defaults to MEDIUM.
     */
    fun getThumbnailSize(context: Context): Int {
        return prefs(context).getInt(KEY_THUMBNAIL_SIZE, ThumbnailSize.MEDIUM)
    }

    fun setThumbnailSize(context: Context, size: Int) {
        prefs(context).edit().putInt(KEY_THUMBNAIL_SIZE, size).apply()
    }
}

package com.example.myviewer

import android.content.Context
import android.content.pm.ResolveInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

/**
 * Renders a list of [ResolveInfo] entries as "icon + app name" rows,
 * used inside the "Open with..." dialog.
 *
 * Each row shows the app's launcher icon and the user-visible app label.
 *
 * Built on top of [BaseAdapter] instead of [android.widget.ArrayAdapter] so
 * we don't depend on the default TextView-with-`@android:id/text1` layout
 * convention, and so we can dedupe activities that share a package name.
 */
class AppPickerAdapter(
    context: Context,
    rawApps: List<ResolveInfo>
) : BaseAdapter() {

    /** Apps after de-duplication (one entry per package). */
    private val apps: List<ResolveInfo> = dedupeByPackage(rawApps)

    private val pm = context.packageManager

    override fun getCount(): Int = apps.size

    override fun getItem(position: Int): ResolveInfo = apps[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)

        val app = apps[position]

        val iconView = view.findViewById<ImageView>(R.id.appIcon)
        val nameView = view.findViewById<TextView>(R.id.appName)

        iconView.setImageDrawable(app.loadIcon(pm))
        nameView.text = app.loadLabel(pm)

        return view
    }

    /**
     * When a single app has multiple activities (e.g. Google's split Gallery +
     * Photo Editor activities, both registered for VIEW/ images), the user
     * sees the same package listed twice. We keep only the first activity we
     * see per package — that's "the app" from the user's perspective.
     */
    private fun dedupeByPackage(list: List<ResolveInfo>): List<ResolveInfo> {
        val seen = HashSet<String>()
        val out = ArrayList<ResolveInfo>(list.size)
        for (info in list) {
            val pkg = info.activityInfo.packageName
            if (seen.add(pkg)) {
                out += info
            }
        }
        return out
    }
}

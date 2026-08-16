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
 * The currently-preferred app (passed as [preferredPackage]) gets a small
 * "✓ Default" badge below its name so the user can see at a glance which
 * one is remembered.
 *
 * Duplicate activities from the same package are deduped - the user sees
 * each app only once.
 */
class AppPickerAdapter(
    context: Context,
    rawApps: List<ResolveInfo>,
    private val preferredPackage: String? = null
) : BaseAdapter() {

    private val apps: List<ResolveInfo> = dedupeByPackage(rawApps)
    private val pm = context.packageManager

    override fun getCount(): Int = apps.size

    override fun getItem(position: Int): ResolveInfo = apps[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)

        val app = apps[position]
        val pkg = app.activityInfo.packageName

        view.findViewById<ImageView>(R.id.appIcon).setImageDrawable(app.loadIcon(pm))
        view.findViewById<TextView>(R.id.appName).text = app.loadLabel(pm)

        // Show a small "✓ Default" badge under the app name if this is the
        // currently remembered app for this file type.
        val badge = view.findViewById<TextView>(R.id.appPreferredBadge)
        badge.visibility = if (pkg == preferredPackage) View.VISIBLE else View.GONE

        return view
    }

    /**
     * When a single app has multiple activities (e.g. Gallery + Photo Editor
     * both registered for VIEW/images), keep only the first activity we
     * see per package.
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

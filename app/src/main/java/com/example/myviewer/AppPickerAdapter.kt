package com.example.myviewer

import android.content.Context
import android.content.pm.ResolveInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

/**
 * Renders a list of [ResolveInfo] entries as "icon + app name" rows,
 * used inside the "Open with..." dialog.
 *
 * Each row shows the app's launcher icon and the user-visible app label.
 */
class AppPickerAdapter(
    context: Context,
    apps: List<ResolveInfo>
) : ArrayAdapter<ResolveInfo>(context, R.layout.item_app, apps) {

    private val pm = context.packageManager

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_app, parent, false)

        val app = getItem(position)!!

        val iconView = view.findViewById<ImageView>(R.id.appIcon)
        val nameView = view.findViewById<TextView>(R.id.appName)

        iconView.setImageDrawable(app.loadIcon(pm))
        nameView.text = app.loadLabel(pm)

        return view
    }
}

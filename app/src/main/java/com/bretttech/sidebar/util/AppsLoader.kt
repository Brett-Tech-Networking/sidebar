package com.bretttech.sidebar.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.bretttech.sidebar.model.AppEntry

object AppsLoader {

    fun getLaunchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities = pm.queryIntentActivities(mainIntent, 0)
        return activities.map { ri ->
            val appLabel = ri.loadLabel(pm)?.toString() ?: ri.activityInfo.packageName
            val appIcon = ri.activityInfo.loadIcon(pm)
            AppEntry(
                label = appLabel,
                packageName = ri.activityInfo.packageName,
                icon = appIcon
            )
        }.distinctBy { it.packageName }
    }

    fun launchApp(context: Context, packageName: String) {
        val pm: PackageManager = context.packageManager
        val launchIntent: Intent? = pm.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    }
}

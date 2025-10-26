package com.bretttech.sidebar.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.bretttech.sidebar.model.AppEntry

object AppsLoader {

    fun getLaunchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // ✅ Backward + forward compatible query
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        }

        // ✅ Map results safely to AppEntry
        val apps = activities.mapNotNull { ri ->
            try {
                val label = ri.loadLabel(pm)?.toString() ?: ri.activityInfo.packageName
                val icon = ri.activityInfo.loadIcon(pm)
                val pkg = ri.activityInfo.packageName
                AppEntry(
                    label = label,
                    packageName = pkg,
                    icon = icon
                )
            } catch (e: Exception) {
                null
            }
        }

        // ✅ Remove duplicates & sort alphabetically
        return apps
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
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

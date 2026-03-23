package com.bretttech.sidebar.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.bretttech.sidebar.data.SelectedAppsStore
import com.bretttech.sidebar.service.OverlayService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON") {
            if (!Settings.canDrawOverlays(context)) return
            val store = SelectedAppsStore(context)
            if (!store.isSidebarEnabled()) return

            val serviceIntent = Intent(context, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}

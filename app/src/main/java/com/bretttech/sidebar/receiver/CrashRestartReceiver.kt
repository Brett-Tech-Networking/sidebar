package com.bretttech.sidebar.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.bretttech.sidebar.data.SelectedAppsStore
import com.bretttech.sidebar.service.OverlayService
import com.bretttech.sidebar.util.CrashRecovery

class CrashRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RESTART_SERVICE) return
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "unknown"

        if (!Settings.canDrawOverlays(context)) {
            CrashRecovery.logEvent(
                context,
                name = "restart_skipped",
                reason = reason,
                extra = mapOf("cause" to "missing_overlay_permission")
            )
            return
        }

        val store = SelectedAppsStore(context)
        if (!store.isSidebarEnabled()) {
            CrashRecovery.logEvent(
                context,
                name = "restart_skipped",
                reason = reason,
                extra = mapOf("cause" to "sidebar_disabled")
            )
            return
        }

        CrashRecovery.logEvent(
            context,
            name = "restart_receiver_triggered",
            reason = reason
        )

        OverlayService.start(context, OverlayService.ACTION_START)
    }

    companion object {
        const val ACTION_RESTART_SERVICE = "com.bretttech.sidebar.action.RESTART_SERVICE"
        const val EXTRA_REASON = "extra_reason"
    }
}
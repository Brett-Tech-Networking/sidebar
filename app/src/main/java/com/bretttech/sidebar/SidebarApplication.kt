package com.bretttech.sidebar

import android.app.Application
import com.bretttech.sidebar.util.CrashRecovery

class SidebarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashRecovery.install(this)
    }
}
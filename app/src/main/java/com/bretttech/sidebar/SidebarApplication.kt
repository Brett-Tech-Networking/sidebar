package com.bretttech.sidebar

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.bretttech.sidebar.util.CrashRecovery

class SidebarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        CrashRecovery.install(this)
    }
}
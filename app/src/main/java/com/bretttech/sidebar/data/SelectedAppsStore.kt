package com.bretttech.sidebar.data

import android.content.Context
import org.json.JSONArray

class SelectedAppsStore(context: Context) {

    private val prefs = context.getSharedPreferences("sidebar_prefs", Context.MODE_PRIVATE)

    fun getSelectedPackages(): List<String> {
        val json = prefs.getString(KEY_PACKAGES, "[]") ?: "[]"
        val arr = JSONArray(json)
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) out.add(arr.optString(i))
        return out
    }

    fun saveSelectedPackages(packages: List<String>) {
        val arr = JSONArray()
        packages.forEach { arr.put(it) }
        prefs.edit().putString(KEY_PACKAGES, arr.toString()).apply()
    }

    fun getSidebarSide(): String = prefs.getString(KEY_SIDE, "right") ?: "right"

    fun setSidebarSide(side: String) {
        prefs.edit().putString(KEY_SIDE, side).apply()
    }

    fun isSidebarEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setSidebarEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getSidebarYOffset(): Int = prefs.getInt(KEY_Y_OFFSET, 0)

    fun setSidebarYOffset(yOffset: Int) {
        prefs.edit().putInt(KEY_Y_OFFSET, yOffset).apply()
    }

    fun getPanelTheme(): String = prefs.getString(KEY_PANEL_THEME, "dark") ?: "dark"

    fun setPanelTheme(theme: String) {
        prefs.edit().putString(KEY_PANEL_THEME, theme).apply()
    }

    companion object {
        private const val KEY_PACKAGES = "selected_packages"
        private const val KEY_SIDE = "sidebar_side"
        private const val KEY_ENABLED = "sidebar_enabled"
        private const val KEY_Y_OFFSET = "sidebar_y_offset"
        private const val KEY_PANEL_THEME = "panel_theme"
    }
}

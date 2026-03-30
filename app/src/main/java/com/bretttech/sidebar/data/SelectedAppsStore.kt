package com.bretttech.sidebar.data

import android.content.Context
import org.json.JSONArray

class SelectedAppsStore(context: Context) {

    private val prefs = context.getSharedPreferences("sidebar_prefs", Context.MODE_PRIVATE)

    fun getSelectedPackages(): List<String> {
        val json = prefs.getString(KEY_PACKAGES, "[]") ?: "[]"
        val arr = runCatching { JSONArray(json) }.getOrElse { JSONArray() }
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

    fun getCustomPanelColor(): Int = prefs.getInt(KEY_CUSTOM_PANEL_COLOR, DEFAULT_CUSTOM_PANEL_COLOR)

    fun setCustomPanelColor(color: Int) {
        prefs.edit().putInt(KEY_CUSTOM_PANEL_COLOR, color).apply()
    }

    fun getPanelWidthDp(): Int {
        val raw = prefs.getInt(KEY_PANEL_WIDTH_DP, DEFAULT_PANEL_WIDTH_DP)
        return raw.coerceIn(MIN_PANEL_WIDTH_DP, MAX_PANEL_WIDTH_DP)
    }

    fun setPanelWidthDp(widthDp: Int) {
        val clamped = widthDp.coerceIn(MIN_PANEL_WIDTH_DP, MAX_PANEL_WIDTH_DP)
        prefs.edit().putInt(KEY_PANEL_WIDTH_DP, clamped).apply()
    }

    fun getPanelHeightDp(): Int {
        val raw = prefs.getInt(KEY_PANEL_HEIGHT_DP, DEFAULT_PANEL_HEIGHT_DP)
        return raw.coerceIn(MIN_PANEL_HEIGHT_DP, MAX_PANEL_HEIGHT_DP)
    }

    fun setPanelHeightDp(heightDp: Int) {
        val clamped = heightDp.coerceIn(MIN_PANEL_HEIGHT_DP, MAX_PANEL_HEIGHT_DP)
        prefs.edit().putInt(KEY_PANEL_HEIGHT_DP, clamped).apply()
    }

    fun getHandleWidthDp(): Int {
        val raw = prefs.getInt(KEY_HANDLE_WIDTH_DP, DEFAULT_HANDLE_WIDTH_DP)
        return raw.coerceIn(MIN_HANDLE_WIDTH_DP, MAX_HANDLE_WIDTH_DP)
    }

    fun setHandleWidthDp(widthDp: Int) {
        val clamped = widthDp.coerceIn(MIN_HANDLE_WIDTH_DP, MAX_HANDLE_WIDTH_DP)
        prefs.edit().putInt(KEY_HANDLE_WIDTH_DP, clamped).apply()
    }

    fun getHandleHeightDp(): Int {
        val raw = prefs.getInt(KEY_HANDLE_HEIGHT_DP, DEFAULT_HANDLE_HEIGHT_DP)
        return raw.coerceIn(MIN_HANDLE_HEIGHT_DP, MAX_HANDLE_HEIGHT_DP)
    }

    fun setHandleHeightDp(heightDp: Int) {
        val clamped = heightDp.coerceIn(MIN_HANDLE_HEIGHT_DP, MAX_HANDLE_HEIGHT_DP)
        prefs.edit().putInt(KEY_HANDLE_HEIGHT_DP, clamped).apply()
    }

    companion object {
        private const val KEY_PACKAGES = "selected_packages"
        private const val KEY_SIDE = "sidebar_side"
        private const val KEY_ENABLED = "sidebar_enabled"
        private const val KEY_Y_OFFSET = "sidebar_y_offset"
        private const val KEY_PANEL_THEME = "panel_theme"
        private const val KEY_CUSTOM_PANEL_COLOR = "custom_panel_color"
        private const val KEY_PANEL_WIDTH_DP = "panel_width_dp"
        private const val KEY_PANEL_HEIGHT_DP = "panel_height_dp"
        private const val KEY_HANDLE_WIDTH_DP = "handle_width_dp"
        private const val KEY_HANDLE_HEIGHT_DP = "handle_height_dp"

        const val DEFAULT_PANEL_WIDTH_DP = 220
        const val DEFAULT_PANEL_HEIGHT_DP = 520
        const val MIN_PANEL_WIDTH_DP = 140
        const val MAX_PANEL_WIDTH_DP = 360
        const val MIN_PANEL_HEIGHT_DP = 280
        const val MAX_PANEL_HEIGHT_DP = 760

        const val DEFAULT_HANDLE_WIDTH_DP = 14
        const val DEFAULT_HANDLE_HEIGHT_DP = 78
        const val MIN_HANDLE_WIDTH_DP = 8
        const val MAX_HANDLE_WIDTH_DP = 40
        const val MIN_HANDLE_HEIGHT_DP = 40
        const val MAX_HANDLE_HEIGHT_DP = 220

        const val DEFAULT_CUSTOM_PANEL_COLOR = -11177012
    }
}

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

    // LEFT / RIGHT side
    fun getSidebarSide(): String = prefs.getString(KEY_SIDE, "left") ?: "left"

    fun setSidebarSide(side: String) {
        prefs.edit().putString(KEY_SIDE, side).apply()
    }

    companion object {
        private const val KEY_PACKAGES = "selected_packages"
        private const val KEY_SIDE = "sidebar_side"
    }
}

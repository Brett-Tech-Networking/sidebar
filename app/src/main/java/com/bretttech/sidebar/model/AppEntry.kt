package com.bretttech.sidebar.model

import android.graphics.drawable.Drawable

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

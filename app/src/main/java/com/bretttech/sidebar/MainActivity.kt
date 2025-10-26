package com.bretttech.sidebar

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bretttech.sidebar.databinding.ActivityMainBinding
import com.bretttech.sidebar.service.OverlayService
import com.bretttech.sidebar.ui.settings.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPostNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result: granted/denied — we proceed regardless, FGS will still show a silent notif if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Ask for overlay permission if needed
        ensureOverlayPermission()

        // Ask for notifications permission on Android 13+
        if (Build.VERSION.SDK_INT >= 33) {
            requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Start/ensure the overlay service is running
        startOverlayService()
    }

    override fun onResume() {
        super.onResume()
        // In case the user just granted overlay permission in Settings
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun ensureOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            // Send the user to the system overlay permission screen
            val uri = Uri.parse("package:$packageName")
            val permIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
            startActivity(permIntent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(SettingsActivity.intent(this))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

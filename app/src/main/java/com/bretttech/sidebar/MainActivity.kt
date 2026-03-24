package com.bretttech.sidebar

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bretttech.sidebar.databinding.ActivityMainBinding
import com.bretttech.sidebar.service.OverlayService
import com.bretttech.sidebar.ui.theme.settings.PreferencesActivity
import com.bretttech.sidebar.ui.theme.settings.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPostNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val baseLeft = binding.root.paddingLeft
        val baseTop = binding.root.paddingTop
        val baseRight = binding.root.paddingRight
        val baseBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                baseLeft + bars.left,
                baseTop + bars.top,
                baseRight + bars.right,
                baseBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        binding.btnGrantOverlay.setOnClickListener {
            openOverlayPermissionScreen()
        }

        binding.btnOpenSettings.setOnClickListener {
            startActivity(SettingsActivity.intent(this))
        }

        binding.btnPreferences.setOnClickListener {
            startActivity(PreferencesActivity.intent(this))
        }

        binding.btnStartSidebar.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.overlay_required, Toast.LENGTH_SHORT).show()
                openOverlayPermissionScreen()
                return@setOnClickListener
            }
            OverlayService.start(this, OverlayService.ACTION_RELOAD)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        updateUiState()
    }

    override fun onResume() {
        super.onResume()
        updateUiState()
    }

    private fun updateUiState() {
        val hasOverlayPermission = Settings.canDrawOverlays(this)
        binding.tvStatus.text = if (hasOverlayPermission) {
            getString(R.string.status_overlay_granted)
        } else {
            getString(R.string.status_overlay_missing)
        }
        binding.btnStartSidebar.isEnabled = hasOverlayPermission

        if (hasOverlayPermission) {
            OverlayService.start(this, OverlayService.ACTION_START)
        }
    }

    private fun openOverlayPermissionScreen() {
        if (Settings.canDrawOverlays(this)) {
            return
        }
        val uri = Uri.parse("package:$packageName")
        val permIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
        startActivity(permIntent)
    }
}

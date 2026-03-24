package com.bretttech.sidebar

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bretttech.sidebar.databinding.ActivityMainBinding
import com.bretttech.sidebar.service.OverlayService
import com.bretttech.sidebar.ui.theme.settings.PreferencesActivity
import com.bretttech.sidebar.ui.theme.settings.SettingsActivity
import com.bretttech.sidebar.util.CrashRecovery
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

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

        binding.btnCrashLogs.setOnClickListener {
            showCrashLogsDialog()
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
        binding.tvStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (hasOverlayPermission) R.color.home_chip_ok_text else R.color.home_chip_warn_text
            )
        )
        binding.tvStatus.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (hasOverlayPermission) R.color.home_chip_ok_bg else R.color.home_chip_warn_bg
            )
        )
        binding.btnStartSidebar.isEnabled = hasOverlayPermission

        if (hasOverlayPermission) {
            OverlayService.start(this, OverlayService.ACTION_START)
        }
    }

    private fun showCrashLogsDialog() {
        val entries = CrashRecovery.getRecentEvents(this)
        val message = if (entries.isEmpty()) {
            getString(R.string.crash_logs_empty)
        } else {
            entries.joinToString(separator = "\n\n") { raw -> formatCrashEvent(raw) }
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.crash_logs_title))
            .setMessage(message)
            .setNegativeButton(getString(R.string.close), null)
            .setPositiveButton(getString(R.string.clear_logs)) { _, _ ->
                CrashRecovery.clearLogs(this)
            }
            .show()
    }

    private fun formatCrashEvent(raw: String): String {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return raw
        val ts = json.optLong("ts", 0L)
        val timeText = if (ts > 0L) DateFormat.getDateTimeInstance().format(Date(ts)) else "unknown time"
        val name = json.optString("name", "event")
        val reason = json.optString("reason", "")
        val extra = json.optJSONObject("extra")
        val extraText = if (extra == null || extra.length() == 0) {
            ""
        } else {
            val keys = extra.keys().asSequence().toList()
            keys.joinToString(separator = ", ", prefix = "\n") { key ->
                "$key=${extra.optString(key, "")}" }
        }
        return "$timeText\n$name ($reason)$extraText"
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

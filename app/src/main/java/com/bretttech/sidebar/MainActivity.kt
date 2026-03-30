package com.bretttech.sidebar

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
    private var awaitingBatterySettingsResult = false

    private val requestPostNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val requestBatteryOptimization =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (!awaitingBatterySettingsResult) return@registerForActivityResult
            awaitingBatterySettingsResult = false

            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                CrashRecovery.logEvent(this, name = "battery_settings_result", reason = "granted")
                return@registerForActivityResult
            }

            // If user denied OR OEM settings page failed/crashed, open app details as a
            // reliable fallback location for Battery > Allow background usage.
            openAppDetailsBatteryFallback("result_not_ignored")
        }

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

        binding.btnBatteryOptimization.setOnClickListener {
            requestBatteryOptimizationExemption()
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

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(packageName)
        val batteryStepColor = ContextCompat.getColor(
            this,
            if (isIgnoring) R.color.home_chip_ok_text else R.color.home_chip_warn_text
        )
        binding.tvBatteryStep.setTextColor(batteryStepColor)
        binding.tvBatteryStep.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (isIgnoring) R.color.home_chip_ok_bg else R.color.home_chip_warn_bg
            )
        )
        binding.tvBatteryDesc.text = getString(
            if (isIgnoring) R.string.battery_status_ok else R.string.battery_status_warn
        )
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

    private fun requestBatteryOptimizationExemption() {
        val intents = listOf(
            Intent("android.settings.APP_BATTERY_SETTINGS").apply {
                data = Uri.parse("package:$packageName")
                putExtra("package_name", packageName)
                putExtra("app_package", packageName)
                putExtra("android.provider.extra.APP_PACKAGE", packageName)
            },
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
        )

        for ((index, intent) in intents.withIndex()) {
            val launched = runCatching {
                awaitingBatterySettingsResult = true
                requestBatteryOptimization.launch(intent)
                CrashRecovery.logEvent(
                    this,
                    name = "battery_settings_opened",
                    reason = "intent_$index",
                    extra = mapOf("action" to (intent.action ?: ""))
                )
                true
            }.getOrElse { error ->
                awaitingBatterySettingsResult = false
                CrashRecovery.logEvent(
                    this,
                    name = "battery_settings_launch_failed",
                    reason = "intent_$index",
                    extra = mapOf(
                        "action" to (intent.action ?: ""),
                        "error" to (error.message ?: error.javaClass.simpleName)
                    )
                )
                false
            }

            if (launched) return
        }

        openAppDetailsBatteryFallback("all_intents_failed")
    }

    private fun openAppDetailsBatteryFallback(reason: String) {
        val fallbackIntents = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )

        for ((index, intent) in fallbackIntents.withIndex()) {
            val launched = runCatching {
                startActivity(intent)
                CrashRecovery.logEvent(
                    this,
                    name = "battery_settings_fallback_opened",
                    reason = "${reason}_$index",
                    extra = mapOf("action" to (intent.action ?: ""))
                )
                true
            }.getOrElse { error ->
                CrashRecovery.logEvent(
                    this,
                    name = "battery_settings_fallback_failed",
                    reason = "${reason}_$index",
                    extra = mapOf(
                        "action" to (intent.action ?: ""),
                        "error" to (error.message ?: error.javaClass.simpleName)
                    )
                )
                false
            }

            if (launched) return
        }

        Toast.makeText(this, R.string.battery_settings_open_failed, Toast.LENGTH_LONG).show()
    }
}

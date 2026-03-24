package com.bretttech.sidebar.ui.theme.settings

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bretttech.sidebar.R
import com.bretttech.sidebar.data.SelectedAppsStore
import com.bretttech.sidebar.databinding.ActivityPreferencesBinding
import com.bretttech.sidebar.service.OverlayService
import com.bretttech.sidebar.util.PanelStylePalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PreferencesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreferencesBinding
    private lateinit var store: SelectedAppsStore
    private var applyJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreferencesBinding.inflate(layoutInflater)
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

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.sidebar_preferences)

        store = SelectedAppsStore(this)

        binding.switchEnable.isChecked = store.isSidebarEnabled()
        when (store.getSidebarSide()) {
            "right" -> binding.radioSideRight.isChecked = true
            else -> binding.radioSideLeft.isChecked = true
        }
        when (store.getPanelTheme()) {
            "light" -> binding.radioThemeLight.isChecked = true
            "blue" -> binding.radioThemeBlue.isChecked = true
            "custom" -> binding.radioThemeCustom.isChecked = true
            else -> binding.radioThemeDark.isChecked = true
        }
        updateCustomColorUi()

        val initialWidth = store.getHandleWidthDp()
        val initialHeight = store.getHandleHeightDp()

        binding.sliderPanelWidth.valueFrom = SelectedAppsStore.MIN_HANDLE_WIDTH_DP.toFloat()
        binding.sliderPanelWidth.valueTo = SelectedAppsStore.MAX_HANDLE_WIDTH_DP.toFloat()
        binding.sliderPanelWidth.stepSize = 1f
        binding.sliderPanelWidth.value = initialWidth.toFloat()

        binding.sliderPanelHeight.valueFrom = SelectedAppsStore.MIN_HANDLE_HEIGHT_DP.toFloat()
        binding.sliderPanelHeight.valueTo = SelectedAppsStore.MAX_HANDLE_HEIGHT_DP.toFloat()
        binding.sliderPanelHeight.stepSize = 1f
        binding.sliderPanelHeight.value = initialHeight.toFloat()

        updateSizeLabels(initialWidth, initialHeight)

        binding.sliderPanelWidth.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val widthDp = value.toInt()
            val heightDp = binding.sliderPanelHeight.value.toInt()
            store.setHandleWidthDp(widthDp)
            updateSizeLabels(widthDp, heightDp)
            scheduleOverlayReload()
        }

        binding.sliderPanelHeight.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val heightDp = value.toInt()
            val widthDp = binding.sliderPanelWidth.value.toInt()
            store.setHandleHeightDp(heightDp)
            updateSizeLabels(widthDp, heightDp)
            scheduleOverlayReload()
        }

        binding.btnResetHandleSize.setOnClickListener {
            val defaultWidth = SelectedAppsStore.DEFAULT_HANDLE_WIDTH_DP
            val defaultHeight = SelectedAppsStore.DEFAULT_HANDLE_HEIGHT_DP
            store.setHandleWidthDp(defaultWidth)
            store.setHandleHeightDp(defaultHeight)
            binding.sliderPanelWidth.value = defaultWidth.toFloat()
            binding.sliderPanelHeight.value = defaultHeight.toFloat()
            updateSizeLabels(defaultWidth, defaultHeight)
            scheduleOverlayReload()
        }

        binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
            store.setSidebarEnabled(isChecked)
            scheduleOverlayReload()
        }

        binding.radioSideGroup.setOnCheckedChangeListener { _, checkedId ->
            val side = if (checkedId == binding.radioSideRight.id) "right" else "left"
            store.setSidebarSide(side)
            scheduleOverlayReload()
        }

        binding.radioThemeGroup.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                binding.radioThemeLight.id -> "light"
                binding.radioThemeBlue.id -> "blue"
                binding.radioThemeCustom.id -> "custom"
                else -> "dark"
            }
            store.setPanelTheme(theme)
            updateCustomColorUi()
            scheduleOverlayReload()
        }

        binding.btnChooseCustomColor.setOnClickListener {
            showCustomColorPicker()
        }
    }

    private fun updateSizeLabels(widthDp: Int, heightDp: Int) {
        binding.tvWidthValue.text = getString(R.string.panel_width_value, widthDp)
        binding.tvHeightValue.text = getString(R.string.panel_height_value, heightDp)
    }

    private fun updateCustomColorUi() {
        val isCustom = store.getPanelTheme() == "custom"
        binding.customColorRow.visibility = if (isCustom) View.VISIBLE else View.GONE
        val color = store.getCustomPanelColor()
        binding.viewCustomColorPreview.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun showCustomColorPicker() {
        val options = PanelStylePalette.customToneOptions
        val labels = options.map { it.name }.toTypedArray()
        val selectedIndex = options.indexOfFirst { it.color == store.getCustomPanelColor() }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.custom_color_title))
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val selected = options[which]
                store.setCustomPanelColor(selected.color)
                updateCustomColorUi()
                scheduleOverlayReload()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.close), null)
            .show()
    }

    private fun scheduleOverlayReload() {
        applyJob?.cancel()
        applyJob = lifecycleScope.launch {
            delay(200L)
            OverlayService.start(this@PreferencesActivity, OverlayService.ACTION_APPLY_PREFERENCES)
        }
    }

    override fun onDestroy() {
        applyJob?.cancel()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else super.onOptionsItemSelected(item)
    }

    companion object {
        fun intent(ctx: Context): Intent = Intent(ctx, PreferencesActivity::class.java)
    }
}
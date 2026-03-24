package com.bretttech.sidebar.ui.theme.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bretttech.sidebar.R
import com.bretttech.sidebar.data.SelectedAppsStore
import com.bretttech.sidebar.databinding.ActivitySettingsBinding
import com.bretttech.sidebar.model.AppEntry
import com.bretttech.sidebar.service.OverlayService
import com.bretttech.sidebar.ui.adapters.AppSelectAdapter
import com.bretttech.sidebar.ui.theme.settings.PreferencesActivity
import com.bretttech.sidebar.util.AppsLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var store: SelectedAppsStore
    private lateinit var adapter: AppSelectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
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

        store = SelectedAppsStore(this)

        binding.appsRecycler.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
        }

        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            val allApps: List<AppEntry> = withContext(Dispatchers.IO) {
                AppsLoader.getLaunchableApps(this@SettingsActivity)
            }
            val selected = store.getSelectedPackages().toSet()

            adapter = AppSelectAdapter(allApps = allApps, initiallySelected = selected)
            binding.appsRecycler.adapter = this@SettingsActivity.adapter
            binding.btnSave.isEnabled = true
        }

        binding.inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (::adapter.isInitialized) {
                    adapter.filter(s?.toString().orEmpty())
                }
            }
        })

        binding.btnSave.setOnClickListener {
            if (!::adapter.isInitialized) return@setOnClickListener
            store.saveSelectedPackages(adapter.getSelectedPackagesInVisualOrder())
            OverlayService.start(this, OverlayService.ACTION_RELOAD)
            finish()
        }

        binding.btnOpenPreferences.setOnClickListener {
            startActivity(PreferencesActivity.intent(this))
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else super.onOptionsItemSelected(item)
    }

    companion object {
        fun intent(ctx: Context): Intent = Intent(ctx, SettingsActivity::class.java)
    }
}

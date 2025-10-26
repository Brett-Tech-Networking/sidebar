package com.bretttech.sidebar.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bretttech.sidebar.data.SelectedAppsStore
import com.bretttech.sidebar.databinding.ActivitySettingsBinding
import com.bretttech.sidebar.model.AppEntry
import com.bretttech.sidebar.ui.adapters.AppSelectAdapter
import com.bretttech.sidebar.util.AppsLoader

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var store: SelectedAppsStore
    private lateinit var adapter: AppSelectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Sidebar Settings"

        store = SelectedAppsStore(this)

        val allApps: List<AppEntry> = AppsLoader.getLaunchableApps(this)
            .sortedBy { it.label.lowercase() }
        val selected = store.getSelectedPackages().toMutableSet()

        adapter = AppSelectAdapter(
            allApps = allApps,
            initiallySelected = selected
        ) { packageName, isChecked ->
            if (isChecked) selected.add(packageName) else selected.remove(packageName)
        }

        binding.appsRecycler.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = this@SettingsActivity.adapter
        }

        binding.btnSave.setOnClickListener {
            store.saveSelectedPackages(selected.toList())
            finish()
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

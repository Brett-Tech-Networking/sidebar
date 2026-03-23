package com.bretttech.sidebar.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bretttech.sidebar.databinding.ItemAppSelectBinding
import com.bretttech.sidebar.model.AppEntry

class AppSelectAdapter(
    allApps: List<AppEntry>,
    initiallySelected: Set<String>
) : RecyclerView.Adapter<AppSelectAdapter.VH>() {

    private val allApps: List<AppEntry> = allApps.sortedBy { it.label.lowercase() }
    private val displayedApps = this.allApps.toMutableList()
    private val selected: MutableSet<String> = initiallySelected.toMutableSet()

    class VH(val binding: ItemAppSelectBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAppSelectBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = displayedApps[position]
        holder.binding.appIcon.setImageDrawable(item.icon)
        holder.binding.appLabel.text = item.label
        holder.binding.appPackage.text = item.packageName
        holder.binding.checkbox.setOnCheckedChangeListener(null)
        holder.binding.checkbox.isChecked = selected.contains(item.packageName)
        holder.binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selected.add(item.packageName) else selected.remove(item.packageName)
        }
        val rowClick = View.OnClickListener {
            val newState = !holder.binding.checkbox.isChecked
            holder.binding.checkbox.isChecked = newState
        }
        holder.binding.root.setOnClickListener(rowClick)
        holder.binding.appIcon.setOnClickListener(rowClick)
        holder.binding.appLabel.setOnClickListener(rowClick)
    }

    override fun getItemCount(): Int = displayedApps.size

    fun filter(query: String) {
        val normalized = query.trim().lowercase()
        displayedApps.clear()
        if (normalized.isEmpty()) {
            displayedApps.addAll(allApps)
        } else {
            displayedApps.addAll(
                allApps.filter {
                    it.label.lowercase().contains(normalized) ||
                        it.packageName.lowercase().contains(normalized)
                }
            )
        }
        notifyDataSetChanged()
    }

    fun getSelectedPackagesInVisualOrder(): List<String> {
        return allApps
            .map { it.packageName }
            .filter { selected.contains(it) }
    }
}

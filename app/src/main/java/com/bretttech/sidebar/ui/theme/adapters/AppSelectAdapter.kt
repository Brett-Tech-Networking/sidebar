package com.bretttech.sidebar.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bretttech.sidebar.databinding.ItemAppSelectBinding
import com.bretttech.sidebar.model.AppEntry

class AppSelectAdapter(
    private val allApps: List<AppEntry>,
    initiallySelected: Set<String>,
    private val onSelectionChanged: (packageName: String, isChecked: Boolean) -> Unit
) : RecyclerView.Adapter<AppSelectAdapter.VH>() {

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
        val item = allApps[position]
        holder.binding.appIcon.setImageDrawable(item.icon)
        holder.binding.appLabel.text = item.label
        holder.binding.checkbox.setOnCheckedChangeListener(null)
        holder.binding.checkbox.isChecked = selected.contains(item.packageName)
        holder.binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selected.add(item.packageName) else selected.remove(item.packageName)
            onSelectionChanged(item.packageName, isChecked)
        }
        // Tapping row toggles checkbox
        holder.binding.root.setOnClickListener {
            val newState = !holder.binding.checkbox.isChecked
            holder.binding.checkbox.isChecked = newState
        }
    }

    override fun getItemCount(): Int = allApps.size
}

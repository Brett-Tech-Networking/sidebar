package com.bretttech.sidebar.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bretttech.sidebar.databinding.ItemAppBinding
import com.bretttech.sidebar.model.AppEntry
import java.util.Collections

class SidebarAdapter(
    private val onClick: (AppEntry) -> Unit,
    private val onRemove: (AppEntry) -> Unit,
    private val onOrderChanged: (List<String>) -> Unit
) : RecyclerView.Adapter<SidebarAdapter.VH>() {

    private val items = mutableListOf<AppEntry>()

    private var editMode: Boolean = false
    private var labelTextColor: Int = 0xFFFFFFFF.toInt()

    fun setEditMode(enabled: Boolean) {
        if (editMode == enabled) return
        editMode = enabled
        notifyDataSetChanged()
    }

    fun isEditMode(): Boolean = editMode

    fun setLabelTextColor(color: Int) {
        if (labelTextColor == color) return
        labelTextColor = color
        notifyDataSetChanged()
    }

    fun submitList(list: List<AppEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in items.indices || toPosition !in items.indices) return
        if (fromPosition == toPosition) return

        if (fromPosition < toPosition) {
            for (index in fromPosition until toPosition) {
                Collections.swap(items, index, index + 1)
            }
        } else {
            for (index in fromPosition downTo toPosition + 1) {
                Collections.swap(items, index, index - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        persistOrder()
    }

    fun persistOrder() {
        onOrderChanged(items.map { it.packageName })
    }

    class VH(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAppBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.appIcon.setImageDrawable(item.icon)
        holder.binding.appLabel.text = item.label
        holder.binding.appLabel.setTextColor(labelTextColor)
        holder.binding.btnRemove.visibility = if (editMode) View.VISIBLE else View.GONE
        holder.binding.btnRemove.setOnClickListener { onRemove(item) }
        holder.binding.root.setOnClickListener {
            if (editMode) {
                onRemove(item)
            } else {
                onClick(item)
            }
        }
    }

    override fun getItemCount(): Int = items.size
}

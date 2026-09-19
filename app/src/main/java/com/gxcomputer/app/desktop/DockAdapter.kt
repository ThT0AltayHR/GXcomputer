package com.gxcomputer.app.desktop

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gxcomputer.app.R
import com.gxcomputer.app.container.InstalledApp

class DockAdapter(
    private val items: MutableList<InstalledApp>,
    private val onClick: (InstalledApp) -> Unit,
    private val onLongClick: (InstalledApp) -> Unit
) : RecyclerView.Adapter<DockAdapter.VH>() {

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val icon: android.widget.ImageView = view.findViewById(R.id.appIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dock_app, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        holder.icon.setImageResource(R.drawable.ic_app_generic)
        holder.icon.contentDescription = app.name
        holder.itemView.setOnClickListener { onClick(app) }
        holder.itemView.setOnLongClickListener { onLongClick(app); true }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<InstalledApp>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}

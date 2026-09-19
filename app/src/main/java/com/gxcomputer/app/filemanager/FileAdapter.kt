package com.gxcomputer.app.filemanager

import android.content.ClipData
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gxcomputer.app.R
import com.gxcomputer.app.container.StorageHelper
import java.io.File

class FileAdapter(
    private var files: List<File>,
    private val onClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.fileIcon)
        val name: TextView = view.findViewById(R.id.fileName)
        val meta: TextView = view.findViewById(R.id.fileMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = files[position]
        holder.name.text = file.name
        holder.icon.setImageResource(if (file.isDirectory) R.drawable.ic_file_manager else R.drawable.ic_app_generic)
        holder.meta.text = if (file.isDirectory) "Klasör" else StorageHelper.formatBytes(file.length())

        holder.itemView.setOnClickListener { onClick(file) }

        // Sürükle-bırak: dosyayı basılı tutup çöp kutusu bölgesinin üzerine taşımak için
        holder.itemView.setOnLongClickListener { view ->
            val clipData = ClipData.newPlainText("file_path", file.absolutePath)
            val shadow = View.DragShadowBuilder(view)
            view.startDragAndDrop(clipData, shadow, file.absolutePath, 0)
        }
    }

    override fun getItemCount() = files.size

    fun update(newFiles: List<File>) {
        files = newFiles
        notifyDataSetChanged()
    }
}

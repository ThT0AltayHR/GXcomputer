package com.gxcomputer.app.filemanager

import android.content.ClipData
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gxcomputer.app.R
import com.gxcomputer.app.container.ContainerLauncher
import com.gxcomputer.app.container.GraphicsDriverConfig
import com.gxcomputer.app.container.StorageHelper
import java.io.File

class FileManagerActivity : AppCompatActivity() {

    private lateinit var storage: StorageHelper
    private lateinit var trashManager: TrashManager
    private lateinit var launcher: ContainerLauncher
    private lateinit var adapter: FileAdapter
    private lateinit var currentPathText: TextView
    private lateinit var trashDropZone: ImageView
    private lateinit var emptyText: TextView

    private var currentDir: File = File("/")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filemanager)

        storage = StorageHelper(this)
        trashManager = TrashManager(storage)
        launcher = ContainerLauncher(this, storage, GraphicsDriverConfig(this, storage))
        currentDir = storage.rootDir()

        currentPathText = findViewById(R.id.currentPathText)
        trashDropZone = findViewById(R.id.trashDropZone)
        emptyText = findViewById(R.id.emptyText)

        val recycler = findViewById<RecyclerView>(R.id.fileRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = FileAdapter(emptyList()) { file -> onFileClicked(file) }
        recycler.adapter = adapter

        findViewById<ImageView>(R.id.upButton).setOnClickListener { navigateUp() }
        findViewById<ImageView>(R.id.trashViewButton).setOnClickListener { showTrashDialog() }

        setupTrashDropTarget()
        refreshFileList()
    }

    private fun onFileClicked(file: File) {
        if (file.isDirectory) {
            currentDir = file
            refreshFileList()
            return
        }
        val ext = file.extension.lowercase()
        if (ext == "exe" || ext == "msi") {
            android.widget.Toast.makeText(this, "${file.name} başlatılıyor…", android.widget.Toast.LENGTH_SHORT).show()
            Thread {
                try {
                    launcher.launch(file.absolutePath)
                } catch (e: ContainerLauncher.WineNotInstalledException) {
                    runOnUiThread {
                        AlertDialog.Builder(this)
                            .setTitle("Motor henüz eklenmedi")
                            .setMessage("Bu dosyayı çalıştıracak Wine motoru henüz eklenmedi - README.md'de nasıl ekleneceği anlatılıyor.")
                            .setPositiveButton(R.string.action_ok, null)
                            .show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        android.widget.Toast.makeText(this, getString(R.string.setup_error_generic, e.message ?: "bilinmeyen hata"), android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }.apply { isDaemon = true }.start()
        } else {
            android.widget.Toast.makeText(this, file.name, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateUp() {
        val parent = currentDir.parentFile
        if (parent != null && currentDir != storage.rootDir()) {
            currentDir = parent
            refreshFileList()
        }
    }

    private fun refreshFileList() {
        val entries = (currentDir.listFiles()?.toList() ?: emptyList())
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
        adapter.update(entries)
        emptyText.visibility = if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        currentPathText.text = currentDir.path.removePrefix(storage.rootDir().path).ifEmpty { "/GXcomputer" }
        findViewById<ImageView>(R.id.upButton).visibility =
            if (currentDir == storage.rootDir()) android.view.View.INVISIBLE else android.view.View.VISIBLE
    }

    private fun setupTrashDropTarget() {
        trashDropZone.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    (view as ImageView).setImageResource(R.drawable.ic_trash_open)
                    view.animate().scaleX(1.2f).scaleY(1.2f).setDuration(120).start()
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    (view as ImageView).setImageResource(R.drawable.ic_trash)
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    true
                }
                DragEvent.ACTION_DROP -> {
                    (view as ImageView).setImageResource(R.drawable.ic_trash)
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    val path = event.clipData?.getItemAt(0)?.text?.toString()
                    if (path != null) showTrashConfirmDialog(File(path))
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    (view as ImageView).setImageResource(R.drawable.ic_trash)
                    true
                }
                else -> true
            }
        }
    }

    private fun showTrashConfirmDialog(file: File) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_trash_confirm, null)
        view.findViewById<TextView>(R.id.trashDialogTitle).text =
            getString(R.string.trash_confirm_title, file.name)

        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<android.view.View>(R.id.optionTemporary).setOnClickListener {
            trashManager.moveToTrashTemporary(file)
            refreshFileList()
            dialog.dismiss()
        }
        view.findViewById<android.view.View>(R.id.optionPermanent).setOnClickListener {
            trashManager.deletePermanently(file)
            refreshFileList()
            dialog.dismiss()
        }
        view.findViewById<android.view.View>(R.id.optionCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showTrashDialog() {
        val entries = trashManager.listTrash()
        if (entries.isEmpty()) {
            AlertDialog.Builder(this).setTitle(R.string.trash_empty_title).setPositiveButton(R.string.action_close, null).show()
            return
        }
        val names = entries.map { File(it.originalPath).name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.trash_item_count, entries.size))
            .setItems(names) { _, which ->
                val entry = entries[which]
                AlertDialog.Builder(this)
                    .setTitle(names[which])
                    .setPositiveButton(R.string.trash_restore) { _, _ -> trashManager.restore(entry); refreshFileList() }
                    .setNegativeButton(R.string.trash_delete_forever) { _, _ -> trashManager.deleteFromTrashForever(entry); refreshFileList() }
                    .show()
            }
            .setNegativeButton(R.string.trash_empty_all) { _, _ -> trashManager.emptyTrash() }
            .setPositiveButton(R.string.action_close, null)
            .show()
    }
}

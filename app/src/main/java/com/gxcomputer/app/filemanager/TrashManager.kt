package com.gxcomputer.app.filemanager

import com.gxcomputer.app.container.StorageHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class TrashEntry(val id: String, val originalPath: String, val trashedName: String, val deletedAt: Long)

/**
 * Kalıcı ve geçici silme mantığı.
 * "Geçici" silme: dosya çöp kutusu klasörüne taşınır, orijinal konumu kaydedilir,
 * istenildiğinde geri yüklenebilir.
 * "Kalıcı" silme: dosya doğrudan ve geri dönüşsüz olarak silinir.
 */
class TrashManager(private val storage: StorageHelper) {

    private fun metaFile(): File = File(storage.trashDir(), ".trash_meta.json")

    fun moveToTrashTemporary(file: File): Boolean {
        if (!file.exists()) return false
        val trashedName = "${UUID.randomUUID()}_${file.name}"
        val dest = File(storage.trashDir(), trashedName)
        val moved = file.renameTo(dest)
        if (moved) {
            val entries = readEntries().toMutableList()
            entries.add(TrashEntry(UUID.randomUUID().toString(), file.absolutePath, trashedName, System.currentTimeMillis()))
            writeEntries(entries)
        }
        return moved
    }

    fun deletePermanently(file: File): Boolean {
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    fun listTrash(): List<TrashEntry> = readEntries()

    fun restore(entry: TrashEntry): Boolean {
        val trashedFile = File(storage.trashDir(), entry.trashedName)
        if (!trashedFile.exists()) return false
        val original = File(entry.originalPath)
        original.parentFile?.mkdirs()
        val restored = trashedFile.renameTo(original)
        if (restored) {
            writeEntries(readEntries().filterNot { it.id == entry.id })
        }
        return restored
    }

    fun deleteFromTrashForever(entry: TrashEntry) {
        val trashedFile = File(storage.trashDir(), entry.trashedName)
        if (trashedFile.exists()) {
            if (trashedFile.isDirectory) trashedFile.deleteRecursively() else trashedFile.delete()
        }
        writeEntries(readEntries().filterNot { it.id == entry.id })
    }

    fun emptyTrash() {
        readEntries().forEach { entry ->
            val f = File(storage.trashDir(), entry.trashedName)
            if (f.isDirectory) f.deleteRecursively() else f.delete()
        }
        writeEntries(emptyList())
    }

    private fun readEntries(): List<TrashEntry> {
        val file = metaFile()
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TrashEntry(o.getString("id"), o.getString("originalPath"), o.getString("trashedName"), o.getLong("deletedAt"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeEntries(entries: List<TrashEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("originalPath", e.originalPath)
                put("trashedName", e.trashedName)
                put("deletedAt", e.deletedAt)
            })
        }
        metaFile().writeText(arr.toString())
    }
}

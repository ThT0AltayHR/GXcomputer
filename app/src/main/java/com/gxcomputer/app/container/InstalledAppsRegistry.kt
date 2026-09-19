package com.gxcomputer.app.container

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class InstalledApp(val name: String, val exePath: String)

/**
 * Container'a kurulan Windows uygulamalarının basit, yerel bir kaydı.
 * Dock'ta ve "Yüklü Uygulamalar" panelinde gösterilir.
 */
class InstalledAppsRegistry(private val storage: StorageHelper) {

    private fun registryFile(): File = File(storage.containerDir(), "installed_apps.json")

    fun list(): List<InstalledApp> {
        val file = registryFile()
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                InstalledApp(o.getString("name"), o.getString("exePath"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(app: InstalledApp) {
        val current = list().toMutableList()
        current.removeAll { it.exePath == app.exePath }
        current.add(app)
        save(current)
    }

    fun remove(exePath: String) {
        save(list().filterNot { it.exePath == exePath })
    }

    private fun save(apps: List<InstalledApp>) {
        val arr = JSONArray()
        apps.forEach { app ->
            arr.put(JSONObject().apply {
                put("name", app.name)
                put("exePath", app.exePath)
            })
        }
        registryFile().writeText(arr.toString())
    }
}

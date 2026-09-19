package com.gxcomputer.app.terminal

import com.gxcomputer.app.container.GraphicsDriverConfig
import com.gxcomputer.app.container.StorageHelper
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Her komutu container motoru ortam değişkenleriyle (bkz. GraphicsDriverConfig)
 * birlikte çalıştırır - böylece uçbirimden doğrudan box64/wine komutları da
 * doğru ortamda elle çalıştırılabilir.
 */
class ShellSession(
    private val storage: StorageHelper,
    private val gpuConfig: GraphicsDriverConfig
) {
    fun run(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(storage.containerDir())
                .redirectErrorStream(true)
                .apply { environment().putAll(gpuConfig.detectAndBuildProfile().envVars) }
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            process.waitFor()
            output.ifBlank { "" }
        } catch (e: Exception) {
            "hata: ${e.message}"
        }
    }
}

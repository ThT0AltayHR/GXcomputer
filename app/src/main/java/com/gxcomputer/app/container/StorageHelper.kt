package com.gxcomputer.app.container

import android.content.Context
import android.os.StatFs
import com.gxcomputer.app.core.Constants
import java.io.File

/**
 * ÖNEMLİ MİMARİ KARAR:
 * Container'lar sabit boyutlu bir disk imajı (data.img/ext4 loop mount) İÇİNDE DEĞİL,
 * doğrudan cihazın harici depolamasında düz bir klasör ağacı olarak tutulur.
 * Böylece "container 6GB'a ayarlanmış, doldu, her şey çöküyor" sınıfındaki hatalar
 * kökünden ortadan kalkar: container'ın büyüyebileceği üst sınır, cihazda o an
 * fiilen boşta olan alandır - başka hiçbir şey değil.
 */
class StorageHelper(private val context: Context) {

    /** Container'ların ve tüm motor verilerinin kök dizini. */
    fun rootDir(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "gxcomputer").apply { mkdirs() }
    }

    fun containersDir(): File =
        File(rootDir(), Constants.CONTAINERS_DIR).apply { mkdirs() }

    fun containerDir(name: String = Constants.DEFAULT_CONTAINER): File =
        File(containersDir(), name).apply { mkdirs() }

    fun componentsCacheDir(): File =
        File(rootDir(), "engine").apply { mkdirs() }

    fun trashDir(): File =
        File(rootDir(), Constants.TRASH_DIR).apply { mkdirs() }

    /** Cihazda fiilen boş olan alan (bayt). Hiçbir yapay tavan uygulanmaz. */
    fun freeSpaceBytes(): Long {
        val stat = StatFs(rootDir().path)
        return stat.availableBytes
    }

    fun totalSpaceBytes(): Long {
        val stat = StatFs(rootDir().path)
        return stat.totalBytes
    }

    fun freeSpaceFormatted(): String = formatBytes(freeSpaceBytes())

    fun isLowOnSpace(): Boolean =
        freeSpaceBytes() < Constants.MIN_FREE_SPACE_WARNING_MB * 1024 * 1024

    /** Bir kurulumun sığıp sığmayacağını, cihazın gerçek boş alanına göre kontrol eder. */
    fun canFit(sizeBytes: Long): Boolean {
        // %5 pay bırak: dosya sistemi/metadata için, katı bir üst sınır değil, güvenlik payı
        val safetyMargin = (freeSpaceBytes() * 0.05).toLong()
        return sizeBytes < (freeSpaceBytes() - safetyMargin)
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var value = bytes.toDouble()
            var unitIndex = 0
            while (value >= 1024 && unitIndex < units.size - 1) {
                value /= 1024
                unitIndex++
            }
            return "%.1f %s".format(value, units[unitIndex])
        }
    }
}

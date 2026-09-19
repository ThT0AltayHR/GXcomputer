package com.gxcomputer.app.container

import android.content.Context
import com.github.luben.zstd.ZstdInputStream
import com.gxcomputer.app.core.Constants
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * .tzst dosyaları = Zstandard ile sıkıştırılmış tar arşivleri.
 * app/src/main/assets/components/ altında GERÇEK box64/dxvk/turnip/vkd3d/wined3d
 * paketleri bulunuyor (orijinal winlator kaynak paketinden alındı) - içerikleri
 * bu proje hazırlanırken TEK TEK AÇILIP DOĞRULANDI:
 *
 *   - box64-0.3.7.tzst    -> usr/local/bin/box64 (tek, çalıştırılabilir ARM64 ikili dosya)
 *   - dxvk-2.6.1.tzst     -> system32/ ve syswow64/ altında Windows DLL'leri (d3d9/d3d11/dxgi...)
 *   - vkd3d-2.14.1.tzst   -> system32/ ve syswow64/ altında d3d12.dll, d3d12core.dll
 *   - wined3d-10.0.tzst   -> system32/ ve syswow64/ altında d3d8/d3d9/d3d10/d3d11/ddraw/dxgi/wined3d.dll
 *   - turnip-26.0.3.tzst  -> usr/lib/libvulkan_freedreno.so + usr/share/vulkan/icd.d/freedreno_icd.aarch64.json
 *
 * ÖNEMLİ VE DÜRÜST NOT: dxvk/vkd3d/wined3d, Wine'ın KENDİSİ DEĞİL, Wine prefix'inin
 * system32/syswow64 klasörlerine kopyalanacak DLL "override" paketleridir - bunlar tek
 * başlarına hiçbir şey çalıştırmaz, Wine çalışırken onları yükler. Bu beş paketin HİÇBİRİ
 * Wine'ın asıl çalıştırılabilir motorunu (wine64 + çekirdek Windows API .so uygulamaları)
 * içermiyor; winlator-11_2_0.zip içinde de bu motor mevcut değildi. Bu, "container gerçekten
 * bir .exe çalıştırabilsin" hedefindeki TEK eksik dış bileşen: ARM64 için derlenmiş bir Wine
 * build'i applyDllOverridesToContainer() ile aynı container'a yerleştirilmeli
 * (bkz. README.md -> "Bilinen eksik: Wine motoru").
 *
 * NOT (Android platform kısıtı): Android 10+ üzerinde uygulamanın kendi özel
 * depolamasına açılan dosyalar varsayılan olarak "noexec" bayrağı taşıyan bir
 * bölümde bulunduğundan doğrudan çalıştırılamaz. Bunu aşmak için asıl box64
 * ikili dosyası derleme sırasında jniLibs/arm64-v8a altına "libbox64.so" adıyla
 * yerleştirilir (bkz. app/build.gradle -> useLegacyPackaging); sistem bu dosyayı
 * paket kurulumunda otomatik olarak çalıştırılabilir izinli bir dizine çıkarır.
 * ComponentInstaller burada sadece DESTEK dosyalarını (DXVK/VKD3D/wined3d DLL'leri,
 * Turnip .so'su) container içine açar - bunlar Wine tarafından İÇERİDEN yüklenir,
 * Android tarafından doğrudan çalıştırılmaz, bu yüzden noexec kısıtı onları etkilemez.
 */
class ComponentInstaller(
    private val context: Context,
    private val storage: StorageHelper
) {

    sealed class Progress {
        data class Extracting(val componentName: String, val index: Int, val total: Int) : Progress()
        data class Done(val installedDir: File) : Progress()
        data class Error(val message: String) : Progress()
    }

    fun isInstalled(): Boolean {
        val marker = File(storage.componentsCacheDir(), ".installed")
        return marker.exists()
    }

    /**
     * Tüm motor bileşenlerini assets'ten container motor dizinine açar.
     * Bloklamalı (background thread'den çağrılmalı) - callback ile ilerleme bildirir.
     */
    fun installAll(onProgress: (Progress) -> Unit) {
        val targetDir = storage.componentsCacheDir()
        val components = Constants.ENGINE_COMPONENTS

        // Depolama kontrolü: assets içindeki tahmini açılmış boyutun ~4 katı pay bırak,
        // ama gerçek üst sınır her zaman cihazın o anki boş alanıdır (bkz. StorageHelper).
        if (!storage.canFit(200L * 1024 * 1024)) {
            onProgress(Progress.Error("Yetersiz depolama alanı"))
            return
        }

        components.forEachIndexed { index, fileName ->
            onProgress(Progress.Extracting(fileName.substringBefore(".tzst"), index + 1, components.size))
            try {
                extractTzstAsset(fileName, targetDir)
            } catch (e: Exception) {
                onProgress(Progress.Error("${fileName}: ${e.message}"))
                return
            }
        }

        File(targetDir, ".installed").writeText(System.currentTimeMillis().toString())
        onProgress(Progress.Done(targetDir))
    }

    /**
     * DXVK/VKD3D/wined3d DLL'lerini, açıldıkları motor önbelleğinden container'ın
     * Wine prefix'i içindeki drive_c/windows/{system32,syswow64} klasörlerine kopyalar.
     * Aynı isimli bir DLL birden fazla pakette varsa (ör. d3d9.dll hem dxvk'de hem
     * wined3d'de var), aşağıdaki dllPackages listesindeki SIRA önceliği belirler:
     * son kopyalanan üstüne yazar. dxvk/vkd3d modern GPU'larda wined3d'den daha
     * performanslı olduğu için listede en sona, yani "kazanan" konuma konuldu.
     */
    fun applyDllOverridesToContainer(containerName: String = Constants.DEFAULT_CONTAINER) {
        val engineDir = storage.componentsCacheDir()
        val prefixWindows = File(storage.containerDir(containerName), "drive_c/windows")
        val dllPackages = listOf("wined3d", "vkd3d", "dxvk") // sondaki, aynı isimli DLL'lerde önceliklidir

        dllPackages.forEach { pkg ->
            listOf("system32", "syswow64").forEach { arch ->
                val src = File(engineDir, "$pkg/$arch")
                if (!src.exists()) return@forEach
                val dest = File(prefixWindows, arch).apply { mkdirs() }
                src.listFiles()?.forEach { dll ->
                    // Yalnızca gerçek dosyaları kopyala - beklenmedik bir alt klasörle
                    // karşılaşılırsa copyTo() istisna fırlatıp kurulumu çökertmesin.
                    if (dll.isFile) {
                        dll.copyTo(File(dest, dll.name), overwrite = true)
                    }
                }
            }
        }
    }

    private fun extractTzstAsset(assetFileName: String, targetDir: File) {
        val componentName = assetFileName.substringBefore("-")
        val outputSubdir = File(targetDir, componentName).apply { mkdirs() }

        context.assets.open("${Constants.COMPONENTS_ASSET_DIR}/$assetFileName").use { rawIn ->
            ZstdInputStream(rawIn).use { zstdIn ->
                TarArchiveInputStream(zstdIn).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    while (entry != null) {
                        val outFile = File(outputSubdir, entry.name)
                        // Zip/Tar Slip korumasi: hedefin gercekten outputSubdir altinda kaldigini dogrula
                        if (!outFile.canonicalPath.startsWith(outputSubdir.canonicalPath + File.separator)) {
                            throw SecurityException("Güvensiz arşiv yolu: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out -> tarIn.copyTo(out) }
                            if (entry.mode and 0b001001001 != 0) {
                                outFile.setExecutable(true, false)
                            }
                        }
                        entry = tarIn.nextTarEntry
                    }
                }
            }
        }
    }

    /** Bir container'a hangi motor sürümlerinin bağlı olduğunu döner (arayüzde göstermek için). */
    fun installedComponentSummary(): List<String> =
        Constants.ENGINE_COMPONENTS.map { it.removeSuffix(".tzst") }
}

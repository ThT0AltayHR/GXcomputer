package com.gxcomputer.app.container

import android.content.Context
import com.github.luben.zstd.ZstdInputStream
import com.gxcomputer.app.core.Constants
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * GE-proton-11_0-3-arm64ec.wcp GERÇEK, doğrulanmış bir Android/Bionic Wine build'i:
 *   - bin/wine, bin/wineserver, bin/wine-preloader -> ARM64 ELF, interpreter /system/bin/linker64
 *     (box64 denemesindeki gibi kırık, başka bir paketin veri dizinine sabitlenmiş bir yol DEĞİL -
 *     /system/bin/linker64 her Android cihazında var olan, erişilebilir sistem bağlayıcısı)
 *   - lib/ -> Turnip/Freedreno Vulkan sürücüsü + Mesa Gallium dahil, hepsi ARM64
 *   - lib/wine/aarch64-windows/ ve lib/wine/i386-windows/ -> Wine'ın kendi DLL'leri
 *   - prefixPack.txz -> hazır, boş bir Wine prefix şablonu (her yeni container bundan başlar)
 *   - profile.json -> paket meta verisi (bkz. wine.binPath/libPath/prefixPack)
 *
 * Wine artık box64 ile SARMALANMIYOR - doğrudan, kendi başına, ARM64 üzerinde NATIF çalışıyor.
 * box64/dxvk/turnip/vkd3d/wined3d paketleri, bu Wine'ın ÇALIŞTIRACAĞI x86/x64 Windows
 * UYGULAMALARININ kendi kodunu çevirmek için hâlâ gerekli olabilir (Wine'ın kendisi için değil).
 */
class WineEngineInstaller(
    private val context: Context,
    private val storage: StorageHelper
) {
    fun engineDir(): File = File(storage.componentsCacheDir(), "wine").apply { mkdirs() }
    fun binDir(): File = File(engineDir(), "bin")
    fun libDir(): File = File(engineDir(), "lib")
    fun prefixTemplateDir(): File = File(engineDir(), "prefix_template")

    fun wineBinary(): File = File(binDir(), "wine")
    fun winePreloaderBinary(): File = File(binDir(), "wine-preloader")
    fun wineserverBinary(): File = File(binDir(), "wineserver")
    /** Gerçek Bionic box64 (The412Banner/Nightlies) - jniLibs'teki eski, kırık yol sorunlu olanın yerini alır. */
    fun box64BionicBinary(): File = File(binDir(), "box64")
    fun fexUnixLibDir(): File = File(libDir(), "wine/aarch64-unix")

    fun isInstalled(): Boolean = File(engineDir(), ".installed").exists()

    /** .wcp'yi (zstd+tar) açar, prefixPack.txz'i (xz+tar) ayrıca çözüp şablon olarak saklar. */
    fun install(onStatus: (String) -> Unit) {
        val dir = engineDir()
        if (!storage.canFit(2_200L * 1024 * 1024)) {
            throw IllegalStateException("Wine motoru için yetersiz depolama alanı (~2,2GB gerekli)")
        }

        onStatus("Wine motoru açılıyor")
        context.assets.open(Constants.WINE_ENGINE_ASSET).use { rawIn ->
            ZstdInputStream(rawIn).use { zstdIn ->
                TarArchiveInputStream(zstdIn).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    while (entry != null) {
                        if (entry.name == "prefixPack.txz") {
                            // Bunu ayrı işleyeceğiz - önce ham baytları çıkar
                            val tmp = File(dir, "prefixPack.txz")
                            FileOutputStream(tmp).use { out -> tarIn.copyTo(out) }
                        } else if (!entry.isDirectory) {
                            val outFile = File(dir, entry.name)
                            if (!outFile.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
                                throw SecurityException("Güvensiz arşiv yolu: ${entry.name}")
                            }
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out -> tarIn.copyTo(out) }
                            if (entry.mode and 0b001001001 != 0) outFile.setExecutable(true, false)
                        }
                        entry = tarIn.nextTarEntry
                    }
                }
            }
        }

        onStatus("Wine prefix şablonu hazırlanıyor")
        extractPrefixTemplate(File(dir, "prefixPack.txz"))
        File(dir, "prefixPack.txz").delete() // şablona açıldı, ham .txz artık gerekmiyor

        onStatus("FEX-Emu ve Box64 (Bionic) kuruluyor")
        installFexAndBox64()

        File(dir, ".installed").writeText(Constants.WINE_ENGINE_VERSION_NAME)
    }

    /**
     * FEX-Emu (.so unixlib + .dll) ve gerçek Bionic box64 - her ikisi de The412Banner/Nightlies'ten,
     * profile.json'daki source/target eşlemesine birebir uyularak yerleştirilir:
     *   box64            -> ${bindir}          (motorun bin/ klasörü)
     *   *fex.so          -> ${libdir}/wine/aarch64-unix  (motorun lib/ klasörü)
     *   *fex.dll         -> ${system32}         (prefix ŞABLONUNUN system32'si, her yeni
     *                                            container bunu otomatik miras alır)
     */
    private fun installFexAndBox64() {
        val system32Template = File(prefixTemplateDir(), ".wine/drive_c/windows/system32").apply { mkdirs() }
        fexUnixLibDir().mkdirs()
        binDir().mkdirs()

        // box64 (Bionic)
        context.assets.open(Constants.BOX64_BIONIC_ASSET).use { rawIn ->
            XZInputStream(rawIn).use { xzIn ->
                TarArchiveInputStream(xzIn).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    while (entry != null) {
                        if (entry.name.endsWith("/box64")) {
                            FileOutputStream(box64BionicBinary()).use { out -> tarIn.copyTo(out) }
                            box64BionicBinary().setExecutable(true, false)
                        }
                        entry = tarIn.nextTarEntry
                    }
                }
            }
        }

        // FEX-Emu: .so -> motor lib/, .dll -> prefix şablonunun system32'si
        context.assets.open(Constants.FEX_ASSET).use { rawIn ->
            XZInputStream(rawIn).use { xzIn ->
                TarArchiveInputStream(xzIn).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    while (entry != null) {
                        val fileName = File(entry.name).name
                        when {
                            entry.name.endsWith(".so") -> {
                                FileOutputStream(File(fexUnixLibDir(), fileName)).use { out -> tarIn.copyTo(out) }
                            }
                            entry.name.endsWith(".dll") -> {
                                FileOutputStream(File(system32Template, fileName)).use { out -> tarIn.copyTo(out) }
                            }
                        }
                        entry = tarIn.nextTarEntry
                    }
                }
            }
        }
    }

    private fun extractPrefixTemplate(txzFile: File) {
        val destDir = prefixTemplateDir().apply { mkdirs() }
        txzFile.inputStream().use { rawIn ->
            XZInputStream(rawIn).use { xzIn ->
                TarArchiveInputStream(xzIn).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    while (entry != null) {
                        val outFile = File(destDir, entry.name)
                        if (!outFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                            throw SecurityException("Güvensiz arşiv yolu: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else if (entry.isSymbolicLink) {
                            // Wine prefix şablonları sembolik bağlantılar içerir (ör. dosdevices/c:).
                            // Hedef Android depolamasında genelde desteklenir; olmazsa sessizce atla.
                            try {
                                java.nio.file.Files.createSymbolicLink(outFile.toPath(), File(entry.linkName).toPath())
                            } catch (e: Exception) { /* atla - kritik değil, wineboot eksikleri tamamlar */ }
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out -> tarIn.copyTo(out) }
                        }
                        entry = tarIn.nextTarEntry
                    }
                }
            }
        }
    }

    /** Taze bir container için hazır prefix şablonunu kopyalar (sıfırdan wineboot'tan çok daha hızlı/güvenilir). */
    fun bootstrapContainerFromTemplate(containerDir: File) {
        val template = File(prefixTemplateDir(), ".wine")
        if (!template.exists()) return
        val prefixDir = containerDir
        template.copyRecursively(prefixDir, overwrite = false)
    }
}

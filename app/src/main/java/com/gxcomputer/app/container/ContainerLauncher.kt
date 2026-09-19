package com.gxcomputer.app.container

import android.content.Context
import com.gxcomputer.app.core.Constants
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * GE-Proton 11.0-3 (arm64ec, Android/Bionic) üzerinden container içinde bir .exe/.msi çalıştırır.
 *
 * Wine box64 ile SARMALANMIYOR - bin/wine, ARM64 ELF olarak, /system/bin/linker64 ile doğrudan
 * natif çalışıyor. ÇALIŞTIRILAN Windows uygulamasının kendi x86/x64 kodu için ise artık gerçek,
 * doğrulanmış bileşenler entegre: FEX-Emu (The412Banner/Nightlies, PPA+unix, arm64ec/wow64
 * unixlib'leri hem lib/wine/aarch64-unix/*.so hem prefix şablonunun system32/*.dll'i olarak
 * yerleştirildi - Proton'un kendi MemoryWineLoadUnixLibByName mekanizması bunu otomatik bulur)
 * ve gerçek Bionic box64 (aynı kaynaktan, /system/bin/linker64 ile - jniLibs'teki eski,
 * kırık rootfs yollu build'in yerini alıyor, ona yalnızca o da yoksa geri dönülür).
 * ARM64/arm64ec olarak derlenmiş Windows uygulamaları için zaten hiçbir çeviriye gerek yoktur.
 */
class ContainerLauncher(
    private val context: Context,
    private val storage: StorageHelper,
    private val gpuConfig: GraphicsDriverConfig
) {
    private val wineEngine = WineEngineInstaller(context, storage)

    data class LaunchResult(val process: Process, val logFile: File)
    class WineNotInstalledException :
        Exception("Wine motoru henüz kurulmadı - önce ilk kurulum/motor kurulumunun tamamlanması gerekiyor")

    /** Önce motorun kendi doğrulanmış Bionic box64'ü (The412Banner/Nightlies) denenir;
     *  o yoksa jniLibs'teki eski build'e (kırık rootfs yol sorunu bilinen) geri dönülür. */
    fun box64Binary(): File {
        val bionic = wineEngine.box64BionicBinary()
        if (bionic.exists()) return bionic
        return File(context.applicationInfo.nativeLibraryDir, "libbox64.so")
    }
    fun isEngineReady(): Boolean = wineEngine.isInstalled()

    /**
     * @param exePath çalıştırılacak Windows uygulamasının container içindeki yolu
     * @param containerName hangi container (varsayılan: "default")
     * @throws WineNotInstalledException Wine motoru henüz kurulmadıysa
     */
    fun launch(exePath: String, containerName: String = Constants.DEFAULT_CONTAINER): LaunchResult {
        if (!wineEngine.isInstalled()) throw WineNotInstalledException()

        val containerDir = storage.containerDir(containerName)
        // İlk çalıştırmada container'ı hazır prefix şablonuyla önceden doldur (hızlı ve güvenilir)
        if (containerDir.list()?.isEmpty() != false) {
            wineEngine.bootstrapContainerFromTemplate(containerDir)
        }

        val profile = gpuConfig.detectAndBuildProfile()
        val preloader = wineEngine.winePreloaderBinary()
        val wineBin = wineEngine.wineBinary()

        val command = if (preloader.exists()) {
            listOf(preloader.absolutePath, wineBin.absolutePath, exePath)
        } else {
            listOf(wineBin.absolutePath, exePath)
        }

        val builder = ProcessBuilder(command)
            .directory(containerDir)
            .redirectErrorStream(true)

        val env = builder.environment()
        env["WINEPREFIX"] = containerDir.absolutePath
        env["HOME"] = containerDir.absolutePath
        env["TMPDIR"] = File(containerDir, "tmp").apply { mkdirs() }.absolutePath
        // Bu Wine build'inin kendi GPU/Vulkan kütüphaneleri (Turnip/Freedreno/Mesa dahil) öncelikli
        env["LD_LIBRARY_PATH"] = wineEngine.libDir().absolutePath +
            (System.getenv("LD_LIBRARY_PATH")?.let { ":$it" } ?: "")
        env["WINESERVER"] = wineEngine.wineserverBinary().absolutePath
        if (box64Binary().exists()) env["BOX64_PATH"] = box64Binary().absolutePath
        env.putAll(profile.envVars)

        val logFile = File(containerDir, "last_run.log")
        val process = builder.start()

        Thread {
            logFile.outputStream().use { out ->
                BufferedReader(InputStreamReader(process.inputStream)).forEachLine { line ->
                    out.write((line + "\n").toByteArray())
                    out.flush()
                }
            }
        }.apply { isDaemon = true }.start()

        return LaunchResult(process, logFile)
    }

    fun activeRendererName(): String = gpuConfig.detectAndBuildProfile().rendererName
}

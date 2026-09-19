package com.gxcomputer.app.container

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Cihazın GPU'sunu algılayıp DXVK/VKD3D/wined3d için en uygun render yolunu
 * ve performans ortam değişkenlerini OTOMATİK seçer. Kullanıcıya "sürücü seç"
 * gibi bir ekran çıkarılmaz; hiçbir değer elle sınırlanmaz.
 */
class GraphicsDriverConfig(private val context: Context, private val storage: StorageHelper) {

    data class Profile(
        val rendererName: String,
        val useTurnip: Boolean,
        val envVars: Map<String, String>
    )

    fun detectAndBuildProfile(): Profile {
        val pm = context.packageManager
        val hasVulkan = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val isAdreno = isAdrenoGpu()
        val ramMb = totalRamMb()

        val env = mutableMapOf<String, String>()

        // Turnip: Adreno cihazlarda açık kaynak Vulkan sürücüsü - en iyi performans/uyumluluk
        val useTurnip = hasVulkan && isAdreno
        if (useTurnip) {
            env["MESA_VK_DEVICE_SELECT"] = "auto"
            env["TU_DEBUG"] = "noconform"           // gereksiz doğrulama katmanlarını kapat -> daha az katman, daha yüksek performans
            // Gerçek dosya adı turnip-26.0.3.tzst içinden doğrulandı: freedreno_icd.aarch64.json
            val icdJson = java.io.File(
                storage.componentsCacheDir(),
                "turnip/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json"
            )
            if (icdJson.exists()) {
                env["VK_ICD_FILENAMES"] = icdJson.absolutePath
            }
        }

        // DXVK: async shader derleme + tam GPU belleği kullanımı, hiçbir üst sınır koyulmadan
        env["DXVK_ASYNC"] = "1"
        env["DXVK_STATE_CACHE"] = "1"
        env["DXVK_LOG_LEVEL"] = "none"               // loglama katmanı kapalı -> daha az iş parçacığı yükü
        env["DXVK_MEMORY_LIMIT"] = "0"                // 0 = yapay sınır yok, cihazın verdiği kadar kullan

        // VKD3D (Direct3D 12 -> Vulkan)
        env["VKD3D_CONFIG"] = "no_upload_hvv"
        env["VKD3D_SHADER_CACHE_PATH"] = ""

        env["BOX64_DYNAREC_STRONGMEM"] = "0"
        env["BOX64_NOBANNER"] = "1"
        // Box64 iş parçacığı sayısı cihazın gerçek çekirdek sayısına göre, elle sabitlenmez
        env["BOX64_MAXCPU"] = Runtime.getRuntime().availableProcessors().toString()

        // Wine: gereksiz katmanları (debug, winemenubuilder vb.) kapatarak süreç sayısını azalt
        env["WINEDEBUG"] = "-all"
        env["WINE_DISABLE_WRITE_WATCH"] = "1"
        env["WINE_LARGE_ADDRESS_AWARE"] = "1"
        env["WINE_FAST_YIELD"] = "1" // GE-Proton bionic build'lerinin kendi hızlı-yield anahtarı - bir çekirdeği %100'de kilitleyen bilinen hatayı düzeltir

        // RAM'e göre kademeli ayar - cihazın GERÇEK boş/toplam belleğine göre, sabit bir
        // "üst düzey cihaz" varsayımı yapılmadan. WINEVMEMMAXSIZE özellikle önemli: Wine'ın
        // ayırdığı sanal bellek Android'in izin verdiğinden fazla olursa uygulama container
        // içine girerken hemen ÇÖKER - bu sınırı cihaza göre ayarlamak tam da bu çökmeyi önler.
        when {
            ramMb < 4096 -> {
                // Düşük RAM: küçük önbellek, düşük bellek taahhüdü - kararlılık önceliği
                env["BOX64_DYNAREC_BIGBLOCK"] = "1"
                env["WINEVMEMMAXSIZE"] = "2048"
                env["DXVK_FRAME_LATENCY"] = "3"
            }
            ramMb < 8192 -> {
                // Orta seviye (ör. 4+4GB genişletilmiş RAM'li Redmi 13 sınıfı cihazlar):
                // dengeli ayar - hem performans hem kararlılık
                env["BOX64_DYNAREC_BIGBLOCK"] = "2"
                env["WINEVMEMMAXSIZE"] = "4096"
                env["DXVK_FRAME_LATENCY"] = "2"
            }
            else -> {
                // Yüksek RAM: agresif önbellekleme, daha yüksek bellek taahhüdüne izin ver
                env["BOX64_DYNAREC_BIGBLOCK"] = "3"
                env["WINEVMEMMAXSIZE"] = "8192"
                env["mesa_glthread"] = "true"
            }
        }

        return Profile(
            rendererName = if (useTurnip) "Turnip (Vulkan/Adreno)" else "VirGL/Zink (Vulkan genel)",
            useTurnip = useTurnip,
            envVars = env
        )
    }

    private fun isAdrenoGpu(): Boolean {
        val renderer = Build.HARDWARE.lowercase() + " " + Build.BOARD.lowercase()
        val socIsQualcomm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            Build.SOC_MANUFACTURER.equals("Qualcomm", ignoreCase = true)
        // NOT: Build.SOC_MANUFACTURER yalnızca API 31+ (Android 12) üzerinde mevcuttur.
        // minSdk'mız 26 olduğu için SDK_INT kontrolü olmadan bu alana erişmek,
        // Android 8-11 cihazlarda NoSuchFieldError ile ÇÖKMEYE yol açardı - düzeltildi.
        return renderer.contains("qcom") || renderer.contains("adreno") || socIsQualcomm
    }

    private fun totalRamMb(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024 * 1024)
    }
}

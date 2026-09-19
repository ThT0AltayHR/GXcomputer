package com.gxcomputer.app.core

/**
 * GXcomputer genelinde kullanılan sabitler.
 * API anahtarları burada boş bırakılmıştır - README.md'de anlatıldığı gibi
 * kendi anahtarınızı girmeniz gerekir. Anahtar girilmezse ilgili panel
 * "servis ayarlanmadı" durumunu net biçimde gösterir; sahte veri UYDURULMAZ.
 */
object Constants {
    const val PREFS_NAME = "gxcomputer_secure_prefs"
    const val KEY_PASSWORD_HASH = "password_hash"
    const val KEY_PASSWORD_SALT = "password_salt"
    const val KEY_USER_NAME = "user_name"
    const val KEY_FIRST_RUN_DONE = "first_run_done"
    const val KEY_LAST_LAT = "last_lat"
    const val KEY_LAST_LON = "last_lon"

    // Bileşen motoru - assets/components içindeki dosya adlarıyla birebir eşleşir
    const val COMPONENTS_ASSET_DIR = "components"
    val ENGINE_COMPONENTS = listOf(
        "box64-0.3.7.tzst",
        "dxvk-2.6.1.tzst",
        "turnip-26.0.3.tzst",
        "vkd3d-2.14.1.tzst",
        "wined3d-10.0.tzst"
    )

    // Gerçek, doğrulanmış ARM64/Bionic Wine motoru (GE-Proton 11.0-3 arm64ec, .wcp formatı)
    // /system/bin/linker64 kullanıyor - box64'ün aksine kırık bir rootfs yoluna bağımlı DEĞİL.
    const val WINE_ENGINE_ASSET = "wine/GE-proton-11_0-3-arm64ec.wcp"
    const val WINE_ENGINE_VERSION_NAME = "11.0-3-arm64ec"
    // Gerçek Android/Bionic FEX-Emu (.so unixlib + .dll) ve Bionic box64 - The412Banner/Nightlies
    const val FEX_ASSET = "fex/FEX-2609_94-Nightly-555e8bd2d-PPA-unix.wcp"
    const val BOX64_BIONIC_ASSET = "box64bionic/Box64-0_4_5-38f4831b2-Bionic.wcp"

    // Depolama: container'lar cihazın kendi harici depolama alanında, sabit boyutlu
    // bir imaj dosyası OLMADAN, düz klasör olarak tutulur -> yapay üst sınır yok.
    const val CONTAINERS_DIR = "containers"
    const val DEFAULT_CONTAINER = "default"
    const val TRASH_DIR = "GXcomputer_Trash"
    const val MIN_FREE_SPACE_WARNING_MB = 500L

    // Canlı servisler - kendi ücretsiz API anahtarınızı buraya girin (bkz. README.md)
    // Boş bırakılırsa ilgili panel gerçek veri UYDURMAZ, "servis ayarlanmadı" gösterir.
    const val OPENWEATHER_API_KEY = "" // https://openweathermap.org/api
    const val NEWSAPI_API_KEY = ""     // https://newsapi.org
    const val NEWSAPI_COUNTRY = "tr"

    const val TERMINAL_PROMPT = "gx>"
}

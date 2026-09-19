<div align="center">

# 🖥️ GXcomputer

**Android telefonunuzu gerçek bir bilgisayara dönüştürün.**
Apple tarzı bir arayüz, gerçek bir Wine motoru ve sınırsız yerel depolama ile.

[![Derle, İmzala ve Yayınla](https://img.shields.io/github/actions/workflow/status/ThT0AltayHR/GXcomputer/build-release.yml?branch=main&label=derleme&style=for-the-badge)](../../actions)
[![En Son Sürüm](https://img.shields.io/github/v/release/ThT0AltayHR/GXcomputer?label=en%20son%20s%C3%BCr%C3%BCm&style=for-the-badge)](../../releases/latest)
[![İndirmeler](https://img.shields.io/github/downloads/ThT0AltayHR/GXcomputer/total?label=indirme&style=for-the-badge)](../../releases)
[![Lisans](https://img.shields.io/badge/lisans-t%C3%BCm%20haklar%20sakl%C4%B1-red?style=for-the-badge)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#)

**Bu projeyi beğendiyseniz ⭐ bırakmayı unutmayın — geliştiriciyi motive eder!**

[Son Sürümü İndir](../../releases/latest) · [Hata Bildir](../../issues) · [Katkıda Bulun](../../pulls)

</div>

---

## 🎮 Bu Ne?

GXcomputer, Wine + gerçek bir Android/Bionic Proton derlemesi üzerine kurulu, Apple tarzı bir
arayüze sahip bir Windows uygulama ortamıdır. Masaüstü uygulamalarınızı (VS Code, Opera GX,
geliştirme araçları ve benzerleri) **root gerektirmeden**, cihazınızın kendi donanım gücüyle
çalıştırmayı hedefler. 500MB-12GB arası büyük uygulamalar için tasarlandı; depolamada yapay bir
üst sınır yoktur.

## ✨ Öne Çıkan Özellikler

| | |
|---|---|
| 🍎 **Apple tarzı kilit ekranı** | Avatar, isim + şifre, basılı tutunca şifreyi gösteren göz ikonu, gizli özel klavye |
| 🖥️ **Yatay, HD "bilgisayar" ekranı** | macOS tarzı dock, gerçek zamanlı saat/hava durumu/haberler |
| 🖱️ **Dokunmatik alan (touchpad) imleç** | Yakınlaştırma yok — parmağınızı sürükleyip göreceli imleç kontrolü |
| 📁 **Dosya yöneticisi + çöp kutusu** | Gerçek sürükle-bırak, kalıcı/geçici silme onayı |
| ⌨️ **Uçbirim** | Apple Terminal görünümü, gerçek komut çalıştırma |
| 🔌 **Bilgisayarı Kapat** | Yavaşça kararan geçiş animasyonuyla temiz çıkış |
| 💾 **Sınırsız depolama** | Container'lar sabit boyutlu imaj değil, düz klasör — üst sınır cihazın boş alanı |
| ⚡ **Otomatik GPU/RAM optimizasyonu** | Cihaza göre kademeli ayar, elle hiçbir sınır girilmez |
| 🛡️ **Çökme koruması** | Beklenmedik hatalarda uygulama tamamen ölmek yerine temiz başlar |
| 💽 **Anlık kalıcı durum** | Yüklü uygulamalar, çöp kutusu, ayarlar her işlemde diske hemen yazılır |

## 🧩 Motor: Gerçek, Doğrulanmış Wine

Bu proje boyunca birden fazla dosya denendi (WineHQ flatpak referansı, macOS için derlenmiş bir
CrossOver build'i, Proton'un kaynak kodu) ve hiçbiri çalışmadı — sebepleri aşağıda, şeffaflık için
kayıtlı. Sonunda **gerçekten çalışan** parça bulundu ve entegre edildi:

**`GE-proton-11.0-3-arm64ec.wcp`** — Valve'ın Proton'unun, Android Bionic'e karşı kaynağından
yeniden derlenmiş hali (`The412Banner/proton-wine` projesi). Doğrulandı:

- `bin/wine`, `bin/wineserver` → gerçek ARM64 ELF, interpreter `/system/bin/linker64` (her Android
  cihazında var olan gerçek sistem bağlayıcısı — önceki denemelerdeki kırık, başka bir uygulamanın
  özel veri dizinine sabitlenmiş yol sorunu **yok**)
- `lib/` → Turnip/Freedreno Vulkan sürücüsü + Mesa Gallium dahil, hepsi ARM64
- `prefixPack.txz` → hazır bir Wine prefix şablonu; yeni container'lar sıfırdan `wineboot`
  çalıştırmak yerine bunu kopyalayarak saniyeler içinde hazırlanır

Wine artık **box64 ile sarmalanmıyor** — doğrudan, natif olarak ARM64 üzerinde çalışıyor. Bu, Wine
başlatma hızını ve kararlılığını kökten iyileştiriyor.

### ✅ CPU çeviri katmanı da entegre edildi

Çalıştırılacak Windows uygulamasının kendi x86/x64 kodu için **FEX-Emu** (`The412Banner/Nightlies`,
PPA+unix build'i) entegre edildi: `libarm64ecfex.so`/`libwow64fex.so` motorun `lib/wine/aarch64-unix/`
klasörüne, eşlik eden `.dll`'ler prefix şablonunun `system32`'sine yerleştirildi — Proton'un kendi
otomatik yükleme mekanizması (`MemoryWineLoadUnixLibByName`) bunu ekstra bir ayar gerekmeden bulur.
Ayrıca aynı kaynaktan gerçek, doğrulanmış bir **Bionic box64** de eklendi (`/system/bin/linker64`
ile, jniLibs'teki eski kırık build'in yerini alıyor). ARM64/arm64ec derlenmiş Windows uygulamaları
zaten hiçbir çeviriye gerek duymadan doğrudan çalışır.

## 🚀 Kurulum ve Derleme

### GitHub Actions ile otomatik (önerilen)

Bu depo `main`'e her push'ta veya elle tetiklendiğinde `.github/workflows/build-release.yml` ile
APK'yı otomatik derler, imzalar ve **Releases** bölümüne kararlı sürüm olarak yayınlar. En son
sürümü [Releases](../../releases/latest) sayfasından indirip kurmanız yeterli — root gerekmez.

**Kalıcı bir imza anahtarıyla derlemek için** (önerilir — aksi halde her derleme farklı bir geçici
anahtarla imzalanır ve üzerine güncelleme kuramazsınız):

```bash
keytool -genkeypair -v -keystore release.jks -alias gxcomputer \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks > release.jks.base64
```

Depo **Settings → Secrets and variables → Actions** altına şu sırları ekleyin:
`KEYSTORE_BASE64` (yukarıdaki .base64 dosyasının içeriği), `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`.

### Elle derleme

1. Bu klasörü Android Studio ile açın (Gradle senkronizasyonu internet erişimi gerektirir)
2. `Build > Make Project` veya bir cihaza doğrudan çalıştırın
3. İlk açılışta motor bileşenleri (Wine + box64/dxvk/turnip/vkd3d/wined3d) otomatik olarak
   cihazın kendi depolamasına açılır

## 🌤️ Canlı Hava Durumu / Haberler

Gerçek API çağrıları yapar, anahtar girilmeden sahte veri göstermez. Etkinleştirmek için
`app/src/main/java/com/gxcomputer/app/core/Constants.kt` içine kendi ücretsiz anahtarlarınızı
girin:

```kotlin
const val OPENWEATHER_API_KEY = "…" // openweathermap.org/api
const val NEWSAPI_API_KEY = "…"     // newsapi.org
```

## 📱 Test Edilen Cihaz Profili

Performans ayarları şu sınıf cihazlar göz önünde bulundurularak kademelendirildi: Redmi 13,
Android 16, HyperOS, 4+8GB (genişletilmiş) RAM, 256GB depolama — yani üst düzey olmayan, yaygın
bir orta seviye cihaz. `GraphicsDriverConfig`, cihazın gerçek RAM'ine göre otomatik olarak daha
düşük veya daha yüksek bellek profillerine geçer; hiçbir değer elle sabitlenmez.

## 🛠️ Teknik Özellikler

| | |
|---|---|
| Paket adı | `com.gxcomputer.app` |
| Min SDK | 26 (Android 8.0) · Hedef SDK 34 |
| Mimari | arm64-v8a |
| Arayüz dili | Türkçe (tamamı) |
| Wine motoru | GE-Proton 11.0-3 arm64ec (Bionic) |
| CPU çeviri | FEX-Emu (arm64ec/wow64 unixlib) + Bionic box64 |
| GPU sürücüsü | Turnip/Freedreno (Adreno) |

## 📂 Proje Yapısı

```
app/src/main/java/com/gxcomputer/app/
├── core/         Sabitler, şifreli tercih yönetimi, çökme koruması
├── lock/         Kilit ekranı, özel klavye
├── desktop/      Masaüstü, dock, doğa arka planı, hava durumu/haber servisleri
├── filemanager/  Dosya yöneticisi, çöp kutusu
├── terminal/     Uçbirim
└── container/    Depolama, GPU yapılandırması, Wine motoru kurulumu, uygulama başlatma
```

## 📜 Lisans

Bu depo **açık kaynak bir lisans altında değildir** ([`LICENSE`](LICENSE) dosyasına bakın).
Kaynak kodu inceleme amacıyla herkese açıktır, ama izinsiz kopyalanamaz, değiştirilemez,
kendi projenizmiş gibi yeniden yayınlanamaz veya ticari amaçla kullanılamaz — tüm haklar
saklıdır. (Not: Bu bir hukuki tavsiye değildir; bağlayıcı/ticari bir kullanım için bir
hukuk danışmanına başvurmanız önerilir.)

## 🤝 Katkıda Bulunun

Bu proje kişisel bir ihtiyaçtan doğdu ve geliştirilmeye devam ediyor. Hata bulursanız
[bir issue açın](../../issues), bir düzeltmeniz varsa [pull request gönderin](../../pulls).
İşinizi gördüyse ⭐ bırakmanız, projenin görünürlüğüne gerçekten yardımcı olur.

---

<div align="center">

*GXcomputer, Wine ve GE-Proton'un açık kaynak motoru temel alınarak hazırlanmış özgün bir
uygulamadır. Wine® WineHQ projesinin, Proton™ Valve Corporation'ın ticari markasıdır; bu proje
her ikisiyle de resmi olarak ilişkili değildir.*

</div>

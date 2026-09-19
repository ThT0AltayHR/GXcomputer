package com.gxcomputer.app.desktop

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gxcomputer.app.R
import com.gxcomputer.app.container.ComponentInstaller
import com.gxcomputer.app.container.ContainerLauncher
import com.gxcomputer.app.container.GraphicsDriverConfig
import com.gxcomputer.app.container.InstalledApp
import com.gxcomputer.app.container.InstalledAppsRegistry
import com.gxcomputer.app.container.StorageHelper
import com.gxcomputer.app.container.WineEngineInstaller
import com.gxcomputer.app.filemanager.FileManagerActivity
import com.gxcomputer.app.lock.LockScreenActivity
import com.gxcomputer.app.terminal.TerminalActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DesktopActivity : AppCompatActivity() {

    private lateinit var storage: StorageHelper
    private lateinit var installer: ComponentInstaller
    private lateinit var wineEngine: WineEngineInstaller
    private lateinit var registry: InstalledAppsRegistry
    private lateinit var launcher: ContainerLauncher
    private lateinit var dockAdapter: DockAdapter

    private lateinit var storageText: TextView
    private lateinit var clockTextTop: TextView
    private lateinit var weatherTextTop: TextView
    private lateinit var weatherIconTop: ImageView
    private lateinit var newsContent: TextView
    private lateinit var setupPanel: LinearLayout
    private lateinit var setupStatusText: TextView
    private lateinit var setupProgressBar: ProgressBar
    private lateinit var virtualCursor: ImageView
    private lateinit var nativeKeyboardToggle: ImageView
    private lateinit var nativeKeyboardTrigger: EditText
    private lateinit var rootContent: android.view.View
    private lateinit var shutdownOverlay: android.view.View

    private val clockHandler = Handler(Looper.getMainLooper())

    private val installAppPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onAppSelectedForInstall(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_desktop)

        storage = StorageHelper(this)
        installer = ComponentInstaller(this, storage)
        wineEngine = WineEngineInstaller(this, storage)
        registry = InstalledAppsRegistry(storage)
        launcher = ContainerLauncher(this, storage, GraphicsDriverConfig(this, storage))

        bindViews()
        setupDock()
        updateStorageText()
        startClock()
        loadWeatherMini()
        loadNewsMini()
        ensureEngineInstalled()
        setupVirtualCursor()
        setupNativeKeyboardToggle()

        findViewById<ImageView>(R.id.dockFileManager).setOnClickListener {
            startActivity(Intent(this, FileManagerActivity::class.java))
        }
        findViewById<ImageView>(R.id.dockTerminal).setOnClickListener {
            startActivity(Intent(this, TerminalActivity::class.java))
        }
        findViewById<ImageView>(R.id.dockAddApp).setOnClickListener {
            // NOT: .exe/.msi dosyaları, dosya sağlayıcısına göre application/octet-stream,
            // application/x-msdownload, application/x-ole-storage gibi farklı MIME türleriyle
            // raporlanabiliyor. Dar bir filtre bazı geçerli dosyaları listeden gizleyebileceği
            // için "*/*" kullanılıp kullanıcının istediği dosyayı seçmesine izin veriliyor.
            installAppPicker.launch(arrayOf("*/*"))
        }
        findViewById<ImageView>(R.id.dockLock).setOnClickListener {
            startActivity(Intent(this, LockScreenActivity::class.java))
            finish()
        }
        findViewById<ImageView>(R.id.dockShutdown).setOnClickListener { confirmShutdown() }
    }

    private fun bindViews() {
        storageText = findViewById(R.id.storageText)
        clockTextTop = findViewById(R.id.clockTextTop)
        weatherTextTop = findViewById(R.id.weatherTextTop)
        weatherIconTop = findViewById(R.id.weatherIconTop)
        newsContent = findViewById(R.id.newsContent)
        setupPanel = findViewById(R.id.setupProgressPanel)
        setupStatusText = findViewById(R.id.setupStatusText)
        setupProgressBar = findViewById(R.id.setupProgressBar)
        virtualCursor = findViewById(R.id.virtualCursor)
        nativeKeyboardToggle = findViewById(R.id.nativeKeyboardToggle)
        nativeKeyboardTrigger = findViewById(R.id.nativeKeyboardTrigger)
        rootContent = findViewById(android.R.id.content)
        shutdownOverlay = findViewById(R.id.shutdownOverlay)
    }

    /**
     * Winlator'daki gibi dokunmatik alan (touchpad) modu: parmağınızı ekranın
     * herhangi bir yerinde sürüklemeniz, imleci parmağınızın ALTINA değil,
     * hareketin YÖNÜ VE MİKTARI kadar (göreceli olarak) taşır. Sabitleme/
     * yakınlaştırma jesti YOKTUR - kasıtlı olarak eklenmedi.
     */
    private fun setupVirtualCursor() {
        var lastX = 0f
        var lastY = 0f
        virtualCursor.post {
            virtualCursor.x = (rootContent.width - virtualCursor.width) / 2f
            virtualCursor.y = (rootContent.height - virtualCursor.height) / 2f
        }
        rootContent.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY
                    lastX = event.rawX
                    lastY = event.rawY
                    val newX = (virtualCursor.x + dx).coerceIn(0f, (rootContent.width - virtualCursor.width).toFloat())
                    val newY = (virtualCursor.y + dy).coerceIn(0f, (rootContent.height - virtualCursor.height).toFloat())
                    virtualCursor.x = newX
                    virtualCursor.y = newY
                    true
                }
                else -> false
            }
        }
    }

    private fun setupNativeKeyboardToggle() {
        nativeKeyboardToggle.setOnClickListener {
            nativeKeyboardTrigger.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(nativeKeyboardTrigger, InputMethodManager.SHOW_FORCED)
        }
    }

    private fun setupDock() {
        val recycler = findViewById<RecyclerView>(R.id.dockRecycler)
        recycler.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        dockAdapter = DockAdapter(
            registry.list().toMutableList(),
            onClick = { app -> launchApp(app) },
            onLongClick = { app -> confirmRemoveApp(app) }
        )
        recycler.adapter = dockAdapter
    }

    private fun refreshDock() = dockAdapter.update(registry.list())

    private fun launchApp(app: InstalledApp) {
        Toast.makeText(this, "${app.name} başlatılıyor (${launcher.activeRendererName()})", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                launcher.launch(app.exePath)
            } catch (e: ContainerLauncher.WineNotInstalledException) {
                runOnUiThread { showEngineMissingDialog() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, getString(R.string.setup_error_generic, e.message ?: "bilinmeyen hata"), Toast.LENGTH_LONG).show() }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun showEngineMissingDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Motor henüz eklenmedi")
            .setMessage(
                "Uygulamaları çalıştıracak Wine motoru bu kuruluma henüz eklenmedi, " +
                    "bu yüzden şu an gerçek bir .exe çalıştırılamıyor. Depolama, arayüz, " +
                    "GPU otomasyonu ve diğer her şey hazır - README.md'de bu adımın nasıl " +
                    "tamamlanacağı anlatılıyor."
            )
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    private fun confirmRemoveApp(app: InstalledApp) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(app.name)
            .setMessage(R.string.file_manager_delete)
            .setPositiveButton(R.string.action_ok) { _, _ -> registry.remove(app.exePath); refreshDock() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun onAppSelectedForInstall(uri: Uri) {
        Thread {
            try {
                val name = queryDisplayName(uri) ?: "Uygulama"
                val destDir = java.io.File(storage.containerDir(), "installed").apply { mkdirs() }
                val destFile = uniqueFileFor(destDir, name)
                contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                registry.add(InstalledApp(name.substringBeforeLast("."), destFile.absolutePath))
                runOnUiThread {
                    refreshDock()
                    Toast.makeText(this, "$name eklendi", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, getString(R.string.setup_error_generic, e.message ?: "bilinmeyen hata"), Toast.LENGTH_LONG).show() }
            }
        }.apply { isDaemon = true }.start()
    }

    /** Aynı isimli bir dosya zaten varsa, birini sessizce üzerine yazmak yerine (1), (2)... ekler. */
    private fun uniqueFileFor(dir: java.io.File, desiredName: String): java.io.File {
        var candidate = java.io.File(dir, desiredName)
        if (!candidate.exists()) return candidate
        val base = desiredName.substringBeforeLast(".", desiredName)
        val ext = desiredName.substringAfterLast(".", "")
        var counter = 1
        while (candidate.exists()) {
            val newName = if (ext.isNotEmpty()) "$base ($counter).$ext" else "$base ($counter)"
            candidate = java.io.File(dir, newName)
            counter++
        }
        return candidate
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return uri.lastPathSegment
    }

    private fun ensureEngineInstalled() {
        if (installer.isInstalled() && wineEngine.isInstalled()) return
        setupPanel.visibility = android.view.View.VISIBLE
        Thread {
            try {
                if (!installer.isInstalled()) {
                    installer.installAll { progress ->
                        runOnUiThread {
                            when (progress) {
                                is ComponentInstaller.Progress.Extracting -> {
                                    setupStatusText.text = getString(R.string.setup_extracting_component, progress.componentName)
                                    setupProgressBar.progress = (progress.index * 100) / progress.total / 2
                                }
                                is ComponentInstaller.Progress.Done -> {}
                                is ComponentInstaller.Progress.Error -> {
                                    setupStatusText.text = getString(R.string.setup_error_generic, progress.message)
                                }
                            }
                        }
                    }
                }
                if (!wineEngine.isInstalled()) {
                    wineEngine.install { status ->
                        runOnUiThread {
                            setupStatusText.text = status
                            setupProgressBar.progress = 60
                        }
                    }
                }
                runOnUiThread {
                    setupProgressBar.progress = 100
                    setupPanel.visibility = android.view.View.GONE
                }
            } catch (e: Exception) {
                // Kurulum başarısız olsa bile UYGULAMA ÇÖKMEZ - kullanıcıya net bir durum gösterilir,
                // bir sonraki açılışta kaldığı yerden (henüz kurulmamış bileşenlerden) devam eder.
                runOnUiThread {
                    setupStatusText.text = getString(R.string.setup_error_generic, e.message ?: "bilinmeyen hata")
                    setupProgressBar.progress = 0
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun updateStorageText() {
        storageText.text = getString(R.string.desktop_storage_free, storage.freeSpaceFormatted())
    }

    private fun startClock() {
        val tick = object : Runnable {
            override fun run() {
                clockTextTop.text = SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Calendar.getInstance().time)
                clockHandler.postDelayed(this, 1000)
            }
        }
        clockHandler.post(tick)
    }

    private fun confirmShutdown() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.desktop_shutdown_confirm_title)
            .setMessage(R.string.desktop_shutdown_confirm_message)
            .setPositiveButton(R.string.desktop_shutdown) { _, _ -> performShutdown() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** Diğer uygulamalardaki "kapat" akışı gibi: yavaşça kararır, sonra uygulamadan tamamen çıkar. */
    private fun performShutdown() {
        shutdownOverlay.visibility = android.view.View.VISIBLE
        shutdownOverlay.animate()
            .alpha(1f)
            .setDuration(1400)
            .withEndAction {
                finishAffinity()
                Runtime.getRuntime().exit(0)
            }
            .start()
    }

    override fun onDestroy() {
        clockHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun loadWeatherMini() {
        WeatherRepository(this).fetchForCurrentLocation(
            onResult = { result ->
                runOnUiThread {
                    weatherTextTop.text = result.displayText
                    weatherIconTop.setImageResource(result.iconRes)
                }
            },
            onNoApiKey = { runOnUiThread { weatherTextTop.setText(R.string.weather_needs_api_key) } },
            onError = { runOnUiThread { weatherTextTop.setText(R.string.weather_needs_api_key) } }
        )
    }

    private fun loadNewsMini() {
        NewsRepository(this).fetchTopHeadlines(
            onResult = { items ->
                runOnUiThread {
                    newsContent.text = if (items.isEmpty()) getString(R.string.desktop_news_needs_api_key)
                    else items.take(4).joinToString("\n\n") { "• ${it.title}" }
                }
            },
            onNoApiKey = { runOnUiThread { newsContent.setText(R.string.desktop_news_needs_api_key) } },
            onError = { runOnUiThread { newsContent.setText(R.string.desktop_news_needs_api_key) } }
        )
    }

    override fun onResume() {
        super.onResume()
        updateStorageText()
    }
}

package com.gxcomputer.app

import android.app.Application
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import com.gxcomputer.app.core.PreferencesManager
import com.gxcomputer.app.lock.LockScreenActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class GXApplication : Application() {

    lateinit var preferences: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = PreferencesManager(this)
        createNotificationChannels()
        installCrashGuard()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(
                    "gx_engine",
                    "GXcomputer Motoru",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Container kurulum ve çalışma bildirimleri" }
            )
        }
    }

    /**
     * "Açtığımda çökmeyecek" gereksinimi için son çare güvenlik ağı: beklenmedik bir
     * istisna (ör. bir cihaza özgü, öngörülemeyen bir sorun) uygulamayı Android'in sert
     * "uygulama durdu" ekranıyla tamamen öldürmek yerine, hatayı bir günlük dosyasına
     * yazıp kilit ekranından TEMİZ bir şekilde yeniden başlatır. Bu, hatayı gizlemez -
     * crash_log.txt dosyasında kalıcı olarak durur, sorun giderme için kullanılabilir.
     */
    private fun installCrashGuard() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val logFile = File(getExternalFilesDir(null) ?: filesDir, "gxcomputer/crash_log.txt")
                logFile.parentFile?.mkdirs()
                logFile.appendText("\n--- ${java.util.Date()} ---\n$sw")

                val restartIntent = Intent(this, LockScreenActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, restartIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 400, pendingIntent)
            } catch (e: Exception) {
                // Günlükleme/yeniden başlatma bile başarısız olursa, en azından normal
                // sistem davranışına (aşağıdaki defaultHandler) düşülür.
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(1)
            }
        }
    }
}

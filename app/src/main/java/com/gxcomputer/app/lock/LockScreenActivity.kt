package com.gxcomputer.app.lock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gxcomputer.app.GXApplication
import com.gxcomputer.app.R
import com.gxcomputer.app.desktop.DesktopActivity
import com.gxcomputer.app.desktop.WeatherRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LockScreenActivity : AppCompatActivity() {

    private lateinit var nameRow: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var confirmPasswordRow: LinearLayout
    private lateinit var passwordWarningBanner: TextView
    private lateinit var greetingText: TextView
    private lateinit var errorText: TextView
    private lateinit var clockText: TextView
    private lateinit var unlockButton: ImageView
    private lateinit var eyeIcon: ImageView
    private lateinit var weatherText: TextView
    private lateinit var weatherIcon: ImageView
    private lateinit var weatherPanel: LinearLayout
    private lateinit var toggleKeyboardButton: TextView
    private lateinit var keyboardPanel: LinearLayout
    private lateinit var bootingOverlay: LinearLayout

    private lateinit var app: GXApplication
    private var isSetupMode = false
    private val clockHandler = Handler(Looper.getMainLooper())
    private lateinit var weatherRepository: WeatherRepository

    private val locationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) loadWeather() else weatherText.setText(R.string.weather_needs_permission) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        app = application as GXApplication
        weatherRepository = WeatherRepository(this)

        bindViews()
        isSetupMode = !app.preferences.isPasswordSet()

        nameRow.visibility = if (isSetupMode) android.view.View.VISIBLE else android.view.View.GONE
        confirmPasswordRow.visibility = if (isSetupMode) android.view.View.VISIBLE else android.view.View.GONE
        passwordWarningBanner.visibility = if (isSetupMode) android.view.View.VISIBLE else android.view.View.GONE
        greetingText.text = if (isSetupMode) getString(R.string.lock_set_password_title) else greetingForNow()

        CustomKeyboardView(
            container = findViewById(R.id.customKeyboardContainer),
            onKey = { char -> activeField().append(char) },
            onBackspace = {
                val f = activeField()
                val s = f.text
                if (s.isNotEmpty()) f.text = s.subSequence(0, s.length - 1)
            },
            onDone = { attemptSubmit() }
        ).build()

        unlockButton.setOnClickListener { attemptSubmit() }

        toggleKeyboardButton.setOnClickListener {
            keyboardPanel.visibility =
                if (keyboardPanel.visibility == android.view.View.VISIBLE) android.view.View.GONE
                else android.view.View.VISIBLE
        }

        // Göz ikonu: yalnızca basılı tutulunca şifreyi göster, bırakınca hemen gizle
        eyeIcon.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    passwordInput.transformationMethod = null
                    passwordInput.setSelection(passwordInput.text.length)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    passwordInput.transformationMethod =
                        android.text.method.PasswordTransformationMethod.getInstance()
                    passwordInput.setSelection(passwordInput.text.length)
                    true
                }
                else -> false
            }
        }

        weatherPanel.setOnClickListener { requestLocationAndLoadWeather() }
        requestLocationAndLoadWeather()
        startClock()
    }

    private fun bindViews() {
        nameRow = findViewById(R.id.nameRow)
        nameInput = findViewById(R.id.nameInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        nameInput.showSoftInputOnFocus = false
        passwordInput.showSoftInputOnFocus = false
        confirmPasswordInput.showSoftInputOnFocus = false
        confirmPasswordRow = findViewById(R.id.confirmPasswordRow)
        passwordWarningBanner = findViewById(R.id.passwordWarningBanner)
        greetingText = findViewById(R.id.greetingText)
        errorText = findViewById(R.id.errorText)
        clockText = findViewById(R.id.clockText)
        unlockButton = findViewById(R.id.unlockButton)
        eyeIcon = findViewById(R.id.eyeIcon)
        weatherText = findViewById(R.id.weatherText)
        weatherIcon = findViewById(R.id.weatherIcon)
        weatherPanel = findViewById(R.id.weatherPanel)
        toggleKeyboardButton = findViewById(R.id.toggleKeyboardButton)
        keyboardPanel = findViewById(R.id.keyboardPanel)
        bootingOverlay = findViewById(R.id.bootingOverlay)
    }

    private fun activeField(): EditText = when {
        isSetupMode && nameInput.hasFocus() -> nameInput
        isSetupMode && confirmPasswordInput.hasFocus() -> confirmPasswordInput
        else -> passwordInput
    }

    private fun attemptSubmit() {
        errorText.visibility = android.view.View.INVISIBLE
        if (isSetupMode) {
            val name = nameInput.text.toString().trim()
            val p1 = passwordInput.text.toString()
            val p2 = confirmPasswordInput.text.toString()
            if (name.isEmpty()) {
                showError(getString(R.string.lock_name_required)); return
            }
            if (p1.length < 4) {
                showError(getString(R.string.lock_password_too_short)); return
            }
            if (p1 != p2) {
                showError(getString(R.string.lock_password_mismatch)); return
            }
            app.preferences.setUserName(name)
            app.preferences.setPassword(p1)
            bootThenGoToDesktop()
        } else {
            val p = passwordInput.text.toString()
            if (app.preferences.verifyPassword(p)) {
                bootThenGoToDesktop()
            } else {
                showError(getString(R.string.lock_wrong_password))
                passwordInput.text.clear()
            }
        }
    }

    private fun showError(msg: String) {
        errorText.text = msg
        errorText.visibility = android.view.View.VISIBLE
    }

    private fun bootThenGoToDesktop() {
        keyboardPanel.visibility = android.view.View.GONE
        bootingOverlay.visibility = android.view.View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({ goToDesktop() }, 1600)
    }

    private fun goToDesktop() {
        startActivity(Intent(this, DesktopActivity::class.java))
        finish()
    }

    private fun startClock() {
        val tick = object : Runnable {
            override fun run() {
                val now = Calendar.getInstance()
                clockText.text = SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(now.time)
                clockHandler.postDelayed(this, 1000)
            }
        }
        clockHandler.post(tick)
    }

    override fun onDestroy() {
        // Saatin kendini sürekli yeniden zamanlayan Runnable'ı, Activity kapandıktan
        // sonra da çalışmaya devam edip View/Handler sızıntısına yol açmasın diye temizlenir.
        clockHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun greetingForNow(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val name = app.preferences.getUserName()
        val base = when (hour) {
            in 5..11 -> getString(R.string.lock_greeting_morning)
            in 12..17 -> getString(R.string.lock_greeting_afternoon)
            in 18..22 -> getString(R.string.lock_greeting_evening)
            else -> getString(R.string.lock_greeting_night)
        }
        return if (name.isNotBlank()) "$base, $name" else base
    }

    private fun requestLocationAndLoadWeather() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            loadWeather()
        } else {
            weatherText.setText(R.string.weather_needs_permission)
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun loadWeather() {
        weatherText.setText(R.string.weather_loading)
        weatherRepository.fetchForCurrentLocation(
            onResult = { result ->
                runOnUiThread {
                    weatherText.text = result.displayText
                    weatherIcon.setImageResource(result.iconRes)
                }
            },
            onNoApiKey = {
                runOnUiThread { weatherText.setText(R.string.weather_needs_api_key) }
            },
            onError = {
                runOnUiThread { weatherText.setText(R.string.weather_needs_api_key) }
            }
        )
    }
}

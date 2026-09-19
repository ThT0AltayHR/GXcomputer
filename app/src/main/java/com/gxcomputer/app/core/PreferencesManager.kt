package com.gxcomputer.app.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Kullanıcı şifresini ve GXcomputer ayarlarını cihaz üzerinde şifreli olarak saklar.
 * Şifre asla düz metin olarak tutulmaz: PBKDF2 + rastgele salt ile hash'lenir.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            Constants.PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Bazı cihazlarda Keystore sorunlarına karşı düz SharedPreferences'a düş
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isPasswordSet(): Boolean = prefs.contains(Constants.KEY_PASSWORD_HASH)

    fun setUserName(name: String) {
        prefs.edit().putString(Constants.KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String = prefs.getString(Constants.KEY_USER_NAME, "") ?: ""

    fun setPassword(rawPassword: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(rawPassword, salt)
        prefs.edit()
            .putString(Constants.KEY_PASSWORD_SALT, salt.joinToString(",") { it.toString() })
            .putString(Constants.KEY_PASSWORD_HASH, hash)
            .putBoolean(Constants.KEY_FIRST_RUN_DONE, true)
            .apply()
    }

    fun verifyPassword(rawPassword: String): Boolean {
        val saltStr = prefs.getString(Constants.KEY_PASSWORD_SALT, null) ?: return false
        val salt = saltStr.split(",").map { it.toByte() }.toByteArray()
        val expected = prefs.getString(Constants.KEY_PASSWORD_HASH, null) ?: return false
        return hash(rawPassword, salt) == expected
    }

    fun isFirstRunDone(): Boolean = prefs.getBoolean(Constants.KEY_FIRST_RUN_DONE, false)

    fun saveLastLocation(lat: Double, lon: Double) {
        prefs.edit()
            .putString(Constants.KEY_LAST_LAT, lat.toString())
            .putString(Constants.KEY_LAST_LON, lon.toString())
            .apply()
    }

    fun getLastLocation(): Pair<Double, Double>? {
        val lat = prefs.getString(Constants.KEY_LAST_LAT, null)?.toDoubleOrNull()
        val lon = prefs.getString(Constants.KEY_LAST_LON, null)?.toDoubleOrNull()
        return if (lat != null && lon != null) lat to lon else null
    }

    private fun hash(password: String, salt: ByteArray): String {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, 12000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

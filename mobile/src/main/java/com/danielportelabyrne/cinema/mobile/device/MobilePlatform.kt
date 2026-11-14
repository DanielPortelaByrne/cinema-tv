package com.danielportelabyrne.cinema.mobile.device

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.danielportelabyrne.cinema.platform.Log
import com.danielportelabyrne.cinema.platform.Platform
import com.danielportelabyrne.cinema.platform.Preferences
import com.danielportelabyrne.cinema.platform.SecretStore
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The shared code's view of a phone: private files, SharedPreferences and Keystore-sealed secrets. A phone belongs to
 * one person, so unlike the TV there is no per-person scoping of files.
 */
class MobilePlatform(context: Context) : Platform {
    private val app = context.applicationContext

    override val filesDir: File get() = app.filesDir
    override val cacheDir: File get() = app.cacheDir
    override val deviceName: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim()

    override fun preferences(name: String): Preferences = MobilePreferences(app.getSharedPreferences(name, Context.MODE_PRIVATE))
    override fun hasPreferences(name: String): Boolean = File(app.applicationInfo.dataDir, "shared_prefs/$name.xml").exists()
    override fun deletePreferences(name: String): Boolean = app.deleteSharedPreferences(name)

    override fun readAsset(name: String): String? = try {
        app.assets.open(name).bufferedReader().use { it.readText() }
    } catch (_: java.io.IOException) { null }

    override fun secretStore(): SecretStore = KeystoreSecrets(preferences("secrets"))
}

private class MobilePreferences(private val prefs: SharedPreferences) : Preferences {
    override fun getString(key: String, defValue: String?): String? = prefs.getString(key, defValue)
    override fun getBoolean(key: String, defValue: Boolean): Boolean = prefs.getBoolean(key, defValue)
    override fun getLong(key: String, defValue: Long): Long = prefs.getLong(key, defValue)
    override fun getInt(key: String, defValue: Int): Int = prefs.getInt(key, defValue)
    override fun contains(key: String): Boolean = prefs.contains(key)
    override val all: Map<String, *> get() = prefs.all
    override fun edit(): Preferences.Editor = Editor(prefs.edit())

    private class Editor(private val e: SharedPreferences.Editor) : Preferences.Editor {
        override fun putString(key: String, value: String?) = apply { e.putString(key, value) }
        override fun putBoolean(key: String, value: Boolean) = apply { e.putBoolean(key, value) }
        override fun putLong(key: String, value: Long) = apply { e.putLong(key, value) }
        override fun putInt(key: String, value: Int) = apply { e.putInt(key, value) }
        override fun remove(key: String) = apply { e.remove(key) }
        override fun clear() = apply { e.clear() }
        override fun apply() = e.apply()
        override fun commit(): Boolean = e.commit()
    }
}

/** Each value sealed with an AES key that never leaves the Android Keystore (as on the TV). Never throws. */
private class KeystoreSecrets(private val prefs: Preferences) : SecretStore {
    override fun get(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV))
            String(cipher.doFinal(bytes, IV, bytes.size - IV), Charsets.UTF_8)
        } catch (e: Exception) { Log.w("Secrets", "a saved secret could not be read: ${e.javaClass.simpleName}"); null }
    }

    override fun put(name: String, value: String?) {
        if (value.isNullOrEmpty()) { prefs.edit().remove(name).apply(); return }
        try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            prefs.edit().putString(name, Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)).apply()
        } catch (e: Exception) { Log.w("Secrets", "a secret could not be saved: ${e.javaClass.simpleName}") }
    }

    override fun clear() { prefs.edit().clear().apply() }

    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }

    private companion object { const val ALIAS = "cinema_mobile_secrets"; const val TRANSFORM = "AES/GCM/NoPadding"; const val IV = 12 }
}

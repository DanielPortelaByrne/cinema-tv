package com.danielportelabyrne.cinema.device

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.danielportelabyrne.cinema.platform.Platform
import com.danielportelabyrne.cinema.platform.Preferences
import com.danielportelabyrne.cinema.platform.SecretStore
import java.io.File

/** The shared code's view of this TV: private files, SharedPreferences, and the Keystore-backed secrets. */
class AndroidPlatform(context: Context) : Platform {
    private val app = context.applicationContext

    override val filesDir: File get() = app.filesDir
    override val cacheDir: File get() = app.cacheDir
    override val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    override fun preferences(name: String): Preferences = AndroidPreferences(app.getSharedPreferences(scopedName(app, name), Context.MODE_PRIVATE))
    override fun hasPreferences(name: String): Boolean = File(app.applicationInfo.dataDir, "shared_prefs/${scopedName(app, name)}.xml").exists()
    override fun deletePreferences(name: String): Boolean = app.deleteSharedPreferences(scopedName(app, name))

    companion object {
        /**
         * The file [name] really is for the person in use (see ProfileScope). The first time the person who had the TV
         * before accounts opens one of their files, it starts as a copy of the TV's old one, so nothing of theirs is lost.
         */
        fun scopedName(context: Context, name: String): String {
            val app = context.applicationContext
            if (!com.danielportelabyrne.cinema.data.account.ProfileScope.loaded) loadScope(app)
            val real = com.danielportelabyrne.cinema.data.account.ProfileScope.fileName(name)
            if (real != name && com.danielportelabyrne.cinema.data.account.ProfileScope.legacyOwner) {
                val dir = File(app.applicationInfo.dataDir, "shared_prefs")
                if (!File(dir, "$real.xml").exists() && File(dir, "$name.xml").exists()) copy(app.getSharedPreferences(name, Context.MODE_PRIVATE), app.getSharedPreferences(real, Context.MODE_PRIVATE))
            }
            return real
        }

        /** A personal file opened straight from Android (the stores in the app module), for the person in use. */
        fun personalPrefs(context: Context, name: String): SharedPreferences = context.getSharedPreferences(scopedName(context, name), Context.MODE_PRIVATE)

        /** Reads who is in use from the TV's account file, without going through [scopedName] (which needs it). */
        @Synchronized private fun loadScope(app: Context) {
            if (com.danielportelabyrne.cinema.data.account.ProfileScope.loaded) return
            val p = app.getSharedPreferences("accounts", Context.MODE_PRIVATE)
            val ids = com.danielportelabyrne.cinema.data.account.AccountStore.parse(p.getString("list", null)).map { it.userId }
            val id = p.getString("active", null)?.takeIf { it in ids }
            com.danielportelabyrne.cinema.data.account.ProfileScope.set(id, id == null || id == p.getString("original", null))
        }

        @Suppress("UNCHECKED_CAST")
        private fun copy(from: SharedPreferences, to: SharedPreferences) {
            val e = to.edit()
            from.all.forEach { (k, v) ->
                when (v) {
                    is String -> e.putString(k, v); is Boolean -> e.putBoolean(k, v); is Long -> e.putLong(k, v)
                    is Int -> e.putInt(k, v); is Float -> e.putFloat(k, v); is Set<*> -> e.putStringSet(k, v as Set<String>)
                }
            }
            e.commit()
        }
    }
    override fun readAsset(name: String): String? = try {
        app.assets.open(name).bufferedReader().use { it.readText() }
    } catch (_: java.io.FileNotFoundException) { null }

    override fun secretStore(): SecretStore = AndroidSecretStore(preferences("secrets"))

    /**
     * This TV as [userId] would see it, whoever is in use: their own copy of each personal file (for reading their
     * favourites on "Who's watching?"). [original] is the person whose data predates accounts.
     */
    fun forPerson(userId: String, original: Boolean): Platform {
        val base = this
        return object : Platform by base {
            override fun preferences(name: String): Preferences {
                val real = com.danielportelabyrne.cinema.data.account.ProfileScope.fileNameFor(name, userId)
                val dir = File(app.applicationInfo.dataDir, "shared_prefs")
                if (original && real != name && !File(dir, "$real.xml").exists() && File(dir, "$name.xml").exists()) return AndroidPreferences(app.getSharedPreferences(name, Context.MODE_PRIVATE))
                return AndroidPreferences(app.getSharedPreferences(real, Context.MODE_PRIVATE))
            }
        }
    }
}

private class AndroidPreferences(private val prefs: SharedPreferences) : Preferences {
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

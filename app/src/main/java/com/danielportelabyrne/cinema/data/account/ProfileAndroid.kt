package com.danielportelabyrne.cinema.data.account

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.device.AndroidPlatform
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Who the TV's launcher (MCM Launcher, com.example.tvlauncher) says is watching: the account picked on its "Who's
 * watching?" when the TV was switched on. Null when that launcher is not installed, is too old to say, or says nobody.
 */
object LauncherProfile {
    const val AUTHORITY = "com.example.tvlauncher.profile"

    fun current(context: Context): LauncherPerson? = try {
        val cursor = context.contentResolver.query(Uri.parse("content://$AUTHORITY/current"), null, null, null, null)
        if (cursor == null) android.util.Log.w("Accounts", "launcher profile: no provider")
        cursor?.use { c ->
            if (!c.moveToFirst()) { android.util.Log.w("Accounts", "launcher profile: nobody picked"); null }
            else {
                val id = c.getString(c.getColumnIndexOrThrow("id"))?.takeIf { it.isNotBlank() }
                id?.let { LauncherPerson(it, c.getString(c.getColumnIndexOrThrow("name"))) }
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("Accounts", "launcher profile unavailable: ${e.javaClass.simpleName}"); null
    }
}

/**
 * Keeps the person's personal files and their Jellyfin user in step, so the same watchlist, layouts and history are on
 * every TV. Pulled once when the person is chosen; pushed a few seconds after anything personal changes, and when Cinema
 * goes to the background. The newest copy wins. The first time a TV meets a person who already has a saved copy, it
 * keeps anything of its own the saved copy does not have.
 */
class ProfileSyncer(private val context: Context, private val jellyfin: JellyfinClient, private val userId: String) {
    private val platform = AndroidPlatform(context)
    private val device = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)
    private val pool = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "profile-sync").apply { isDaemon = true } }
    @Volatile private var pending: ScheduledFuture<*>? = null
    private var syncedAt: Long
        get() = device.getLong("synced_$userId", 0L)
        set(v) { device.edit().putLong("synced_$userId", v).apply() }
    private var pushedHash: Int
        get() = device.getInt("pushed_$userId", 0)
        set(v) { device.edit().putInt("pushed_$userId", v).apply() }

    /** Kept strongly: Android holds change listeners weakly. */
    private val files = ProfileScope.SYNCED.map { AndroidPlatform.personalPrefs(context, it) }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> pushSoon() }

    /** Blocking: takes the person's saved copy when it is newer than this TV's. */
    fun pull() {
        val saved = jellyfin.profileData()?.let { ProfileSync.fromPrefs(it) } ?: return
        val (snapshot, at) = saved
        if (at <= syncedAt) return
        val firstTime = syncedAt == 0L
        if (if (firstTime) merge(snapshot) else ProfileSync.apply(platform, snapshot)) {
            syncedAt = at
            pushedHash = if (firstTime) 0 else ProfileSync.snapshot(platform).hashCode()
            android.util.Log.i("Accounts", "took the saved profile (${snapshot.length} chars)")
        }
    }

    /** The saved copy laid over this TV's: its values win, and this TV's other keys stay. */
    private fun merge(snapshot: String): Boolean = try {
        val local = org.json.JSONObject(ProfileSync.snapshot(platform))
        val remote = org.json.JSONObject(snapshot)
        remote.keys().forEach { file ->
            val into = local.optJSONObject(file) ?: org.json.JSONObject().also { local.put(file, it) }
            val from = remote.getJSONObject(file)
            from.keys().forEach { k -> into.put(k, from.get(k)) }
        }
        ProfileSync.apply(platform, local.toString())
    } catch (_: Exception) { false }

    fun start() { files.forEach { it.registerOnSharedPreferenceChangeListener(listener) }; pushSoon() }

    fun stop() { files.forEach { it.unregisterOnSharedPreferenceChangeListener(listener) } }

    fun pushSoon(delayMs: Long = 8_000) {
        pending?.cancel(false)
        pending = try { pool.schedule({ push() }, delayMs, TimeUnit.MILLISECONDS) } catch (_: Exception) { null }
    }

    fun pushNow() { pending?.cancel(false); try { pool.execute { push() } } catch (_: Exception) { } }

    private fun push() {
        if (jellyfin.currentUserId != userId) return
        val snapshot = ProfileSync.snapshot(platform)
        val hash = snapshot.hashCode()
        if (hash == pushedHash) return
        val at = System.currentTimeMillis()
        if (jellyfin.saveProfileData(ProfileSync.toPrefs(snapshot, at))) { syncedAt = at; pushedHash = hash }
        else android.util.Log.w("Accounts", "couldn't save the profile to Jellyfin")
    }
}

package com.danielportelabyrne.cinema.desktop.update

import com.danielportelabyrne.cinema.data.update.AvailableUpdate
import com.danielportelabyrne.cinema.data.update.UpdateCheck
import com.danielportelabyrne.cinema.desktop.AppDirs
import com.danielportelabyrne.cinema.platform.Log
import com.danielportelabyrne.cinema.platform.Platform
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Cinema for Linux and the Steam Deck updating itself, as the TV app does: the same GitHub releases (`build-<n>`) carry
 * the Flatpak bundle as [BUNDLE]; this finds a newer one, downloads it and installs it with the Deck's own `flatpak`
 * (through `flatpak-spawn --host`, which the manifest allows by talking to org.freedesktop.Flatpak). Flatpak installs
 * the new version beside the one running, which carries on untouched; the new build is used when Cinema next starts.
 * Every call that touches the network or runs flatpak blocks, so run them off the UI thread.
 */
class DeckUpdater(platform: Platform) {
    private val prefs = platform.preferences("updates")

    /** The build running, from the number the build wrote into the app (0 for a build made by hand). */
    val currentBuild: Long by lazy {
        javaClass.getResourceAsStream("/cinema-build.txt")?.bufferedReader()?.use { it.readText().trim().toLongOrNull() } ?: 0L
    }

    /** Running as the Flatpak (the only form that can install a newer bundle of itself). */
    val inFlatpak: Boolean get() = System.getenv("FLATPAK_ID") != null

    /** Whether the last check got an answer from GitHub (no release yet is still an answer). */
    @Volatile var reached = false
        private set

    /** Whether newer builds download and install by themselves. On unless turned off in Settings. */
    var auto: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(v) { prefs.edit().putBoolean(KEY_AUTO, v).apply() }

    /** A build installed by this app that is newer than the one running: it starts next time. */
    val waiting: Int get() = prefs.getInt(KEY_INSTALLED, 0).takeIf { it > currentBuild } ?: 0

    fun checkDue(): Boolean = System.currentTimeMillis() - prefs.getLong(KEY_CHECKED, 0L) > CHECK_EVERY_MS

    fun dismiss(build: Int) = prefs.edit().putInt(KEY_DISMISSED, build).apply()
    fun dismissed(build: Int): Boolean = prefs.getInt(KEY_DISMISSED, 0) >= build

    /** The newest release with a Deck bundle, or null when GitHub can't be reached or has none. */
    fun latest(): AvailableUpdate? = try {
        reached = false
        val c = URL(TEST_URL ?: UpdateCheck.LATEST_URL).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Cinema-Deck")
        try {
            val code = c.responseCode
            reached = code == 200 || code == 404
            if (code != 200) null else c.inputStream.bufferedReader().use { UpdateCheck.parse(it.readText(), BUNDLE, allowHttp = TEST_URL != null) }
        } finally { c.disconnect() }
    } catch (e: Exception) { Log.w(TAG, "update check failed: ${e.javaClass.simpleName}"); null }
        .also { prefs.edit().putLong(KEY_CHECKED, System.currentTimeMillis()).apply() }

    /** A newer build than the one running and any already installed, or null. */
    fun available(): AvailableUpdate? = latest()?.takeIf { UpdateCheck.isNewer(it, maxOf(currentBuild, waiting.toLong())) }

    /** Downloads [u]'s bundle into the app's cache (a folder the Deck's own flatpak can read), reporting progress 0..1. */
    fun download(u: AvailableUpdate, progress: (Float) -> Unit): File? {
        val dir = File(AppDirs.cache, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, "Cinema-${u.build}.flatpak")
        return try {
            var url = URL(u.apkUrl)
            var c: HttpURLConnection
            var hops = 0
            while (true) {
                c = url.openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false; c.connectTimeout = 10000; c.readTimeout = 60000
                c.setRequestProperty("User-Agent", "Cinema-Deck")
                val code = c.responseCode
                if (code in 300..399 && hops++ < 5) { url = URL(url, c.getHeaderField("Location")); c.disconnect(); continue }
                if (code != 200) { c.disconnect(); return null }
                break
            }
            val total = c.contentLengthLong.takeIf { it > 0 } ?: u.sizeBytes
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024); var done = 0L; var n: Int
                    while (input.read(buf).also { n = it } >= 0) {
                        out.write(buf, 0, n); done += n
                        if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            c.disconnect()
            file.takeIf { u.sizeBytes <= 0 || it.length() == u.sizeBytes }
        } catch (e: Exception) { Log.w(TAG, "update download failed: ${e.javaClass.simpleName}"); null }
    }

    /**
     * Installs [bundle] as build [build] with the Deck's flatpak, into the installation Cinema runs from (the user's, as
     * the README installs it, or the system's). Returns null when it worked, or what went wrong.
     */
    fun install(bundle: File, build: Int): String? {
        if (!inFlatpak) return "Updating works in the Flatpak; this is a build run by hand."
        val scope = if (systemInstall()) "--system" else "--user"
        val cmd = listOf("flatpak-spawn", "--host", "flatpak", "install", scope, "--reinstall", "--noninteractive", "-y", "--bundle", bundle.absolutePath)
        return try {
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(10, TimeUnit.MINUTES)) { p.destroy(); return "flatpak took too long" }
            if (p.exitValue() != 0) { Log.w(TAG, "flatpak install failed: ${out.takeLast(400)}"); return out.lines().lastOrNull { it.isNotBlank() } ?: "flatpak failed" }
            prefs.edit().putInt(KEY_INSTALLED, build).apply()
            bundle.delete()
            null
        } catch (e: Exception) { "Couldn't run flatpak: ${e.javaClass.simpleName}" }
    }

    /** Whether this Flatpak is the system's (under /var/lib/flatpak) rather than the user's, from its own info file. */
    private fun systemInstall(): Boolean = try {
        File("/.flatpak-info").readText().lineSequence().firstOrNull { it.startsWith("app-path=") }?.contains("/var/lib/flatpak/") == true
    } catch (_: Exception) { false }

    /** The build Cinema was updated to since it last ran, once (for "Updated to build 312"). */
    fun justUpdatedTo(): Long? {
        val seen = prefs.getLong(KEY_SEEN, 0L)
        prefs.edit().putLong(KEY_SEEN, currentBuild).apply()
        return currentBuild.takeIf { seen in 1 until it }
    }

    companion object {
        const val BUNDLE = "Cinema.flatpak"
        /** Tests only: CINEMA_UPDATE_URL stands in for GitHub's latest-release address (a release served on the Deck). */
        private val TEST_URL: String? = System.getenv("CINEMA_UPDATE_URL")?.takeIf { it.isNotBlank() }
        private const val TAG = "Updates"
        private const val KEY_CHECKED = "checked"
        private const val KEY_DISMISSED = "dismissed"
        private const val KEY_AUTO = "auto"
        private const val KEY_INSTALLED = "installed"
        private const val KEY_SEEN = "seen"
        private const val CHECK_EVERY_MS = 6 * 60 * 60_000L
    }
}

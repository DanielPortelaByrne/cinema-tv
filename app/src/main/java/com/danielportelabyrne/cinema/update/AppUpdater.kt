package com.danielportelabyrne.cinema.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.danielportelabyrne.cinema.data.update.AvailableUpdate
import com.danielportelabyrne.cinema.data.update.UpdateCheck
import com.danielportelabyrne.cinema.design.cinema.CinemaToast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cinema updating itself: asks GitHub for the newest build, downloads its app file and hands it to Android's package
 * installer. The first time, Android asks the viewer to confirm the install (and to let Cinema install apps); once Cinema
 * has installed itself like that, Android lets it install later builds of itself without asking, which is what automatic
 * updates use ([installQuietly]). Android does not let an app reopen itself after an update, so the viewer opens it
 * again. Every call that touches the network blocks, so run them off the main thread.
 */
class AppUpdater(private val context: Context) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    /** The build number of the Cinema running now (CI sets it from its run number; local builds are 1). */
    val currentBuild: Long by lazy {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    /** Whether the last check got an answer from GitHub (no release yet is still an answer). */
    @Volatile var reached = false
        private set

    /** The newest published build, or null when GitHub can't be reached or has nothing usable. */
    fun latest(): AvailableUpdate? = try {
        reached = false
        val c = URL(UpdateCheck.LATEST_URL).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Cinema-TV")
        try {
            val code = c.responseCode
            reached = code == 200 || code == 404
            if (code != 200) null else c.inputStream.bufferedReader().use { UpdateCheck.parse(it.readText()) }
        } finally { c.disconnect() }
    } catch (e: Exception) { Log.w(TAG, "update check failed: ${e.javaClass.simpleName}"); null }.also { prefs.edit().putLong(KEY_CHECKED, System.currentTimeMillis()).apply() }

    /** Whether newer builds download by themselves and install when Cinema is not on screen. On unless turned off. */
    var auto: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(v) { prefs.edit().putBoolean(KEY_AUTO, v).apply() }

    /**
     * Whether Android will install a new build without asking: Android 12 or later, Cinema allowed to install apps, and the
     * copy running installed by Cinema itself (one installed from a computer or a store still needs the viewer's yes once).
     */
    fun canInstallSilently(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) return false
        return try { pm.getInstallSourceInfo(context.packageName).installingPackageName == context.packageName } catch (_: Exception) { false }
    }

    /** A build already downloaded and waiting to be installed, if it is still newer than the one running. */
    fun ready(): Pair<Int, File>? {
        val build = prefs.getInt(KEY_READY, 0)
        val file = File(context.cacheDir, "updates/cinema-$build.apk")
        return if (build > currentBuild && file.exists()) build to file else null
    }

    fun markReady(build: Int) = prefs.edit().putInt(KEY_READY, build).apply()

    /** Installs the waiting build without asking, when Android allows it. Returns whether it started. */
    fun installQuietly(): Boolean {
        if (!auto || !canInstallSilently()) return false
        val (_, file) = ready() ?: return false
        return try { install(file); true } catch (e: Exception) { Log.w(TAG, "quiet install failed: ${e.javaClass.simpleName}"); false }
    }

    /**
     * The build Cinema was updated to since it last ran, once (for "Updated to build 312"); null when it was not updated
     * by itself.
     */
    fun justUpdatedTo(): Long? {
        val from = prefs.getLong(KEY_FROM, 0L)
        if (from == 0L) return null
        prefs.edit().remove(KEY_FROM).apply()
        return currentBuild.takeIf { it > from }
    }

    /** A newer build than this one, or null. */
    fun available(): AvailableUpdate? = latest()?.takeIf { UpdateCheck.isNewer(it, currentBuild) }

    /** Whether the automatic check is due: at most once every few hours, so opening Cinema never waits on it. */
    fun checkDue(): Boolean = System.currentTimeMillis() - prefs.getLong(KEY_CHECKED, 0L) > CHECK_EVERY_MS

    /** The viewer chose Later for [build]: the automatic prompt waits for a newer one (Settings still offers it). */
    fun dismiss(build: Int) = prefs.edit().putInt(KEY_DISMISSED, build).apply()
    fun dismissed(build: Int): Boolean = prefs.getInt(KEY_DISMISSED, 0) >= build

    /** Downloads [update]'s app file, reporting progress 0..1; returns the file, or null when it failed or came up short. */
    fun download(update: AvailableUpdate, progress: (Float) -> Unit): File? {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, "cinema-${update.build}.apk")
        return try {
            var url = URL(update.apkUrl)
            var c: HttpURLConnection
            // GitHub sends release files from another host: follow the redirects by hand (across hosts Java won't).
            var hops = 0
            while (true) {
                c = url.openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false; c.connectTimeout = 10000; c.readTimeout = 30000
                c.setRequestProperty("User-Agent", "Cinema-TV")
                val code = c.responseCode
                if (code in 300..399 && hops++ < 5) { url = URL(url, c.getHeaderField("Location")); c.disconnect(); continue }
                if (code != 200) { c.disconnect(); return null }
                break
            }
            val total = c.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024); var done = 0L; var n: Int
                    while (input.read(buf).also { n = it } >= 0) {
                        out.write(buf, 0, n); done += n
                        if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            c.disconnect()
            file.takeIf { update.sizeBytes <= 0 || it.length() == update.sizeBytes }
        } catch (e: Exception) { Log.w(TAG, "update download failed: ${e.javaClass.simpleName}"); null }
    }

    /**
     * Hands [apk] to Android's package installer. Android then asks the viewer to confirm (see [InstallResult]); once
     * installed, this process is replaced by the new build.
     */
    fun install(apk: File) {
        prefs.edit().putLong(KEY_FROM, currentBuild).apply()
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input -> session.openWrite("cinema.apk", 0, apk.length()).use { out -> input.copyTo(out); session.fsync(out) } }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val result = PendingIntent.getBroadcast(context, id, Intent(context, InstallResult::class.java), flags)
            session.commit(result.intentSender)
        }
    }

    /** What the installer says: asks the viewer to confirm when it must, and says so when it failed. */
    class InstallResult : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "update installed")
                else -> {
                    val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    Log.w(TAG, "update install failed ($status): $why")
                    if (status != PackageInstaller.STATUS_FAILURE_ABORTED) CinemaToast.show(context, "The update couldn't be installed", Toast.LENGTH_LONG)
                }
            }
        }
    }

    companion object {
        private const val TAG = "Updates"
        private const val KEY_CHECKED = "checked"
        private const val KEY_DISMISSED = "dismissed"
        private const val KEY_AUTO = "auto"
        private const val KEY_READY = "ready"
        private const val KEY_FROM = "updatingFrom"
        private const val CHECK_EVERY_MS = 6 * 60 * 60_000L
    }
}

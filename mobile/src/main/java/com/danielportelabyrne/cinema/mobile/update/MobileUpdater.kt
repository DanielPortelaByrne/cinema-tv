package com.danielportelabyrne.cinema.mobile.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import com.danielportelabyrne.cinema.data.update.AvailableUpdate
import com.danielportelabyrne.cinema.data.update.UpdateCheck
import com.danielportelabyrne.cinema.platform.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from Cinema's GitHub releases, like the TV's AppUpdater but reading the phone's own asset
 * ([ASSET]), so a TV build is never offered to a phone. Android asks the owner to confirm each install.
 */
class MobileUpdater(private val context: Context) {
    val currentBuild: Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    } catch (_: Exception) { 0L }

    /** The newer release, or null. Blocking. */
    fun check(): AvailableUpdate? = try {
        val c = URL(UpdateCheck.LATEST_URL).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json"); c.connectTimeout = 8000; c.readTimeout = 8000
        try {
            if (c.responseCode != 200) null
            else UpdateCheck.parse(c.inputStream.bufferedReader().readText(), ASSET)?.takeIf { UpdateCheck.isNewer(it, currentBuild) }
        } finally { c.disconnect() }
    } catch (e: Exception) { Log.w(TAG, "update check failed: ${e.javaClass.simpleName}"); null }

    /** Downloads and hands the APK to Android's installer. Blocking. */
    fun install(update: AvailableUpdate): Boolean = try {
        val apk = File(context.cacheDir, ASSET)
        (URL(update.apkUrl).openConnection() as HttpURLConnection).run {
            instanceFollowRedirects = true; connectTimeout = 10_000; readTimeout = 30_000
            try { inputStream.use { i -> apk.outputStream().use { i.copyTo(it) } } } finally { disconnect() }
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(context.packageName) }
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("cinema", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            s.commit(PendingIntent.getBroadcast(context, id, Intent(context, InstallResult::class.java), flags).intentSender)
        }
        true
    } catch (e: Exception) { Log.w(TAG, "update install failed: ${e.javaClass.simpleName}"); false }

    class InstallResult : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "update installed")
                PackageInstaller.STATUS_FAILURE_ABORTED -> {}
                else -> Toast.makeText(context, "The update couldn't be installed", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val ASSET = "cinema-mobile.apk"
        private const val TAG = "Updates"
    }
}

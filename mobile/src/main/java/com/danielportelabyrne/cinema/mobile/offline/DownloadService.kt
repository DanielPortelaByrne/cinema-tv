package com.danielportelabyrne.cinema.mobile.offline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import com.danielportelabyrne.cinema.mobile.MainActivity
import com.danielportelabyrne.cinema.mobile.MobileApp
import com.danielportelabyrne.cinema.platform.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Fetches the offline copies from Jellyfin, one at a time, with a notification showing how far along it is. It runs in
 * the foreground so it carries on with the screen off. Cinema does this itself rather than handing it to Android's
 * DownloadManager, which Android 17 does not let reach the home network. Wi-Fi (an unmetered network) only.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    private var job: Job? = null
    @Volatile private var stopping = false

    private val model get() = (application as MobileApp).model
    private val offline get() = model.offline
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        try {
            ServiceCompat.startForeground(this, PROGRESS_ID, notice(offline.next()), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (e: Exception) {
            // Android would not let it start from the background; opening Cinema starts it again.
            Log.w(TAG, "downloads not started: ${e.javaClass.simpleName}")
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive != true) job = scope.launch { work() }
        return START_STICKY
    }

    /** Android limits how long this may run each day; opening Cinema carries on from here. */
    override fun onTimeout(startId: Int, fgsType: Int) { stopping = true; stopSelf() }

    override fun onDestroy() {
        stopping = true
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun work() {
        // Held so that turning the screen off does not slow or stop the download.
        val wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cinema:downloads")
        @Suppress("DEPRECATION")
        val wifi = applicationContext.getSystemService(WifiManager::class.java)?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "cinema:downloads")
        var failures = 0
        try {
            wake.acquire(WAKE_LIMIT_MS)
            wifi?.acquire()
            while (!stopping) {
                val copy = offline.next() ?: break
                if (!unmetered()) {
                    offline.waiting(copy.id, copy.token, "Waiting for Wi-Fi")
                    show(copy.id)
                    delay(5000)
                    continue
                }
                if (!wake.isHeld) wake.acquire(WAKE_LIMIT_MS)
                val before = copy.partFile.length()
                try {
                    val size = withContext(Dispatchers.IO) { fetch(copy) }
                    failures = 0
                    if (size != null) { offline.finished(copy.id, copy.token, size); announce(copy) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "download stopped: ${e.javaClass.simpleName}: ${e.message?.substringBefore('?')}")
                    val code = (e as? Transfer.Refused)?.code
                    when {
                        code != null && code in 400..499 && code != 408 && code != 429 ->
                            offline.failed(copy.id, copy.token, if (code == 401) "Sign in to Jellyfin again." else "Jellyfin answered $code.")
                        offline.dir().usableSpace < SPARE_BYTES -> offline.failed(copy.id, copy.token, "Not enough space on this phone.")
                        else -> {
                            failures = if (copy.partFile.length() > before) 1 else failures + 1
                            offline.waiting(copy.id, copy.token, "Can't reach Jellyfin. Trying again.")
                            show(copy.id)
                            // Asking for the library lets Cinema notice that the server has moved to a new address.
                            if (failures == 3) withContext(Dispatchers.IO) { runCatching { model.client.library(true) } }
                            delay(1000L shl failures.coerceAtMost(5))
                        }
                    }
                }
            }
        } finally {
            if (wake.isHeld) wake.release()
            wifi?.let { if (it.isHeld) it.release() }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Fetches what is left of [copy]. Its size once the file is in place, or null when told to stop. Blocking. */
    private fun fetch(copy: OfflineCopy): Long? {
        val client = model.client
        val headers = client.playbackHeaders()
        if (headers.isEmpty()) throw Transfer.Refused(401)
        // A copy queued by an older build did not note which source it is.
        val source = copy.source ?: client.item(copy.id)?.sourceId ?: copy.id
        val url = JellyfinApi.directStreamUrl(JellyfinApi.cleanBase(client.server), copy.id, source)
        var markAt = SystemClock.elapsedRealtime()
        var markDone = -1L
        var speed = 0L
        var checkedAt = markAt
        var allowed = true
        val keepGoing = {
            val now = SystemClock.elapsedRealtime()
            if (now - checkedAt > 2000) { checkedAt = now; allowed = unmetered() }
            allowed && !stopping && offline.wants(copy.id, copy.token)
        }
        val whole = Transfer.fetch(url, headers, copy.partFile, copy.tagFile, keepGoing) { done, total ->
            val now = SystemClock.elapsedRealtime()
            val first = markDone < 0
            if (first || now - markAt >= 1000) {
                if (!first) {
                    val latest = (done - markDone) * 1000 / (now - markAt)
                    speed = if (speed == 0L) latest else (speed * 2 + latest) / 3
                }
                markAt = now; markDone = done
                val at = speed
                main.post { offline.progress(copy.id, copy.token, done, total, at); show(copy.id) }
            }
        }
        if (!whole) {
            // Deleted while it was arriving.
            if (offline.find(copy.id) == null) { copy.partFile.delete(); copy.tagFile.delete() }
            return null
        }
        if (!offline.wants(copy.id, copy.token)) return null
        val file = File(copy.file)
        file.delete()
        if (!copy.partFile.renameTo(file)) throw IOException("couldn't put the file in place")
        copy.tagFile.delete()
        return file.length()
    }

    private fun unmetered(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
    }

    // --- Notifications -------------------------------------------------------------------------

    private fun open(): PendingIntent =
        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun title(c: OfflineCopy) = listOfNotNull(c.name, c.subtitle).joinToString(" · ")

    private fun notice(c: OfflineCopy?): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(open()).setOngoing(true).setOnlyAlertOnce(true)
        if (c == null) return b.setContentTitle("Downloading").build()
        val queued = offline.copies.count { !it.complete && !it.failed } - 1
        val line = c.status ?: listOfNotNull(DownloadText.amount(c.doneBytes, c.sizeBytes), DownloadText.pace(c.speed, c.doneBytes, c.sizeBytes)).joinToString(" · ")
        return b.setContentTitle(title(c)).setContentText(line)
            .setSubText(if (queued > 0) "$queued more waiting" else null)
            .setProgress(100, (c.progress * 100).toInt(), c.doneBytes <= 0)
            .build()
    }

    private fun show(id: String) {
        if (stopping) return
        val c = offline.find(id)?.takeIf { !it.complete && !it.failed } ?: return
        notifications.notify(PROGRESS_ID, notice(c))
    }

    private fun announce(c: OfflineCopy) = notifications.notify(
        c.id.hashCode(),
        NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title(c)).setContentText("On this phone").setContentIntent(open()).setAutoCancel(true).build(),
    )

    companion object {
        private const val TAG = "Downloads"
        private const val CHANNEL = "downloads"
        private const val PROGRESS_ID = 41
        private const val WAKE_LIMIT_MS = 60 * 60_000L
        private const val SPARE_BYTES = 50_000_000L

        /** Starts fetching, if it is not already. Only works while Cinema is on screen. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
                .onFailure { Log.w(TAG, "downloads not started: ${it.javaClass.simpleName}") }
        }
    }
}

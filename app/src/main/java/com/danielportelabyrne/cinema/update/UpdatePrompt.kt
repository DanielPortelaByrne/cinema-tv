package com.danielportelabyrne.cinema.update

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.danielportelabyrne.cinema.data.update.AvailableUpdate
import com.danielportelabyrne.cinema.data.update.UpdateCheck
import com.danielportelabyrne.cinema.ui.room.InfoSheet
import com.danielportelabyrne.cinema.ui.room.SheetAction
import java.util.concurrent.Executors

/**
 * The update as the viewer sees it, in Cinema's own sheet: "Cinema build 312 is ready" with Install now and Later; then
 * the download's progress in the same sheet, and Android's install confirmation once it has arrived.
 */
class UpdatePrompt(private val activity: Activity, private val sheet: InfoSheet) {
    val updater = AppUpdater(activity)
    private val ui = Handler(Looper.getMainLooper())
    private val pool = Executors.newSingleThreadExecutor()
    @Volatile private var downloading = false

    /**
     * On opening Cinema, when a check is due. With automatic updates on (and Android letting Cinema install itself without
     * asking), a newer build downloads quietly and installs once Cinema is off screen (see CinemaApp). Otherwise it is
     * offered in the sheet, unless the viewer said Later to it.
     */
    fun checkInBackground(canShow: () -> Boolean) {
        if (!updater.checkDue() || downloading) return
        pool.execute {
            val u = updater.available() ?: return@execute
            if (updater.auto && updater.canInstallSilently()) {
                if (updater.ready()?.first != u.build && updater.download(u) { } != null) updater.markReady(u.build)
                return@execute
            }
            if (updater.dismissed(u.build)) return@execute
            showWhenFree(u, canShow, tries = 20)
        }
    }

    /** Debug builds: [u] as if GitHub had just published it, taking the same path as a real one. */
    fun debugArrived(u: AvailableUpdate) {
        if (updater.auto && updater.canInstallSilently()) pool.execute { if (updater.download(u) { } != null) { updater.markReady(u.build); android.util.Log.i("Updates", "build ${u.build} ready") } }
        else offer(u)
    }

    /** After Cinema updated itself: says so, once, when it next opens. */
    fun sayIfUpdated() {
        val build = updater.justUpdatedTo() ?: return
        ui.postDelayed({ if (!activity.isFinishing) com.danielportelabyrne.cinema.design.cinema.CinemaToast.show(activity, "Cinema updated to build $build", android.widget.Toast.LENGTH_LONG) }, 1500)
    }

    /** Offers [u] as soon as nothing else is on screen (setup, another sheet, the opening): looks again every half minute. */
    private fun showWhenFree(u: AvailableUpdate, canShow: () -> Boolean, tries: Int) {
        ui.post {
            if (activity.isFinishing || downloading) return@post
            if (canShow()) offer(u) else if (tries > 0) ui.postDelayed({ showWhenFree(u, canShow, tries - 1) }, 30_000)
        }
    }

    /** Looks now (Settings' Check for updates); [done] gets the newer build, or null when this one is the newest. */
    fun checkNow(done: (AvailableUpdate?, reached: Boolean) -> Unit) {
        pool.execute {
            val latest = updater.latest()
            ui.post { done(latest?.takeIf { UpdateCheck.isNewer(it, updater.currentBuild) }, updater.reached) }
        }
    }

    fun offer(u: AvailableUpdate) {
        sheet.show("UPDATE", "Cinema build ${u.build} is ready", UpdateCheck.headline(u), listOfNotNull(
            "You're on build ${updater.currentBuild}. It downloads in a moment; then the TV asks you to confirm the install. " +
                "Cinema closes while it updates: open it again from the home screen.",
            if (updater.auto) "After this one, updates install by themselves while Cinema isn't on screen." else null
        ), null, listOf(
            SheetAction("Install now", "Download it (${mb(u.sizeBytes)}) and install it", primary = true) { install(u) },
            SheetAction("Later", "Ask again when there's a newer build") { updater.dismiss(u.build) }
        ))
    }

    /** Downloads [u], showing its progress in the sheet, then hands it to Android's installer. */
    fun install(u: AvailableUpdate) {
        if (downloading) return
        downloading = true
        var shown = -1
        fun progress(f: Float) {
            val pct = (f * 100).toInt() / 5 * 5
            if (pct == shown) return
            shown = pct
            ui.post { if (!activity.isFinishing) sheet.show("UPDATE", "Downloading build ${u.build}", "$pct%", listOf("You can carry on watching; the TV asks you to confirm the install once it's here."), refresh = shown > 0) }
        }
        progress(0f)
        pool.execute {
            val file = updater.download(u) { progress(it) }
            downloading = false
            ui.post {
                if (activity.isFinishing) return@post
                if (file == null) { sheet.show("UPDATE", "The update didn't download", null, listOf("Check the TV's connection and try again from Settings."), refresh = true); return@post }
                sheet.hide()
                try { updater.install(file) } catch (e: Exception) {
                    sheet.show("UPDATE", "The update couldn't start", null, listOf("Android refused the install: ${e.javaClass.simpleName}."))
                }
            }
        }
    }

    private fun mb(bytes: Long) = if (bytes > 0) "${(bytes + 500_000) / 1_000_000} MB" else "a few MB"
}

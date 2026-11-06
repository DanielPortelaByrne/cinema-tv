package com.danielportelabyrne.cinema.desktop.update

import com.danielportelabyrne.cinema.data.update.AvailableUpdate
import com.danielportelabyrne.cinema.data.update.UpdateCheck
import com.danielportelabyrne.cinema.desktop.CinemaModel
import com.danielportelabyrne.cinema.desktop.Sheet
import com.danielportelabyrne.cinema.desktop.SheetAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Updates as the Deck's viewer sees them, in Cinema's own sheet and toasts. With automatic updates on, a newer build
 * downloads and installs in the background and a toast says it starts next time; otherwise the sheet offers it, with
 * Install now and Later. Installing never stops what is playing: the new build is used when Cinema next starts.
 */
class DeckUpdates(private val m: CinemaModel) {
    val updater = DeckUpdater(m.platform)
    @Volatile private var busy = false

    /** On starting: says if Cinema was updated, then looks for a newer build when a check is due. */
    fun onStart() {
        updater.justUpdatedTo()?.let { b -> m.scope.launch(m.ui) { delay(2500); m.toast("Cinema updated to build $b") } }
        if (!updater.inFlatpak || !updater.checkDue()) return
        m.scope.launch {
            val u = withContext(Dispatchers.IO) { updater.available() } ?: return@launch
            if (updater.auto) installQuietly(u)
            else if (!updater.dismissed(u.build)) withContext(m.ui) { whenFree { offer(u) } }
        }
    }

    /** Settings' Check for updates: [done] gets the newer build (null when this is the newest) and whether GitHub answered. */
    fun checkNow(done: (AvailableUpdate?, Boolean) -> Unit) {
        m.scope.launch {
            val u = withContext(Dispatchers.IO) { updater.available() }
            withContext(m.ui) { done(u, updater.reached) }
        }
    }

    fun offer(u: AvailableUpdate) {
        m.sheet = Sheet("Update", "Cinema build ${u.build} is ready", UpdateCheck.headline(u), listOf(
            "You're on build ${updater.currentBuild}. It downloads and installs in the background; you can carry on watching. " +
                "The new build is used next time Cinema opens.",
        ), listOf(
            SheetAction("Install now", "Download it (${mb(u.sizeBytes)}) and install it", primary = true) { install(u) },
            SheetAction("Later", "Ask again when there's a newer build") { updater.dismiss(u.build) },
        ))
    }

    /** Downloads and installs [u], with its progress in toasts, then says it starts next time (with a way to quit now). */
    fun install(u: AvailableUpdate) {
        if (busy) return
        busy = true
        m.scope.launch {
            var shown = -1
            val file = withContext(Dispatchers.IO) {
                updater.download(u) { f ->
                    val pct = (f * 100).toInt() / 10 * 10
                    if (pct != shown) { shown = pct; m.scope.launch(m.ui) { m.toast("Downloading build ${u.build} · $pct%") } }
                }
            }
            if (file == null) { busy = false; withContext(m.ui) { m.toast("The update didn't download. Try again from Settings.") }; return@launch }
            withContext(m.ui) { m.toast("Installing build ${u.build}…") }
            val error = withContext(Dispatchers.IO) { updater.install(file, u.build) }
            busy = false
            withContext(m.ui) {
                if (error != null) { m.sheet = Sheet("Update", "The update couldn't be installed", null, listOf(error), emptyList()); return@withContext }
                m.sheet = Sheet("Update", "Build ${u.build} is installed", null, listOf("It's used next time Cinema opens."), listOf(
                    SheetAction("Quit Cinema now", "Open it again to use the new build", primary = true) { m.quit() },
                    SheetAction("Keep going", "Switch over next time") { },
                ))
            }
        }
    }

    /** Automatic: downloads and installs with no sheet; a toast says it is ready for next time. */
    private suspend fun installQuietly(u: AvailableUpdate) {
        if (busy) return
        busy = true
        try {
            val file = withContext(Dispatchers.IO) { updater.download(u) { } } ?: return
            val error = withContext(Dispatchers.IO) { updater.install(file, u.build) }
            withContext(m.ui) { m.toast(if (error == null) "Cinema build ${u.build} is installed: it starts next time" else "An update couldn't be installed") }
        } finally { busy = false }
    }

    /** Runs [show] once no sheet, keyboard or film is in the way: looks again every half minute, for up to ten minutes. */
    private suspend fun whenFree(show: () -> Unit) {
        repeat(20) {
            if (m.sheet == null && m.keyboard == null && m.streamLoading == null && m.playerState is com.danielportelabyrne.cinema.desktop.player.PlayerState.Idle) { show(); return }
            delay(30_000)
        }
    }

    private fun mb(bytes: Long) = if (bytes > 0) "${(bytes + 500_000) / 1_000_000} MB" else "about 100 MB"
}

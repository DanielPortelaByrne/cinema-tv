package com.danielportelabyrne.cinema.desktop.update

import com.danielportelabyrne.cinema.desktop.DesktopPlatform

/**
 * `cinema --update-test [--install]`: the update path without the screen, for the Deck over SSH. Prints the build
 * running, what GitHub (or CINEMA_UPDATE_URL) offers, then downloads it and, with --install, installs it with flatpak.
 * Exit code 0 when every step it was asked for worked.
 */
object UpdateTest {
    fun run(install: Boolean): Int {
        val u = DeckUpdater(DesktopPlatform())
        println("Running build ${u.currentBuild}; in the Flatpak: ${u.inFlatpak}; automatic updates: ${u.auto}")
        val latest = u.latest()
        println("Latest release: ${latest?.let { "build ${it.build} (${it.sizeBytes} bytes) at ${it.apkUrl}" } ?: "none"}; GitHub answered: ${u.reached}")
        if (latest == null) return if (u.reached) 0 else 1
        var last = -1
        val file = u.download(latest) { f -> val p = (f * 100).toInt() / 25 * 25; if (p != last) { last = p; println("Downloaded $p%") } }
        if (file == null) { println("Download failed"); return 1 }
        println("Downloaded ${file.length()} bytes to ${file.absolutePath}")
        if (!install) return 0
        val error = u.install(file, latest.build)
        println(if (error == null) "Installed build ${latest.build}" else "Install failed: $error")
        return if (error == null) 0 else 1
    }
}

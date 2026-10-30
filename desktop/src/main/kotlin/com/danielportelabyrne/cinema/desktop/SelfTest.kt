package com.danielportelabyrne.cinema.desktop

import com.danielportelabyrne.cinema.desktop.player.MpvIpc
import com.danielportelabyrne.cinema.desktop.player.MpvLaunch
import com.danielportelabyrne.cinema.desktop.player.MpvMessage
import com.danielportelabyrne.cinema.desktop.player.Player
import com.studiohartman.jamepad.Configuration
import com.studiohartman.jamepad.ControllerManager
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * `cinema --self-test [file or URL]`: checks, without opening a window, that what Cinema needs works on this machine.
 * mpv starts and answers on its IPC socket, plays something (a generated test picture by default), reports its
 * position, seeks and quits; then SDL lists the game controllers it can see. Prints one line per check and exits
 * non-zero if any fails. Handy on a friend's Steam Deck, over SSH or in Desktop Mode's terminal.
 */
object SelfTest {
    private var failures = 0

    private fun check(name: String, ok: Boolean, detail: String = "") {
        if (!ok) failures++
        println("${if (ok) "PASS" else "FAIL"}  $name${if (detail.isNotBlank()) "  ($detail)" else ""}")
    }

    fun run(media: String?): Int {
        println("Cinema self-test")
        playback(media ?: "av://lavfi:testsrc2=size=1280x720:rate=24:duration=30")
        controllers()
        println(if (failures == 0) "All checks passed" else "$failures check(s) failed")
        return if (failures == 0) 0 else 1
    }

    private fun playback(media: String) {
        val mpv = Player.defaultMpv()
        val socket = File(AppDirs.runtime, "selftest-${ProcessHandle.current().pid()}.sock").apply { delete() }
        val log = File(AppDirs.cache, "selftest-mpv.log")
        // Headless: no window, no sound. Later options win in mpv, so these override the player's own.
        val args = MpvLaunch.args(mpv, socket.path, System.getenv()) + listOf("--vo=null", "--ao=null", "--fullscreen=no", "--force-window=no")
        val process = try { ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log).start() } catch (e: Exception) {
            check("mpv starts", false, "${e.javaClass.simpleName}: ${e.message}"); return
        }
        check("mpv starts", true, mpv)
        val messages = LinkedBlockingQueue<MpvMessage>()
        val ipc = MpvIpc.connect(socket.toPath(), 8000, { process.isAlive }, { messages += it }, {})
        check("mpv answers on its IPC socket", ipc != null, if (ipc == null) "see $log" else "")
        if (ipc == null) { process.destroy(); return }
        ipc.send("observe_property", 1, "time-pos")
        ipc.send("loadfile", media, "replace")

        fun waitFor(timeoutMs: Long, test: (MpvMessage) -> Boolean): MpvMessage? {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val m = messages.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS) ?: return null
                if (test(m)) return m
                if (m is MpvMessage.Event && m.name == "end-file" && m.reason == "error") return m
            }
            return null
        }
        fun pos(m: MpvMessage?) = ((m as? MpvMessage.Property)?.value as? Number)?.toDouble()

        val loaded = waitFor(15_000) { it is MpvMessage.Event && it.name == "file-loaded" }
        check("plays a file", loaded is MpvMessage.Event && loaded.name == "file-loaded", if (loaded is MpvMessage.Event && loaded.reason == "error") "mpv: ${loaded.error}" else media)
        val moving = waitFor(10_000) { it is MpvMessage.Property && it.name == "time-pos" && (pos(it) ?: 0.0) >= 1.0 }
        check("reports its position", moving != null, pos(moving)?.let { "%.1f s".format(it) } ?: "")
        ipc.send("seek", 10, "relative")
        val sought = waitFor(10_000) { it is MpvMessage.Property && it.name == "time-pos" && (pos(it) ?: 0.0) >= 10.0 }
        check("seeks", sought != null, pos(sought)?.let { "now at %.1f s".format(it) } ?: "")
        ipc.send("quit")
        val exited = process.waitFor(5, TimeUnit.SECONDS)
        check("quits when asked", exited)
        if (!exited) process.destroyForcibly()
        socket.delete()
    }

    private fun controllers() {
        try {
            val manager = ControllerManager(Configuration().apply { maxNumControllers = 4 })
            manager.initSDLGamepad()
            // SDL needs a moment to open the devices it finds.
            repeat(30) { manager.update(); Thread.sleep(33) }
            val names = (0 until 4).mapNotNull { i -> manager.getState(i).takeIf { it.isConnected }?.controllerType }
            check("SDL gamepad support loads", true)
            println("      controllers seen: ${names.ifEmpty { listOf("none (in Game Mode, Steam's virtual pad appears once Cinema is launched from Steam)") }.joinToString(", ")}")
            manager.quitSDLGamepad()
        } catch (e: Throwable) {
            check("SDL gamepad support loads", false, "${e.javaClass.simpleName}: ${e.message}")
        }
    }
}

/**
 * `cinema --player-demo [file or URL]`: plays something in the real player, with the player controls over it and made-up
 * details (the film's title, year, cast of genres and a synopsis) in place of a library item, until mpv is closed. For
 * looking at the controls on a Deck without a Jellyfin server; keys can be sent to mpv's socket by hand.
 */
object PlayerDemo {
    fun run(media: String): Int {
        val done = java.util.concurrent.CountDownLatch(1)
        val player = com.danielportelabyrne.cinema.desktop.player.Player(
            com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient(DesktopPlatform()), { state -> println("player: $state") }, { done.countDown() },
        )
        player.playUrl(media, "Dark · S1 E5", com.danielportelabyrne.cinema.desktop.player.chrome.ChromeMeta(
            title = "Dark · S1 E5", year = 2017, genres = listOf("Crime", "Drama", "Mystery"), runtimeMin = 45, rating = 8.5,
            overview = "Hannah takes her obsession with Ulrich too far. The stranger asks Regina to deliver an important package. Martha is torn between Jonas and Bartosz.",
            sizeBytes = 520_000_000,
        ))
        done.await()
        return 0
    }
}

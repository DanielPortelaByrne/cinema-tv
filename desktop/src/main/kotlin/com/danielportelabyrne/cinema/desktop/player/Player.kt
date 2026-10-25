package com.danielportelabyrne.cinema.desktop.player

import com.danielportelabyrne.cinema.platform.Log
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinPlaybackSession
import com.danielportelabyrne.cinema.desktop.AppDirs
import com.danielportelabyrne.cinema.desktop.input.NavAction
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** What the screen behind the player shows. */
sealed class PlayerState {
    data object Idle : PlayerState()
    data class Starting(val title: String) : PlayerState()
    data class Playing(val title: String, val positionMs: Long, val durationMs: Long, val paused: Boolean) : PlayerState()
    data class Failed(val message: String) : PlayerState()
}

/** What the controls do while the film is on screen. Kept as a table so Settings can show it and tests can check it. */
object PlayerControls {
    val commands: Map<NavAction, List<Any>> = mapOf(
        NavAction.SELECT to listOf("cycle", "pause"),
        NavAction.LEFT to listOf("seek", -10, "relative"),
        NavAction.RIGHT to listOf("seek", 10, "relative"),
        NavAction.UP to listOf("seek", 60, "relative"),
        NavAction.DOWN to listOf("seek", -60, "relative"),
        NavAction.PREV to listOf("add", "chapter", -1),
        NavAction.NEXT to listOf("add", "chapter", 1),
        NavAction.ALT to listOf("cycle", "sub"),
        NavAction.SEARCH to listOf("cycle", "audio"),
        NavAction.MENU to listOf("show-progress"),
        NavAction.INFO to listOf("show-text", "\${media-title}\n\${time-pos} / \${duration}   Audio: \${current-tracks/audio/lang:-}   Subs: \${current-tracks/sub/lang:off}", 4000),
    )

    val help: List<Pair<String, String>> = listOf(
        "A" to "Pause / play", "B" to "Stop", "← →" to "Back / forward 10 s", "↑ ↓" to "Forward / back 1 min",
        "LB RB" to "Previous / next chapter", "X" to "Subtitles", "Y" to "Audio track", "≡" to "Show progress", "⧉" to "Details",
    )
}

/**
 * Plays Jellyfin titles in mpv, one window at a time, and reports to Jellyfin (Playing, then Progress every
 * [PROGRESS_EVERY_S] seconds and on pause, then Stopped) so resume points and watched marks follow the viewer.
 *
 * mpv is started idle and handed the stream over its IPC socket, so the sign-in token travels in a header set over
 * the socket and never appears on mpv's command line. When an episode ends, the next one in the season starts in the
 * same window. If the original file will not open, Jellyfin's converted copy is tried once. All the bookkeeping
 * happens on one thread ([worker]), so events from mpv and presses from the pad never race.
 */
class Player(
    private val client: JellyfinClient,
    private val onState: (PlayerState) -> Unit,
    private val onClosed: () -> Unit,
    private val mpvPath: String = defaultMpv(),
) {
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "player").apply { isDaemon = true } }
    private var process: Process? = null
    private var ipc: MpvIpc? = null
    private var progressTask: ScheduledFuture<*>? = null

    private var item: JellyfinItem? = null
    private var next: JellyfinItem? = null
    private var triedConverted = false
    private var session = JellyfinPlaybackSession()
    private var positionMs = 0L
    private var durationMs = 0L
    private var paused = false
    private var loaded = false
    /** What is playing when it is not a library item (Stream now). */
    private var streamTitle: String? = null

    @Volatile var active: Boolean = false
        private set

    /** Starts [target] (a film, an episode, or a series, which plays its next episode). Blocking network calls run on [worker]. */
    fun play(target: JellyfinItem, fromStart: Boolean = false) {
        active = true
        lastState = PlayerState.Starting(target.seriesName ?: target.name)
        onState(lastState)
        worker.execute {
            val playable = if (target.isSeries) client.episodeToPlay(target.id) else target
            if (playable == null) { fail("Nothing to play in ${target.name} yet"); return@execute }
            // The list the viewer chose from may be minutes old: ask the server where they really left off.
            val fresh = client.item(playable.id) ?: playable
            val startMs = if (fromStart || fresh.played) 0L else fresh.resumeMs
            if (!ensureMpv()) return@execute
            load(fresh, startMs, converted = false)
        }
    }

    /**
     * Plays an address that is not in the library (the stream service's copy, for Stream now) in the same window. Nothing
     * is reported to Jellyfin, and it does not move on to anything when it ends.
     */
    fun playUrl(url: String, title: String) {
        active = true
        lastState = PlayerState.Starting(title)
        onState(lastState)
        worker.execute {
            if (!ensureMpv()) return@execute
            item = null; next = null; loaded = false; triedConverted = true; streamTitle = title
            positionMs = 0; durationMs = 0; paused = false
            sendChromeMeta(null, title)
            ipc?.apply {
                send("set_property", "http-header-fields", org.json.JSONArray())
                send("set_property", "force-media-title", title)
                send("set_property", "start", "none")
                send("set_property", "pause", false)
                send("loadfile", url, "replace")
            }
        }
    }

    /** A press while the film is on screen. B stops; the rest go straight to mpv. */
    fun handle(action: NavAction) {
        if (!active) return
        worker.execute {
            ipc?.send("script-message", "cinema-activity")
            if (action == NavAction.BACK) { ipc?.send("stop"); return@execute }
            PlayerControls.commands[action]?.let { ipc?.send(*it.toTypedArray()) }
        }
    }

    /** Stops playback and closes mpv (Quit, or the app closing). */
    fun shutdown() {
        worker.execute { ipc?.send("quit") }
        worker.schedule({ process?.destroy() }, 2, TimeUnit.SECONDS)
    }

    // --- mpv -------------------------------------------------------------------------------------

    private fun themeScriptPath(): String? {
        val resource = javaClass.getResourceAsStream("/mpv/cinema-osc.lua") ?: return null
        val script = File(AppDirs.cache, "mpv/cinema-osc.lua")
        return try {
            script.parentFile?.mkdirs()
            resource.use { input -> script.outputStream().use(input::copyTo) }
            script.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Could not install Cinema's mpv theme: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun ensureMpv(): Boolean {
        if (process?.isAlive == true && ipc != null) return true
        val socket = File(AppDirs.runtime, "mpv-${ProcessHandle.current().pid()}.sock").apply { delete() }
        val args = MpvLaunch.args(mpvPath, socket.path, System.getenv(), themeScriptPath())
        val p = try {
            ProcessBuilder(args).redirectErrorStream(true).redirectOutput(File(AppDirs.cache, "mpv.log")).start()
        } catch (e: Exception) {
            fail("Couldn't start the video player (mpv): ${e.message}"); return false
        }
        process = p
        p.onExit().thenRun { worker.execute { mpvExited(p) } }
        ipc = MpvIpc.connect(socket.toPath(), 8000, { p.isAlive }, { m -> worker.execute { message(m) } }, {})
        if (ipc == null) { p.destroy(); fail("The video player didn't start. See ${File(AppDirs.cache, "mpv.log")}"); return false }
        ipc!!.apply {
            send("observe_property", 1, "time-pos")
            send("observe_property", 2, "pause")
            send("observe_property", 3, "duration")
        }
        return true
    }

    private fun load(target: JellyfinItem, startMs: Long, converted: Boolean) {
        item = target; next = null; loaded = false; triedConverted = converted; streamTitle = null
        positionMs = startMs; durationMs = 0; paused = false
        session = JellyfinPlaybackSession()
        val title = MpvLaunch.title(target)
        val url = if (converted) client.transcodeUrl(target) else client.streamUrl(target)
        sendChromeMeta(target, title)
        ipc?.apply {
            send("set_property", "http-header-fields", org.json.JSONArray(client.playbackHeaders().map { (k, v) -> "$k: $v" }))
            send("set_property", "force-media-title", title)
            send("set_property", "start", if (startMs > 0) "%.3f".format(java.util.Locale.ROOT, startMs / 1000.0) else "none")
            send("set_property", "pause", false)
            send("loadfile", url, "replace")
        }
        onState(PlayerState.Starting(title))
    }

    /** Give the Lua chrome the same library metadata that Android's PlayerChrome displays. */
    private fun sendChromeMeta(target: JellyfinItem?, title: String) {
        val metadata = org.json.JSONObject().put("title", title)
        target?.year?.let { metadata.put("year", it) }
        target?.runtimeMin?.let { metadata.put("runtimeMin", it) }
        target?.officialRating?.let { metadata.put("certificate", it) }
        target?.communityRating?.let { metadata.put("rating", it) }
        target?.overview?.takeIf { it.isNotBlank() }?.let { metadata.put("overview", it) }
        target?.genres?.takeIf { it.isNotEmpty() }?.let { metadata.put("genres", org.json.JSONArray(it)) }
        target?.sizeBytes?.let { metadata.put("sizeBytes", it) }
        target?.audioCodec?.let { metadata.put("audioCodec", it) }
        ipc?.send("script-message", "cinema-meta", metadata.toString())
    }

    private fun message(m: MpvMessage) {
        when (m) {
            is MpvMessage.Property -> when (m.name) {
                "time-pos" -> (m.value as? Number)?.let { positionMs = (it.toDouble() * 1000).toLong(); publish() }
                "duration" -> (m.value as? Number)?.let { durationMs = (it.toDouble() * 1000).toLong(); publish() }
                "pause" -> (m.value as? Boolean)?.let { if (it != paused) { paused = it; if (loaded) report("Progress") }; publish() }
            }
            is MpvMessage.Event -> when (m.name) {
                "file-loaded" -> fileLoaded()
                "end-file" -> fileEnded(m.reason, m.error)
            }
        }
    }

    private fun fileLoaded() {
        loaded = true
        report("Playing")
        progressTask?.cancel(false)
        progressTask = worker.scheduleAtFixedRate({ if (loaded) report("Progress") }, PROGRESS_EVERY_S, PROGRESS_EVERY_S, TimeUnit.SECONDS)
        val current = item ?: return
        // Ready before the credits, so moving on never waits on the server.
        if (current.isEpisode) worker.execute { if (item?.id == current.id) next = client.followingEpisode(current) }
        publish()
    }

    private fun fileEnded(reason: String?, error: String?) {
        progressTask?.cancel(false)
        val current = item
        val wasLoaded = loaded
        loaded = false
        when (reason) {
            "eof" -> {
                if (wasLoaded) report("Stopped", atEnd = true)
                client.invalidateLibrary()
                val following = next
                if (following != null) {
                    ipc?.send("show-text", "Next: ${MpvLaunch.title(following)}", 3000)
                    load(following, 0L, converted = false)
                } else ipc?.send("quit")
            }
            "error" -> {
                Log.w(TAG, "mpv could not play the file: ${error ?: "unknown error"}")
                if (current != null && !triedConverted) {
                    ipc?.send("show-text", "Trying a converted copy…", 3000)
                    load(current, positionMs, converted = true)
                } else { ipc?.send("quit"); fail("This title wouldn't play${error?.let { " ($it)" } ?: ""}") }
            }
            "redirect" -> {}
            else -> { if (wasLoaded) report("Stopped"); client.invalidateLibrary(); ipc?.send("quit") }
        }
    }

    private fun mpvExited(p: Process) {
        if (process !== p) return
        if (loaded) report("Stopped")
        progressTask?.cancel(false)
        loaded = false; process = null; ipc?.close(); ipc = null; item = null; next = null
        client.invalidateLibrary()
        val failed = p.exitValue() != 0 && p.exitValue() != 4 /* mpv: quit by the user */
        active = false
        if (failed && lastState !is PlayerState.Failed) Log.w(TAG, "mpv exited with ${p.exitValue()}")
        if (lastState !is PlayerState.Failed) onState(PlayerState.Idle)
        onClosed()
    }

    @Volatile private var lastState: PlayerState = PlayerState.Idle

    private fun publish() {
        val title = item?.let { MpvLaunch.title(it) } ?: streamTitle ?: return
        lastState = PlayerState.Playing(title, positionMs, durationMs, paused)
        onState(lastState)
    }

    private fun fail(message: String) {
        Log.w(TAG, message)
        lastState = PlayerState.Failed(message)
        onState(lastState)
        if (process?.isAlive != true) { active = false; onClosed() }
    }

    // --- Jellyfin reports ------------------------------------------------------------------------

    private fun report(kind: String, atEnd: Boolean = false) {
        val i = item ?: return
        if (!session.accept(kind)) return
        val position = if (atEnd && durationMs > 0) durationMs else positionMs
        val body = JellyfinApi.playbackBody(i.id, i.sourceId, position, paused, session.id)
        val url = JellyfinApi.reportUrl(JellyfinApi.cleanBase(client.server), kind)
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.connectTimeout = 5000; c.readTimeout = 5000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            client.playbackHeaders().forEach { (k, v) -> c.setRequestProperty(k, v) }
            try {
                c.outputStream.use { it.write(body.toByteArray()) }
                if (c.responseCode !in 200..299) Log.w(TAG, "Playback $kind report failed (HTTP ${c.responseCode})")
            } finally { c.disconnect() }
        } catch (e: Exception) { Log.w(TAG, "Playback $kind report failed: ${e.javaClass.simpleName}") }
    }

    companion object {
        private const val TAG = "Player"
        private const val PROGRESS_EVERY_S = 10L

        /** mpv inside the Flatpak, else CINEMA_MPV, else whatever is on the PATH. */
        fun defaultMpv(): String = System.getenv("CINEMA_MPV")?.takeIf { it.isNotBlank() }
            ?: "/app/bin/mpv".takeIf { File(it).canExecute() }
            ?: "mpv"
    }
}

/** How mpv is started. Pure, so the choices can be tested. */
object MpvLaunch {
    fun args(mpv: String, socket: String, env: Map<String, String>, themeScript: String? = null): List<String> {
        val args = mutableListOf(
            mpv,
            "--no-config",                    // the viewer's own mpv setup must not change how Cinema behaves
            "--idle=yes", "--force-window=yes", "--fullscreen", "--ontop=yes", "--no-terminal",
            // OSC is mpv's Lua controller; keep it visible for gamepad users who do not move a mouse.
            "--osc=yes", "--script-opts=osc-visibility=always",
            "--input-default-bindings=yes", "--input-vo-keyboard=yes",
            "--input-ipc-server=$socket",
            "--title=Cinema", "--keep-open=no", "--save-position-on-quit=no",
            "--hwdec=auto-safe",
            "--cache=yes", "--demuxer-max-bytes=256MiB", "--demuxer-readahead-secs=30",
            "--osd-font=Inter", "--osd-font-size=42", "--osd-bar-h=2.5", "--osd-duration=1500",
            "--osd-color=#F3E9CF", "--osd-border-color=#C0201810", "--osd-bar-align-y=0.9",
            "--sub-font=Inter", "--sub-font-size=46",
            "--slang=${preferredLangs(env)}", "--alang=${preferredLangs(env)}",
            "--background-color=#1A140E",
            "--cursor-autohide=1000",
        )
        if (!themeScript.isNullOrBlank()) {
            args.remove("--osc=yes")
            args.remove("--script-opts=osc-visibility=always")
            args += "--osc=no"
            args += "--script=$themeScript"
        }
        // Steam's Game Mode (gamescope) shows X11 windows; a Wayland window from mpv would never be shown there.
        if (isGamescope(env)) args += "--gpu-context=x11egl"
        return args
    }

    fun isGamescope(env: Map<String, String>): Boolean =
        env["XDG_CURRENT_DESKTOP"].equals("gamescope", ignoreCase = true) || env["GAMESCOPE_WAYLAND_DISPLAY"] != null || env["SteamGamepadUI"] == "1"

    /** The desktop's language first (from LANG), then English, for picking the soundtrack and subtitles. */
    fun preferredLangs(env: Map<String, String>): String {
        val lang = env["LANG"]?.substringBefore('.')?.substringBefore('_')?.lowercase()?.takeIf { it.length in 2..3 && it != "c" }
        return listOfNotNull(lang, "en", "eng").distinct().joinToString(",")
    }

    fun title(item: JellyfinItem): String =
        if (item.isEpisode) listOfNotNull(item.seriesName, item.episodeLabel(), item.name).joinToString(" · ") else listOfNotNull(item.name, item.year?.toString()).joinToString(" · ")
}

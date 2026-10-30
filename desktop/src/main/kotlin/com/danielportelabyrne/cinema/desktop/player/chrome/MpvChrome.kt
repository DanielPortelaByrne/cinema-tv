package com.danielportelabyrne.cinema.desktop.player.chrome

import com.danielportelabyrne.cinema.data.player.BitrateHistory
import com.danielportelabyrne.cinema.data.player.Chapter
import com.danielportelabyrne.cinema.data.player.PlayerMath
import com.danielportelabyrne.cinema.data.player.PlayerUi
import com.danielportelabyrne.cinema.desktop.input.NavAction
import com.danielportelabyrne.cinema.platform.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** What the chrome shows about the film; only what is known (the TV's PlayerMeta). */
data class ChromeMeta(
    val title: String = "", val year: Int? = null, val genres: List<String> = emptyList(), val runtimeMin: Int? = null,
    val certificate: String? = null, val rating: Double? = null, val overview: String? = null, val sizeBytes: Long? = null,
)

/** What the chrome needs the player to do that is not mpv's own business. */
interface ChromeHost {
    /** Back at the picture: leave the film. */
    fun leave()
    /** Another episode is lined up to follow this one. */
    fun hasNextEpisode(): Boolean
    fun playNextEpisode()
}

/**
 * The TV's player chrome on mpv's picture. It is the TV's own state machine ([PlayerUi], shared in :core) fed by the
 * remote's keys (here the keyboard, which mpv passes on as `script-message cinema-key`, and the pad), drawing
 * [PlayerChromeUi] and handing the picture to mpv as an overlay. Everything runs on the player's one worker thread.
 */
class MpvChrome(private val dir: File, private val host: ChromeHost) {
    private val renderer = ChromeRenderer()
    private var send: ((Array<out Any?>) -> Unit)? = null

    private var ui: PlayerUi = PlayerUi.Hidden
    private var focus = ChromeFocus.PAUSE
    private var chapterFocus = 0
    private var lastInput = 0L
    private var lastKeyAt = 0L
    private var repeat = 0

    private var meta = ChromeMeta()
    private var resumeMs: Long? = null

    // mpv's side of it.
    private var positionMs = 0L
    private var durationMs = 0L
    private var paused = false
    private var buffering = false
    private var cacheAheadS = 0.0
    private var chapters: List<ChromeChapter> = emptyList()
    private var tracks: List<JSONObject> = emptyList()
    private var videoParams: JSONObject? = null
    private var audioParams: JSONObject? = null
    private var videoCodec: String? = null
    private var audioCodec: String? = null
    private var videoBitrate = 0L
    private var audioBitrate = 0L
    private var windowW = 1920
    private var windowH = 1080
    private var bitrates = BitrateHistory()
    private var bitrateAt = 0L

    private var shown: ChromeModel? = null
    private var overlayUp = false
    private var subsHidden = false
    private var frame = 0

    fun attach(sender: (Array<out Any?>) -> Unit) {
        send = sender
        overlayUp = false; shown = null
        // mpv's own keys are off, so the keys the remote has are routed here.
        listOf(
            "LEFT" to "LEFT", "RIGHT" to "RIGHT", "UP" to "UP", "DOWN" to "DOWN", "ENTER" to "OK", "KP_ENTER" to "OK",
            "SPACE" to "PLAY_PAUSE", "p" to "PLAY_PAUSE", "PLAYPAUSE" to "PLAY_PAUSE", "PLAY" to "PLAY", "PAUSE" to "PAUSE",
            "ESC" to "BACK", "BS" to "BACK", "q" to "BACK", "MBTN_BACK" to "BACK", "MBTN_LEFT" to "WAKE", "MOUSE_BTN0" to "WAKE",
            "PREV" to "PREVIOUS", "NEXT" to "NEXT", "REWIND" to "LEFT", "FORWARD" to "RIGHT",
        ).forEach { (key, name) -> send?.invoke(arrayOf("keybind", key, "${if (name in REPEATING) "repeatable " else ""}script-message cinema-key $name")) }
    }

    fun detach() { send = null; overlayUp = false; shown = null; renderer.close() }

    /** A new film is loading: back to the picture, with what is known of it. */
    fun load(item: ChromeMeta, resume: Long?) {
        meta = item; resumeMs = resume?.takeIf { it > 0 }
        ui = PlayerUi.Hidden; focus = ChromeFocus.PAUSE
        positionMs = resume ?: 0; durationMs = 0; paused = false; chapters = emptyList(); tracks = emptyList()
        videoParams = null; audioParams = null; videoCodec = null; audioCodec = null; videoBitrate = 0; audioBitrate = 0
        bitrates = BitrateHistory()
        flush(force = true)
    }

    // --- What mpv says ---------------------------------------------------------------------------

    fun onProperty(name: String, value: Any?) {
        when (name) {
            "time-pos" -> (value as? Number)?.let { positionMs = (it.toDouble() * 1000).toLong() }
            "duration" -> (value as? Number)?.let { durationMs = (it.toDouble() * 1000).toLong() }
            "pause" -> (value as? Boolean)?.let { paused = it }
            "paused-for-cache" -> buffering = value as? Boolean ?: false
            "demuxer-cache-time" -> cacheAheadS = ((value as? Number)?.toDouble() ?: 0.0) - positionMs / 1000.0
            "osd-width" -> (value as? Number)?.toInt()?.takeIf { it > 0 }?.let { windowW = it }
            "osd-height" -> (value as? Number)?.toInt()?.takeIf { it > 0 }?.let { windowH = it }
            "video-format" -> videoCodec = value as? String
            "audio-codec-name" -> audioCodec = value as? String
            "video-bitrate" -> videoBitrate = (value as? Number)?.toLong() ?: 0
            "audio-bitrate" -> audioBitrate = (value as? Number)?.toLong() ?: 0
            "video-params" -> videoParams = value as? JSONObject
            "audio-params" -> audioParams = value as? JSONObject
            "chapter-list" -> chapters = (value as? JSONArray)?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapIndexed { i, o ->
                    ChromeChapter(i, o.optString("title").ifBlank { "Chapter ${i + 1}" }, (o.optDouble("time", 0.0) * 1000).toLong())
                }
            }.orEmpty()
            "track-list" -> tracks = (value as? JSONArray)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
        }
    }

    /** Called about four times a second while a film is open: keeps the clock, the bitrate and the hide timer going. */
    fun tick() {
        val now = System.currentTimeMillis()
        if (!paused && now - bitrateAt >= 1000) { bitrateAt = now; bitrates.add(videoBitrate + audioBitrate) }
        if (ui == PlayerUi.Controls && !paused && now - lastInput > 5000) setState(PlayerUi.Hidden)
        flush(force = false)
    }

    // --- Keys ------------------------------------------------------------------------------------

    /** A key mpv passed on, by the name it was bound to. */
    fun onKey(name: String) {
        val now = System.currentTimeMillis()
        repeat = if (now - lastKeyAt < 350) repeat + 1 else 0
        lastKeyAt = now
        when (name) {
            "WAKE" -> { lastInput = now; if (ui == PlayerUi.Hidden) setState(PlayerUi.Controls) }
            "PREVIOUS" -> previous()
            "NEXT" -> next()
            else -> runCatching { PlayerUi.Key.valueOf(name) }.getOrNull()?.let { press(it) }
        }
        flush(force = true)
    }

    /** A pad press. False when the chrome has nothing for it (the player then does what it did before). */
    fun nav(action: NavAction): Boolean {
        val key = when (action) {
            NavAction.SELECT -> PlayerUi.Key.OK
            NavAction.LEFT -> PlayerUi.Key.LEFT; NavAction.RIGHT -> PlayerUi.Key.RIGHT
            NavAction.UP -> PlayerUi.Key.UP; NavAction.DOWN -> PlayerUi.Key.DOWN
            NavAction.BACK -> PlayerUi.Key.BACK
            NavAction.PREV -> { onKey("PREVIOUS"); return true }
            NavAction.NEXT -> { onKey("NEXT"); return true }
            NavAction.ALT -> { cycle("sub"); lastInput = System.currentTimeMillis(); flush(true); return true }
            NavAction.SEARCH -> { cycle("audio"); lastInput = System.currentTimeMillis(); flush(true); return true }
            NavAction.MENU, NavAction.INFO -> { onKey("WAKE"); return true }
        }
        val name = key.name
        onKey(name)
        return true
    }

    private fun press(key: PlayerUi.Key) {
        lastInput = System.currentTimeMillis()
        val onScrubber = focus == ChromeFocus.SCRUBBER
        when {
            ui == PlayerUi.Controls && (key == PlayerUi.Key.UP || key == PlayerUi.Key.DOWN) -> { moveRow(key == PlayerUi.Key.DOWN); return }
            ui == PlayerUi.Paused && key in setOf(PlayerUi.Key.LEFT, PlayerUi.Key.RIGHT, PlayerUi.Key.UP, PlayerUi.Key.DOWN) -> { movePaused(key); return }
        }
        val step = ui.onKey(key, repeat, positionMs, durationMs, !paused, onScrubber)
        when (val a = step.action) {
            PlayerUi.Action.Play -> setPaused(false)
            PlayerUi.Action.Pause -> setPaused(true)
            is PlayerUi.Action.Seek -> seek(a.ms)
            PlayerUi.Action.None -> Unit
        }
        if (step.state != ui) setState(step.state)
        if (!step.consumed) when {
            key == PlayerUi.Key.BACK && step.state == PlayerUi.Hidden -> host.leave()
            key == PlayerUi.Key.OK -> activate()
            ui == PlayerUi.Controls && (key == PlayerUi.Key.LEFT || key == PlayerUi.Key.RIGHT) -> moveAcross(key == PlayerUi.Key.RIGHT)
        }
    }

    private fun setState(next: PlayerUi) {
        val before = ui
        ui = next
        when {
            next == PlayerUi.Controls && before != PlayerUi.Controls && before !is PlayerUi.Scrubbing -> focus = ChromeFocus.PAUSE
            next == PlayerUi.Controls && before is PlayerUi.Scrubbing -> focus = ChromeFocus.SCRUBBER
            next is PlayerUi.Scrubbing && before !is PlayerUi.Scrubbing -> if (before == PlayerUi.Hidden) focus = ChromeFocus.SCRUBBER
            next == PlayerUi.Paused && before != PlayerUi.Paused -> {
                focus = ChromeFocus.RESUME
                chapterFocus = PlayerMath.chapterAt(chapterList(), positionMs)?.index ?: 0
            }
        }
        lastInput = System.currentTimeMillis()
    }

    private val transport = listOf(ChromeFocus.PREVIOUS, ChromeFocus.BACK_10, ChromeFocus.PAUSE, ChromeFocus.FORWARD_10, ChromeFocus.NEXT)

    private fun chipRow(): List<ChromeFocus> = listOfNotNull(ChromeFocus.SUBTITLES, ChromeFocus.AUDIO.takeIf { audioChip() != null }, ChromeFocus.QUALITY.takeIf { qualityChip() != null }, ChromeFocus.SLIDERS)

    /** Up and Down step between the three rows (transport, scrubber, chips), as on the TV. */
    private fun moveRow(down: Boolean) {
        val inTransport = focus in transport
        val inChips = focus in chipRow()
        if (down) when { inTransport -> focus = ChromeFocus.SCRUBBER; focus == ChromeFocus.SCRUBBER -> focus = ChromeFocus.SUBTITLES }
        else when { inChips -> focus = ChromeFocus.SCRUBBER; focus == ChromeFocus.SCRUBBER -> focus = ChromeFocus.PAUSE; inTransport -> setState(PlayerUi.Hidden) }
    }

    private fun moveAcross(right: Boolean) {
        val row = if (focus in transport) transport else if (focus in chipRow()) chipRow() else return
        val at = row.indexOf(focus)
        row.getOrNull(at + if (right) 1 else -1)?.let { focus = it }
    }

    private fun movePaused(key: PlayerUi.Key) {
        val hasChapters = chapters.isNotEmpty()
        when (focus) {
            ChromeFocus.RESUME -> when (key) { PlayerUi.Key.RIGHT -> focus = ChromeFocus.START_OVER; PlayerUi.Key.DOWN -> if (hasChapters) focus = ChromeFocus.CHAPTERS; else -> Unit }
            ChromeFocus.START_OVER -> when (key) { PlayerUi.Key.LEFT -> focus = ChromeFocus.RESUME; PlayerUi.Key.DOWN -> if (hasChapters) focus = ChromeFocus.CHAPTERS; else -> Unit }
            ChromeFocus.CHAPTERS -> when (key) {
                PlayerUi.Key.LEFT -> chapterFocus = (chapterFocus - 1).coerceAtLeast(0)
                PlayerUi.Key.RIGHT -> chapterFocus = (chapterFocus + 1).coerceAtMost(chapters.size - 1)
                PlayerUi.Key.UP -> focus = ChromeFocus.RESUME
                else -> Unit
            }
            else -> focus = ChromeFocus.RESUME
        }
    }

    /** OK on whatever has focus. */
    private fun activate() {
        when (focus) {
            ChromeFocus.PREVIOUS -> previous()
            ChromeFocus.BACK_10 -> seek((positionMs - 10_000).coerceAtLeast(0))
            ChromeFocus.FORWARD_10 -> seek((positionMs + 10_000).coerceAtMost(durationMs.coerceAtLeast(0)))
            ChromeFocus.NEXT -> next()
            ChromeFocus.PAUSE -> if (!paused) { setPaused(true); setState(PlayerUi.Paused) } else setPaused(false)
            ChromeFocus.SUBTITLES, ChromeFocus.SLIDERS -> cycle("sub")
            ChromeFocus.AUDIO -> cycle("audio")
            ChromeFocus.RESUME -> { setPaused(false); setState(PlayerUi.Hidden) }
            ChromeFocus.START_OVER -> { seek(0); setPaused(false); setState(PlayerUi.Hidden) }
            ChromeFocus.CHAPTERS -> chapters.getOrNull(chapterFocus)?.let { seek(it.startMs); setPaused(false); setState(PlayerUi.Hidden) }
            ChromeFocus.SCRUBBER, ChromeFocus.QUALITY -> Unit
        }
    }

    /** Back to the start of this chapter, or to the one before if it only just began; without chapters, the start. */
    private fun previous() {
        val list = chapterList()
        val now = PlayerMath.chapterAt(list, positionMs)
        seek(when {
            now == null -> 0L
            positionMs - now.startMs > 5000 -> now.startMs
            else -> list.getOrNull(now.index - 1)?.startMs ?: 0L
        })
        lastInput = System.currentTimeMillis()
    }

    private fun next() {
        val list = chapterList()
        val following = PlayerMath.chapterAt(list, positionMs)?.let { list.getOrNull(it.index + 1) }
        if (following != null) seek(following.startMs) else if (host.hasNextEpisode()) host.playNextEpisode()
        lastInput = System.currentTimeMillis()
    }

    private fun chapterList() = chapters.map { Chapter(it.index, it.name, it.startMs, null) }
    private fun seek(ms: Long) { send?.invoke(arrayOf("seek", "%.3f".format(java.util.Locale.ROOT, ms / 1000.0), "absolute")); positionMs = ms }
    private fun setPaused(p: Boolean) { send?.invoke(arrayOf("set_property", "pause", p)); paused = p }
    private fun cycle(property: String) { send?.invoke(arrayOf("cycle", property)) }

    // --- The picture -----------------------------------------------------------------------------

    private fun audioTrack() = tracks.firstOrNull { it.optString("type") == "audio" && it.optBoolean("selected") }
    private fun subTracks() = tracks.filter { it.optString("type") == "sub" }

    private fun audioChip(): String? = audioTrack()?.let { t ->
        listOfNotNull(PlayerMath.language(t.optString("lang").ifBlank { null }), PlayerMath.channels(t.optInt("demux-channel-count", audioParams?.optInt("channel-count") ?: 0), null))
            .joinToString(" · ").ifBlank { null }
    }

    private fun isHdr() = videoParams?.optString("gamma")?.let { it == "pq" || it == "hlg" } == true
    private fun qualityChip(): String? = videoParams?.let { PlayerMath.qualityChip(it.optInt("w"), it.optInt("h"), isHdr()) }

    private fun specs(): List<ChromeSpec> {
        val out = ArrayList<ChromeSpec>()
        val vp = videoParams
        if (vp != null) {
            PlayerMath.resolution(vp.optInt("w"), vp.optInt("h"))?.let { res ->
                val sub = listOfNotNull(if (isHdr()) (if (vp.optString("gamma") == "hlg") "HLG" else "HDR10") else null).joinToString(" · ").ifBlank { null }
                out += ChromeSpec("Resolution", res, sub)
            }
            codecName(videoCodec)?.let { codec ->
                val tenBit = vp.optString("pixelformat").contains("10") || vp.optString("hw-pixelformat").contains("10") || vp.optInt("average-bpp") > 0 && vp.optString("pixelformat").contains("p10")
                out += ChromeSpec("Video", codec + if (tenBit) " 10-bit" else "", PlayerMath.bitrate(videoBitrate))
            }
        }
        audioTrack()?.let { t ->
            audioChip()?.let { name ->
                out += ChromeSpec("Audio", name, listOfNotNull(audioName(audioCodec), PlayerMath.bitrate(audioBitrate)).joinToString(" · ").ifBlank { null })
            }
        }
        val subs = subTracks()
        if (subs.isNotEmpty()) out += ChromeSpec("Subtitles", if (subs.any { it.optBoolean("selected") }) "On" else "Off", "${subs.size} ${if (subs.size == 1) "language" else "languages"} available")
        val ahead = cacheAheadS.takeIf { it > 1 }?.let { s -> "Buffered ${(s / 60).toInt()}m ${(s % 60).toInt()}s ahead" }
        val size = meta.sizeBytes?.let { PlayerMath.size(it) }
        if (size != null) out += ChromeSpec("Size", size, ahead)
        else if (ahead != null) out += ChromeSpec("Buffer", ahead.removePrefix("Buffered ").removeSuffix(" ahead"), "ahead of playback")
        return out
    }

    private fun codecName(name: String?) = when (name) {
        null -> null
        "hevc", "h265" -> "HEVC"; "h264", "avc", "avc1" -> "H.264"; "av1" -> "AV1"; "vp9" -> "VP9"; "mpeg2video" -> "MPEG-2"
        else -> name.uppercase()
    }

    private fun audioName(name: String?) = when (name) {
        null -> null
        "eac3" -> "E-AC-3"; "ac3" -> "AC-3"; "aac" -> "AAC"; "dts" -> "DTS"; "truehd" -> "TrueHD"; "opus" -> "Opus"; "mp3" -> "MP3"; "flac" -> "FLAC"
        else -> name.uppercase()
    }

    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

    private fun model(): ChromeModel {
        val subs = subTracks()
        val health = if (positionMs <= 0 && durationMs <= 0) null else if (buffering) "Buffering" to false else "Healthy" to true
        return ChromeModel(
            ui = ui, focus = focus, chapterFocus = chapterFocus,
            title = meta.title, year = meta.year, genres = meta.genres, runtimeMin = meta.runtimeMin, certificate = meta.certificate, rating = meta.rating, overview = meta.overview,
            positionMs = positionMs, durationMs = durationMs, playing = !paused, chapters = chapters,
            subtitlesChip = if (subs.isEmpty()) null else "Subtitles · ${if (subs.any { it.optBoolean("selected") }) "On" else "Off"}",
            audioChip = audioChip(), qualityChip = qualityChip(),
            hasNext = chapters.getOrNull((PlayerMath.chapterAt(chapterList(), positionMs)?.index ?: -1) + 1) != null || host.hasNextEpisode(),
            specs = if (ui == PlayerUi.Paused) specs() else emptyList(), health = if (ui == PlayerUi.Paused) health else null,
            bitrateSamples = if (ui == PlayerUi.Paused) bitrates.values() else emptyList(),
            bitratePeak = bitrates.values().maxOrNull()?.let { PlayerMath.bitrate(it) },
            clock = LocalTime.now().format(hhmm),
            endsAt = PlayerMath.endsAt(System.currentTimeMillis(), positionMs, durationMs)?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(hhmm) },
            resumeMs = resumeMs,
        )
    }

    /** Draws and hands mpv a new picture when anything the viewer would see has changed. */
    private fun flush(force: Boolean) {
        val out = send ?: return
        val m = model()
        val covered = m.ui != PlayerUi.Hidden
        if (covered != subsHidden) { subsHidden = covered; out(arrayOf("set_property", "sub-visibility", !covered)) }
        if (m.ui == PlayerUi.Hidden) {
            if (overlayUp) { out(arrayOf("overlay-remove", OVERLAY)); overlayUp = false }
            shown = m
            return
        }
        // The position moves every frame; the picture only needs to follow it a few times a second, or on a key.
        val last = shown
        if (!force && overlayUp && last != null && m.copy(positionMs = last.positionMs) == last && kotlin.math.abs(m.positionMs - last.positionMs) < 900) return
        if (m == last && overlayUp) return
        try {
            val bytes = renderer.bgra(m, windowW, windowH)
            val file = File(dir, "cinema-chrome-${frame++ % 2}.bgra")
            RandomAccessFile(file, "rw").use { it.setLength(0); it.write(bytes) }
            out(arrayOf("overlay-add", OVERLAY, 0, 0, file.path, 0, "bgra", windowW, windowH, windowW * 4))
            overlayUp = true; shown = m
        } catch (e: Exception) {
            Log.w(TAG, "Could not draw the player controls: ${e.javaClass.simpleName} ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PlayerChrome"
        private const val OVERLAY = 0
        private val REPEATING = setOf("LEFT", "RIGHT", "UP", "DOWN")

        /** The mpv properties the chrome follows. */
        val PROPERTIES = listOf(
            "time-pos", "duration", "pause", "paused-for-cache", "demuxer-cache-time", "osd-width", "osd-height", "video-format", "audio-codec-name",
            "video-bitrate", "audio-bitrate", "video-params", "audio-params", "chapter-list", "track-list",
        )
    }
}

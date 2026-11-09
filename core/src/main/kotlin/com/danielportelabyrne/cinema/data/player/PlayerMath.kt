package com.danielportelabyrne.cinema.data.player

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToLong
import kotlin.math.sin

/** One chapter of a film, as Jellyfin lists it. [imageTag] is set when the server has a still for it. */
data class Chapter(val index: Int, val name: String, val startMs: Long, val imageTag: String?)

/**
 * Jellyfin's trickplay tiles for one width: sheets of [tileWidth] x [tileHeight] thumbnails, each [width] x
 * [height] px, one every [intervalMs], [count] in all.
 */
data class Trickplay(val width: Int, val height: Int, val tileWidth: Int, val tileHeight: Int, val count: Int, val intervalMs: Long) {
    /** Which sheet holds the thumbnail for [positionMs], and where in it: (sheet index, left, top) in sheet pixels. */
    fun tile(positionMs: Long): Triple<Int, Int, Int> {
        val perSheet = tileWidth * tileHeight
        val n = (positionMs.coerceAtLeast(0) / intervalMs.coerceAtLeast(1)).toInt().coerceIn(0, (count - 1).coerceAtLeast(0))
        val inSheet = n % perSheet
        return Triple(n / perSheet, (inSheet % tileWidth) * width, (inSheet / tileWidth) * height)
    }
}

object PlayerMath {
    private val GENERIC = Regex("(?i)(chapter|scene)\\s*\\d+|\\d+")
    private val CREDITS = Regex("(?i)\\b(end\\s*)?credits?\\b|\\boutro\\b|\\bclosing\\b")

    /**
     * Where an episode's credits begin, for the next-episode card, from the best thing known, in this order: a chapter
     * named for them ("Credits", "End credits", "Outro") in the last part of the file; the start a community database or
     * the server gave ([knownStartMs]); a short unnamed last chapter; where this series' viewers have tended to leave
     * ([learnedTailMs], how long before the end); and failing all of those, [guessedTailMs] before the end. Null for
     * anything too short to tell, or with no known length.
     */
    fun creditsStartMs(chapters: List<Chapter>, durationMs: Long, knownStartMs: Long? = null, learnedTailMs: Long? = null): Long? {
        if (durationMs < 10 * 60_000L) return null
        chapters.lastOrNull { CREDITS.containsMatchIn(it.name) && it.startMs >= durationMs * 0.6 && it.startMs < durationMs - 5_000 }?.let { return it.startMs }
        if (knownStartMs != null && com.danielportelabyrne.cinema.data.intro.CreditsTimes.plausible(knownStartMs, durationMs)) return knownStartMs
        // Chapters with no real names ("Chapter 9"): a short last one in the final tenth is almost always the credits.
        chapters.maxByOrNull { it.startMs }?.takeIf { GENERIC.matches(it.name.trim()) && it.startMs >= durationMs * 0.9 && durationMs - it.startMs in 45_000L..6 * 60_000L }?.let { return it.startMs }
        val learned = learnedTailMs?.takeIf { it > 0 }?.let { durationMs - it }
        if (learned != null && com.danielportelabyrne.cinema.data.intro.CreditsTimes.plausible(learned, durationMs)) return learned
        return durationMs - guessedTailMs(durationMs)
    }

    /**
     * How long the credits last when the file has no credits chapter: about 6 % of the length, between a minute and four
     * minutes (so an hour-long episode's credits begin about three and a half minutes before the end).
     */
    fun guessedTailMs(durationMs: Long): Long = (durationMs * 0.06).toLong().coerceIn(60_000L, 4 * 60_000L)

    // --- Streams -----------------------------------------------------------------------------------

    /**
     * How far forward a stream can be sought: what the player already holds, or the share of the file the server has
     * downloaded (it downloads in order) less a margin for bitrate swings. Without a duration or a figure from the
     * server, only the buffer counts.
     */
    fun streamSeekLimit(durationMs: Long, downloaded: Float?, bufferedMs: Long): Long {
        val fromServer = if (durationMs > 0 && downloaded != null) {
            val share = downloaded.coerceIn(0f, 1f)
            if (share >= 0.999f) durationMs
            else (durationMs * share).toLong() - maxOf(STREAM_SEEK_MARGIN_MS, (durationMs * 0.03).toLong())
        } else 0L
        return maxOf(bufferedMs, fromServer, 0L)
    }

    /** Back is always fine; forward stops at [limit] (and never goes backwards because of it). */
    fun clampStreamSeek(targetMs: Long, positionMs: Long, limit: Long): Long =
        if (targetMs <= positionMs || targetMs <= limit) targetMs else maxOf(limit, positionMs)

    private const val STREAM_SEEK_MARGIN_MS = 15_000L

    // --- Chapters ----------------------------------------------------------------------------------

    /** Jellyfin's `Chapters` array (StartPositionTicks, Name, ImageTag), in order; untitled ones are numbered. */
    fun parseChapters(array: JSONArray?): List<Chapter> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            Chapter(i, o.optString("Name").ifBlank { "Chapter ${i + 1}" }, o.optLong("StartPositionTicks", 0L) / 10_000L, o.optString("ImageTag").ifBlank { null })
        }.sortedBy { it.startMs }.mapIndexed { i, c -> c.copy(index = i) }
    }

    /** The chapter playing at [positionMs], or null when there are none. */
    fun chapterAt(chapters: List<Chapter>, positionMs: Long): Chapter? = chapters.lastOrNull { it.startMs <= positionMs } ?: chapters.firstOrNull()

    /** "Chapter 5 of 7". */
    fun chapterLabel(chapter: Chapter, total: Int) = "Chapter ${chapter.index + 1} of $total"

    /** How far through [chapter] [positionMs] is (0..1), using the next chapter's start or the film's end. */
    fun chapterProgress(chapters: List<Chapter>, chapter: Chapter, positionMs: Long, durationMs: Long): Float {
        val end = chapters.getOrNull(chapter.index + 1)?.startMs ?: durationMs
        val span = (end - chapter.startMs).coerceAtLeast(1)
        return ((positionMs - chapter.startMs).toFloat() / span).coerceIn(0f, 1f)
    }

    /** Jellyfin's `Trickplay` object: `{ mediaSourceId: { width: { Width, Height, TileWidth, TileHeight, ThumbnailCount, Interval } } }`. The widest under [maxWidth] wins. */
    fun parseTrickplay(o: JSONObject?, sourceId: String?, maxWidth: Int = 480): Trickplay? {
        if (o == null) return null
        val bySource = (sourceId?.let { o.optJSONObject(it) }) ?: o.keys().asSequence().firstNotNullOfOrNull { o.optJSONObject(it) } ?: return null
        val options = bySource.keys().asSequence().mapNotNull { k ->
            val t = bySource.optJSONObject(k) ?: return@mapNotNull null
            Trickplay(t.optInt("Width"), t.optInt("Height"), t.optInt("TileWidth"), t.optInt("TileHeight"), t.optInt("ThumbnailCount"), t.optLong("Interval"))
        }.filter { it.width > 0 && it.height > 0 && it.tileWidth > 0 && it.tileHeight > 0 && it.count > 0 && it.intervalMs > 0 }.toList()
        return options.filter { it.width <= maxWidth }.maxByOrNull { it.width } ?: options.minByOrNull { it.width }
    }

    // --- Time --------------------------------------------------------------------------------------

    /** "41:17", "1:24:17". */
    fun clock(ms: Long): String {
        val s = (ms.coerceAtLeast(0) / 1000.0).roundToLong()
        val h = s / 3600; val m = s % 3600 / 60; val x = s % 60
        return if (h > 0) String.format(Locale.UK, "%d:%02d:%02d", h, m, x) else String.format(Locale.UK, "%d:%02d", m, x)
    }

    /** When the film will end if left playing from [positionMs] at [speed]: now plus what remains. Null when the length is unknown. */
    fun endsAt(nowMs: Long, positionMs: Long, durationMs: Long, speed: Float = 1f): Long? {
        if (durationMs <= 0) return null
        val remaining = (durationMs - positionMs).coerceAtLeast(0)
        return nowMs + (remaining / speed.coerceAtLeast(0.1f)).toLong()
    }

    // --- Wave geometry -------------------------------------------------------------------------------

    /** The scrubber's sine: y at [x] for a wave starting at [x0] with [amplitude] and [wavelength] about [baseY]. */
    fun waveY(x: Float, x0: Float, baseY: Float, amplitude: Float, wavelength: Float): Float =
        baseY + amplitude * sin((x - x0) / wavelength * 2 * PI).toFloat()

    /** Sample points from [x0] to [x1] every [step], always ending exactly at [x1]. */
    fun waveSamples(x0: Float, x1: Float, step: Float): List<Float> {
        if (x1 <= x0 || step <= 0f) return listOf(x0)
        val xs = ArrayList<Float>()
        var x = x0
        while (x < x1) { xs += x; x += step }
        xs += x1
        return xs
    }

    /** Where the playhead sits for [progress] (clamped to 0..1). */
    fun playheadX(progress: Float, x0: Float, x1: Float): Float = x0 + (x1 - x0) * progress.coerceIn(0f, 1f)

    // --- Formats -----------------------------------------------------------------------------------

    /**
     * The picture's class by its larger side: a 1920 x 804 scope film is 1080p, not "804p". Standard heights win
     * (2160, 1440, 1080, 720, 576, 480); anything else rounds down to the nearest of them.
     */
    fun lines(width: Int, height: Int): Int {
        val effective = maxOf(height, width * 9 / 16)
        return listOf(2160, 1440, 1080, 720, 576, 480).firstOrNull { effective >= it - 40 } ?: effective
    }

    /** "2160p · 4K", "1080p · HD", "720p". Null when unknown. */
    fun resolution(width: Int, height: Int): String? {
        if (height <= 0) return null
        val l = lines(width, height)
        return when { l >= 2160 -> "${l}p · 4K"; l >= 1080 -> "${l}p · HD"; else -> "${l}p" }
    }

    /** A short chip label: "4K HDR", "1080p". */
    fun qualityChip(width: Int, height: Int, hdr: Boolean): String? {
        if (height <= 0) return null
        val l = lines(width, height)
        return if (l >= 2160) (if (hdr) "4K HDR" else "4K") else "${l}p" + if (hdr) " HDR" else ""
    }

    /** "HEVC", "H.264", "AV1", "VP9" from a mime type. */
    fun videoCodec(mime: String?): String? = when (mime) {
        "video/hevc" -> "HEVC"; "video/avc" -> "H.264"; "video/av01" -> "AV1"; "video/x-vnd.on2.vp9" -> "VP9"; "video/mpeg2" -> "MPEG-2"
        null -> null
        else -> mime.substringAfter('/').uppercase(Locale.UK)
    }

    /** "E-AC-3", "AC-3", "AAC", "DTS", "TrueHD", "Opus" from a mime type. */
    fun audioCodec(mime: String?): String? = when (mime) {
        "audio/eac3", "audio/eac3-joc" -> "E-AC-3"; "audio/ac3" -> "AC-3"; "audio/mp4a-latm" -> "AAC"; "audio/vnd.dts", "audio/vnd.dts.hd" -> "DTS"
        "audio/true-hd" -> "TrueHD"; "audio/opus" -> "Opus"; "audio/mpeg" -> "MP3"; "audio/flac" -> "FLAC"
        null -> null
        else -> mime.substringAfter('/').uppercase(Locale.UK)
    }

    /** "Stereo", "5.1", "7.1", "Atmos 7.1" (Dolby Atmos rides in E-AC-3 JOC). */
    fun channels(count: Int, mime: String?): String? {
        val base = when (count) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; in 3..20 -> "$count ch"; else -> null }
        return if (mime == "audio/eac3-joc") listOfNotNull("Atmos", base?.takeIf { it != "Stereo" && it != "Mono" }).joinToString(" ") else base
    }

    /** "18.4 Mbps", "768 kbps". Null for unknown. */
    fun bitrate(bps: Long): String? = when {
        bps <= 0 -> null
        bps >= 1_000_000 -> String.format(Locale.UK, "%.1f Mbps", bps / 1e6)
        else -> "${bps / 1000} kbps"
    }

    /** "14.8 GB", "820 MB". */
    fun size(bytes: Long): String? = when {
        bytes <= 0 -> null
        bytes >= 1_000_000_000 -> String.format(Locale.UK, "%.1f GB", bytes / 1e9)
        else -> "${bytes / 1_000_000} MB"
    }

    /** A language code as a name ("eng" -> "English"), or null. */
    fun language(code: String?): String? = code?.takeIf { it.isNotBlank() && it != "und" }?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.UK).ifBlank { null } }

    /** "English · Forced", "English · SDH", "English · Signs & Songs". */
    fun subtitleName(code: String?, label: String?, forced: Boolean, captions: Boolean): String {
        val lang = language(code)
        val name = label?.trim()?.takeIf { it.isNotEmpty() }
        val text = (name ?: "").lowercase(Locale.UK)
        val parts = mutableListOf<String>()
        if (lang != null && (name == null || !text.contains(lang.lowercase(Locale.UK)))) parts += lang
        if (name != null) parts += name
        if (forced && "forced" !in text) parts += "Forced"
        if (captions && listOf("sdh", "cc", "hearing").none { it in text }) parts += "SDH"
        return parts.joinToString(" · ").ifEmpty { "Unknown" }
    }
}

/** The last 60 bitrate samples, one a second, for the paused screen's sparkline. */
class BitrateHistory(private val capacity: Int = 60) {
    private val samples = ArrayDeque<Long>()
    fun add(bps: Long) { if (bps <= 0) return; samples.addLast(bps); while (samples.size > capacity) samples.removeFirst() }
    fun values(): List<Long> = samples.toList()
    val peak: Long get() = samples.maxOrNull() ?: 0L
    val isEmpty: Boolean get() = samples.size < 2
}

package com.danielportelabyrne.cinema.data.intro

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Where an episode's credits begin, as TheIntroDB or Jellyfin's own segments say. Pure, so the parsing can be tested. */
object CreditsTimes {
    /** TheIntroDB's `GET /v4/media` address for an episode, found by its IMDb id and its season and episode numbers. */
    fun introDbUrl(imdbId: String, season: Int, episode: Int): String =
        "https://api.theintrodb.org/v4/media?imdb_id=${URLEncoder.encode(imdbId, "UTF-8")}&season=$season&episode=$episode"

    /** The start of the first usable credits segment in a TheIntroDB answer, in ms; null when there is none. */
    fun fromIntroDb(json: String): Long? {
        val credits = try { JSONObject(json).optJSONArray("credits") } catch (_: Exception) { null } ?: return null
        for (i in 0 until credits.length()) {
            val segment = credits.optJSONObject(i) ?: continue
            // Credits are always given a start; an end at or before the start is the API's way of saying "none here".
            if (segment.isNull("start_ms")) continue
            val start = segment.optLong("start_ms", 0L)
            val end = if (segment.isNull("end_ms")) null else segment.optLong("end_ms", 0L)
            if (start > 0 && (end == null || end > start)) return start
        }
        return null
    }

    /** The start of Jellyfin's Outro segment (its end credits), in ms, from a `/MediaSegments` answer; null when there is none. */
    fun fromJellyfinSegments(json: String): Long? {
        val items = try { JSONObject(json).optJSONArray("Items") } catch (_: Exception) { null } ?: return null
        for (i in 0 until items.length()) {
            val segment = items.optJSONObject(i) ?: continue
            if (segment.optString("Type") != "Outro") continue
            // Jellyfin counts in 100-nanosecond ticks.
            val ticks = segment.optLong("StartTicks", 0L)
            if (ticks > 0) return ticks / 10_000L
        }
        return null
    }

    /** Whether [startMs] can be the credits of a file [durationMs] long: in its second half, and before the very end. */
    fun plausible(startMs: Long, durationMs: Long): Boolean = durationMs > 0 && startMs >= durationMs / 2 && startMs < durationMs - 5_000L
}

/** Learns where a series' credits begin from where its viewers leave: leaving near the end is the credits rolling. */
object CreditsLearning {
    /** An exit this close to the end (or further from it) does not say anything about the credits. */
    const val MIN_TAIL_MS = 30_000L
    const val MAX_TAIL_MS = 6 * 60_000L
    /** How many exits are kept for a series; the median of them is used. */
    const val KEEP = 9

    /** How long before the end the viewer left, when that is a credits-sized gap; null otherwise. */
    fun tailOf(durationMs: Long, exitMs: Long): Long? {
        if (durationMs <= 0) return null
        return (durationMs - exitMs).takeIf { it in MIN_TAIL_MS..MAX_TAIL_MS }
    }

    /** The median of [tails], or null when there are none. */
    fun median(tails: List<Long>): Long? {
        if (tails.isEmpty()) return null
        val sorted = tails.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}

/** An answer already had for an episode: its credits start, or null when the source had none for it. */
data class CachedStart(val startMs: Long?)

/**
 * What this TV keeps about credits, as one JSON object: each series' recent exits (as tails, see [CreditsLearning]), and
 * the answers already had from TheIntroDB per episode, so an episode is not asked for twice. Unreadable saved data starts afresh.
 */
object CreditsStore {
    /** How long an answer that gave a credits start is kept, and one that gave none (it may be added later). */
    const val FOUND_TTL_MS = 30L * 86_400_000L
    const val MISSING_TTL_MS = 86_400_000L

    fun addTail(json: String?, seriesKey: String, tailMs: Long): String {
        val root = parse(json)
        val tails = root.optJSONObject("tails") ?: JSONObject().also { root.put("tails", it) }
        val kept = (tailsOf(tails, seriesKey) + tailMs).takeLast(CreditsLearning.KEEP)
        tails.put(seriesKey, JSONArray(kept))
        return root.toString()
    }

    /** The credits tail learned for a series, or null when none of its exits has been learned from yet. */
    fun learnedTail(json: String?, seriesKey: String): Long? =
        CreditsLearning.median(tailsOf(parse(json).optJSONObject("tails"), seriesKey))

    /** The saved answer for [episodeKey], when it is still fresh; null when it is not known or has gone stale. */
    fun cachedStart(json: String?, episodeKey: String, now: Long): CachedStart? {
        val saved = parse(json).optJSONObject("found")?.optJSONObject(episodeKey) ?: return null
        val start = if (saved.isNull("start")) null else saved.optLong("start")
        val age = now - saved.optLong("at")
        return if (age < (if (start == null) MISSING_TTL_MS else FOUND_TTL_MS)) CachedStart(start) else null
    }

    fun putStart(json: String?, episodeKey: String, startMs: Long?, now: Long): String {
        val root = parse(json)
        val found = root.optJSONObject("found") ?: JSONObject().also { root.put("found", it) }
        found.put(episodeKey, JSONObject().put("start", startMs ?: JSONObject.NULL).put("at", now))
        return root.toString()
    }

    private fun tailsOf(tails: JSONObject?, seriesKey: String): List<Long> {
        val list = tails?.optJSONArray(seriesKey) ?: return emptyList()
        return (0 until list.length()).map { list.optLong(it) }
    }

    private fun parse(json: String?): JSONObject = if (json == null) JSONObject() else try { JSONObject(json) } catch (_: Exception) { JSONObject() }
}

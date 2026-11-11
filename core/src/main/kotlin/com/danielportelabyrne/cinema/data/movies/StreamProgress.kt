package com.danielportelabyrne.cinema.data.movies

import org.json.JSONObject

/**
 * How far an episode got in a stream the viewer played here: the furthest point reached and the episode's length. Streams
 * leave no record in Jellyfin, so this keeps the watched state for stream-played episodes on this TV.
 */
data class StreamedEpisode(val season: Int, val episode: Int, val positionMs: Long, val durationMs: Long, val atMs: Long,
    /** Where playback last was; older records without it resume from the furthest point. */
    val resumeMs: Long = positionMs,
    /** False for an older record saved before the resume point was kept, so [resumeMs] is only the furthest point. */
    val hasResume: Boolean = true) {
    /** Watched: at least 90 % of the way through, at some time (the furthest point). For a tick on the episode's card. */
    val played: Boolean get() = durationMs > 0 && positionMs >= durationMs * 0.9

    /**
     * The latest viewing reached 90 %: what decides which episode comes next. A finished episode watched again and
     * stopped partway is not finished by its old furthest point; an older record that kept no resume point can only go by
     * that furthest point.
     */
    val finishedLast: Boolean get() = if (!hasResume) played else durationMs > 0 && resumeMs >= durationMs * 0.9
}

/** The saved stream progress, as one JSON object: a key per episode, "imdb|season|episode". Pure, so it can be tested. */
object StreamProgress {
    fun key(imdbId: String, season: Int, episode: Int) = "$imdbId|$season|$episode"

    /**
     * [json] with this episode's progress: the furthest point is kept if it is further than before, while the resume point
     * ("r") always becomes [positionMs], so seeking back moves it back. Anything unreadable starts afresh.
     */
    fun record(json: String?, imdbId: String, season: Int, episode: Int, positionMs: Long, durationMs: Long, atMs: Long): String {
        val all = parse(json)
        val key = key(imdbId, season, episode)
        val before = all.optJSONObject(key)
        val furthest = maxOf(positionMs, before?.optLong("p", 0L) ?: 0L)
        val length = if (durationMs > 0) durationMs else before?.optLong("d", 0L) ?: 0L
        all.put(key, JSONObject().put("p", furthest).put("r", positionMs).put("d", length).put("t", atMs))
        return all.toString()
    }

    /** Everything saved for the series [imdbId]. */
    fun forSeries(json: String?, imdbId: String): List<StreamedEpisode> {
        val all = parse(json)
        return all.keys().asSequence().filter { it.startsWith("$imdbId|") }.mapNotNull { k ->
            val parts = k.split("|")
            val o = all.optJSONObject(k) ?: return@mapNotNull null
            val season = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val episode = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            episodeFrom(season, episode, o)
        }.toList()
    }

    private fun episodeFrom(season: Int, episode: Int, o: JSONObject) =
        StreamedEpisode(season, episode, o.optLong("p"), o.optLong("d"), o.optLong("t"), o.optLong("r", o.optLong("p")), hasResume = o.has("r"))

    /** Every saved episode with its series' IMDb id. */
    fun all(json: String?): List<Pair<String, StreamedEpisode>> {
        val all = parse(json)
        return all.keys().asSequence().mapNotNull { k ->
            val parts = k.split("|")
            val o = all.optJSONObject(k) ?: return@mapNotNull null
            val season = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val episode = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            parts[0].takeIf { it.isNotBlank() }?.let { it to episodeFrom(season, episode, o) }
        }.toList()
    }

    private fun parse(json: String?): JSONObject {
        if (json == null) return JSONObject()
        return try { JSONObject(json) } catch (_: Exception) { JSONObject() }
    }
}

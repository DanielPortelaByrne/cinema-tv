package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.platform.Platform
import org.json.JSONArray
import org.json.JSONObject

/**
 * One film or episode the viewer really watched here. Keeps every id it is known by (a streamed copy and a library
 * copy are one record) and enough to draw a card and reopen it offline. Separate from the recommendation WatchHistory.
 */
data class PlayedEntry(
    val kind: Kind,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val jellyfinId: String? = null,
    /** For an episode: the series' ids. A streamed episode knows its series by IMDb id. */
    val seriesTmdbId: String? = null,
    val seriesImdbId: String? = null,
    val seriesJellyfinId: String? = null,
    /** The film's name, or the series' name for an episode. May be blank for a record rebuilt from old progress. */
    val title: String = "",
    val episodeTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val still: String? = null,
    /** When the viewer last really watched it, in wall-clock milliseconds. */
    val playedAt: Long = 0L,
    /** Where playback last was (it moves back when the viewer seeks back). */
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val completed: Boolean = false,
    /** Where it was played from: [SOURCE_JELLYFIN] (the library) or [SOURCE_STREAM]. */
    val source: String = SOURCE_STREAM
) {
    val isEpisode get() = kind == Kind.SHOW

    /** True when [o] is the same film or episode: same kind and a shared id. Never compared by name. */
    fun sameAs(o: PlayedEntry): Boolean {
        if (kind != o.kind) return false
        if (!isEpisode) return shares(tmdbId, o.tmdbId) || shares(imdbId, o.imdbId) || shares(jellyfinId, o.jellyfinId)
        // An episode's own library id is enough; otherwise it must be the same season and episode of the same series.
        if (shares(jellyfinId, o.jellyfinId)) return true
        if (season == null || episode == null || season != o.season || episode != o.episode) return false
        return shares(seriesTmdbId, o.seriesTmdbId) || shares(seriesImdbId, o.seriesImdbId) || shares(seriesJellyfinId, o.seriesJellyfinId)
    }

    /** True when [o] is the same film, or any episode of the same series: a shared id (a series' own, for episodes). Never by name. */
    fun sameTitle(o: PlayedEntry): Boolean {
        if (kind != o.kind) return false
        if (!isEpisode) return shares(tmdbId, o.tmdbId) || shares(imdbId, o.imdbId) || shares(jellyfinId, o.jellyfinId)
        return shares(seriesTmdbId, o.seriesTmdbId) || shares(seriesImdbId, o.seriesImdbId) || shares(seriesJellyfinId, o.seriesJellyfinId)
    }

    /** Whether it can be told apart from every other: a film needs an id, an episode a series id with its numbers, or its own library id. */
    val hasId get() = if (isEpisode) !jellyfinId.isNullOrBlank() ||
        (season != null && episode != null && listOf(seriesTmdbId, seriesImdbId, seriesJellyfinId).any { !it.isNullOrBlank() })
    else listOf(tmdbId, imdbId, jellyfinId).any { !it.isNullOrBlank() }

    /** This entry with the ids and details [o] knows that this one does not. Playback facts stay this entry's own. */
    fun learn(o: PlayedEntry) = copy(
        tmdbId = tmdbId ?: o.tmdbId, imdbId = imdbId ?: o.imdbId, jellyfinId = jellyfinId ?: o.jellyfinId,
        seriesTmdbId = seriesTmdbId ?: o.seriesTmdbId, seriesImdbId = seriesImdbId ?: o.seriesImdbId, seriesJellyfinId = seriesJellyfinId ?: o.seriesJellyfinId,
        title = title.ifBlank { o.title }, episodeTitle = episodeTitle ?: o.episodeTitle, season = season ?: o.season, episode = episode ?: o.episode,
        poster = poster ?: o.poster, backdrop = backdrop ?: o.backdrop, still = still ?: o.still,
        durationMs = if (durationMs > 0) durationMs else o.durationMs
    )

    private fun shares(a: String?, b: String?) = !a.isNullOrBlank() && a == b

    companion object {
        const val SOURCE_JELLYFIN = "jellyfin"
        const val SOURCE_STREAM = "stream"
    }
}

/** Who a playing session is for: the entry's identity, and the per-server/user [scope] its history is kept under. */
data class HistoryTarget(val scope: String, val entry: PlayedEntry) {
    fun toJson(): String = JSONObject().put("scope", scope).put("entry", PlayHistory.entryJson(entry)).toString()

    companion object {
        fun fromJson(json: String?): HistoryTarget? = try {
            val o = JSONObject(json ?: return null)
            PlayHistory.entryFrom(o.getJSONObject("entry"))?.let { HistoryTarget(o.optString("scope"), it) }
        } catch (_: Exception) { null }
    }
}

/** The completion rules the app already uses: an episode counts at 90 %, a film at 95 %, and the file running out always counts. */
object PlayCompletion {
    const val EPISODE = 0.9
    const val FILM = 0.95
    fun reached(isEpisode: Boolean, positionMs: Long, durationMs: Long, ended: Boolean): Boolean {
        if (ended) return true
        if (durationMs <= 0) return false   // an unknown length never implies completion
        return positionMs >= durationMs * (if (isEpisode) EPISODE else FILM)
    }
}

/**
 * Adds up the time something is really playing. Fed play/pause changes with a monotonic clock, so seeking, buffering
 * and pauses (the player is not "playing" then) add nothing, and repeating a callback changes nothing.
 */
class PlayTimer {
    private var since = -1L
    var accumulatedMs = 0L; private set
    val playing get() = since >= 0

    fun setPlaying(playing: Boolean, monoMs: Long) {
        if (playing) { if (since < 0) since = monoMs }
        else if (since >= 0) { accumulatedMs += (monoMs - since).coerceAtLeast(0); since = -1 }
    }

    fun totalMs(monoMs: Long) = accumulatedMs + if (since >= 0) (monoMs - since).coerceAtLeast(0) else 0L
}

/**
 * One playing session. Decides, at each checkpoint, whether history should hear of it: only after [QUALIFY_MS] of real
 * playing, or a real playback-ended event. Returns the entry to store, or null when there is nothing new to say.
 */
class PlaySession(private val target: HistoryTarget) {
    private val timer = PlayTimer()
    private var activityAt = 0L
    private var completedHere = false
    private var lastSaved: PlayedEntry? = null

    val scope get() = target.scope
    val totalPlayedMs get() = timer.accumulatedMs

    fun setPlaying(playing: Boolean, monoMs: Long, wallMs: Long) {
        if (playing || timer.playing) activityAt = wallMs
        timer.setPlaying(playing, monoMs)
    }

    /**
     * [position] and [duration] are the player's (duration <= 0 when unknown). [trustworthy] is false mid-seek or while
     * buffering, when the position is not a real one. [ended] is a genuine end of playback; [credits] means the viewer left in the credits, which counts as finished but does not itself count as watching. [more] fills in details
     * learned since the session began (an episode's title, its still).
     */
    fun checkpoint(monoMs: Long, wallMs: Long, position: Long, duration: Long, trustworthy: Boolean, ended: Boolean, more: PlayedEntry? = null, credits: Boolean = false): PlayedEntry? {
        if (timer.playing) activityAt = wallMs
        if (!ended && (!trustworthy || timer.totalMs(monoMs) < QUALIFY_MS)) return null
        if (activityAt == 0L) activityAt = wallMs
        val length = duration.coerceAtLeast(0)
        val at = if ((ended || credits) && length > 0) length else position.coerceAtLeast(0)
        if (!ended && at <= 0) return null
        if (credits || PlayCompletion.reached(target.entry.isEpisode, at, length, ended)) completedHere = true
        val base = more?.let { target.entry.learn(it) } ?: target.entry
        val entry = base.copy(playedAt = activityAt, positionMs = at, durationMs = if (length > 0) length else base.durationMs, completed = completedHere)
        if (entry == lastSaved) return null
        lastSaved = entry
        return entry
    }

    companion object { const val QUALIFY_MS = 30_000L }
}

/** The pure list operations behind the history. */
object PlayHistory {
    const val CAP = 200

    /** [list] with [e] at the front, absorbing the entries it matches. Newest first, at most [CAP]. */
    fun record(list: List<PlayedEntry>, e: PlayedEntry): List<PlayedEntry> {
        if (!e.hasId) return list
        val merged = list.filter { it.sameAs(e) }.fold(e) { acc, old -> acc.learn(old) }
        return (listOf(merged) + list.filterNot { it.sameAs(e) }).sortedByDescending { it.playedAt }.take(CAP)
    }

    /** What stream progress genuinely knows: which episode, how far, and when. No titles; those stay blank. */
    fun fromStreamProgress(json: String?): List<PlayedEntry> = StreamProgress.all(json).mapNotNull { (imdb, ep) ->
        if (ep.atMs <= 0 || ep.resumeMs <= 0 || (ep.positionMs < 30_000 && !ep.played)) return@mapNotNull null
        // Finished only when its latest viewing was: a rewatch stopped partway is not finished by the old furthest point.
        PlayedEntry(Kind.SHOW, seriesImdbId = imdb, season = ep.season, episode = ep.episode, playedAt = ep.atMs,
            positionMs = ep.resumeMs, durationMs = ep.durationMs, completed = ep.finishedLast)
    }

    fun entryJson(e: PlayedEntry): JSONObject = JSONObject().put("kind", e.kind.name).put("tmdb", e.tmdbId ?: "").put("imdb", e.imdbId ?: "").put("jf", e.jellyfinId ?: "")
        .put("sTmdb", e.seriesTmdbId ?: "").put("sImdb", e.seriesImdbId ?: "").put("sJf", e.seriesJellyfinId ?: "")
        .put("title", e.title).put("epTitle", e.episodeTitle ?: "").put("season", e.season ?: -1).put("episode", e.episode ?: -1)
        .put("poster", e.poster ?: "").put("backdrop", e.backdrop ?: "").put("still", e.still ?: "")
        .put("at", e.playedAt).put("pos", e.positionMs).put("dur", e.durationMs).put("done", e.completed).put("src", e.source)

    fun entryFrom(o: JSONObject): PlayedEntry? {
        val kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrNull() ?: return null
        fun s(k: String) = o.optString(k).ifBlank { null }
        val e = PlayedEntry(kind, s("tmdb"), s("imdb"), s("jf"), s("sTmdb"), s("sImdb"), s("sJf"), o.optString("title"), s("epTitle"),
            o.optInt("season", -1).takeIf { it >= 0 }, o.optInt("episode", -1).takeIf { it >= 0 }, s("poster"), s("backdrop"), s("still"),
            o.optLong("at"), o.optLong("pos"), o.optLong("dur"), o.optBoolean("done"), s("src") ?: PlayedEntry.SOURCE_STREAM)
        return e.takeIf { it.hasId }
    }

    fun toJson(list: List<PlayedEntry>): String = JSONArray().also { a -> list.forEach { a.put(entryJson(it)) } }.toString()

    fun parse(json: String?): List<PlayedEntry> = try {
        val a = JSONArray(json ?: "[]")
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { entryFrom(it) } }
    } catch (_: Exception) { emptyList() }
}

/**
 * Playback history kept on this TV, in its own preferences (not the watchlist, favourites, Continue watching or the
 * recommendation history). Each Jellyfin server and signed-in user has its own list ([FavouritesStore.scope]).
 */
class PlayHistoryStore(platform: Platform) {
    private val prefs = platform.preferences("play_history")

    private fun key(scope: String) = "hist_" + scope.ifBlank { "local" }

    /**
     * The history for [scope]. A server and user with none of their own yet start from what was watched before any server
     * was set up (streams), never carrying a Jellyfin id across.
     */
    fun list(scope: String): List<PlayedEntry> {
        prefs.getString(key(scope), null)?.let { return PlayHistory.parse(it) }
        if (scope.isBlank()) return emptyList()
        return PlayHistory.parse(prefs.getString(key(""), null)).map { it.copy(jellyfinId = null, seriesJellyfinId = null) }.filter { it.hasId }
    }

    /** Adds [items] to what [scope] holds (a restore): the same film or episode is one record, the newer viewing's facts kept. */
    fun merge(scope: String, items: List<PlayedEntry>) {
        if (items.isEmpty()) return
        val now = list(scope)
        val merged = items.fold(now) { acc, e ->
            val same = acc.firstOrNull { it.sameAs(e) }
            if (same != null && same.playedAt >= e.playedAt) PlayHistory.record(acc.filterNot { it === same }, same.learn(e)) else PlayHistory.record(acc, e)
        }
        prefs.edit().putString(key(scope), PlayHistory.toJson(merged)).apply()
    }

    /** Stores [e]; the first time a scope is written to, the facts stream progress already holds are added too. */
    fun record(scope: String, e: PlayedEntry, streamProgress: String? = null) {
        var now = list(scope)
        val seed = "seeded_" + scope.ifBlank { "local" }
        if (!prefs.getBoolean(seed, false)) {
            now = PlayHistory.fromStreamProgress(streamProgress).fold(now) { acc, old -> PlayHistory.record(acc, old) }
            prefs.edit().putBoolean(seed, true).apply()
        }
        prefs.edit().putString(key(scope), PlayHistory.toJson(PlayHistory.record(now, e))).apply()
    }
}

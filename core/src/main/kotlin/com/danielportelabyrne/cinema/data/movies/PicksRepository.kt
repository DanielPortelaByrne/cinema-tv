package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.platform.Platform
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What has been played or streamed in Cinema, by TMDB id, so picks can learn from streams too (they never
 * reach the library). The newest 200 are kept, in a small file.
 */
class WatchHistory(platform: Platform) {
    private val file = File(platform.filesDir, "watch_history.json")

    @Synchronized fun all(): List<HistoryEntry> = try {
        if (!file.exists()) emptyList() else JSONArray(file.readText()).let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val g = o.optJSONArray("genres")
                HistoryEntry(o.getLong("id"), Kind.valueOf(o.getString("kind")), o.getString("name"),
                    (0 until (g?.length() ?: 0)).map { g!!.getString(it) }, o.getLong("at"), o.optBoolean("streamed"))
            }
        }
    } catch (_: Exception) { emptyList() }

    /** Notes that [name] was played ([streamed] when it came from the stream service). Blocking, but small. */
    @Synchronized fun add(tmdbId: Long, kind: Kind, name: String, genres: List<String>, streamed: Boolean, atMs: Long = System.currentTimeMillis()) {
        val list = (all().filterNot { it.tmdbId == tmdbId && it.kind == kind && atMs - it.atMs < 6 * 60 * 60 * 1000L } +
            HistoryEntry(tmdbId, kind, name, genres, atMs, streamed)).takeLast(200)
        val a = JSONArray()
        list.forEach { h -> a.put(JSONObject().put("id", h.tmdbId).put("kind", h.kind.name).put("name", h.name).put("genres", JSONArray(h.genres)).put("at", h.atMs).put("streamed", h.streamed)) }
        try { File(file.parentFile, file.name + ".tmp").also { it.writeText(a.toString()); it.renameTo(file) } } catch (_: Exception) { }
    }
}

/**
 * "Picked for you", kept on disk so the page shows the last picks at once. They are worked out again once a day, or
 * sooner (at most every half hour) when something new has been watched or added. Blocking; call off the main thread.
 */
class PicksRepository(
    platform: Platform, private val seerr: SeerrClient, private val jellyfin: JellyfinClient, val history: WatchHistory,
    private val remote: () -> SuggestashowClient? = { null }
) {
    private val file = File(platform.filesDir, "picks.json")
    private val prefs = platform.preferences("picks")
    @Volatile private var memory: List<Pick>? = null

    /** The last picks worked out, without any network. */
    fun saved(): List<Pick> = memory ?: (try { if (file.exists()) Picks.fromJson(file.readText()) else emptyList() } catch (_: Exception) { emptyList() }).also { memory = it }

    /** Fresh picks when they are due, else the saved ones. Null when they could not be worked out (Seerr signed out or unreachable). */
    @Synchronized fun fresh(): List<Pick>? {
        remote()?.let { server -> return server.picks()?.also { keep(it) } }
        if (!seerr.isSignedIn) return null
        val library = jellyfin.takeIf { it.isConnected }?.library().orEmpty()
        val watched = history.all()
        val signature = "${library.size}:${library.firstOrNull()?.id}:${library.count { it.played }}:${watched.size}:${watched.lastOrNull()?.atMs}"
        val age = System.currentTimeMillis() - prefs.getLong("at", 0L)
        val saved = saved()
        val due = saved.isEmpty() || age > DAY_MS || (signature != prefs.getString("signature", null) && age > HALF_HOUR_MS)
        if (!due) return saved

        val librarySeeds = library.mapNotNull { item ->
            val id = item.tmdbId?.toLongOrNull() ?: return@mapNotNull null
            LibrarySeed(id, if (item.isSeries) Kind.SHOW else Kind.MOVIE, item.name, item.genres, item.played, item.resumeMs > 0)
        }
        val seeds = Picks.seeds(librarySeeds, watched, System.currentTimeMillis())
        if (seeds.isEmpty()) return emptyList()
        val suggestions = HashMap<String, List<Suggestion>>()
        var reached = 0
        seeds.forEach { s -> seerr.recommendations(s.kind, s.tmdbId)?.let { suggestions[s.key] = it; reached++ } }
        if (reached == 0) return null
        val exclude = librarySeeds.map { Picks.key(it.kind, it.tmdbId) }.toSet() + watched.map { Picks.key(it.kind, it.tmdbId) }
        val picks = Picks.rank(seeds, suggestions, exclude)
        keep(picks)
        prefs.edit().putLong("at", System.currentTimeMillis()).putString("signature", signature).apply()
        return picks
    }

    /** Tells a suggestashow server, when one is in use, that these picks were on screen. */
    fun shown(picks: List<Pick>) { remote()?.shown(picks) }

    private fun keep(picks: List<Pick>) {
        try { File(file.parentFile, file.name + ".tmp").also { it.writeText(Picks.toJson(picks)); it.renameTo(file) } } catch (_: Exception) { }
        memory = picks
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val HALF_HOUR_MS = 30 * 60 * 1000L
    }
}

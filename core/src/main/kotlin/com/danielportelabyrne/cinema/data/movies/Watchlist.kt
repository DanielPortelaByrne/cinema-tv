package com.danielportelabyrne.cinema.data.movies

import org.json.JSONArray
import org.json.JSONObject

/**
 * A film or series the owner means to watch, kept on the TV for the Watchlist rows. [kind] is null for what was kept
 * before it was recorded, and for Letterboxd's lists, which only hold films: [isSeries] treats those as films.
 */
data class WatchlistFilm(val imdbId: String, val name: String, val year: Int?, val poster: String?, val kind: Kind? = null) {
    val isSeries: Boolean get() = kind == Kind.SHOW
}

/** How the watchlist is kept and combined. Plain Kotlin so it is tested without a TV. */
object Watchlist {
    fun toJson(films: List<WatchlistFilm>): String = JSONArray().also { a ->
        films.forEach { f ->
            a.put(JSONObject().put("id", f.imdbId).put("name", f.name).put("year", f.year ?: 0).put("poster", f.poster ?: "").apply { f.kind?.let { put("kind", it.name) } })
        }
    }.toString()

    fun parse(json: String?): List<WatchlistFilm> = try {
        val a = JSONArray(json ?: "[]")
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WatchlistFilm(id, o.optString("name").ifBlank { id }, o.optInt("year").takeIf { it > 0 }, o.optString("poster").ifBlank { null },
                runCatching { Kind.valueOf(o.optString("kind")) }.getOrNull())
        }
    } catch (_: Exception) { emptyList() }

    /** [films] without the film [imdbId]; unchanged when it is not there. */
    fun without(films: List<WatchlistFilm>, imdbId: String): List<WatchlistFilm> = films.filterNot { it.imdbId == imdbId }

    /** [added] go in front of [existing], and a film already there keeps its place. */
    fun merge(existing: List<WatchlistFilm>, added: List<WatchlistFilm>): List<WatchlistFilm> {
        val have = existing.map { it.imdbId }.toSet()
        // One already there whose kind was never recorded takes it from the new copy (a series imported from Stremio).
        val kinds = added.filter { it.kind != null }.associate { it.imdbId to it.kind }
        return added.filter { it.imdbId !in have }.distinctBy { it.imdbId } + existing.map { f -> if (f.kind == null && kinds[f.imdbId] != null) f.copy(kind = kinds[f.imdbId]) else f }
    }
}

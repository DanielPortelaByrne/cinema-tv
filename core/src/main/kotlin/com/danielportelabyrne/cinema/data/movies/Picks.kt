package com.danielportelabyrne.cinema.data.movies

import org.json.JSONArray
import org.json.JSONObject

/** A film or series the household downloaded, watched or streamed, that picks are drawn from. */
data class PickSeed(val tmdbId: Long, val kind: Kind, val name: String, val weight: Double, val genres: List<String> = emptyList()) {
    val key: String get() = Picks.key(kind, tmdbId)
}

/** One library title as a seed sees it: newest first in the list it comes from. */
data class LibrarySeed(val tmdbId: Long, val kind: Kind, val name: String, val genres: List<String>, val played: Boolean, val started: Boolean)

/** Something played or streamed in Cinema, at [atMs]. */
data class HistoryEntry(val tmdbId: Long, val kind: Kind, val name: String, val genres: List<String>, val atMs: Long, val streamed: Boolean)

/** A title TMDB suggests from a seed (via Seerr), in TMDB's order. [owned] is Seerr saying it is already on the server. */
data class Suggestion(
    val tmdbId: Long, val kind: Kind, val name: String, val year: Int?, val overview: String?,
    val posterPath: String?, val backdropPath: String?, val genres: List<String>, val vote: Double?, val owned: Boolean,
    val posterUrl: String? = null, val backdropUrl: String? = null
) {
    val key: String get() = Picks.key(kind, tmdbId)

    /** The poster for a row: the full address a server sent, else TMDB's at row size. */
    val rowPoster: String? get() = posterUrl ?: posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }

    /** The backdrop for the featured title: the full address a server sent, else TMDB's full size. */
    val heroBackdrop: String? get() = backdropUrl ?: backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
}

/** A pick, with the seed that did most to suggest it ("Because you watched Inception"). */
data class Pick(val suggestion: Suggestion, val score: Double, val because: String)

/**
 * "Picked for you": titles TMDB recommends from what the household has downloaded, watched and streamed. A title that
 * several of those point to ranks above one only a single title points to; recent and finished ones count for more;
 * genres watched most get a small lift; anything already owned or watched is left out. Pure, so it can be tested.
 */
object Picks {
    fun key(kind: Kind, tmdbId: Long) = (if (kind == Kind.MOVIE) "m:" else "s:") + tmdbId

    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /**
     * The seeds, strongest first, at most [limit]. Library titles count by how recently they were added ([library] is
     * newest first), more when watched or started; things played or streamed here count by how recently, halving every
     * month. The same title from both adds up.
     */
    fun seeds(library: List<LibrarySeed>, history: List<HistoryEntry>, nowMs: Long, limit: Int = 12): List<PickSeed> {
        val weights = LinkedHashMap<String, PickSeed>()
        fun add(tmdbId: Long, kind: Kind, name: String, genres: List<String>, w: Double) {
            val k = key(kind, tmdbId)
            val before = weights[k]
            weights[k] = PickSeed(tmdbId, kind, name, (before?.weight ?: 0.0) + w, before?.genres?.takeIf { it.isNotEmpty() } ?: genres)
        }
        library.take(40).forEachIndexed { i, l ->
            var w = Math.pow(0.95, i.toDouble())
            if (l.played) w *= 1.5 else if (l.started) w *= 1.3
            add(l.tmdbId, l.kind, l.name, l.genres, w)
        }
        history.forEach { h ->
            val ageDays = ((nowMs - h.atMs).coerceAtLeast(0L)).toDouble() / DAY_MS
            add(h.tmdbId, h.kind, h.name, h.genres, 1.5 * Math.pow(0.5, ageDays / 30.0))
        }
        return weights.values.sortedByDescending { it.weight }.take(limit)
    }

    /**
     * Ranks what the seeds suggest. [suggestions] is each seed's list, keyed by [PickSeed.key]. [exclude] holds keys
     * already in the library or already watched. One seed cannot fill the list: each further pick it is the main
     * reason for counts a little less.
     */
    fun rank(seeds: List<PickSeed>, suggestions: Map<String, List<Suggestion>>, exclude: Set<String>, limit: Int = 20): List<Pick> {
        val seedKeys = seeds.map { it.key }.toSet()
        val genreTotal = HashMap<String, Double>()
        seeds.forEach { s -> s.genres.forEach { g -> genreTotal[norm(g)] = (genreTotal[norm(g)] ?: 0.0) + s.weight } }
        val genreSum = genreTotal.values.sum().takeIf { it > 0 } ?: 1.0

        class Tally(val s: Suggestion) { var score = 0.0; val by = HashMap<String, Double>() }
        val tallies = LinkedHashMap<String, Tally>()
        seeds.forEach { seed ->
            suggestions[seed.key].orEmpty().forEachIndexed { rank, sug ->
                if (sug.owned || sug.key in exclude || sug.key in seedKeys) return@forEachIndexed
                val t = tallies.getOrPut(sug.key) { Tally(sug) }
                val part = seed.weight / (1.0 + 0.12 * rank)
                t.score += part
                t.by[seed.name] = (t.by[seed.name] ?: 0.0) + part
            }
        }
        val scored = tallies.values.map { t ->
            val taste = t.s.genres.sumOf { g -> (genreTotal[norm(g)] ?: 0.0) / genreSum }.coerceAtMost(1.0)
            val quality = ((t.s.vote ?: 6.5) - 6.5) * 0.08
            Pick(t.s, t.score + 0.3 * taste + quality, t.by.maxByOrNull { it.value }!!.key)
        }.sortedByDescending { it.score }

        // Greedy, so the list is not ten titles all because of one film.
        val out = ArrayList<Pick>()
        val used = HashMap<String, Int>()
        val pool = scored.toMutableList()
        while (out.size < limit && pool.isNotEmpty()) {
            val best = pool.maxByOrNull { it.score * Math.pow(0.8, (used[it.because] ?: 0).toDouble()) }!!
            pool.remove(best); out += best
            used[best.because] = (used[best.because] ?: 0) + 1
        }
        return out
    }

    private fun norm(genre: String) = genre.lowercase().trim()

    /** TMDB's genre ids, films and series, by the names Jellyfin also uses. */
    private val GENRES = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary", 18 to "Drama",
        10751 to "Family", 14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music", 9648 to "Mystery", 10749 to "Romance",
        878 to "Science Fiction", 10770 to "TV Movie", 53 to "Thriller", 10752 to "War", 37 to "Western",
        10759 to "Action & Adventure", 10762 to "Kids", 10763 to "News", 10764 to "Reality", 10765 to "Sci-Fi & Fantasy",
        10766 to "Soap", 10767 to "Talk", 10768 to "War & Politics"
    )

    /** Seerr's `/movie/{id}/recommendations` (or `/tv/...`) answer. Status 4 (partly) and 5 (available) count as owned. */
    fun parseSuggestions(json: String): List<Suggestion> = try {
        val results = JSONObject(json).optJSONArray("results") ?: JSONArray()
        (0 until results.length()).mapNotNull { i ->
            val o = results.optJSONObject(i) ?: return@mapNotNull null
            val kind = when (o.optString("mediaType")) { "movie" -> Kind.MOVIE; "tv" -> Kind.SHOW; else -> return@mapNotNull null }
            val id = o.optLong("id", 0L).takeIf { it > 0 } ?: return@mapNotNull null
            val name = o.optString("title").ifBlank { o.optString("name") }.ifBlank { return@mapNotNull null }
            val date = o.optString("releaseDate").ifBlank { o.optString("firstAirDate") }
            val ids = o.optJSONArray("genreIds")
            val status = o.optJSONObject("mediaInfo")?.optInt("status", 0) ?: 0
            Suggestion(
                tmdbId = id, kind = kind, name = name, year = date.take(4).toIntOrNull(),
                overview = o.optString("overview").ifBlank { null },
                posterPath = o.optString("posterPath").takeIf { it.startsWith("/") },
                backdropPath = o.optString("backdropPath").takeIf { it.startsWith("/") },
                genres = (0 until (ids?.length() ?: 0)).mapNotNull { GENRES[ids!!.optInt(it)] },
                vote = o.optDouble("voteAverage", -1.0).takeIf { it > 0 },
                owned = status == 4 || status == 5
            )
        }
    } catch (_: Exception) { emptyList() }

    // --- Saving picks between launches ------------------------------------------------------------

    fun toJson(picks: List<Pick>): String = JSONArray().apply {
        picks.forEach { p ->
            val s = p.suggestion
            put(JSONObject().put("id", s.tmdbId).put("kind", s.kind.name).put("name", s.name).put("year", s.year ?: 0)
                .put("overview", s.overview ?: "").put("poster", s.posterPath ?: "").put("backdrop", s.backdropPath ?: "")
                .put("posterUrl", s.posterUrl ?: "").put("backdropUrl", s.backdropUrl ?: "")
                .put("genres", JSONArray(s.genres)).put("vote", s.vote ?: 0.0).put("score", p.score).put("because", p.because))
        }
    }.toString()

    fun fromJson(json: String): List<Pick> = try {
        val a = JSONArray(json)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val g = o.optJSONArray("genres")
            Pick(Suggestion(o.getLong("id"), Kind.valueOf(o.getString("kind")), o.getString("name"), o.optInt("year").takeIf { it > 0 },
                o.optString("overview").ifBlank { null }, o.optString("poster").ifBlank { null }, o.optString("backdrop").ifBlank { null },
                (0 until (g?.length() ?: 0)).map { g!!.getString(it) }, o.optDouble("vote").takeIf { it > 0 }, false,
                o.optString("posterUrl").ifBlank { null }, o.optString("backdropUrl").ifBlank { null }),
                o.optDouble("score"), o.getString("because"))
        }
    } catch (_: Exception) { emptyList() }
}

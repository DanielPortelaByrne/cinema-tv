package com.danielportelabyrne.cinema.data.movies

import org.json.JSONArray
import org.json.JSONObject

/** What the watchlist page knows of a film beyond its name: read from Cinemeta once and kept. */
data class WatchlistFacts(val genres: List<String>, val runtimeMin: Int?, val rating: Double?, val backdrop: String? = null)

/** How the watchlist page orders films. The watchlist itself keeps the newest first. */
enum class WatchlistSort(val label: String) {
    ADDED("Recently added"), TITLE("Title A–Z"), NEWEST("Newest"), OLDEST("Oldest"), RATING("Highest rated"), SHORTEST("Shortest first")
}

/** How the watchlist page groups films into rows. */
enum class WatchlistGroup(val label: String) { NONE("Everything"), GENRE("By genre"), DECADE("By decade") }

/** Which films the watchlist page shows. */
enum class WatchlistShow(val label: String) { ALL("All"), IN_LIBRARY("In my library"), NOT_IN_LIBRARY("Not in my library") }

object WatchlistView {
    /** A row of the page: its heading (none for the plain grid) and its films, in order. */
    data class Section(val heading: String?, val films: List<WatchlistFilm>)

    /** Heading for films whose details have not been read yet. */
    const val UNSORTED = "Details on the way"

    /**
     * The page's rows. [inLibrary] says whether a film (by IMDb id) is in the owner's library. Grouped by genre, a film
     * appears under each of its genres, the biggest genres first; by decade, newest decade first.
     */
    fun arrange(films: List<WatchlistFilm>, facts: Map<String, WatchlistFacts>, inLibrary: (String) -> Boolean,
                sort: WatchlistSort, group: WatchlistGroup, show: WatchlistShow): List<Section> {
        val shown = films.filter {
            when (show) { WatchlistShow.ALL -> true; WatchlistShow.IN_LIBRARY -> inLibrary(it.imdbId); WatchlistShow.NOT_IN_LIBRARY -> !inLibrary(it.imdbId) }
        }
        val sorted = sorted(shown, facts, sort)
        return when (group) {
            WatchlistGroup.NONE -> if (sorted.isEmpty()) emptyList() else listOf(Section(null, sorted))
            WatchlistGroup.GENRE -> {
                val byGenre = LinkedHashMap<String, MutableList<WatchlistFilm>>()
                val unknown = ArrayList<WatchlistFilm>()
                sorted.forEach { f ->
                    val g = facts[f.imdbId]?.genres.orEmpty()
                    if (g.isEmpty()) unknown += f else g.forEach { byGenre.getOrPut(it) { ArrayList() } += f }
                }
                byGenre.entries.sortedWith(compareByDescending<Map.Entry<String, MutableList<WatchlistFilm>>> { it.value.size }.thenBy { it.key })
                    .map { Section(it.key, it.value) } + listOfNotNull(unknown.takeIf { it.isNotEmpty() }?.let { Section(UNSORTED, it) })
            }
            WatchlistGroup.DECADE -> sorted.groupBy { it.year?.let { y -> y / 10 * 10 } }.entries
                .sortedByDescending { it.key ?: Int.MIN_VALUE }
                .map { Section(it.key?.let { d -> "${d}s" } ?: "Year unknown", it.value) }
        }
    }

    fun sorted(films: List<WatchlistFilm>, facts: Map<String, WatchlistFacts>, sort: WatchlistSort): List<WatchlistFilm> = when (sort) {
        WatchlistSort.ADDED -> films
        WatchlistSort.TITLE -> films.sortedBy { it.name.removePrefix("The ").lowercase() }
        WatchlistSort.NEWEST -> films.sortedWith(compareByDescending<WatchlistFilm> { it.year ?: Int.MIN_VALUE })
        WatchlistSort.OLDEST -> films.sortedWith(compareBy<WatchlistFilm> { it.year ?: Int.MAX_VALUE })
        WatchlistSort.RATING -> films.sortedWith(compareByDescending<WatchlistFilm> { facts[it.imdbId]?.rating ?: -1.0 })
        WatchlistSort.SHORTEST -> films.sortedWith(compareBy<WatchlistFilm> { facts[it.imdbId]?.runtimeMin ?: Int.MAX_VALUE })
    }

    fun factsJson(facts: Map<String, WatchlistFacts>): String = JSONObject().also { o ->
        facts.forEach { (id, f) ->
            o.put(id, JSONObject().put("g", JSONArray(f.genres)).put("r", f.runtimeMin ?: 0).put("s", f.rating ?: 0.0).put("b", f.backdrop ?: ""))
        }
    }.toString()

    fun parseFacts(json: String?): Map<String, WatchlistFacts> = try {
        val o = JSONObject(json ?: "{}")
        o.keys().asSequence().associateWith { id ->
            val f = o.getJSONObject(id)
            val g = f.optJSONArray("g")
            WatchlistFacts((0 until (g?.length() ?: 0)).map { g!!.optString(it) }.filter { it.isNotBlank() },
                f.optInt("r").takeIf { it > 0 }, f.optDouble("s").takeIf { it > 0 }, f.optString("b").ifBlank { null })
        }
    } catch (_: Exception) { emptyMap() }
}

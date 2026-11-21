package com.danielportelabyrne.cinema.data.movies

import java.util.concurrent.ConcurrentHashMap

/**
 * The connections of a Film Link session, from TMDB's recommendations (through Seerr), the same list the title page's
 * "More films like this" row shows. A film's connections are fetched once and kept for the session, so checking a pair
 * and then playing it always see the same list, and a hop never waits for something already looked up. A failed lookup
 * is not kept: asking again tries again.
 */
class CachedFilmGraph(private val recommendations: (Long) -> List<Suggestion>?) : FilmGraph {
    private val kept = ConcurrentHashMap<Long, List<LinkFilm>>()

    override fun neighbours(id: Long): List<LinkFilm>? {
        kept[id]?.let { return it }
        val fetched = recommendations(id) ?: return null
        return FilmLinkRules.connected(id, fetched).also { kept[id] = it }
    }

    /** What is already known about [id], without a lookup. */
    fun known(id: Long): List<LinkFilm>? = kept[id]
}

object FilmLinkSeeds {
    /** The pages of popular films a start is drawn from: broad enough to vary, shallow enough to be well connected. */
    const val PAGES = 5

    /**
     * Films to start from: the popular films of a few random pages, films only and without repeats. [discover] returns
     * one page (1-based) or null when it could not be fetched; pages that fail are skipped.
     */
    fun collect(discover: (Int) -> List<Suggestion>?, random: kotlin.random.Random, pages: Int = 2): List<LinkFilm> =
        (1..PAGES).shuffled(random).take(pages)
            .flatMap { discover(it).orEmpty() }
            .filter { it.kind == Kind.MOVIE && it.tmdbId > 0 && it.posterPath != null }
            .distinctBy { it.tmdbId }
            .map(LinkFilm::of)
}

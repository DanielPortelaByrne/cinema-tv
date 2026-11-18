package com.danielportelabyrne.cinema.data.movies

import java.time.LocalDate

/**
 * Whether a series earns the "New episode" badge: an episode really released (its air date, never the date it reached the
 * library) in the last [WINDOW_DAYS] days that this viewer has not finished. Pure: it reads a cached [EpisodeCatalogue] and
 * the viewer's play history, and never fetches. Release is not availability: the episode need not be playable yet.
 */
object NewEpisodes {
    const val WINDOW_DAYS = 7

    /**
     * [today] and each air date are ISO dates ("2026-12-21"). An episode counts from the day its date arrives (a date-only
     * value has no time zone, so it is never counted early) until [WINDOW_DAYS] days later. No catalogue, no date, a date that
     * does not parse, or a future date: not new. [watched] holds the viewer's finished (season, episode) pairs.
     */
    fun has(catalogue: EpisodeCatalogue?, today: String, watched: Set<Pair<Int, Int>>): Boolean {
        if (catalogue == null) return false
        val now = parse(today) ?: return false
        return catalogue.episodes.any { e ->
            if (e.season <= 0) return@any false
            val aired = parse(e.airDate) ?: return@any false
            val age = java.time.temporal.ChronoUnit.DAYS.between(aired, now)
            age in 0 until WINDOW_DAYS && (e.season to e.episode) !in watched && (e.episode..e.lastEpisode).none { (e.season to it) in watched }
        }
    }

    /** The finished episodes of one series for the viewer whose [history] this is, matched by the series' ids, never by name. */
    fun watchedOf(history: List<PlayedEntry>, tmdbId: String?, imdbId: String?, jellyfinId: String?): Set<Pair<Int, Int>> =
        history.filter { h ->
            h.isEpisode && h.completed && h.season != null && h.episode != null &&
                (same(h.seriesTmdbId, tmdbId) || same(h.seriesImdbId, imdbId) || same(h.seriesJellyfinId, jellyfinId))
        }.map { it.season!! to it.episode!! }.toSet()

    private fun same(a: String?, b: String?) = !a.isNullOrBlank() && a == b

    private fun parse(iso: String?): LocalDate? = runCatching { LocalDate.parse(iso!!.take(10)) }.getOrNull()
}

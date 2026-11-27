package com.danielportelabyrne.cinema.mobile.request

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.movies.Availability
import com.danielportelabyrne.cinema.data.movies.EpisodeInfo
import com.danielportelabyrne.cinema.data.movies.EpisodeState
import com.danielportelabyrne.cinema.data.movies.SeasonInfo
import com.danielportelabyrne.cinema.ui.EpisodeCards

/** An episode by its numbers. */
data class EpisodeNumber(val season: Int, val episode: Int)

/** The numbers a library item holds: one file can hold a run of episodes. */
fun JellyfinItem.episodeNumbers(): List<EpisodeNumber> {
    val s = season ?: return emptyList()
    val first = episode ?: return emptyList()
    return (first..maxOf(first, episodeEnd ?: first)).map { EpisodeNumber(s, it) }
}

/**
 * A season's episodes that are out but not on the server. [whole] when none of it has been had or asked for, so Seerr
 * can take it as one request; otherwise the episodes go to Sonarr. [asked] when every one is already asked for.
 */
data class MissingSeason(val number: Int, val name: String, val episodes: List<Int>, val whole: Boolean, val asked: Boolean)

object Missing {
    private val UNDER_WAY = setOf(Availability.REQUESTED, Availability.PROCESSING, Availability.DOWNLOADING)

    /**
     * What a series lacks. [episodes] is each season's list from Seerr, [owned] the library's episodes, [sonarr] what
     * Sonarr says of each episode (empty when it does not know the series) and [today] an ISO date.
     */
    fun of(
        seasons: List<SeasonInfo>, episodes: Map<Int, List<EpisodeInfo>>, owned: List<JellyfinItem>,
        sonarr: Map<Pair<Int, Int>, EpisodeState>, today: String,
    ): List<MissingSeason> {
        val had = owned.filter { !it.missing }.flatMap { it.episodeNumbers() }.toSet()
        return seasons.mapNotNull { s ->
            val lacking = episodes[s.number].orEmpty()
                .filter { !EpisodeCards.unaired(it.airDate, today) }
                .map { it.number }
                .filter { EpisodeNumber(s.number, it) !in had && sonarr[s.number to it] != EpisodeState.DOWNLOADED }
                .sorted()
            if (lacking.isEmpty()) return@mapNotNull null
            val wanted = lacking.count { sonarr[s.number to it] == EpisodeState.REQUESTED }
            val none = had.none { it.season == s.number }
            MissingSeason(
                s.number, s.name, lacking,
                whole = none && wanted == 0 && s.availability == Availability.NONE,
                asked = wanted == lacking.size || s.availability in UNDER_WAY,
            )
        }
    }
}

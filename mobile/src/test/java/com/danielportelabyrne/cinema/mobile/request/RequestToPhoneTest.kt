package com.danielportelabyrne.cinema.mobile.request

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.movies.Availability
import com.danielportelabyrne.cinema.data.movies.EpisodeInfo
import com.danielportelabyrne.cinema.data.movies.EpisodeState
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.SeasonInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestToPhoneTest {
    private fun episode(season: Int, number: Int, end: Int? = null, missing: Boolean = false, size: Long? = 1000) =
        JellyfinItem(id = "s${season}e$number", name = "E$number", year = null, type = "Episode", mediaSourceId = null, season = season, episode = number,
            episodeEnd = end, missing = missing, sizeBytes = size)

    private fun season(n: Int, availability: Availability = Availability.NONE) = SeasonInfo(n, "Season $n", 3, availability)
    private fun listing(n: Int) = (1..3).map { EpisodeInfo(n, it, "E$it", null, "2025-0$n-0$it") }

    private fun missing(seasons: List<SeasonInfo>, owned: List<JellyfinItem>, sonarr: Map<Pair<Int, Int>, EpisodeState> = emptyMap(), today: String = "2026-01-01") =
        Missing.of(seasons, seasons.associate { it.number to listing(it.number) }, owned, sonarr, today)

    @Test fun findsGapsAndWholeSeasons() {
        val found = missing(listOf(season(1, Availability.PARTIAL), season(2)), listOf(episode(1, 1), episode(1, 3)))
        assertEquals(listOf(MissingSeason(1, "Season 1", listOf(2), whole = false, asked = false),
            MissingSeason(2, "Season 2", listOf(1, 2, 3), whole = true, asked = false)), found)
    }

    @Test fun completeSeriesLacksNothing() =
        assertTrue(missing(listOf(season(1, Availability.AVAILABLE)), listOf(episode(1, 1, end = 3))).isEmpty())

    @Test fun listsNothingThatHasNotAired() {
        val found = missing(listOf(season(1)), emptyList(), today = "2025-01-02")
        assertEquals(listOf(1, 2), found.single().episodes)
    }

    @Test fun saysWhatIsAlreadyAskedFor() {
        val asked = (1..3).associate { (1 to it) to EpisodeState.REQUESTED }
        val found = missing(listOf(season(1)), emptyList(), asked).single()
        assertTrue(found.asked)
        assertEquals(false, found.whole)
    }

    @Test fun aFileSonarrHasIsNotMissing() =
        assertEquals(listOf(2, 3), missing(listOf(season(1)), emptyList(), mapOf((1 to 1) to EpisodeState.DOWNLOADED)).single().episodes)

    @Test fun virtualEpisodesAreNotOwned() =
        assertEquals(listOf(1, 2, 3), missing(listOf(season(1)), listOf(episode(1, 1, missing = true))).single().episodes)

    private val want = Waiting(Kind.SHOW, "42", "tt1", "A Series", null, setOf(EpisodeNumber(1, 1), EpisodeNumber(1, 2), EpisodeNumber(1, 3)))

    @Test fun takesOnlyWantedEpisodesThatHaveFiles() {
        val ready = ServerWatch.ready(want, listOf(episode(1, 1), episode(1, 2, missing = true), episode(1, 3, size = null), episode(2, 1)))
        assertEquals(listOf("s1e1"), ready.map { it.id })
    }

    @Test fun forgetsEpisodesAsTheyArrive() {
        val left = want.without(listOf(episode(1, 1, end = 2)))!!
        assertEquals(setOf(EpisodeNumber(1, 3)), left.episodes)
        assertNull(left.without(listOf(episode(1, 3))))
    }

    @Test fun aFilmIsDoneOnceItArrives() {
        val film = Waiting(Kind.MOVIE, "7", null, "A Film", null)
        assertEquals(film, film.without(emptyList()))
        assertNull(film.without(listOf(JellyfinItem(id = "f", name = "A Film", year = null, type = "Movie", mediaSourceId = null))))
    }

    @Test fun survivesBeingSaved() = assertEquals(want, Waiting.of(org.json.JSONObject(want.toJson().toString())))
}

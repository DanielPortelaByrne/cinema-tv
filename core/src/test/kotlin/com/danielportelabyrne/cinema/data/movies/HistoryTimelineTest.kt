package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class HistoryTimelineTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val h = 3_600_000L
    private val day = 24 * h
    private val t0 = 1_000_000L

    private fun film(id: String, at: Long = 0) = PlayedEntry(Kind.MOVIE, tmdbId = id, title = "Film $id", playedAt = at)
    private fun ep(series: String, season: Int?, n: Int, at: Long = 0) =
        PlayedEntry(Kind.SHOW, seriesTmdbId = series, title = "Show $series", season = season, episode = n, episodeTitle = "Ep $n", playedAt = at)

    /** [watched] in viewing order, oldest first, one hour apart; stops come back newest first. */
    private fun stops(vararg watched: PlayedEntry): List<HistoryItem> =
        HistoryTimeline.group(RecentlyWatched.shelf(watched.mapIndexed { i, e -> e.copy(playedAt = t0 + i * h) }))

    private fun shape(items: List<HistoryItem>) = items.map {
        when (it) {
            is HistoryItem.Film -> it.card.title
            is HistoryItem.Run -> "${it.title} S${it.season}: " + it.cards.joinToString(",") { c -> "E${c.entry.episode}" }
        }
    }

    @Test fun consecutiveEpisodesOfOneSeasonFormOneGroup() {
        assertEquals(listOf("Show 1 S2: E2,E1"), shape(stops(ep("1", 2, 1), ep("1", 2, 2))))
    }

    @Test fun theBriefsExampleGroupsAsDescribed() {
        val s = stops(film("A"), ep("1", 2, 1), ep("1", 2, 2), film("B"), ep("1", 1, 20), ep("2", 1, 1), film("C"))
        assertEquals(listOf("Film C", "Show 2 S1: E1", "Show 1 S1: E20", "Film B", "Show 1 S2: E2,E1", "Film A"), shape(s))
    }

    @Test fun aMovieInterruptsAGroup() {
        assertEquals(3, stops(ep("1", 1, 1), film("A"), ep("1", 1, 2)).size)
    }

    @Test fun aDifferentSeasonInterruptsAGroup() {
        assertEquals(listOf("Show 1 S2: E1", "Show 1 S1: E2,E1"), shape(stops(ep("1", 1, 1), ep("1", 1, 2), ep("1", 2, 1))))
    }

    @Test fun theSameSeasonLaterIsAnIndependentGroup() {
        assertEquals(listOf("Show 1 S1: E2", "Show 2 S1: E1", "Show 1 S1: E1"), shape(stops(ep("1", 1, 1), ep("2", 1, 1), ep("1", 1, 2))))
    }

    @Test fun aGroupHoldsOnlyWhatWasWatchedNeverTheGapsBetween() {
        val run = stops(ep("1", 1, 3), ep("1", 1, 7)).single() as HistoryItem.Run
        assertEquals(listOf(7, 3), run.cards.map { it.entry.episode })
        assertEquals("Season 1 · 2 episodes", run.summary)
    }

    @Test fun timestampsAndOrderAreKeptWhateverOrderTheyArriveIn() {
        val cards = RecentlyWatched.shelf(listOf(ep("1", 1, 1, t0), ep("1", 1, 2, t0 + h), film("A", t0 + 2 * h))).reversed()
        val s = HistoryTimeline.group(cards)
        assertEquals(t0 + 2 * h, s[0].at)
        val run = s[1] as HistoryItem.Run
        assertEquals(listOf(t0 + h, t0), run.cards.map { it.entry.playedAt })
        assertEquals(t0 + h, run.at)
    }

    @Test fun anEpisodeWithNoKnownSeasonNeverJoinsAGroup() {
        val cards = listOf(ep("1", null, 1, 20), ep("1", null, 2, 10)).map { RecentCard(it, "Show 1", null, null, null, true, null, null) }
        assertEquals(2, HistoryTimeline.group(cards).size)
    }

    @Test fun aRepeatedWatchIsOneEventNotTwo() {
        // The store keeps one record per episode (its latest play), so a re-watch is that one record: nothing is invented.
        val run = HistoryTimeline.group(RecentlyWatched.shelf(listOf(ep("1", 1, 1, 10), ep("1", 1, 1, 30), ep("1", 1, 2, 20)))).single() as HistoryItem.Run
        assertEquals(listOf(1, 2), run.cards.map { it.entry.episode!! }.sorted())
        assertEquals(30L, run.at)
    }

    @Test fun unnamedRecordsAreLeftOutAndEmptyIsEmpty() {
        assertTrue(HistoryTimeline.group(RecentlyWatched.shelf(listOf(film("A", 5).copy(title = "")))).isEmpty())
        assertTrue(HistoryTimeline.group(emptyList()).isEmpty())
    }

    @Test fun daysAreHeadedByDate() {
        val now = 100 * day + 15 * h   // Saturday 11 April 1970, 15:00 UTC
        val items = listOf(film("a", now - h), film("b", now - 16 * h), film("c", now - 3 * day), film("d", now - 40 * day), film("e", 0L))
            .map { HistoryItem.Film(RecentlyWatched.card(it)!!) }
        assertEquals(listOf("Today", "Yesterday", "Wednesday", "Monday 2 March", "Earlier"), HistoryTimeline.days(items, now, utc, Locale.UK).map { it.label })
    }

    @Test fun oneDayHoldsItsStopsInOrder() {
        val now = 5 * day + 20 * h
        val items = listOf(film("a", now - h), film("b", now - 2 * h)).map { HistoryItem.Film(RecentlyWatched.card(it)!!) }
        val d = HistoryTimeline.days(items, now, utc, Locale.UK).single()
        assertEquals(listOf("Film a", "Film b"), d.items.map { (it as HistoryItem.Film).card.title })
        assertEquals("19:00", HistoryTimeline.clock(now - h, utc, Locale.UK))
    }
}

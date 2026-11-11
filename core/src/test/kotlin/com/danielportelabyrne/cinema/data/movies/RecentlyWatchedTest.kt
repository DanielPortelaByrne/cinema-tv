package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.platform.MemoryPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentlyWatchedTest {
    private fun film(id: String, at: Long, pos: Long = 600_000, dur: Long = 6_000_000, done: Boolean = false, title: String = "Film $id") =
        PlayedEntry(Kind.MOVIE, tmdbId = id, title = title, playedAt = at, positionMs = pos, durationMs = dur, completed = done, poster = "p$id")
    private fun ep(n: Int, at: Long, season: Int = 2, series: String = "100", title: String = "Show", done: Boolean = false, pos: Long = 1_200_000, dur: Long = 2_400_000) =
        PlayedEntry(Kind.SHOW, seriesTmdbId = series, title = title, season = season, episode = n, episodeTitle = "Ep $n", playedAt = at, positionMs = pos, durationMs = dur, completed = done)

    @Test fun newestPlaybackFirst() {
        val s = RecentlyWatched.shelf(listOf(film("1", 10), film("2", 30), ep(1, 20)))
        assertEquals(listOf(30L, 20L, 10L), s.map { it.entry.playedAt })
    }

    @Test fun filmCardShowsTitleAndProgress() {
        val c = RecentlyWatched.shelf(listOf(film("1", 5, pos = 1_500_000, dur = 6_000_000))).single()
        assertEquals("Film 1", c.title); assertNull(c.code); assertEquals(0.25f, c.progress!!, 0.0001f)
        assertFalse(c.watched); assertEquals(75, c.minutesLeft); assertEquals("1h 15m left", c.meta); assertEquals("p1", c.artUrl)
    }

    @Test fun episodeCardIdentifiesSeasonAndEpisode() {
        val c = RecentlyWatched.shelf(listOf(ep(4, 5))).single()
        assertEquals("Show", c.title); assertEquals("S2 E4", c.code); assertEquals("Ep 4", c.episodeTitle)
        assertEquals(0.5f, c.progress!!, 0.0001f); assertEquals("S2 E4 · 20 min left · Ep 4", c.meta)
    }

    @Test fun completedShowsWatchedWithNoResumeBar() {
        val f = RecentlyWatched.shelf(listOf(film("1", 5, pos = 5_900_000, done = true))).single()
        assertTrue(f.watched); assertNull(f.progress); assertNull(f.minutesLeft); assertEquals("Watched", f.meta)
        val e = RecentlyWatched.shelf(listOf(ep(3, 5, done = true))).single()
        assertEquals("S2 E3 · Watched · Ep 3", e.meta); assertNull(e.progress)
    }

    @Test fun unknownDurationHasNoProgressOrFakeTimeLeft() {
        val c = RecentlyWatched.shelf(listOf(film("1", 5, dur = 0))).single()
        assertNull(c.progress); assertNull(c.minutesLeft); assertEquals("", c.meta)
    }

    @Test fun neverPastTheEndAndNeverZeroMinutesLeft() {
        val c = RecentlyWatched.shelf(listOf(film("1", 5, pos = 6_500_000))).single()
        assertEquals(1f, c.progress!!, 0.0001f)
        assertEquals(1, RecentlyWatched.shelf(listOf(film("2", 5, pos = 5_990_000))).single().minutesLeft)
    }

    @Test fun distinctEpisodesOfOneSeriesAreSeparateCards() {
        val s = RecentlyWatched.shelf(listOf(ep(1, 10), ep(2, 20), ep(1, 5, season = 1)))
        assertEquals(listOf(2 to 2, 2 to 1, 1 to 1), s.map { it.entry.season!! to it.entry.episode!! })
    }

    @Test fun sameEpisodeAppearsOnce() {
        assertEquals(1, RecentlyWatched.shelf(listOf(ep(1, 10), ep(1, 20))).size)
    }

    @Test fun emptyHistoryGivesEmptyShelf() {
        assertTrue(RecentlyWatched.shelf(emptyList()).isEmpty())
    }

    @Test fun blankTitleIsDeferredNotInventedOrDeleted() {
        val bare = PlayedEntry(Kind.SHOW, seriesTmdbId = "100", season = 1, episode = 2, playedAt = 9, positionMs = 100_000, durationMs = 200_000)
        assertTrue(RecentlyWatched.shelf(listOf(bare)).isEmpty())
        // Once something known names it, it appears, with that title and picture.
        val known = listOf(KnownTitle(Kind.SHOW, "Named", tmdbId = "100", backdrop = "b.jpg"))
        val c = RecentlyWatched.shelf(listOf(bare), known).single()
        assertEquals("Named", c.title); assertEquals("b.jpg", c.artUrl); assertNull(c.episodeTitle); assertEquals("S1 E2", c.code)
    }

    @Test fun enrichmentMatchesByIdNeverByName() {
        val bare = PlayedEntry(Kind.MOVIE, imdbId = "tt1", playedAt = 1)
        assertTrue(RecentlyWatched.shelf(listOf(bare), listOf(KnownTitle(Kind.MOVIE, "Other", imdbId = "tt2"))).isEmpty())
        assertEquals("Hit", RecentlyWatched.shelf(listOf(bare), listOf(KnownTitle(Kind.SHOW, "Wrong kind", imdbId = "tt1"), KnownTitle(Kind.MOVIE, "Hit", imdbId = "tt1"))).single().title)
    }

    @Test fun enrichmentSuppliesTheSeriesIdThatMakesAnImdbOnlyEpisodeOpenable() {
        val streamed = PlayedEntry(Kind.SHOW, seriesImdbId = "tt9", season = 1, episode = 1, playedAt = 3, positionMs = 90_000, durationMs = 200_000)
        assertNull(RecentlyWatched.target(streamed))
        val c = RecentlyWatched.shelf(listOf(streamed), listOf(KnownTitle(Kind.SHOW, "Lib", imdbId = "tt9", tmdbId = "77", jellyfinId = "jf"))).single()
        assertEquals(RecentTarget.Episode(c.entry, 1, 1), RecentlyWatched.target(c.entry)); assertEquals("77", c.entry.seriesTmdbId)
    }

    @Test fun enrichmentKeepsWhatTheRecordKnowsAndPlaybackFacts() {
        val e = ep(1, 7, title = "Mine").copy(poster = "mine.jpg")
        val out = RecentlyWatched.enrich(e, listOf(KnownTitle(Kind.SHOW, "Theirs", tmdbId = "100", poster = "theirs.jpg")))
        assertEquals("Mine", out.title); assertEquals("mine.jpg", out.poster); assertEquals(7L, out.playedAt); assertEquals(e.positionMs, out.positionMs)
    }

    @Test fun navigationTargetsKeepTheRecordedEpisode() {
        val f = film("1", 1)
        assertEquals(RecentTarget.Film(f), RecentlyWatched.target(f))
        val t = RecentlyWatched.target(ep(9, 1, season = 5)) as RecentTarget.Episode
        assertEquals(5 to 9, t.season to t.episode)
        // A library episode with only its series' library id opens that series.
        val lib = PlayedEntry(Kind.SHOW, jellyfinId = "e1", seriesJellyfinId = "s1", title = "S", season = 1, episode = 3)
        val l = RecentlyWatched.target(lib) as RecentTarget.Episode
        assertEquals(1 to 3, l.season to l.episode)
        assertNull(RecentlyWatched.target(PlayedEntry(Kind.SHOW, seriesTmdbId = "1", title = "x")))
    }

    @Test fun libraryAndStreamedCopiesOfOneEpisodeAreOneCard() {
        val streamed = ep(2, 10).copy(seriesImdbId = "tt5")
        val library = PlayedEntry(Kind.SHOW, jellyfinId = "e", seriesJellyfinId = "s", seriesImdbId = "tt5", title = "Show", season = 2, episode = 2, playedAt = 20, positionMs = 1, durationMs = 2)
        assertEquals(1, RecentlyWatched.shelf(PlayHistory.record(listOf(streamed), library)).size)
    }

    @Test fun shelfIsCapped() {
        assertEquals(RecentlyWatched.LIMIT, RecentlyWatched.shelf((1..50).map { film("$it", it.toLong()) }).size)
    }

    @Test fun focusFollowsTheSameCardWhenTheShelfReorders() {
        val a = film("a", 1); val b = film("b", 2); val c = film("c", 3)
        assertEquals(0, RecentlyWatched.focusAfter(listOf(a, b, c), 1, listOf(b, c, a)))
        assertEquals(2, RecentlyWatched.focusAfter(listOf(a, b, c), 0, listOf(b, c, a)))
    }

    @Test fun focusFallsToNearestSurvivor() {
        val a = film("a", 1); val b = film("b", 2); val c = film("c", 3)
        assertEquals(1, RecentlyWatched.focusAfter(listOf(a, b, c), 1, listOf(a, c)))   // c took b's place
        assertEquals(0, RecentlyWatched.focusAfter(listOf(a, b, c), 2, listOf(b, a)))   // c gone: b, the one before it
        assertEquals(-1, RecentlyWatched.focusAfter(listOf(a), 0, emptyList()))
        assertEquals(-1, RecentlyWatched.focusAfter(listOf(a), 5, listOf(a)))
    }

    @Test fun storedHistoryDrivesTheShelfAndSurvivesRebuilds() {
        val p = MemoryPlatform()
        PlayHistoryStore(p).apply { record("s", film("1", 100)); record("s", ep(1, 200)); record("s", film("1", 300)) }
        val first = RecentlyWatched.shelf(PlayHistoryStore(p).list("s"))
        assertEquals(listOf("Film 1", "Show"), first.map { it.title })
        assertTrue(RecentlyWatched.shelf(PlayHistoryStore(p).list("other")).isEmpty())
        assertEquals(first, RecentlyWatched.shelf(PlayHistoryStore(p).list("s")))
    }
}

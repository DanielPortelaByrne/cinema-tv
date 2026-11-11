package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.platform.MemoryPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayHistoryTest {
    private val scope = FavouritesStore.scope("http://192.168.1.20:8096", "u1")
    private val film = PlayedEntry(Kind.MOVIE, tmdbId = "603", imdbId = "tt0133093", title = "The Matrix", poster = "p.jpg")
    private fun episode(n: Int, series: String = "tt0903747") =
        PlayedEntry(Kind.SHOW, seriesImdbId = series, title = "Show", season = 1, episode = n, episodeTitle = "Ep $n")
    private fun session(e: PlayedEntry = film) = PlaySession(HistoryTarget(scope, e))

    @Test fun underThirtySecondsMakesNoEntry() {
        val s = session(); s.setPlaying(true, 0, 1000)
        assertNull(s.checkpoint(29_999, 31_000, 29_999, 7_200_000, true, false))
    }

    @Test fun thirtySecondsMakesAnEntry() {
        val s = session(); s.setPlaying(true, 0, 1000)
        val e = s.checkpoint(30_000, 31_000, 30_500, 7_200_000, true, false)
        assertNotNull(e); assertEquals(30_500, e!!.positionMs); assertEquals(7_200_000, e.durationMs); assertFalse(e.completed)
    }

    @Test fun genuineEndOfShortContentMakesAnEntry() {
        val s = session(); s.setPlaying(true, 0, 1000); s.setPlaying(false, 12_000, 13_000)
        val e = s.checkpoint(12_000, 13_000, 12_000, 12_000, true, ended = true)
        assertNotNull(e); assertTrue(e!!.completed)
    }

    @Test fun bufferingSeekingAndFailureAddNothing() {
        val s = session()
        // Never playing (buffering, seeking, a failed start): the clock does not run, and untrusted positions are ignored.
        assertNull(s.checkpoint(60_000, 61_000, 5_000, 100_000, false, false))
        assertNull(s.checkpoint(90_000, 91_000, 5_000, 100_000, true, false))
        s.setPlaying(true, 100_000, 101_000); s.setPlaying(false, 110_000, 111_000)   // 10 s, then buffering
        assertNull(s.checkpoint(200_000, 201_000, 50_000, 100_000, true, false))
        s.setPlaying(true, 210_000, 211_000)
        assertNull(s.checkpoint(235_000, 236_000, 80_000, 100_000, false, false))   // 25 s but mid-seek
        assertEquals(10_000L + 25_000L, 35_000L)
    }

    @Test fun pauseResumeAndRepeatedCallbacksDoNotDoubleCount() {
        val s = session()
        s.setPlaying(true, 0, 0); s.setPlaying(true, 5_000, 5_000); s.setPlaying(true, 9_000, 9_000)
        s.setPlaying(false, 10_000, 10_000); s.setPlaying(false, 20_000, 20_000)   // paused for a long time
        s.setPlaying(true, 50_000, 50_000); s.setPlaying(false, 60_000, 60_000)
        assertEquals(20_000L, s.totalPlayedMs)
        assertNull(s.checkpoint(70_000, 70_000, 20_000, 100_000, true, false))
        s.setPlaying(true, 70_000, 70_000)
        assertNotNull(s.checkpoint(80_000, 80_000, 30_000, 100_000, true, false))
    }

    @Test fun repeatedCheckpointsWithNothingNewWriteOnce() {
        val s = session(); s.setPlaying(true, 0, 0); s.setPlaying(false, 31_000, 31_000)
        assertNotNull(s.checkpoint(31_000, 31_000, 31_000, 100_000, true, false))
        assertNull(s.checkpoint(32_000, 32_000, 31_000, 100_000, true, false))
        assertNull(s.checkpoint(40_000, 40_000, 31_000, 100_000, true, false))
    }

    @Test fun seekingBackwardsStoresTheLatestPosition() {
        val s = session(); s.setPlaying(true, 0, 0)
        val ahead = s.checkpoint(40_000, 40_000, 3_000_000, 7_200_000, true, false)!!
        val back = s.checkpoint(50_000, 50_000, 600_000, 7_200_000, true, false)!!
        val list = PlayHistory.record(PlayHistory.record(emptyList(), ahead), back)
        assertEquals(1, list.size); assertEquals(600_000, list[0].positionMs)
    }

    @Test fun unknownLengthNeverImpliesCompletion() {
        val s = session(); s.setPlaying(true, 0, 0)
        assertFalse(s.checkpoint(40_000, 40_000, 5_000_000, 0, true, false)!!.completed)
    }

    @Test fun completionUsesEpisode90AndFilm95() {
        assertTrue(PlayCompletion.reached(true, 90, 100, false)); assertFalse(PlayCompletion.reached(true, 89, 100, false))
        assertFalse(PlayCompletion.reached(false, 94, 100, false)); assertTrue(PlayCompletion.reached(false, 95, 100, false))
        assertTrue(PlayCompletion.reached(false, 10, 0, true))
    }

    @Test fun completedStaysInOnceReachedEvenAfterSeekingBack() {
        val s = session(episode(1)); s.setPlaying(true, 0, 0)
        assertTrue(s.checkpoint(40_000, 40_000, 95_000, 100_000, true, false)!!.completed)
        assertTrue(s.checkpoint(50_000, 50_000, 10_000, 100_000, true, false)!!.completed)
        val list = PlayHistory.record(PlayHistory.record(emptyList(), s.checkpoint(60_000, 60_000, 11_000, 100_000, true, false)!!), episode(2).copy(playedAt = 99))
        assertTrue(list.first { it.episode == 1 }.completed)
    }

    @Test fun rewatchUpdatesAndReordersWithoutDuplicating() {
        val other = PlayedEntry(Kind.MOVIE, tmdbId = "1", title = "Other", playedAt = 200)
        var list = PlayHistory.record(emptyList(), film.copy(playedAt = 100, positionMs = 50_000))
        list = PlayHistory.record(list, other)
        assertEquals(listOf("Other", "The Matrix"), list.map { it.title })
        list = PlayHistory.record(list, film.copy(playedAt = 300, positionMs = 90_000, poster = null))
        assertEquals(listOf("The Matrix", "Other"), list.map { it.title })
        assertEquals(90_000, list[0].positionMs); assertEquals("p.jpg", list[0].poster)
    }

    @Test fun episodesOfOneSeriesStayDistinctAndNamesNeverMatch() {
        val list = listOf(1, 2).fold(emptyList<PlayedEntry>()) { acc, n -> PlayHistory.record(acc, episode(n).copy(playedAt = n.toLong())) }
        assertEquals(listOf(2, 1), list.map { it.episode })
        // Same name and numbers but another series is another record.
        assertEquals(3, PlayHistory.record(list, episode(1, "tt9999999").copy(playedAt = 5)).size)
        // Same title, no shared id: a different film.
        assertEquals(2, PlayHistory.record(listOf(film), film.copy(tmdbId = "999", imdbId = "tt999")).size)
    }

    @Test fun streamAndLibraryCopiesReconcile() {
        val streamed = episode(3).copy(playedAt = 10, positionMs = 100, still = "s.jpg")
        val library = PlayedEntry(Kind.SHOW, jellyfinId = "ep-9", seriesJellyfinId = "ser-1", seriesImdbId = "tt0903747", season = 1, episode = 3,
            title = "Show", playedAt = 20, positionMs = 500, source = PlayedEntry.SOURCE_JELLYFIN)
        val list = PlayHistory.record(listOf(streamed), library)
        assertEquals(1, list.size)
        assertEquals("ep-9", list[0].jellyfinId); assertEquals("s.jpg", list[0].still); assertEquals(500, list[0].positionMs)
        // Later, the stream copy (series IMDb only) still finds the merged record.
        assertEquals(1, PlayHistory.record(list, streamed.copy(playedAt = 30)).size)
        val f = PlayHistory.record(listOf(film.copy(jellyfinId = "j1")), PlayedEntry(Kind.MOVIE, jellyfinId = "j1", title = "The Matrix", playedAt = 5))
        assertEquals(1, f.size); assertEquals("tt0133093", f[0].imdbId)
    }

    @Test fun capsAtTwoHundredKeepingTheNewest() {
        val list = (1..250).fold(emptyList<PlayedEntry>()) { acc, n -> PlayHistory.record(acc, PlayedEntry(Kind.MOVIE, tmdbId = "$n", title = "T$n", playedAt = n.toLong())) }
        assertEquals(200, list.size); assertEquals("T250", list.first().title); assertEquals("T51", list.last().title)
    }

    @Test fun completedEntriesPersistAcrossReload() {
        val p = MemoryPlatform()
        PlayHistoryStore(p).record(scope, film.copy(playedAt = 5, positionMs = 99, durationMs = 100, completed = true))
        PlayHistoryStore(p).record(scope, episode(1).copy(playedAt = 6))
        val back = PlayHistoryStore(p).list(scope)
        assertEquals(2, back.size); assertTrue(back.first { it.kind == Kind.MOVIE }.completed)
    }

    @Test fun oldAndPartialStoredDataStaysReadable() {
        val p = MemoryPlatform()
        val old = """[{"kind":"MOVIE","tmdb":"603","title":"The Matrix","at":5},{"kind":"SHOW","sImdb":"tt1","season":2,"episode":4,"future":"x"},{"kind":"BOGUS","tmdb":"1"},{"kind":"MOVIE"},"junk"]"""
        p.preferences("play_history").edit().putString("hist_" + scope, old).apply()
        val list = PlayHistoryStore(p).list(scope)
        assertEquals(2, list.size)
        assertEquals("", list[1].title); assertNull(list[1].poster); assertEquals(2, list[1].season)
        assertEquals(emptyList<PlayedEntry>(), PlayHistory.parse("not json"))
        assertEquals(emptyList<PlayedEntry>(), PlayHistory.parse(null))
    }

    @Test fun serverAndUserScopesAreIsolated() {
        val p = MemoryPlatform(); val s = PlayHistoryStore(p)
        val other = FavouritesStore.scope("http://192.168.1.20:8096", "u2")
        val elsewhere = FavouritesStore.scope("http://192.168.1.21:8096", "u1")
        s.record(scope, film.copy(playedAt = 1))
        assertTrue(s.list(other).isEmpty()); assertTrue(s.list(elsewhere).isEmpty()); assertTrue(s.list("").isEmpty())
        s.record(other, film.copy(playedAt = 2))
        assertEquals(1, s.list(scope).single().playedAt)
    }

    @Test fun historyFromBeforeJellyfinCarriesOverWithoutJellyfinIds() {
        val p = MemoryPlatform(); val s = PlayHistoryStore(p)
        s.record("", episode(4).copy(playedAt = 10, seriesJellyfinId = "stray", jellyfinId = "e-stray"))
        val carried = s.list(scope).single()
        assertNull(carried.seriesJellyfinId); assertNull(carried.jellyfinId)
        // The first viewing under the server keeps what came before, then the scope has its own list.
        s.record(scope, film.copy(playedAt = 20))
        assertEquals(listOf(20L, 10L), s.list(scope).map { it.playedAt })
        s.record("", episode(5).copy(playedAt = 30))
        assertEquals(2, s.list(scope).size)
    }

    @Test fun backfillsOnlyWhatStreamProgressKnowsAndOnlyOnce() {
        val progress = StreamProgress.record(null, "tt0903747", 1, 2, 3_000_000, 3_300_000, 777)
            .let { StreamProgress.record(it, "tt0903747", 1, 3, 10_000, 3_300_000, 888) }   // only 10 s: not backfilled
        val p = MemoryPlatform(); val s = PlayHistoryStore(p)
        s.record(scope, film.copy(playedAt = 1000), progress)
        s.record(scope, film.copy(playedAt = 2000), progress)
        val list = s.list(scope)
        assertEquals(2, list.size)
        val ep = list.first { it.isEpisode }
        assertEquals(777, ep.playedAt); assertTrue(ep.completed); assertEquals("", ep.title); assertEquals(2, ep.episode)
    }

    @Test fun leavingInTheCreditsCompletesButNeedsRealPlayingFirst() {
        val quick = session(episode(1)); quick.setPlaying(true, 0, 0)
        assertNull(quick.checkpoint(5_000, 5_000, 99_000, 100_000, true, false, credits = true))
        val s = session(episode(1)); s.setPlaying(true, 0, 0)
        val e = s.checkpoint(40_000, 40_000, 92_000, 100_000, true, false, credits = true)!!
        assertTrue(e.completed); assertEquals(100_000, e.positionMs)
    }

    @Test fun targetSurvivesTheIntentRoundTrip() {
        val t = HistoryTarget(scope, episode(4).copy(poster = "p", jellyfinId = "j", seriesTmdbId = "1396"))
        assertEquals(t, HistoryTarget.fromJson(t.toJson()))
        assertNull(HistoryTarget.fromJson("{}")); assertNull(HistoryTarget.fromJson(null))
    }
}

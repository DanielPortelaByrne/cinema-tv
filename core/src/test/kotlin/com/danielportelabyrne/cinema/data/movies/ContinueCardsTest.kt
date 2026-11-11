package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueCardsTest {
    private val scope = FavouritesStore.scope("http://192.168.1.20:8096", "u1")
    private val today = "2026-11-11"
    private val min = 60_000L
    private val hour = 60 * min
    private val day = 24 * hour
    private val now = 1_800_000_000_000L

    private val matrix = PlayedEntry(Kind.MOVIE, tmdbId = "603", imdbId = "tt0133093", title = "The Matrix")
    private fun film(at: Long, pos: Long) = matrix.copy(playedAt = at, positionMs = pos, durationMs = 136 * min)
    private fun ep(s: Int, n: Int, at: Long, pos: Long, done: Boolean = false, tmdb: String = "1396", imdb: String = "tt0903747", name: String = "Breaking Bad") =
        PlayedEntry(Kind.SHOW, seriesTmdbId = tmdb, seriesImdbId = imdb, title = name, season = s, episode = n, episodeTitle = "Ep $s.$n",
            playedAt = at, positionMs = pos, durationMs = 47 * min, completed = done)
    private fun inputs(history: List<PlayedEntry> = emptyList(), saved: List<SavedStream> = emptyList(), server: List<JellyfinItem> = emptyList(),
                       known: List<KnownTitle> = emptyList()) = ContinueInputs(scope, today, history, null, saved, server, known)
    private fun cat(vararg eps: Pair<Int, Int>, complete: Boolean = true) =
        EpisodeCatalogue(eps.map { CatalogueEpisode(it.first, it.second, "Episode ${it.second}", "2020-01-01") }, complete)
    private fun seasons(vararg counts: Int) = counts.flatMapIndexed { i, c -> (1..c).map { (i + 1) to it } }.toTypedArray()
    private fun select(i: ContinueInputs, c: EpisodeCatalogue? = null) = ContinueEngine.select(i, { c })
    private fun payload(imdb: String, title: String) = JSONObject().put("items", "[]").put("imdb", imdb).put("title", title).put("jf", "")
        .put("episode", "").put("history", "").toString()
    private fun jfFilm(id: String, resume: Long = 0, at: Long? = null) = JellyfinItem(id, "The Matrix", 1999, "Movie", null,
        resumeMs = resume, imdbId = "tt0133093", tmdbId = "603", runtimeMin = 136, playedMs = at)
    private fun jfEp(id: String, s: Int, n: Int, resume: Long = 0, at: Long? = null, series: String = "abc-123") =
        JellyfinItem(id, "Ep $s.$n", null, "Episode", null, resumeMs = resume, runtimeMin = 47, season = s, episode = n, seriesName = "Breaking Bad", seriesId = series, playedMs = at)
    private val bb = listOf(KnownTitle(Kind.SHOW, "Breaking Bad", tmdbId = "1396", imdbId = "tt0903747", jellyfinId = "abc-123"))

    // 1
    @Test fun a_streamed_film_reopens_its_stream_with_resume_progress_and_time_left() {
        val p = payload("tt0133093", "The Matrix")
        val e = select(inputs(saved = listOf(SavedStream(p, 40 * min, 136 * min, now)))).single()
        val m = ContinueCards.model(e)
        assertEquals(ContinueOpen.PLAY_STREAM, m.open); assertEquals("Resume", m.label); assertEquals("The Matrix", m.title)
        assertNull(m.detail); assertEquals("1h 36m left", m.remaining); assertEquals(40f / 136f, m.progress!!, 0.001f); assertFalse(m.fromStart)
        assertEquals(40 * min, m.resumeMs); assertEquals("Resume", m.featuredKicker)
    }

    // 2
    @Test fun a_library_film_plays_the_exact_item() {
        val e = select(inputs(server = listOf(jfFilm("f1", 50 * min, now)))).single()
        val m = ContinueCards.model(e)
        assertEquals(ContinueOpen.PLAY_LIBRARY, m.open); assertEquals(50 * min, m.resumeMs)
        assertEquals("f1", ContinueCards.libraryItem(e, listOf(jfFilm("f0"), jfFilm("f1", 50 * min)))!!.id)
    }

    // 3
    @Test fun an_exact_episode_resumes_and_its_series_id_is_never_played() {
        val e = select(inputs(server = listOf(jfEp("e24", 2, 4, 20 * min, now)), known = bb)).single()
        val m = ContinueCards.model(e)
        assertEquals(ContinueOpen.PLAY_LIBRARY, m.open); assertEquals("S2 E4 · Ep 2.4", m.detail); assertEquals("Breaking Bad", m.title)
        assertEquals("27 min left", m.remaining); assertEquals("Resume · S2 E4 · Ep 2.4", m.featuredKicker); assertEquals("S2 E4 · Ep 2.4 · 27 min left", m.smallMeta)
        val series = JellyfinItem("abc-123", "Breaking Bad", 2008, "Series", null)
        assertEquals("e24", ContinueCards.libraryItem(e, listOf(series, jfEp("e24", 2, 4)))!!.id)
        assertNull(ContinueCards.libraryItem(e, listOf(series)))               // the series is not the episode
        assertNull(ContinueCards.libraryItem(e, listOf(jfEp("e24", 2, 4).copy(missing = true))))
        assertEquals("E24", ContinueCards.libraryItem(e, listOf(jfEp("E24", 2, 4)))!!.id)     // an id written another way
    }

    // 4
    @Test fun a_finished_episode_offers_the_next_with_no_progress_and_never_resumes() {
        val e = select(inputs(history = listOf(ep(1, 3, now, 47 * min, done = true))), cat(*seasons(7))).single()
        val m = ContinueCards.model(e)
        assertEquals("Next episode", m.label); assertEquals("S1 E4 · Episode 4", m.detail); assertNull(m.progress); assertNull(m.remaining); assertNull(m.resumeMs)
        assertTrue(m.fromStart); assertEquals("Next episode · S1 E4 · Episode 4", m.smallMeta); assertEquals("", m.featuredMeta)
        assertEquals(ContinueOpen.EPISODE_PAGE, m.open)   // no source known yet: a page, never a promise to play
    }

    // 5
    @Test fun a_season_finale_moves_to_the_next_season() {
        val m = ContinueCards.model(select(inputs(history = listOf(ep(1, 7, now, 47 * min, done = true))), cat(*seasons(7, 13))).single())
        assertEquals("S2 E1 · Episode 1", m.detail)
    }

    // 6
    @Test fun a_finished_series_is_omitted_and_never_wraps() {
        assertTrue(select(inputs(history = listOf(ep(2, 13, now, 47 * min, done = true))), cat(*seasons(7, 13))).isEmpty())
    }

    // 7
    @Test fun a_missing_source_opens_the_page_with_the_episode_picked() {
        val m = ContinueCards.model(select(inputs(history = listOf(ep(2, 4, now, 20 * min)))).single())
        assertEquals(ContinueOpen.EPISODE_PAGE, m.open); assertEquals("S2 E4 · Ep 2.4", m.detail); assertEquals("Resume", m.label)
        val f = ContinueCards.model(select(inputs(history = listOf(film(now, 40 * min)))).single())
        assertEquals(ContinueOpen.PAGE, f.open)
    }

    // 8
    @Test fun an_unknown_next_episode_opens_the_series_page_and_names_no_episode() {
        val e = select(inputs(history = listOf(ep(1, 3, now, 47 * min, done = true)))).single()
        val m = ContinueCards.model(e)
        assertEquals(ContinueOpen.PAGE, m.open); assertEquals("Next episode", m.label); assertEquals("After S1 E3", m.detail); assertNull(e.episode)
        assertEquals("Breaking Bad", m.title)
    }

    // 9
    @Test fun seeking_back_shows_the_latest_position() {
        val e = select(inputs(history = listOf(ep(1, 3, now, 11 * min)))).single()
        assertEquals(11f / 47f, ContinueCards.model(e).progress!!, 0.001f)
    }

    // 10
    @Test fun after_playback_a_resume_becomes_the_next_episode_under_the_same_key() {
        val before = select(inputs(history = listOf(ep(1, 3, now - hour, 20 * min))), cat(*seasons(7))).single()
        val after = select(inputs(history = listOf(ep(1, 3, now, 46 * min, done = true))), cat(*seasons(7))).single()
        assertEquals(before.key, after.key); assertEquals(ContinueAction.RESUME, before.action); assertEquals(ContinueAction.NEXT_EPISODE, after.action)
        assertEquals(listOf(0), listOf(ContinueCards.focusAfter(listOf(before.key), 0, listOf(after.key))))
    }

    // 11
    @Test fun focus_follows_the_same_title_else_the_nearest_survivor() {
        val old = listOf("a", "b", "c", "d")
        assertEquals(0, ContinueCards.focusAfter(old, 2, listOf("c", "a", "b")))     // moved to the front
        assertEquals(1, ContinueCards.focusAfter(old, 1, listOf("a", "c", "d")))     // b gone: the one that followed
        assertEquals(1, ContinueCards.focusAfter(old, 3, listOf("x", "c")))         // d gone, nothing after: the one before
        assertEquals(0, ContinueCards.focusAfter(old, 0, listOf("b")))
        assertEquals(-1, ContinueCards.focusAfter(old, 1, listOf("x", "y")))       // nothing survived: a neighbouring shelf
        assertEquals(-1, ContinueCards.focusAfter(old, -1, old)); assertEquals(-1, ContinueCards.focusAfter(old, 9, old))
    }

    // 12
    @Test fun nothing_played_gives_no_cards() {
        assertTrue(select(inputs()).isEmpty())
    }

    // 13
    @Test fun two_different_series_with_one_name_are_two_cards() {
        val a = ep(1, 2, now, 10 * min)
        val b = ep(3, 1, now - hour, 10 * min, tmdb = "9999", imdb = "tt9999999")
        val cards = select(inputs(history = listOf(a, b)))
        assertEquals(2, cards.size); assertNotEquals(cards[0].key, cards[1].key); assertEquals(listOf("Breaking Bad", "Breaking Bad"), cards.map { it.title })
    }

    // 15
    @Test fun the_catalogue_joins_seerrs_listing_with_the_librarys_own_episodes_of_that_series_only() {
        val ids = TitleRef("1396", "tt0903747", "abc-123")
        val tmdb = EpisodeCatalogue.fromTmdb(listOf(SeasonInfo(1, "Season 1", 2, com.danielportelabyrne.cinema.data.movies.Availability.NONE)),
            listOf(EpisodeInfo(1, 1, "Pilot", null, "2008-01-20"), EpisodeInfo(1, 2, "Cat", null, "2008-01-27")))
        val library = listOf(jfEp("e1", 1, 1), jfEp("e2", 1, 2), jfEp("x1", 1, 1, series = "other"))
        val merged = ContinueCards.catalogue(ids, tmdb, library)!!
        assertEquals(listOf("e1", "e2"), merged.episodes.map { it.jellyfinId }); assertTrue(merged.complete)
        assertNull(ContinueCards.catalogue(TitleRef("1", null, null), null, library))      // nothing cached: the engine falls back safely
        assertEquals(2, ContinueCards.catalogue(ids, null, library)!!.episodes.size)
        assertFalse(ContinueCards.catalogue(ids, null, library)!!.complete)
    }

    @Test fun a_start_over_from_a_saved_stream_opens_a_page_never_a_resume() {
        val e = ContinueEntry("k", Kind.SHOW, ContinueAction.START_OVER, "Breaking Bad", TitleRef("1396", null, null), ContinueEpisode(1, 3, "Ep"), null, now, null, null,
            ContinueSource.Stream("{}"))
        val m = ContinueCards.model(e)
        assertEquals("Start over", m.label); assertEquals(ContinueOpen.EPISODE_PAGE, m.open); assertTrue(m.fromStart); assertNull(m.progress)
        assertEquals(ContinueOpen.PLAY_LIBRARY, ContinueCards.route(e.copy(source = ContinueSource.Library("e3"))))
    }

    @Test fun a_card_with_no_name_is_never_blank() {
        val e = ContinueEntry("k", Kind.SHOW, ContinueAction.NEXT_EPISODE, "", TitleRef(), null, null, now, null, null, ContinueSource.Unknown)
        assertEquals("Series", ContinueCards.model(e).title); assertNull(ContinueCards.model(e).detail)
        assertEquals("Film", ContinueCards.model(e.copy(kind = Kind.MOVIE)).title)
    }
}

class ContinueMenuTest {
    private fun cat(complete: Boolean, vararg eps: Pair<Int, Int>) = EpisodeCatalogue(eps.map { CatalogueEpisode(it.first, it.second) }, complete)

    @org.junit.Test fun nextEpisodeIsTheNextNumberOrTheNextSeasonsFirst() {
        val c = cat(false, 1 to 1, 1 to 2, 1 to 3, 2 to 1)
        org.junit.Assert.assertEquals(1 to 3, ContinueCards.nextEpisode(c, 1, 2)?.let { it.season to it.episode })
        org.junit.Assert.assertEquals(2 to 1, ContinueCards.nextEpisode(c, 1, 3)?.let { it.season to it.episode })
        org.junit.Assert.assertNull(ContinueCards.nextEpisode(c, 2, 1))
        org.junit.Assert.assertNull(ContinueCards.nextEpisode(null, 1, 1))
    }

    @org.junit.Test fun aGapIsOnlyCrossedInACompleteListing() {
        org.junit.Assert.assertNull(ContinueCards.nextEpisode(cat(false, 1 to 1, 1 to 3), 1, 1))
        org.junit.Assert.assertEquals(1 to 3, ContinueCards.nextEpisode(cat(true, 1 to 1, 1 to 3), 1, 1)?.let { it.season to it.episode })
    }

    @org.junit.Test fun aFileCoveringTwoEpisodesSkipsBoth() {
        val c = EpisodeCatalogue(listOf(CatalogueEpisode(1, 1, lastEpisode = 2), CatalogueEpisode(1, 3)), false)
        org.junit.Assert.assertEquals(3, ContinueCards.nextEpisode(c, 1, 1)?.episode)
    }

    @org.junit.Test fun aRemovedCardStaysAwayUntilItIsPlayedAgain() {
        val p = com.danielportelabyrne.cinema.platform.MemoryPlatform()
        val d = ContinueDismissals(p)
        val e = ContinueEntry("show:tmdb=5|imdb=|jf=", Kind.SHOW, ContinueAction.RESUME, "Show", TitleRef("5"), null, null, 100, 1, 2, ContinueSource.PickSource)
        d.dismiss("s", e.key, 100)
        val gone = ContinueDismissals(p).all("s")
        org.junit.Assert.assertTrue(ContinueDismissals.hidden(gone, e))
        org.junit.Assert.assertFalse(ContinueDismissals.hidden(gone, e.copy(lastPlayedAt = 101)))
        org.junit.Assert.assertTrue(ContinueDismissals(p).all("other").isEmpty())
    }
}

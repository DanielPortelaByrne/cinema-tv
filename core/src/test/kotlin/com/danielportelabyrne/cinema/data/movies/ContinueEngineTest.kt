package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueEngineTest {
    private val scope = FavouritesStore.scope("http://192.168.1.20:8096", "u1")
    private val today = "2026-11-11"
    private val min = 60_000L
    private val hour = 60 * min
    private val day = 24 * hour
    private val now = 1_800_000_000_000L

    private val matrix = PlayedEntry(Kind.MOVIE, tmdbId = "603", imdbId = "tt0133093", title = "The Matrix", poster = "m.jpg")
    private fun film(at: Long, pos: Long, dur: Long = 136 * min, done: Boolean = false, e: PlayedEntry = matrix) =
        e.copy(playedAt = at, positionMs = pos, durationMs = dur, completed = done)
    private fun ep(s: Int, n: Int, at: Long, pos: Long, dur: Long = 47 * min, done: Boolean = false) =
        PlayedEntry(Kind.SHOW, seriesTmdbId = "1396", seriesImdbId = "tt0903747", title = "Breaking Bad", season = s, episode = n,
            episodeTitle = "Ep $s.$n", playedAt = at, positionMs = pos, durationMs = dur, completed = done)

    private fun inputs(history: List<PlayedEntry> = emptyList(), progress: String? = null, saved: List<SavedStream> = emptyList(),
                       server: List<JellyfinItem> = emptyList(), known: List<KnownTitle> = emptyList()) =
        ContinueInputs(scope, today, history, progress, saved, server, known)

    private fun cat(vararg eps: Pair<Int, Int>, complete: Boolean = true, air: String? = "2020-01-01") =
        EpisodeCatalogue(eps.map { CatalogueEpisode(it.first, it.second, "Episode ${it.second}", air) }, complete)
    private fun seasons(vararg counts: Int) = counts.flatMapIndexed { i, c -> (1..c).map { (i + 1) to it } }.toTypedArray()

    private fun select(i: ContinueInputs, catalogue: EpisodeCatalogue? = null) = ContinueEngine.select(i, { catalogue })

    private fun jfFilm(id: String, resume: Long = 0, played: Boolean = false, at: Long? = null) = JellyfinItem(id, "The Matrix", 1999, "Movie", null,
        resumeMs = resume, played = played, imdbId = "tt0133093", tmdbId = "603", runtimeMin = 136, playedMs = at)
    private fun jfEp(id: String, s: Int, n: Int, resume: Long = 0, played: Boolean = false, at: Long? = null, series: String = "abc-123") =
        JellyfinItem(id, "Ep $s.$n", null, "Episode", null, resumeMs = resume, played = played, runtimeMin = 47, season = s, episode = n,
            seriesName = "Breaking Bad", seriesId = series, playedMs = at)

    private fun payload(imdb: String, title: String, episode: Pair<Int, Int>? = null, history: HistoryTarget? = null) =
        JSONObject().put("items", "[]").put("imdb", imdb).put("title", title).put("jf", "")
            .put("episode", episode?.let { "${it.first}:${it.second}" } ?: "").put("history", history?.toJson() ?: "").toString()

    // 1
    @Test fun a_part_watched_film_resumes() {
        val c = select(inputs(history = listOf(film(now, 40 * min)))).single()
        assertEquals(ContinueAction.RESUME, c.action); assertEquals(Kind.MOVIE, c.kind); assertEquals("The Matrix", c.title)
        assertEquals(40 * min, c.resumeMs); assertEquals(136 * min, c.durationMs); assertEquals(96, c.minutesLeft)
        assertEquals(ContinueSource.PickSource, c.source); assertFalse(c.source.playable); assertEquals(now, c.lastPlayedAt)
    }

    // 2
    @Test fun a_finished_film_is_left_out() {
        assertTrue(select(inputs(history = listOf(film(now, 131 * min, done = true)))).isEmpty())
    }

    // 3
    @Test fun a_part_watched_episode_resumes_that_episode() {
        var asked = 0
        val i = inputs(history = listOf(ep(1, 2, now - day, 47 * min, done = true), ep(1, 3, now, 20 * min)))
        val c = ContinueEngine.select(i, { asked++; null }).single()
        assertEquals(ContinueAction.RESUME, c.action); assertEquals(1, c.episode!!.season); assertEquals(3, c.episode!!.episode)
        assertEquals("Ep 1.3", c.episode!!.title); assertEquals(20 * min, c.resumeMs); assertEquals(0, asked)
    }

    // 4
    @Test fun a_finished_episode_offers_the_next_with_no_progress() {
        val c = select(inputs(history = listOf(ep(1, 2, now, 46 * min, done = true))), cat(*seasons(7))).single()
        assertEquals(ContinueAction.NEXT_EPISODE, c.action); assertEquals("S1 E3", c.episode!!.code); assertEquals("S1 E2", c.finished!!.code)
        assertNull(c.resumeMs); assertNull(c.durationMs); assertNull(c.progress); assertNull(c.minutesLeft)
        assertEquals(ContinueSource.PickSource, c.source)
    }

    // 5
    @Test fun a_season_finale_moves_to_the_next_season() {
        val c = select(inputs(history = listOf(ep(1, 7, now, 47 * min, done = true))), cat(*seasons(7, 13))).single()
        assertEquals("S2 E1", c.episode!!.code)
    }

    // 6
    @Test fun a_finished_series_never_wraps() {
        assertTrue(select(inputs(history = listOf(ep(2, 13, now, 47 * min, done = true))), cat(*seasons(7, 13))).isEmpty())
    }

    // 7
    @Test fun specials_are_never_offered_and_never_set_the_place() {
        val withSpecials = cat(0 to 1, 0 to 2, *seasons(2))
        // Last regular episode finished: specials are not "next".
        assertTrue(select(inputs(history = listOf(ep(1, 2, now, 47 * min, done = true))), withSpecials).isEmpty())
        // A special finished after S1E1: the series carries on from S1E1.
        val c = select(inputs(history = listOf(ep(1, 1, now - day, 47 * min, done = true), ep(0, 1, now, 47 * min, done = true))), withSpecials).single()
        assertEquals("S1 E2", c.episode!!.code); assertEquals(now, c.lastPlayedAt)
    }

    // 8
    @Test fun an_episode_airing_later_is_not_offered() {
        val future = EpisodeCatalogue(listOf(CatalogueEpisode(1, 1, airDate = "2026-10-01"), CatalogueEpisode(1, 2, airDate = "2026-12-21")), complete = true)
        assertTrue(select(inputs(history = listOf(ep(1, 1, now, 47 * min, done = true))), future).isEmpty())
        val outToday = EpisodeCatalogue(listOf(CatalogueEpisode(1, 1, airDate = "2026-10-01"), CatalogueEpisode(1, 2, airDate = today)), complete = true)
        assertEquals("S1 E2", select(inputs(history = listOf(ep(1, 1, now, 47 * min, done = true))), outToday).single().episode!!.code)
    }

    // 9
    @Test fun an_unknown_air_date_is_not_offered_unless_the_library_has_the_file() {
        val tba = EpisodeCatalogue(listOf(CatalogueEpisode(1, 1, airDate = "2026-10-01"), CatalogueEpisode(1, 2, airDate = null)), complete = true)
        assertTrue(select(inputs(history = listOf(ep(1, 1, now, 47 * min, done = true))), tba).isEmpty())
        val owned = EpisodeCatalogue.merge(tba, EpisodeCatalogue(listOf(CatalogueEpisode(1, 2, jellyfinId = "E2-ID")), complete = false))
        val c = select(inputs(history = listOf(ep(1, 1, now, 47 * min, done = true))), owned).single()
        assertEquals("S1 E2", c.episode!!.code); assertEquals(ContinueSource.Library("E2-ID"), c.source); assertTrue(c.source.playable)
    }

    // 10
    @Test fun a_series_never_played_is_left_out() {
        var asked = 0
        val library = listOf(jfEp("e1", 1, 1), jfEp("e2", 1, 2))   // in the library, no user data
        assertTrue(ContinueEngine.select(inputs(server = library), { asked++; cat(*seasons(2)) }).isEmpty())
        assertEquals(0, asked)
    }

    // 11
    @Test fun seeking_back_resumes_from_the_latest_point_not_the_furthest() {
        var json = StreamProgress.record(null, "tt0903747", 1, 3, 30 * min, 47 * min, now - hour)
        json = StreamProgress.record(json, "tt0903747", 1, 3, 11 * min, 47 * min, now)
        val c = select(inputs(progress = json)).single()
        assertEquals(ContinueAction.RESUME, c.action); assertEquals(11 * min, c.resumeMs)
        val p = ContinueEngine.playback(inputs(progress = json)).single().state
        assertEquals(30 * min, p.furthestMs); assertFalse(p.legacyResume)
        // The play history says the same: its position moves back too.
        assertEquals(11 * min, select(inputs(history = listOf(ep(1, 3, now, 11 * min)))).single().resumeMs)
    }

    // 12
    @Test fun a_real_end_finishes_it_even_with_no_length() {
        val s = PlaySession(HistoryTarget(scope, matrix)); s.setPlaying(true, 0, now - 10_000)
        val ended = s.checkpoint(10_000, now, 10_000, 0, true, ended = true)!!
        assertTrue(ended.completed)
        assertTrue(select(inputs(history = listOf(ended))).isEmpty())
        // An episode that ended moves the series on.
        val e = PlaySession(HistoryTarget(scope, ep(1, 1, 0, 0))).apply { setPlaying(true, 0, now - 10_000) }.checkpoint(10_000, now, 5_000, 0, true, ended = true)!!
        assertEquals("S1 E2", select(inputs(history = listOf(e)), cat(*seasons(3))).single().episode!!.code)
    }

    // 13
    @Test fun an_unknown_length_never_means_finished() {
        val json = StreamProgress.record(null, "tt0903747", 1, 3, 50 * min, 0, now)
        val c = select(inputs(progress = json), cat(*seasons(5))).single()
        assertEquals(ContinueAction.RESUME, c.action); assertEquals("S1 E3", c.episode!!.code); assertEquals(50 * min, c.resumeMs)
        assertNull(c.durationMs); assertNull(c.progress)
        val saved = SavedStream(payload("tt0133093", "The Matrix"), 130 * min, 0, now)
        assertEquals(ContinueAction.RESUME, select(inputs(saved = listOf(saved))).single().action)
    }

    // 14
    @Test fun the_servers_played_state_is_respected() {
        val local = film(now - hour, 70 * min, e = matrix.copy(jellyfinId = "jf1"))
        assertTrue(select(inputs(history = listOf(local), server = listOf(jfFilm("jf1", played = true, at = now)))).isEmpty())
        // Played with no date: still finished.
        assertTrue(select(inputs(history = listOf(local), server = listOf(jfFilm("jf1", played = true)))).isEmpty())
        // Played on the server alone: nothing to resume.
        assertTrue(select(inputs(server = listOf(jfFilm("jf1", played = true, at = now)))).isEmpty())
    }

    // 15
    @Test fun fresher_local_progress_beats_an_older_server_state() {
        val local = film(now, 50 * min)
        val c = select(inputs(history = listOf(local), server = listOf(jfFilm("JF-1", resume = 10 * min, at = now - day)))).single()
        assertEquals(50 * min, c.resumeMs); assertEquals(ContinueSource.Library("JF-1"), c.source)
        // A rewatch here after the server said played reopens it.
        val again = select(inputs(history = listOf(local), server = listOf(jfFilm("JF-1", played = true, at = now - day)))).single()
        assertEquals(50 * min, again.resumeMs)
    }

    // 16
    @Test fun a_fresher_server_state_beats_older_local_progress() {
        val c = select(inputs(history = listOf(film(now - day, 30 * min)), server = listOf(jfFilm("JF-1", resume = 70 * min, at = now)))).single()
        assertEquals(70 * min, c.resumeMs); assertEquals(now, c.lastPlayedAt)
        // Cleared on the server since: nothing to resume.
        assertTrue(select(inputs(history = listOf(film(now - day, 30 * min)), server = listOf(jfFilm("JF-1", resume = 0, at = now)))).isEmpty())
    }

    // 17
    @Test fun a_stream_and_its_library_copy_are_one_card() {
        // A film known by TMDB id here and by both ids in its saved stream.
        val p = payload("tt0133093", "The Matrix", history = HistoryTarget(scope, matrix))
        val films = select(inputs(history = listOf(film(now - min, 40 * min, e = PlayedEntry(Kind.MOVIE, tmdbId = "603", title = "The Matrix"))),
            saved = listOf(SavedStream(p, 41 * min, 136 * min, now))))
        assertEquals(1, films.size); assertEquals(41 * min, films[0].resumeMs); assertEquals(ContinueSource.Stream(p), films[0].source)
        assertEquals("tt0133093", films[0].ids.imdbId)
        // A series streamed by IMDb id, then carried on in the library: the library knows both, its id written another way.
        val streamed = StreamProgress.record(null, "tt0903747", 1, 2, 47 * min, 47 * min, now - day)
        val known = listOf(KnownTitle(Kind.SHOW, "Breaking Bad", tmdbId = "1396", imdbId = "tt0903747", jellyfinId = "ABC123", poster = "bb.jpg"))
        val shows = select(inputs(progress = streamed, server = listOf(jfEp("ep3", 1, 3, resume = 10 * min, at = now)), known = known))
        assertEquals(1, shows.size)
        val c = shows[0]
        assertEquals(ContinueAction.RESUME, c.action); assertEquals("S1 E3", c.episode!!.code); assertEquals(ContinueSource.Library("ep3"), c.source)
        assertEquals(TitleRef("1396", "tt0903747", "abc123"), c.ids); assertEquals("Breaking Bad", c.title); assertEquals("bb.jpg", c.poster)
    }

    // 18
    @Test fun different_titles_sharing_a_name_or_a_number_stay_apart() {
        val film1396 = PlayedEntry(Kind.MOVIE, tmdbId = "1396", title = "Breaking Bad").copy(playedAt = now, positionMs = 20 * min, durationMs = 100 * min)
        val both = select(inputs(history = listOf(film1396, ep(1, 1, now - min, 20 * min))))
        assertEquals(setOf(Kind.MOVIE, Kind.SHOW), both.map { it.kind }.toSet())
        // Same name, different films.
        val dune1 = PlayedEntry(Kind.MOVIE, tmdbId = "841", title = "Dune", playedAt = now, positionMs = 20 * min, durationMs = 137 * min)
        val dune2 = PlayedEntry(Kind.MOVIE, tmdbId = "438631", title = "Dune", playedAt = now - min, positionMs = 30 * min, durationMs = 155 * min)
        assertEquals(2, select(inputs(history = listOf(dune1, dune2))).size)
        // One id shared, another disagreeing: not merged.
        val a = PlayedEntry(Kind.MOVIE, tmdbId = "1", imdbId = "tt1", title = "A", playedAt = now, positionMs = 20 * min, durationMs = 100 * min)
        val b = PlayedEntry(Kind.MOVIE, tmdbId = "2", imdbId = "tt1", title = "B", playedAt = now - min, positionMs = 30 * min, durationMs = 100 * min)
        assertEquals(2, select(inputs(history = listOf(a, b))).size)
        // Two episodes of one series stay two records.
        assertEquals(2, ContinueEngine.playback(inputs(history = listOf(ep(1, 1, now, 20 * min), ep(1, 2, now, 20 * min)))).size)
    }

    // 19
    @Test fun without_a_known_source_the_card_asks_for_one() {
        val c = select(inputs(history = listOf(ep(1, 1, now, 47 * min, done = true))), cat(*seasons(3))).single()
        assertEquals(ContinueSource.PickSource, c.source); assertFalse(c.source.playable)
        val noListing = select(inputs(history = listOf(ep(1, 1, now, 47 * min, done = true))), null).single()
        assertEquals(ContinueSource.Unknown, noListing.source); assertNull(noListing.episode); assertEquals("S1 E1", noListing.finished!!.code)
        // A saved stream of another episode never stands in for this one.
        val other = payload("tt0903747", "Breaking Bad · S1 E2", 1 to 2)
        val r = select(inputs(history = listOf(ep(1, 3, now, 20 * min)), saved = listOf(SavedStream(other, 30 * min, 47 * min, now - day))))
        assertEquals("S1 E3", r.single().episode!!.code); assertEquals(ContinueSource.PickSource, r.single().source)
    }

    // 20
    @Test fun episode_listings_come_only_from_the_cache_once_per_series_in_need() {
        val asked = ArrayList<TitleRef>()
        val i = inputs(history = listOf(film(now, 40 * min), ep(1, 1, now - 2 * day, 47 * min, done = true), ep(1, 2, now - day, 47 * min, done = true),
            PlayedEntry(Kind.SHOW, seriesTmdbId = "60059", title = "Better Call Saul", season = 1, episode = 1, playedAt = now, positionMs = 10 * min, durationMs = 46 * min)))
        val out = ContinueEngine.select(i, { asked += it; cat(*seasons(5)) })
        assertEquals(3, out.size)
        assertEquals(listOf(TitleRef("1396", "tt0903747", null)), asked)
    }

    // 21
    @Test fun an_old_record_resumes_from_its_furthest_point_and_a_cleared_one_starts_over() {
        val legacy = """{"tt0903747|1|3":{"p":1200000,"d":2820000,"t":$now}}"""
        val c = select(inputs(progress = legacy)).single()
        assertEquals(ContinueAction.RESUME, c.action); assertEquals(20 * min, c.resumeMs)
        assertTrue(ContinueEngine.playback(inputs(progress = legacy)).single().state.legacyResume)
        val cleared = """{"tt0903747|1|3":{"p":1200000,"r":5000,"d":2820000,"t":$now}}"""
        val d = select(inputs(progress = cleared)).single()
        assertEquals(ContinueAction.START_OVER, d.action); assertEquals("S1 E3", d.episode!!.code); assertNull(d.resumeMs); assertNull(d.progress)
    }

    // 22
    @Test fun under_a_minute_here_is_no_resume_point_but_the_servers_is() {
        assertTrue(select(inputs(history = listOf(film(now, 45_000)))).isEmpty())
        assertEquals(ContinueAction.START_OVER, select(inputs(history = listOf(ep(1, 1, now, 45_000)))).single().action)
        assertEquals(45_000L, select(inputs(server = listOf(jfFilm("jf1", resume = 45_000, at = now)))).single().resumeMs)
    }

    // 23
    @Test fun a_gap_in_the_listing_is_never_jumped() {
        val gap = cat(1 to 1, 1 to 2, 1 to 3, 1 to 5, complete = false)
        val c = select(inputs(history = listOf(ep(1, 3, now, 47 * min, done = true))), gap).single()
        assertEquals(ContinueSource.Unknown, c.source); assertNull(c.episode)
        val end = select(inputs(history = listOf(ep(1, 5, now, 47 * min, done = true))), gap).single()
        assertEquals(ContinueSource.Unknown, end.source)
        assertEquals("S1 E3", select(inputs(history = listOf(ep(1, 2, now, 47 * min, done = true))), gap).single().episode!!.code)
        // A TMDB listing missing one season's episodes is not complete.
        val partial = EpisodeCatalogue.fromTmdb(listOf(SeasonInfo(1, "Season 1", 2, Availability.NONE), SeasonInfo(2, "Season 2", 2, Availability.NONE)),
            listOf(EpisodeInfo(1, 1, "a", null, "2020-01-01"), EpisodeInfo(1, 2, "b", null, "2020-01-08")))
        assertFalse(partial.complete)
        assertEquals(ContinueSource.Unknown, select(inputs(history = listOf(ep(1, 2, now, 47 * min, done = true))), partial).single().source)
    }

    // 24
    @Test fun matching_times_still_give_the_same_order() {
        val a = PlayedEntry(Kind.MOVIE, tmdbId = "11", title = "A", playedAt = now, positionMs = 20 * min, durationMs = 100 * min)
        val b = PlayedEntry(Kind.MOVIE, tmdbId = "22", title = "B", playedAt = now, positionMs = 20 * min, durationMs = 100 * min)
        val c = PlayedEntry(Kind.MOVIE, tmdbId = "33", title = "C", playedAt = now, positionMs = 20 * min, durationMs = 100 * min)
        val first = select(inputs(history = listOf(a, b, c))).map { it.key }
        assertEquals(first, select(inputs(history = listOf(c, a, b))).map { it.key })
        assertEquals(first, select(inputs(history = listOf(b, c, a))).map { it.key })
        assertEquals(first.sorted(), first)
    }

    // --- Further rules

    @Test fun an_old_saved_stream_never_beats_fresher_progress() {
        val p = payload("tt0133093", "The Matrix")
        val c = select(inputs(history = listOf(film(now, 20 * min)), saved = listOf(SavedStream(p, 90 * min, 136 * min, now - day)))).single()
        assertEquals(20 * min, c.resumeMs); assertEquals(ContinueSource.Stream(p), c.source)
        // Finished in the play history; the frame saved as the viewer left in the credits does not bring it back.
        assertTrue(select(inputs(history = listOf(film(now, 136 * min, done = true)), saved = listOf(SavedStream(p, 125 * min, 136 * min, now + 5_000)))).isEmpty())
    }

    @Test fun a_later_short_viewing_left_in_the_credits_still_finishes_the_episode() {
        val history = ep(1, 3, now - day, 40 * min)
        val json = StreamProgress.record(null, "tt0903747", 1, 3, 46 * min, 47 * min, now)
        assertEquals("S1 E4", select(inputs(history = listOf(history), progress = json), cat(*seasons(5))).single().episode!!.code)
        // The same viewing as a rewatch the history knows about (minutes apart): the rewatch stands.
        val rewatch = ep(1, 3, now - 5 * min, 5 * min)
        assertEquals(ContinueAction.RESUME, select(inputs(history = listOf(rewatch), progress = json), cat(*seasons(5))).single().action)
    }

    @Test fun a_rewatch_paused_over_half_an_hour_is_not_finished_by_the_old_furthest_point() {
        // Finished once (furthest 46 of 47 min); rewatched from the start and paused 12 minutes in, long after history last wrote.
        val history = ep(1, 3, now - 3 * hour, 5 * min)
        var json = StreamProgress.record(null, "tt0903747", 1, 3, 46 * min, 47 * min, now - day)
        json = StreamProgress.record(json, "tt0903747", 1, 3, 12 * min, 47 * min, now)
        val c = select(inputs(history = listOf(history), progress = json), cat(*seasons(5))).single()
        assertEquals(ContinueAction.RESUME, c.action); assertEquals("S1 E3", c.episode!!.code); assertEquals(12 * min, c.resumeMs)
        val p = ContinueEngine.playback(inputs(history = listOf(history), progress = json)).single().state
        assertFalse(p.completed); assertEquals(46 * min, p.furthestMs)
        // Authoritative played state on the server still wins when it is fresher.
        val server = listOf(jfEp("e3", 1, 3, played = true, at = now + min))
        assertEquals("S1 E4", select(inputs(history = listOf(history), progress = json, server = server,
            known = listOf(KnownTitle(Kind.SHOW, "Breaking Bad", tmdbId = "1396", imdbId = "tt0903747", jellyfinId = "abc-123"))), cat(*seasons(5))).single().episode!!.code)
    }

    @Test fun a_part_watched_next_episode_resumes_and_finished_ones_are_passed_over() {
        val resumeNext = select(inputs(history = listOf(ep(1, 4, now - day, 12 * min), ep(1, 3, now, 47 * min, done = true))), cat(*seasons(6))).single()
        assertEquals(ContinueAction.RESUME, resumeNext.action); assertEquals("S1 E4", resumeNext.episode!!.code); assertEquals(12 * min, resumeNext.resumeMs)
        assertEquals(now, resumeNext.lastPlayedAt)
        val skip = select(inputs(history = listOf(ep(1, 4, now - day, 47 * min, done = true), ep(1, 3, now, 47 * min, done = true))), cat(*seasons(6))).single()
        assertEquals("S1 E5", skip.episode!!.code)
        // Watching E2 again after E5 does not pass the earlier part-watched E5 by, and nothing before E2 is assumed watched.
        val back = select(inputs(history = listOf(ep(1, 5, now - day, 12 * min), ep(1, 2, now, 47 * min, done = true))), cat(*seasons(6))).single()
        assertEquals("S1 E3", back.episode!!.code); assertEquals(ContinueAction.NEXT_EPISODE, back.action)
    }

    @Test fun a_payload_that_contradicts_itself_or_names_nothing_is_ignored() {
        assertNull(ContinueEngine.savedStreamEntry(payload("tt0903747", "x", 1 to 2, HistoryTarget(scope, ep(1, 3, 0, 0))), scope))
        assertNull(ContinueEngine.savedStreamEntry(payload("tt0903747", "x", 1 to 2, HistoryTarget(scope, matrix)), scope))
        assertNull(ContinueEngine.savedStreamEntry(JSONObject().put("title", "The Matrix").toString(), scope))
        assertNull(ContinueEngine.savedStreamEntry("not json", scope))
        // Another server's library ids are not trusted here.
        val other = HistoryTarget("http://192.168.1.30:8096|u2", matrix.copy(jellyfinId = "jfX"))
        val e = ContinueEngine.savedStreamEntry(payload("tt0133093", "The Matrix", history = other), scope)!!
        assertNull(e.jellyfinId); assertEquals("603", e.tmdbId)
    }

    @Test fun library_listing_and_tmdb_listing_combine() {
        val lib = EpisodeCatalogue.fromLibrary(listOf(jfEp("e1", 1, 1), jfEp("e2", 1, 2).copy(episodeEnd = 3), jfEp("e9", 1, 9).copy(missing = true)))
        assertFalse(lib.complete)
        assertEquals(listOf("e1", "e2", null), lib.episodes.map { it.jellyfinId })
        // A two-part file counts as both: after it comes E4.
        val tmdb = EpisodeCatalogue.fromTmdb(listOf(SeasonInfo(1, "Season 1", 4, Availability.NONE)), (1..4).map { EpisodeInfo(1, it, "E$it", null, "2020-01-0$it") })
        assertTrue(tmdb.complete)
        val both = EpisodeCatalogue.merge(tmdb, lib)!!
        assertEquals("S1 E4", select(inputs(history = listOf(ep(1, 2, now, 47 * min, done = true))), both).single().episode!!.code)
    }
}

package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PicksTest {
    @Test fun canonicalKeyUsesTheCatalogueKeyWhenTheTmdbIdIsKnown() {
        assertEquals("tmdb:m:603", Picks.canonicalKey(Kind.MOVIE, "603", "lb:the-matrix"))
        assertEquals("tmdb:s:603", Picks.canonicalKey(Kind.SHOW, " 603 ", "lb:the-matrix"))
    }

    @Test fun canonicalKeyKeepsTheFallbackWithoutAUsableId() {
        assertEquals("lb:x", Picks.canonicalKey(Kind.MOVIE, null, "lb:x"))
        assertEquals("lb:x", Picks.canonicalKey(Kind.MOVIE, "", "lb:x"))
        assertEquals("lb:x", Picks.canonicalKey(Kind.MOVIE, "tt0133093", "lb:x"))
        assertEquals("lb:x", Picks.canonicalKey(Kind.MOVIE, "0", "lb:x"))
    }

    private fun sug(id: Long, name: String = "T$id", genres: List<String> = emptyList(), vote: Double? = null, owned: Boolean = false) =
        Suggestion(id, Kind.MOVIE, name, 2020, null, "/p$id.jpg", "/b$id.jpg", genres, vote, owned)

    @Test fun recent_and_watched_library_titles_make_stronger_seeds() {
        val library = listOf(
            LibrarySeed(1, Kind.MOVIE, "Newest", emptyList(), played = false, started = false),
            LibrarySeed(2, Kind.MOVIE, "Older but watched", emptyList(), played = true, started = false)
        )
        val seeds = Picks.seeds(library, emptyList(), nowMs = 0)
        assertEquals(listOf("Older but watched", "Newest"), seeds.map { it.name })   // 0.95 * 1.5 beats 1.0
    }

    @Test fun streams_count_and_fade_with_age() {
        val day = 24 * 60 * 60 * 1000L
        val history = listOf(
            HistoryEntry(5, Kind.MOVIE, "Streamed long ago", emptyList(), atMs = 0, streamed = true),
            HistoryEntry(6, Kind.MOVIE, "Streamed yesterday", emptyList(), atMs = 89 * day, streamed = true)
        )
        val seeds = Picks.seeds(emptyList(), history, nowMs = 90 * day)
        assertEquals("Streamed yesterday", seeds.first().name)
        assertTrue(seeds.last().weight < 0.25)   // three months: an eighth of 1.5
    }

    @Test fun a_title_several_seeds_point_to_ranks_first() {
        val a = PickSeed(1, Kind.MOVIE, "Inception", 1.0); val b = PickSeed(2, Kind.MOVIE, "Interstellar", 1.0)
        val picks = Picks.rank(listOf(a, b), mapOf(
            a.key to listOf(sug(10), sug(11)),
            b.key to listOf(sug(12), sug(11))
        ), exclude = emptySet())
        assertEquals(11L, picks.first().suggestion.tmdbId)
    }

    @Test fun owned_watched_and_seed_titles_are_left_out() {
        val a = PickSeed(1, Kind.MOVIE, "Inception", 1.0); val b = PickSeed(2, Kind.MOVIE, "Interstellar", 1.0)
        val picks = Picks.rank(listOf(a, b), mapOf(a.key to listOf(sug(2), sug(20, owned = true), sug(21), sug(22))),
            exclude = setOf(Picks.key(Kind.MOVIE, 21)))
        assertEquals(listOf(22L), picks.map { it.suggestion.tmdbId })
    }

    @Test fun each_pick_says_which_title_it_comes_from() {
        val strong = PickSeed(1, Kind.MOVIE, "Inception", 2.0); val weak = PickSeed(2, Kind.MOVIE, "Cars", 0.5)
        val picks = Picks.rank(listOf(strong, weak), mapOf(strong.key to listOf(sug(30)), weak.key to listOf(sug(30))), emptySet())
        assertEquals("Inception", picks.single().because)
    }

    @Test fun one_seed_does_not_fill_the_list() {
        val big = PickSeed(1, Kind.MOVIE, "Big", 1.0); val small = PickSeed(2, Kind.MOVIE, "Small", 0.7)
        val picks = Picks.rank(listOf(big, small), mapOf(
            big.key to (100L..105L).map { sug(it) },
            small.key to listOf(sug(200))
        ), emptySet(), limit = 4)
        assertTrue(picks.any { it.because == "Small" })
    }

    @Test fun favourite_genres_lift_a_title() {
        val seed = PickSeed(1, Kind.MOVIE, "Alien", 1.0, genres = listOf("Horror"))
        val picks = Picks.rank(listOf(seed), mapOf(seed.key to listOf(sug(40, genres = listOf("Comedy")), sug(41, genres = listOf("Horror")))), emptySet())
        assertEquals(41L, picks.first().suggestion.tmdbId)
    }

    @Test fun seerr_recommendations_are_read() {
        val json = """{"page":1,"results":[
            {"id":27205,"mediaType":"movie","title":"Inception","releaseDate":"2010-07-15","posterPath":"/p.jpg","backdropPath":"/b.jpg","genreIds":[28,878],"voteAverage":8.4},
            {"id":1399,"mediaType":"tv","name":"Game of Thrones","firstAirDate":"2011-04-17","genreIds":[10765],"mediaInfo":{"status":5}},
            {"id":3,"mediaType":"person","name":"Someone"}]}"""
        val s = Picks.parseSuggestions(json)
        assertEquals(2, s.size)
        assertEquals(listOf("Action", "Science Fiction"), s[0].genres)
        assertEquals(2010, s[0].year); assertFalse(s[0].owned)
        assertEquals(Kind.SHOW, s[1].kind); assertTrue(s[1].owned)
    }

    @Test fun picks_survive_saving() {
        val picks = listOf(Pick(sug(7, "Heat", listOf("Crime"), 8.3), 1.5, "Collateral"))
        assertEquals(picks.map { it.suggestion.name to it.because }, Picks.fromJson(Picks.toJson(picks)).map { it.suggestion.name to it.because })
    }
}

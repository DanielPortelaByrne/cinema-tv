package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.settings.CinemaSettings
import com.danielportelabyrne.cinema.platform.MemoryPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchlistTest {
    private val a = WatchlistFilm("tt1", "A", 2001, "https://p/a")
    private val b = WatchlistFilm("tt2", "B", null, null)

    @Test fun round_trips_through_json() {
        val back = Watchlist.parse(Watchlist.toJson(listOf(a, b)))
        assertEquals(listOf(a, b), back)
        assertNull(back[1].year)
        assertNull(back[1].poster)
    }

    @Test fun junk_reads_as_empty() {
        assertEquals(emptyList<WatchlistFilm>(), Watchlist.parse("not json"))
        assertEquals(emptyList<WatchlistFilm>(), Watchlist.parse(null))
    }

    @Test fun merge_adds_new_films_in_front_and_skips_known_ones() {
        val c = WatchlistFilm("tt3", "C", null, null)
        assertEquals(listOf(c, a, b), Watchlist.merge(listOf(a, b), listOf(a, c)))
    }

    @Test fun without_takes_one_film_out_and_keeps_the_rest() {
        assertEquals(listOf(b), Watchlist.without(listOf(a, b), "tt1"))
        assertEquals(listOf(a, b), Watchlist.without(listOf(a, b), "tt9"))
    }

    @Test fun settings_keep_the_watchlist_and_count_new_films() {
        val settings = CinemaSettings(MemoryPlatform())
        assertEquals(2, settings.addToWatchlist(listOf(a, b)))
        assertEquals(1, settings.addToWatchlist(listOf(b, WatchlistFilm("tt3", "C", null, null))))
        assertEquals(listOf("tt3", "tt1", "tt2"), settings.watchlist.map { it.imdbId })
    }
}

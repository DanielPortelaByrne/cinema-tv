package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchlistViewTest {
    private val heat = WatchlistFilm("tt1", "Heat", 1995, null)
    private val dune = WatchlistFilm("tt2", "Dune", 2021, null)
    private val alien = WatchlistFilm("tt3", "Alien", 1979, null)
    private val odd = WatchlistFilm("tt4", "The Zone", null, null)
    private val films = listOf(dune, heat, odd, alien)   // newest added first
    private val facts = mapOf(
        "tt1" to WatchlistFacts(listOf("Crime", "Drama"), 170, 8.3),
        "tt2" to WatchlistFacts(listOf("Sci-Fi", "Drama"), 155, 8.0),
        "tt3" to WatchlistFacts(listOf("Sci-Fi", "Horror"), 117, 8.5)
    )

    private fun names(s: List<WatchlistFilm>) = s.map { it.name }

    @Test fun sortsEachWay() {
        assertEquals(names(films), names(WatchlistView.sorted(films, facts, WatchlistSort.ADDED)))
        assertEquals(listOf("Alien", "Dune", "Heat", "The Zone"), names(WatchlistView.sorted(films, facts, WatchlistSort.TITLE)))
        assertEquals(listOf("Dune", "Heat", "Alien", "The Zone"), names(WatchlistView.sorted(films, facts, WatchlistSort.NEWEST)))
        assertEquals(listOf("Alien", "Heat", "Dune", "The Zone"), names(WatchlistView.sorted(films, facts, WatchlistSort.OLDEST)))
        assertEquals(listOf("Alien", "Heat", "Dune", "The Zone"), names(WatchlistView.sorted(films, facts, WatchlistSort.RATING)))
        assertEquals(listOf("Alien", "Dune", "Heat", "The Zone"), names(WatchlistView.sorted(films, facts, WatchlistSort.SHORTEST)))
    }

    @Test fun groupsByGenreBiggestFirstWithUnknownLast() {
        val rows = WatchlistView.arrange(films, facts, { false }, WatchlistSort.ADDED, WatchlistGroup.GENRE, WatchlistShow.ALL)
        assertEquals(listOf("Drama", "Sci-Fi", "Crime", "Horror", WatchlistView.UNSORTED), rows.map { it.heading })
        assertEquals(listOf("Dune", "Heat"), names(rows[0].films))
        assertEquals(listOf("The Zone"), names(rows.last().films))
    }

    @Test fun groupsByDecadeNewestFirst() {
        val rows = WatchlistView.arrange(films, facts, { false }, WatchlistSort.TITLE, WatchlistGroup.DECADE, WatchlistShow.ALL)
        assertEquals(listOf("2020s", "1990s", "1970s", "Year unknown"), rows.map { it.heading })
    }

    @Test fun filtersByLibraryAndKeepsFactsThroughJson() {
        val inLib = setOf("tt1")
        assertEquals(listOf("Heat"), names(WatchlistView.arrange(films, facts, { it in inLib }, WatchlistSort.ADDED, WatchlistGroup.NONE, WatchlistShow.IN_LIBRARY).single().films))
        assertEquals(3, WatchlistView.arrange(films, facts, { it in inLib }, WatchlistSort.ADDED, WatchlistGroup.NONE, WatchlistShow.NOT_IN_LIBRARY).single().films.size)
        assertEquals(facts, WatchlistView.parseFacts(WatchlistView.factsJson(facts)))
    }
}

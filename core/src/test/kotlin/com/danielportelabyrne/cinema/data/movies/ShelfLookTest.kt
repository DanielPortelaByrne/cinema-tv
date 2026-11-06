package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.movies.ShelfLook.GRID
import com.danielportelabyrne.cinema.data.movies.ShelfLook.POSTERS
import com.danielportelabyrne.cinema.data.movies.ShelfLook.RANKED
import com.danielportelabyrne.cinema.data.movies.ShelfLook.SMALL_STILLS
import com.danielportelabyrne.cinema.data.movies.ShelfLook.SPOTLIGHT
import com.danielportelabyrne.cinema.data.movies.ShelfLook.STILLS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShelfLookTest {
    private fun title(name: String, backdrop: Boolean) = Title(name, Kind.MOVIE, name, 2020, null, null, emptyList(), null, null,
        "https://img/$name.jpg", if (backdrop) "https://img/$name-wide.jpg" else null, null, null)

    @Test fun homeRowsTakeTurnsAndTop10KeepsItsNumbers() {
        val looks = ShelfLooks.forShelves(Catalog.homePage())
        assertEquals(RANKED, looks.first())
        assertEquals(listOf(SPOTLIGHT, STILLS, POSTERS, GRID, SMALL_STILLS, SPOTLIGHT), looks.drop(1).take(6))
        looks.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
    }

    @Test fun theSpotlightLeadsEveryPageAndTop10ComesNext() {
        val pages = listOf(Catalog.homePage(), Catalog.filmsPage(), Catalog.seriesPage(), Catalog.documentariesPage()) +
            Catalog.SERVICES.map { Catalog.servicePage(it) } + Catalog.GENRES.map { Catalog.genrePage(it) }
        pages.forEach { page ->
            val plan = ShelfLooks.plan(page)
            assertEquals(SPOTLIGHT, plan[0].second)
            assertEquals(false, plan[0].first.ranked)
            assertEquals(RANKED, plan[1].second)
            assertEquals(page.toSet(), plan.map { it.first }.toSet())
        }
        assertEquals("Trending films", ShelfLooks.plan(Catalog.homePage())[0].first.heading)
    }

    @Test fun neighbouringRowsNeverMatchOnAnyCataloguePage() {
        val pages = listOf(Catalog.homePage(), Catalog.filmsPage(), Catalog.seriesPage(), Catalog.documentariesPage()) +
            Catalog.SERVICES.map { Catalog.servicePage(it) } + Catalog.GENRES.map { Catalog.genrePage(it) }
        pages.forEach { page -> ShelfLooks.plan(page).map { it.second }.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) } }
    }

    @Test fun aWideRowWithFewWidePicturesFallsBackToPosters() {
        val mostly = (1..10).map { title("t$it", backdrop = it <= 8) }
        val few = (1..10).map { title("t$it", backdrop = it <= 3) }
        assertEquals(STILLS, ShelfLooks.resolve(STILLS, mostly))
        assertEquals(POSTERS, ShelfLooks.resolve(SMALL_STILLS, few))
        assertEquals(GRID, ShelfLooks.resolve(GRID, mostly))
        assertEquals(POSTERS, ShelfLooks.resolve(STILLS, emptyList()))
    }

    @Test fun aSpotlightIsLedByAnEarlyTitleWithAWidePicture() {
        assertEquals(1, ShelfLooks.spotlightIndex(listOf(title("a", false), title("b", true), title("c", true))))
        assertEquals(POSTERS, ShelfLooks.resolve(SPOTLIGHT, listOf(title("a", false), title("b", false), title("c", false), title("d", true))))
        assertEquals(SPOTLIGHT, ShelfLooks.resolve(SPOTLIGHT, listOf(title("a", true))))
    }
}

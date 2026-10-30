package com.danielportelabyrne.cinema.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentSearchesTest {
    @Test fun savedFormRoundTrips() {
        val terms = listOf("Heat", "a24", "Sharp Objects")
        assertEquals(terms, RecentSearches.parse(RecentSearches.encode(terms)))
        assertNull(RecentSearches.encode(emptyList()))
        assertEquals(emptyList<String>(), RecentSearches.parse(null))
        assertEquals(listOf("Heat"), RecentSearches.parse("\nHeat\n\n"))
    }

    @Test fun newestGoesFirst() {
        val terms = RecentSearches.add(RecentSearches.add(emptyList(), "Heat"), "Alien")
        assertEquals(listOf("Alien", "Heat"), terms)
    }

    @Test fun aRepeatMovesToTheFrontOnceInTheCaseJustTyped() {
        val terms = listOf("Alien", "Heat", "Dark")
        assertEquals(listOf("heat", "Alien", "Dark"), RecentSearches.add(terms, "heat"))
    }

    @Test fun typingOnReplacesTheLatestStart() {
        var terms = emptyList<String>()
        for (q in listOf("Bat", "Batm", "Batman")) terms = RecentSearches.add(terms, q)
        assertEquals(listOf("Batman"), terms)
    }

    @Test fun aShorterStartReplacesTheLongerOne() {
        assertEquals(listOf("Bat", "Heat"), RecentSearches.add(listOf("Batman", "Heat"), "Bat"))
    }

    @Test fun shortQueriesAreNotKept() {
        assertEquals(listOf("Heat"), RecentSearches.add(listOf("Heat"), "h"))
        assertEquals(emptyList<String>(), RecentSearches.add(emptyList(), "  "))
    }

    @Test fun keepsAtMostTheLimit() {
        var terms = emptyList<String>()
        for (i in 1..(RecentSearches.MAX + 3)) terms = RecentSearches.add(terms, "title $i")
        assertEquals(RecentSearches.MAX, terms.size)
        assertEquals("title ${RecentSearches.MAX + 3}", terms.first())
    }
}

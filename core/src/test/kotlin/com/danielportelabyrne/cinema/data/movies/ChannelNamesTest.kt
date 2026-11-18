package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelNamesTest {
    @Test fun disneyAsksForTheDisneyNetworks() {
        val names = ChannelNames.expand("Disney")
        assertTrue(names.containsAll(listOf("Disney Channel", "Disney XD", "Disney Junior", "Playhouse Disney")))
    }

    @Test fun popFindsPopPopGirlAndTinyPopExactFirst() {
        val names = ChannelNames.expand("pop")
        assertEquals("Pop", names.first())
        assertTrue(names.containsAll(listOf("Pop Girl", "Tiny Pop")))
    }

    @Test fun spacingAndCaseDoNotMatter() {
        assertEquals(listOf("Tiny Pop"), ChannelNames.expand("tinypop"))
        assertEquals(listOf("Tiny Pop"), ChannelNames.expand("  TINY pop "))
        assertEquals(listOf("Pop Girl"), ChannelNames.expand("pop girl"))
    }

    @Test fun aliasesFindTheirNetwork() {
        assertEquals("CITV", ChannelNames.expand("citv").first())
        assertEquals("CBBC", ChannelNames.expand("cbbc").first())
        assertEquals("Nickelodeon", ChannelNames.expand("nick").first())
        assertTrue(ChannelNames.expand("nick").containsAll(listOf("Nick Jr.", "Nicktoons")))
        assertEquals("RTÉ2", ChannelNames.expand("rte2").first())
        assertEquals("Cartoon Network", ChannelNames.expand("cn").first())
    }

    @Test fun shortUnknownOrBlankQueriesAskForNothingExtra() {
        assertEquals(emptyList<String>(), ChannelNames.expand(""))
        assertEquals(emptyList<String>(), ChannelNames.expand("p"))
        assertEquals(emptyList<String>(), ChannelNames.expand("zzzz"))
        assertEquals(emptyList<String>(), ChannelNames.expand("history"))
    }

    @Test fun expansionIsBounded() {
        assertTrue(ChannelNames.expand("ni").size <= ChannelNames.MAX)
    }

    @Test fun namedKeepsOnlyMatchesCalledThat() {
        val found = listOf(
            Triple("Q1", "Tiny Pop", "British television channel"),
            Triple("Q2", "TinyPop (band)", null),
            Triple("Q3", "Pop music", null),
            Triple("Q4", "tiny pop", "duplicate label")
        )
        assertEquals(listOf("Q1", "Q2", "Q4"), ChannelData.named(found, "Tiny Pop").map { it.first })
        assertEquals(1, ChannelData.named(found, "Tiny Pop", limit = 1).size)
    }

    @Test fun aChannelIsRankedByItsNameThenByWhatPeopleCallIt() {
        assertEquals(85, ChannelNames.score("Disney Channel", "disney"))
        assertEquals(70, ChannelNames.score("Tiny Pop", "pop"))
        // "cn" is nowhere in "Cartoon Network", but it is what people call it.
        assertEquals(75, ChannelNames.score("Cartoon Network", "cn"))
        assertEquals(0, ChannelNames.score("History", "cn"))
    }

    @Test fun theChannelOutranksTheServiceThatSharesItsAlias() {
        val itvx = Service("itv", "ITVX", null)
        assertTrue(ChannelNames.score("CITV", "citv") > ServiceDirectory.scoreOf(itvx, "citv"))
    }

    @Test fun spacingAside_aNameIsFound() {
        assertEquals(90, ServiceDirectory.score("Tiny Pop", "tinypop"))
        assertEquals(80, ServiceDirectory.score("Tiny Pop", "tinyp"))
        assertEquals(0, ServiceDirectory.score("itv", "tubi tv"))
    }
}

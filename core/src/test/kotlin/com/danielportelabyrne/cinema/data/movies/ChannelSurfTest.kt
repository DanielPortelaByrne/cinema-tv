package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelSurfTest {
    private val ids = listOf("a", "b", "c")

    @Test fun upGoesToTheNextChannel() = assertEquals("b", ChannelSurf.destination(ids, "a", up = true))
    @Test fun downGoesToThePreviousChannel() = assertEquals("a", ChannelSurf.destination(ids, "b", up = false))
    @Test fun upWrapsFromTheLastToTheFirst() = assertEquals("a", ChannelSurf.destination(ids, "c", up = true))
    @Test fun downWrapsFromTheFirstToTheLast() = assertEquals("c", ChannelSurf.destination(ids, "a", up = false))
    @Test fun oneChannelHasNowhereToGo() = assertNull(ChannelSurf.destination(listOf("a"), "a", up = true))
    @Test fun noChannelsHasNowhereToGo() = assertNull(ChannelSurf.destination(emptyList(), "a", up = false))
    @Test fun aChannelThatWasDeletedEntersAtTheEnds() {
        assertEquals("a", ChannelSurf.destination(ids, "gone", up = true))
        assertEquals("c", ChannelSurf.destination(ids, "gone", up = false))
    }
}

package com.danielportelabyrne.cinema.data.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamChoicesTest {
    private val options = listOf(
        StreamOption("1080p WEB-DL", "Torrentio", 8_400_000L),
        StreamOption("720p HDTV", "Home server", null),
        StreamOption("", "Add-on", 0L)
    )

    @Test fun the_picker_is_offered_only_with_a_second_copy_and_not_live() {
        assertFalse(StreamChoices.offered(1, live = false))
        assertTrue(StreamChoices.offered(2, live = false))
        assertFalse(StreamChoices.offered(3, live = true))
    }

    @Test fun current_and_recommended_are_marked_accurately() {
        val rows = StreamChoices.rows(options, current = 1)
        assertEquals("Recommended", rows[0].tag)
        assertEquals("Current", rows[1].tag)
        assertNull(rows[2].tag)
        // The recommended copy is also the one playing: both tags.
        assertEquals("Current · Recommended", StreamChoices.rows(options, 0)[0].tag)
    }

    @Test fun unknown_values_are_left_out_not_invented() {
        val rows = StreamChoices.rows(options, 0)
        assertEquals("Torrentio · 8 Mbps", rows[0].detail)
        assertEquals("Home server", rows[1].detail)
        assertEquals("Add-on", rows[2].title)       // no label: the provider names it
        assertNull(rows[2].detail)                  // zero bitrate is not a figure
    }

    @Test fun rows_keep_the_ranked_order_and_indexes() {
        assertEquals(listOf(0, 1, 2), StreamChoices.rows(options, 0).map { it.index })
    }

    @Test fun automatic_failure_carries_on_then_gives_up() {
        assertEquals(AfterFailure.TryNext(1), StreamChoices.afterFailure(0, 3, manual = false))
        assertEquals(AfterFailure.GiveUp, StreamChoices.afterFailure(2, 3, manual = false))
    }

    @Test fun a_picked_copy_that_fails_asks_again_instead_of_moving_on() {
        assertEquals(AfterFailure.AskAgain, StreamChoices.afterFailure(0, 3, manual = true))
        assertEquals(AfterFailure.AskAgain, StreamChoices.afterFailure(2, 3, manual = true))
    }

    @Test fun switching_keeps_the_position_only_when_something_played() {
        assertEquals(754_000L, StreamChoices.resumeAt(754_000L, started = true))
        assertNull(StreamChoices.resumeAt(0L, started = true))
        assertNull(StreamChoices.resumeAt(90_000L, started = false))
    }
}

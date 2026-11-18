package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class BroadcastBannerTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val at2015 = 20L * 3_600_000 + 15 * 60_000   // 20:15 UTC on 1970-01-01
    private val min = 60_000L

    @Test fun lengthsAreReadable() {
        assertEquals("24 min", BroadcastBanner.length(24 * min))
        assertEquals("1 h", BroadcastBanner.length(60 * min))
        assertEquals("1 h 52 min", BroadcastBanner.length(112 * min))
        assertEquals("1 min", BroadcastBanner.length(10_000))
        assertNull(BroadcastBanner.length(0))
    }

    @Test fun nowLineBacksUpToTheStartAndMeetsTheEnd() {
        // 4 minutes in: it began at 20:11 and ends at 20:35.
        assertEquals("NOW 20:11–20:35 · 24 min", BroadcastBanner.nowLine(at2015, 4 * min, 24 * min, utc))
        assertNull(BroadcastBanner.nowLine(at2015, 4 * min, 0, utc))
    }

    @Test fun nextLineIsWhenThisOneEndsAndIsMarkedAsAnEstimate() {
        assertEquals("NEXT ~20:35 · Malcolm in the Middle", BroadcastBanner.nextLine(at2015, 4 * min, 24 * min, "Malcolm in the Middle", utc))
        assertEquals("NEXT · Malcolm", BroadcastBanner.nextLine(at2015, 4 * min, 0, "Malcolm", utc))
        assertNull(BroadcastBanner.nextLine(at2015, 4 * min, 24 * min, null, utc))
        assertNull(BroadcastBanner.nextLine(at2015, 4 * min, 24 * min, "  ", utc))
    }

    @Test fun crossingMidnightWraps() {
        val at2350 = 23L * 3_600_000 + 50 * min
        assertEquals("NOW 23:50–00:14 · 24 min", BroadcastBanner.nowLine(at2350, 0, 24 * min, utc))
        assertEquals("NEXT ~00:14 · X", BroadcastBanner.nextLine(at2350, 0, 24 * min, "X", utc))
    }

    @Test fun aPositionPastTheEndNeverGivesAnEarlierNext() {
        assertEquals("NEXT ~20:15 · X", BroadcastBanner.nextLine(at2015, 30 * min, 24 * min, "X", utc))
    }
}

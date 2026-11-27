package com.danielportelabyrne.cinema.mobile.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomTest {
    // A 16:9 frame fitted to a wide phone: bars at the sides.
    private val fill = Zoom.fill(2400, 1080, 1920, 1080)

    @Test fun fillCoversTheLongerGap() {
        assertEquals(1.25f, fill, 0.001f)
        assertEquals(1.2f, Zoom.fill(1920, 1080, 1920, 900), 0.001f)
        assertEquals(1f, Zoom.fill(2400, 1080, 0, 0), 0.001f)
    }

    @Test fun staysWithinItsRange() {
        assertEquals(1f, Zoom.clamp(0.4f, fill), 0.001f)
        assertEquals(2f, Zoom.clamp(9f, fill), 0.001f)
    }

    @Test fun snapsToTheWholeFrameAndToFill() {
        assertEquals(1f, Zoom.settle(1.04f, fill), 0.001f)
        assertEquals(fill, Zoom.settle(1.22f, fill), 0.001f)
        assertEquals(fill, Zoom.settle(1.28f, fill), 0.001f)
    }

    /** Past fill, to lose a 2.39:1 film's own bars on this screen, it stays where the fingers left it. */
    @Test fun canRestBeyondFill() {
        assertEquals(1.34f, Zoom.settle(1.34f, fill), 0.001f)
        assertEquals(1.6f, Zoom.settle(1.6f, fill), 0.001f)
    }

    @Test fun saysWhereItIs() {
        assertEquals("Original", Zoom.label(1f, fill))
        assertEquals("Fill screen", Zoom.label(fill, fill))
        assertEquals("134%", Zoom.label(1.34f, fill))
    }
}

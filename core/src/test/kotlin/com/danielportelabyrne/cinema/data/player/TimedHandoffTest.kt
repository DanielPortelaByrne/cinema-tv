package com.danielportelabyrne.cinema.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimedHandoffTest {
    private var now = 1_000L
    private val note = TimedHandoff<Pair<Int, Int>>(60_000L) { now }

    @Test fun `a fresh note is collected once`() {
        note.put(1 to 3)
        now += 5_000
        assertEquals(1 to 3, note.take())
        assertNull(note.take())
    }

    @Test fun `an old note is never played later`() {
        note.put(1 to 3)   // left by a player that then failed
        now += 61_000
        assertNull(note.take())
    }

    @Test fun `a cleared note is gone`() {
        note.put(1 to 3); note.clear()
        assertNull(note.take())
    }

    @Test fun `the newest note replaces the one before it`() {
        note.put(1 to 3); note.put(1 to 4)
        assertEquals(1 to 4, note.take())
    }

    @Test fun `a clock that went backwards does not revive a note`() {
        note.put(1 to 3)
        now -= 10
        assertNull(note.take())
    }
}

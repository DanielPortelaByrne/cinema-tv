package com.danielportelabyrne.cinema.data.letterboxd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainGateTest {
    /** A clock the test moves by hand. */
    private class Clock(var now: Long = 1_000L) : () -> Long {
        override fun invoke(): Long = now
    }

    @Test fun `allows plain requests to begin with`() {
        assertTrue(PlainGate().allow())
    }

    @Test fun `closes after the limit of refusals in a row`() {
        val gate = PlainGate(limit = 3, clock = Clock())
        repeat(3) { gate.refusal() }
        assertFalse(gate.allow())
    }

    @Test fun `a success in between resets the count`() {
        val gate = PlainGate(limit = 3, clock = Clock())
        repeat(2) { gate.refusal() }
        gate.success()
        repeat(2) { gate.refusal() }
        assertTrue(gate.allow())
    }

    /** The latch (plan 2A): a closed gate must not stay closed for good; a probe gets through once the interval has passed. */
    @Test fun `a closed gate lets one probe through each interval`() {
        val clock = Clock()
        val gate = PlainGate(limit = 3, probeEveryMs = 60_000L, clock = clock)
        repeat(3) { gate.refusal() }
        clock.now += 59_999L
        assertFalse(gate.allow())
        clock.now += 1L
        assertTrue(gate.allow())
        assertFalse(gate.allow())
        clock.now += 60_000L
        assertTrue(gate.allow())
    }

    /** A browser load that clears the check opens the gate at once, so plain requests are tried without waiting. */
    @Test fun `a success reopens a closed gate at once`() {
        val gate = PlainGate(limit = 3, clock = Clock())
        repeat(3) { gate.refusal() }
        assertFalse(gate.allow())
        gate.success()
        assertTrue(gate.allow())
        assertEquals(0, gate.refusalsInARow())
    }
}

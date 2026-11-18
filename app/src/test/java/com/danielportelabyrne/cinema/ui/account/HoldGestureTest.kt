package com.danielportelabyrne.cinema.ui.account

import com.danielportelabyrne.cinema.data.account.GoldProfiles
import com.danielportelabyrne.cinema.data.account.InMemoryGoldProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldGestureTest {
    @Test fun shortPressIsAClick() {
        val g = HoldGesture()
        assertTrue(g.down(0))
        assertFalse(g.tick(1000))
        assertEquals(HoldGesture.End.CLICK, g.up(1000))
    }

    @Test fun holdFiresOnceAndConsumesKeyUp() {
        val g = HoldGesture()
        g.down(0)
        assertFalse(g.tick(2999))
        assertTrue(g.tick(3000))
        assertFalse(g.tick(3500))
        assertEquals(HoldGesture.End.CONSUMED, g.up(4000))
    }

    @Test fun keyRepeatsDoNotRestartTheHold() {
        val g = HoldGesture()
        assertTrue(g.down(0))
        assertFalse(g.down(500))
        assertFalse(g.down(2500))
        assertTrue(g.tick(3000))
    }

    @Test fun cancelForgetsTheHoldAndItsKeyUpDoesNothing() {
        val g = HoldGesture()
        g.down(0)
        g.cancel()
        assertFalse(g.holding)
        assertEquals(0f, g.progress(2000), 0f)
        assertEquals(HoldGesture.End.CANCELLED, g.up(2100))
        assertFalse(g.tick(5000))
    }

    @Test fun keyUpWithoutKeyDownDoesNotOpenAnything() {
        assertEquals(HoldGesture.End.CANCELLED, HoldGesture().up(10))
    }

    @Test fun aSecondHoldAfterACompletedOneWorks() {
        val g = HoldGesture()
        g.down(0); g.tick(3000); g.up(3100)
        assertTrue(g.down(5000))
        assertEquals(HoldGesture.End.CLICK, g.up(5200))
    }

    @Test fun progressRunsZeroToOne() {
        val g = HoldGesture()
        g.down(100)
        assertEquals(0f, g.progress(100), 0f)
        assertEquals(0.5f, g.progress(1600), 0.001f)
        assertEquals(1f, g.progress(9000), 0f)
    }

    @Test fun firstHoldUnlocksAndEnablesThenLaterHoldsToggle() {
        val gold: GoldProfiles = InMemoryGoldProfiles()
        assertFalse(gold.isEnabled("a"))
        assertEquals(GoldProfiles.Held.UNLOCKED, gold.held("a"))
        assertTrue(gold.isUnlocked("a")); assertTrue(gold.isEnabled("a"))
        assertEquals(GoldProfiles.Held.DISABLED, gold.held("a"))
        assertFalse(gold.isEnabled("a")); assertTrue(gold.isUnlocked("a"))
        assertEquals(GoldProfiles.Held.ENABLED, gold.held("a"))
    }

    @Test fun goldIsPerPerson() {
        val gold = InMemoryGoldProfiles()
        gold.held("a")
        assertFalse(gold.isUnlocked("b")); assertFalse(gold.isEnabled("b"))
    }
}

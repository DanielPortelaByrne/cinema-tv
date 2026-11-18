package com.danielportelabyrne.cinema.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredGoldProfilesTest {
    private class FakeStore(val map: MutableMap<String, Boolean> = mutableMapOf()) : GoldFlagStore {
        override fun get(key: String) = map[key] ?: false
        override fun put(key: String, value: Boolean) { map[key] = value }
    }

    @Test fun newPeopleAreLockedAndOff() {
        val gold = StoredGoldProfiles(FakeStore())
        assertFalse(gold.isUnlocked("a"))
        assertFalse(gold.isEnabled("a"))
    }

    @Test fun stateSurvivesARestart() {
        val store = FakeStore()
        assertEquals(GoldProfiles.Held.UNLOCKED, StoredGoldProfiles(store).held("a"))
        val reopened = StoredGoldProfiles(store)
        assertTrue(reopened.isUnlocked("a"))
        assertTrue(reopened.isEnabled("a"))
    }

    @Test fun turningOffKeepsTheUnlock() {
        val store = FakeStore()
        val gold = StoredGoldProfiles(store)
        gold.held("a")
        assertEquals(GoldProfiles.Held.DISABLED, gold.held("a"))
        val reopened = StoredGoldProfiles(store)
        assertTrue(reopened.isUnlocked("a"))
        assertFalse(reopened.isEnabled("a"))
        assertEquals(GoldProfiles.Held.ENABLED, reopened.held("a"))
    }

    @Test fun peopleDoNotShareState() {
        val gold = StoredGoldProfiles(FakeStore())
        gold.held("a")
        assertFalse(gold.isUnlocked("b"))
        assertFalse(gold.isEnabled("b"))
    }

    @Test fun enabledWithoutUnlockIsOff() {
        val store = FakeStore(mutableMapOf("gold.enabled.a" to true))
        val gold = StoredGoldProfiles(store)
        assertFalse(gold.isEnabled("a"))
        gold.setEnabled("a", true)
        assertFalse(gold.isEnabled("a"))
    }
}

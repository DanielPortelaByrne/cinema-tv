package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FeaturedRotationTest {
    private val hour = 60L * 60 * 1000
    private val day = 24 * hour
    private val now = 100 * day
    private fun c(key: String, group: String) = FeaturedCandidate(key, group, key)
    private val pool = listOf(c("a1", "today"), c("a2", "today"), c("n1", "nfx"), c("b1", "bbc"), c("k1", "nick"))

    @Test fun threeTitlesFromThreeDifferentPlaces() {
        repeat(20) { seed ->
            val got = FeaturedRotation.pick(pool, emptyMap(), now, random = Random(seed))
            assertEquals(3, got.size); assertEquals(3, got.map { it.group }.toSet().size)
        }
    }

    @Test fun theLeastShownComeFirst() {
        val seen = mapOf("n1" to FeaturedSeen(3, now - day, now - hour, false), "b1" to FeaturedSeen(2, now - day, now - hour, false), "k1" to FeaturedSeen(1, now - day, now - hour, false))
        val got = FeaturedRotation.pick(pool, seen, now, random = Random(1)).map { it.key }
        assertTrue(got.first().startsWith("a"))                // never shown
        assertEquals(listOf("k1", "b1"), got.drop(1))          // then fewest showings
    }

    @Test fun aTitleShownOftenOrForLongWithoutBeingOpenedIsRested() {
        val often = FeaturedSeen(FeaturedRotation.SHOWS_BEFORE_TIRED, now - hour, now, false)
        val long = FeaturedSeen(2, now - FeaturedRotation.HOURS_BEFORE_TIRED * hour, now, false)
        assertTrue(FeaturedRotation.tired(often, now)); assertTrue(FeaturedRotation.tired(long, now))
        assertFalse(FeaturedRotation.eligible(often, now)); assertFalse(FeaturedRotation.eligible(long, now))
        val got = FeaturedRotation.pick(pool, mapOf("n1" to often, "b1" to long), now, random = Random(2)).map { it.key }
        assertFalse(got.contains("n1")); assertFalse(got.contains("b1"))
    }

    @Test fun anOpenedTitleGetsThreeTimesThePatience() {
        val s = FeaturedSeen(FeaturedRotation.SHOWS_BEFORE_TIRED, now - hour, now, true)
        assertFalse(FeaturedRotation.tired(s, now))
        assertTrue(FeaturedRotation.tired(s.copy(shown = FeaturedRotation.SHOWS_BEFORE_TIRED * 3), now))
    }

    @Test fun aRestedTitleComesBackAndStartsAgain() {
        val old = FeaturedSeen(9, now - 30 * day, now - (FeaturedRotation.REST_DAYS + 1) * day, false)
        assertTrue(FeaturedRotation.eligible(old, now))
        val next = FeaturedRotation.markShown(mapOf("x" to old), listOf("x"), now)
        assertEquals(FeaturedSeen(1, now, now, false), next["x"])
    }

    @Test fun whenTooFewAreFreshTheLongestRestedFillTheGap() {
        val tiredSince = { n: Long -> FeaturedSeen(9, now - 3 * day, now - n * hour, false) }
        val seen = mapOf("a1" to tiredSince(1), "a2" to tiredSince(2), "n1" to tiredSince(3), "b1" to tiredSince(5))
        val got = FeaturedRotation.pick(pool, seen, now, random = Random(3)).map { it.key }
        assertEquals("k1", got.first())                       // the only fresh one
        assertEquals(3, got.size)
        assertEquals(listOf("b1", "n1"), got.drop(1))         // longest since shown
    }

    @Test fun showingAndOpeningAreCountedAndKept() {
        var seen = FeaturedRotation.markShown(emptyMap(), listOf("a", "b", "a"), now)
        assertEquals(1, seen["a"]!!.shown)
        seen = FeaturedRotation.markShown(seen, listOf("a"), now + hour)
        assertEquals(2, seen["a"]!!.shown); assertEquals(now, seen["a"]!!.firstAt); assertEquals(now + hour, seen["a"]!!.lastAt)
        assertEquals(seen, FeaturedRotation.markOpened(seen, "nope"))
        seen = FeaturedRotation.markOpened(seen, "a")
        assertTrue(seen["a"]!!.opened)
        assertEquals(seen, FeaturedRotation.parse(FeaturedRotation.toJson(seen)))
        assertEquals(emptyMap<String, FeaturedSeen>(), FeaturedRotation.parse("nope"))
    }

    @Test fun theHistoryStaysSmall() {
        var seen = emptyMap<String, FeaturedSeen>()
        repeat(FeaturedRotation.KEPT + 50) { i -> seen = FeaturedRotation.markShown(seen, listOf("k$i"), now + i) }
        assertEquals(FeaturedRotation.KEPT, seen.size)
        assertTrue(seen.containsKey("k${FeaturedRotation.KEPT + 49}"))
    }
}

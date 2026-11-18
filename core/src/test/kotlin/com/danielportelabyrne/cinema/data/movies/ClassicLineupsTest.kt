package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicLineupsTest {
    private val tgif = ClassicLineups.find("us-abc-tgif")!!

    @Test fun every_lineup_has_a_unique_id_a_summary_and_programmes() {
        assertEquals(ClassicLineups.all.size, ClassicLineups.all.map { it.id }.toSet().size)
        ClassicLineups.all.forEach {
            assertTrue(it.name, it.programmes.size >= 5)
            assertTrue(it.summary.contains(it.era))
            assertEquals(it.programmes.size, it.programmes.map { p -> ScheduleKnowledge.normalise(p.name) }.toSet().size)
        }
        assertNotNull(ClassicLineups.find("us-nbc-must-see"))
    }

    @Test fun matches_come_back_in_the_lineups_order_with_the_gaps_named() {
        val library = listOf("Boy Meets World", "Some Other Show", "Full House", "Dinosaurs")
        val m = ClassicLineups.match(tgif, library) { it }
        assertEquals(listOf("Full House", "Boy Meets World", "Dinosaurs"), m.found)
        assertEquals(listOf("Family Matters", "Step by Step", "Perfect Strangers", "Hangin' with Mr. Cooper", "Sabrina the Teenage Witch"), m.missing)
    }

    @Test fun case_punctuation_a_leading_the_and_aliases_still_match() {
        val library = listOf("FULL HOUSE", "Hanging With Mr Cooper")
        val m = ClassicLineups.match(tgif, library) { it }
        assertEquals(library, m.found)
    }

    @Test fun a_longer_or_different_title_is_never_a_match() {
        val m = ClassicLineups.match(tgif, listOf("Fuller House", "Full House Reunion Special")) { it }
        assertTrue(m.found.isEmpty())
        assertEquals(tgif.programmes.size, m.missing.size)
    }

    @Test fun an_empty_library_matches_nothing_and_misses_everything() {
        val m = ClassicLineups.match(tgif, emptyList<String>()) { it }
        assertTrue(m.found.isEmpty())
        assertEquals(tgif.programmes.map { it.name }, m.missing)
    }

    @Test fun duplicates_in_the_library_count_once() {
        val a = "Full House"; val b = String("Full House".toCharArray())
        val m = ClassicLineups.match(tgif, listOf(a, b)) { it }
        assertEquals(1, m.found.size)
    }
}

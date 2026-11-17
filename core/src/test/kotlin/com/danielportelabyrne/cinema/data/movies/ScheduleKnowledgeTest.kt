package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleKnowledgeTest {
    private val fixture = """{"v":1,"seasons":3,"shows":[["The Cosby Show",1984,1991,8],["A Different World",1987,1992,6],
        ["Cheers",1982,1992,11],["L.A. Law",1986,1993,8]],"adjacent":[[0,1,5]],"sameNight":[[0,1,5],[0,2,3]]}"""

    @Test fun names_are_keyed_without_accents_brackets_punctuation_and_a_leading_the() {
        // These exact results are what the build script's normalise gives, so the two always agree.
        assertEquals("cosby show", ScheduleKnowledge.normalise("The Cosby Show (TV series)"))
        assertEquals("l a law", ScheduleKnowledge.normalise("L.A. Law"))
        assertEquals("law and order", ScheduleKnowledge.normalise("Law & Order"))
        assertEquals("cafe americain", ScheduleKnowledge.normalise("Café Américain"))
        assertEquals("dallas", ScheduleKnowledge.normalise("Dallas [1978]"))
        assertEquals("24", ScheduleKnowledge.normalise("24"))
        assertEquals("the", ScheduleKnowledge.normalise("The"))
        assertEquals("", ScheduleKnowledge.normalise("(Untitled)"))
    }

    @Test fun a_show_is_found_however_it_is_written() {
        val data = ScheduleKnowledge.parse(fixture)!!
        assertEquals(4, data.showCount)
        listOf("The Cosby Show", "cosby show", "COSBY SHOW (1984)", "Cosby Show").forEach { assertNotNull(it, data.find(it)) }
        assertEquals(1984, data.find("Cosby Show")!!.firstYear)
        assertEquals(1991, data.find("Cosby Show")!!.lastYear)
        assertEquals(8, data.find("Cosby Show")!!.seasons)
        assertNull(data.find("Some Show Nobody Scheduled"))
        assertEquals(2, data.known(listOf("Cheers", "L.A. Law", "Nothing")))
    }

    @Test fun pairs_are_the_same_either_way_round_and_unknown_pairs_are_none() {
        val data = ScheduleKnowledge.parse(fixture)!!
        assertEquals(ScheduleKnowledge.Together(5, 5), data.together("The Cosby Show", "A Different World"))
        assertEquals(ScheduleKnowledge.Together(5, 5), data.together("A Different World", "The Cosby Show"))
        assertEquals(ScheduleKnowledge.Together(0, 3), data.together("Cosby Show", "Cheers"))
        assertTrue(data.together("Cheers", "L.A. Law").none)
        assertTrue(data.together("Cheers", "Not In The Data").none)
        assertTrue(data.together("Cheers", "cheers").none)
    }

    @Test fun anything_that_is_not_the_data_is_refused_rather_than_half_read() {
        assertNull(ScheduleKnowledge.parse("not json"))
        assertNull(ScheduleKnowledge.parse("""{"v":2,"shows":[],"adjacent":[],"sameNight":[]}"""))
        assertNull(ScheduleKnowledge.parse("""{"v":1,"shows":[["x"]],"adjacent":[],"sameNight":[]}"""))
    }

    @Test fun the_bundled_data_is_there_and_knows_television_history() {
        val data = ScheduleKnowledge.bundled()
        assertNotNull("the dataset resource is missing from core", data)
        data!!
        assertTrue("only ${data.showCount} shows", data.showCount > 3000)
        assertTrue("only ${data.seasons} seasons", data.seasons >= 90)
        // Pairings every TV history agrees on.
        assertTrue(data.together("The Cosby Show", "A Different World").adjacent >= 3)
        assertTrue(data.together("Happy Days", "Laverne & Shirley").adjacent >= 3)
        assertTrue(data.together("Murder, She Wrote", "60 Minutes").adjacent >= 3)
        assertTrue(data.together("Dallas", "Falcon Crest").adjacent >= 3)
        assertTrue(data.together("Cheers", "Wings").sameNight >= 1)
        // Years on air.
        val cheers = data.find("Cheers")!!
        assertTrue(cheers.firstYear <= 1982 && cheers.lastYear >= 1991)
        assertTrue(data.find("I Love Lucy")!!.firstYear <= 1952)
        // Shows that never ran together are not linked.
        assertTrue(data.together("I Love Lucy", "The Simpsons").none)
        // Read once, the same data comes back.
        assertEquals(data, ScheduleKnowledge.bundled())
        assertEquals(data, ScheduleKnowledge.bundledIfReady())
    }
}

package com.danielportelabyrne.cinema.data.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class IntroDbTest {
    private val now = 1_000_000_000_000L
    private val credits = """{"credits":[{"start_ms":2400000,"end_ms":null}]}"""

    @Test fun an_episode_is_looked_up_once_and_then_kept() {
        val asked = mutableListOf<String>()
        val db = IntroDb { url -> asked += url; 200 to credits }
        val first = db.creditsStart("tt5290382", 1, 2, null, now)
        assertEquals(2_400_000L, first.startMs)
        assertNotNull(first.storeJson)
        val second = db.creditsStart("tt5290382", 1, 2, first.storeJson, now + 1_000)
        assertEquals(2_400_000L, second.startMs)
        assertEquals(1, asked.size)
        assertEquals("https://api.theintrodb.org/v4/media?imdb_id=tt5290382&season=1&episode=2", asked.single())
    }

    @Test fun an_episode_it_does_not_know_is_kept_as_unknown() {
        val db = IntroDb { 404 to """{"detail":"not found"}""" }
        val result = db.creditsStart("tt5290382", 1, 3, null, now)
        assertNull(result.startMs)
        assertNotNull(result.storeJson)
        assertNull(CreditsStore.cachedStart(result.storeJson, "tt5290382|1|3", now)?.startMs)
    }

    @Test fun a_failed_or_rate_limited_lookup_saves_nothing_so_it_is_tried_again() {
        val limited = IntroDb { 429 to "" }.creditsStart("tt5290382", 1, 4, null, now)
        assertNull(limited.startMs)
        assertNull(limited.storeJson)
        val offline = IntroDb { null }.creditsStart("tt5290382", 1, 4, null, now)
        assertNull(offline.storeJson)
    }
}

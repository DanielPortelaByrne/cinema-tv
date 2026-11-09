package com.danielportelabyrne.cinema.data.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsKnowledgeTest {
    @Test fun theintrodb_credits_start_is_the_first_usable_segment() {
        val json = """{"type":"tv","season":1,"episode":2,"intro":[{"start_ms":0,"end_ms":60000}],
            "credits":[{"start_ms":2400000,"end_ms":null}],"recap":[],"preview":[]}"""
        assertEquals(2_400_000L, CreditsTimes.fromIntroDb(json))
    }

    @Test fun mindhunter_s1e1_as_theintrodb_answers_it_skips_the_zero_entry() {
        // The real answer for Mindhunter (tt5290382) S1E1: a 0/0 credits placeholder, then the credits from 57 min 18 s.
        val json = """{"tvdb_id":328708,"type":"tv","season":1,"episode":1,"recap":[{"start_ms":0,"end_ms":0}],
            "credits":[{"start_ms":0,"end_ms":0},{"start_ms":3438139,"end_ms":null}]}"""
        assertEquals(3_438_139L, CreditsTimes.fromIntroDb(json))
    }

    @Test fun theintrodb_segments_without_a_usable_start_are_skipped() {
        assertNull(CreditsTimes.fromIntroDb("""{"credits":[]}"""))
        assertNull(CreditsTimes.fromIntroDb("""{"credits":[{"start_ms":null,"end_ms":null}]}"""))
        // An end at or before the start is the API saying "confirmed absent", not a credits range.
        assertNull(CreditsTimes.fromIntroDb("""{"credits":[{"start_ms":0,"end_ms":0}]}"""))
        assertEquals(1_800_000L, CreditsTimes.fromIntroDb("""{"credits":[{"start_ms":0,"end_ms":0},{"start_ms":1800000,"end_ms":null}]}"""))
        assertNull(CreditsTimes.fromIntroDb("not json"))
    }

    @Test fun the_introdb_address_names_the_episode_by_imdb_season_and_episode() {
        assertEquals("https://api.theintrodb.org/v4/media?imdb_id=tt5290382&season=2&episode=5",
            CreditsTimes.introDbUrl("tt5290382", 2, 5))
    }

    @Test fun jellyfin_outro_start_is_read_from_ticks() {
        // 2,400 seconds is 24,000,000,000 ticks (100-nanosecond units).
        val json = """{"Items":[{"Id":"a","ItemId":"b","Type":"Intro","StartTicks":0,"EndTicks":600000000},
            {"Id":"c","ItemId":"b","Type":"Outro","StartTicks":24000000000,"EndTicks":26000000000}],"TotalRecordCount":2}"""
        assertEquals(2_400_000L, CreditsTimes.fromJellyfinSegments(json))
        assertNull(CreditsTimes.fromJellyfinSegments("""{"Items":[{"Type":"Intro","StartTicks":5}],"TotalRecordCount":1}"""))
        assertNull(CreditsTimes.fromJellyfinSegments("""{"Items":[],"TotalRecordCount":0}"""))
    }

    @Test fun plausible_starts_are_in_the_second_half_and_before_the_end() {
        val hour = 3_600_000L
        assertTrue(CreditsTimes.plausible(3_400_000L, hour))
        assertFalse(CreditsTimes.plausible(1_000_000L, hour))          // too early to be the credits
        assertFalse(CreditsTimes.plausible(hour - 1_000L, hour))       // too close to the end
        assertFalse(CreditsTimes.plausible(3_400_000L, 0L))            // no known length
    }

    @Test fun an_exit_counts_as_the_credits_only_when_it_is_credits_sized() {
        val hour = 3_600_000L
        assertEquals(200_000L, CreditsLearning.tailOf(hour, hour - 200_000L))  // left 3 min 20 s before the end
        assertNull(CreditsLearning.tailOf(hour, hour - 10_000L))               // at the very end
        assertNull(CreditsLearning.tailOf(hour, hour - 15 * 60_000L))          // mid-episode, not the credits
        assertNull(CreditsLearning.tailOf(0L, 0L))                             // no known length
    }

    @Test fun the_learned_tail_is_the_median_of_recent_exits() {
        assertNull(CreditsLearning.median(emptyList()))
        assertEquals(200_000L, CreditsLearning.median(listOf(180_000L, 200_000L, 260_000L)))
        assertEquals(190_000L, CreditsLearning.median(listOf(180_000L, 200_000L)))
    }

    @Test fun the_store_keeps_the_last_exits_per_series_and_reads_back_the_median() {
        var json: String? = null
        for (i in 1..12) json = CreditsStore.addTail(json, "tt5290382", i * 10_000L)
        // Only the last nine (40 s .. 120 s) are kept, and the median of those is 80 s.
        assertEquals(80_000L, CreditsStore.learnedTail(json, "tt5290382"))
        assertNull(CreditsStore.learnedTail(json, "tt0000000"))
        assertNull(CreditsStore.learnedTail("not json", "tt5290382"))
    }

    @Test fun a_saved_answer_is_used_until_it_goes_stale() {
        val now = 1_000_000_000_000L
        val json = CreditsStore.putStart(null, "tt1|1|2", 2_400_000L, now)
        assertEquals(CachedStart(2_400_000L), CreditsStore.cachedStart(json, "tt1|1|2", now + 1_000))
        assertNull(CreditsStore.cachedStart(json, "tt1|1|2", now + CreditsStore.FOUND_TTL_MS))
        val none = CreditsStore.putStart(null, "tt1|1|3", null, now)
        assertEquals(CachedStart(null), CreditsStore.cachedStart(none, "tt1|1|3", now + 1_000))
        assertNull(CreditsStore.cachedStart(none, "tt1|1|3", now + CreditsStore.MISSING_TTL_MS))
        assertNull(CreditsStore.cachedStart(json, "tt1|9|9", now))
    }
}

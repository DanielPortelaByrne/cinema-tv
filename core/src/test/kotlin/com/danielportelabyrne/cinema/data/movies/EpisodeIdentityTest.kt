package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.movies.EpisodeIdentity.Verdict.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeIdentityTest {
    @Test fun `the requested episode matches`() { assertEquals(MATCH, EpisodeIdentity.check(2, 6, "Victorious.S02E06.Freak.the.Freak.Out.1080p")) }
    @Test fun `a neighbouring episode is a mismatch, so part two is not played as part one`() {
        assertEquals(MISMATCH, EpisodeIdentity.check(2, 6, "Victorious.S02E07.Freak.the.Freak.Out.Part.2.1080p"))
    }
    @Test fun `another season is a mismatch`() { assertEquals(MISMATCH, EpisodeIdentity.check(2, 6, "Victorious S01E06")) }
    @Test fun `a combined release covers both parts`() {
        assertEquals(MATCH, EpisodeIdentity.check(2, 6, "Victorious S02E06E07 720p"))
        assertEquals(MATCH, EpisodeIdentity.check(2, 7, "Victorious S02E06-E07 720p"))
        assertEquals(MATCH, EpisodeIdentity.check(2, 7, "Victorious S02E06-07 720p"))
        assertEquals(MISMATCH, EpisodeIdentity.check(2, 8, "Victorious S02E06-07 720p"))
    }
    @Test fun `the 2x06 form is read`() {
        assertEquals(MATCH, EpisodeIdentity.check(2, 6, "Victorious 2x06 DVDRip"))
        assertEquals(MISMATCH, EpisodeIdentity.check(2, 6, "Victorious 2x07 DVDRip"))
    }
    @Test fun `a resolution is not an episode`() { assertEquals(UNKNOWN, EpisodeIdentity.check(2, 6, "Victorious Complete Series 1920x1080")) }
    @Test fun `a season pack or a name without numbers is unknown`() {
        assertEquals(UNKNOWN, EpisodeIdentity.check(2, 6, "Victorious.S02.COMPLETE.720p"))
        assertEquals(UNKNOWN, EpisodeIdentity.check(2, 6, null))
    }
    @Test fun `films and unknowns are accepted, only a plain mismatch is not`() {
        assertTrue(EpisodeIdentity.acceptable(null, "Anything S09E09"))
        assertTrue(EpisodeIdentity.acceptable(2 to 6, "Victorious S02 pack"))
        assertTrue(EpisodeIdentity.acceptable(2 to 6, "Victorious S02E06"))
        assertFalse(EpisodeIdentity.acceptable(2 to 6, "Victorious S02E07"))
    }
}

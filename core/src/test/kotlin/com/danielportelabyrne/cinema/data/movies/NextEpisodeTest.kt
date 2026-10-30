package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEpisodeTest {
    private fun ep(s: Int, e: Int, played: Boolean = false, resumeMs: Long = 0, at: Long? = null) = EpisodeWatch(s, e, played, resumeMs, at)

    @Test fun nothing_in_the_library_gives_no_button() {
        assertNull(NextEpisode.of(emptyList()))
    }

    @Test fun nothing_watched_yet_starts_at_the_first_episode() {
        val up = NextEpisode.of(listOf(ep(3, 1), ep(1, 2), ep(1, 1)))!!
        assertEquals(NextUp(1, 1, continuing = false), up)
    }

    @Test fun a_part_watched_episode_is_continued() {
        val up = NextEpisode.of(listOf(ep(3, 1, played = true, at = 100), ep(3, 2, resumeMs = 500_000, at = 300), ep(3, 3)))!!
        assertEquals(NextUp(3, 2, continuing = true), up)
    }

    @Test fun a_finished_episode_gives_the_next_one() {
        val up = NextEpisode.of(listOf(ep(3, 1, played = true, at = 100), ep(3, 2, played = true, at = 300), ep(3, 3)))!!
        assertEquals(NextUp(3, 3, continuing = false), up)
    }

    @Test fun the_next_one_can_be_the_next_season() {
        val up = NextEpisode.of(listOf(ep(1, 8, played = true, at = 300), ep(2, 1), ep(3, 1)))!!
        assertEquals(NextUp(2, 1, continuing = false), up)
    }

    @Test fun the_most_recent_one_decides_not_the_furthest() {
        val up = NextEpisode.of(listOf(ep(3, 5, played = true, at = 100), ep(3, 2, played = true, at = 900), ep(3, 3)))!!
        assertEquals(NextUp(3, 3, continuing = false), up)
    }

    @Test fun an_episode_finished_in_a_stream_counts_as_the_last_watched() {
        val streamed = listOf(EpisodeWatch(3, 2, played = true, resumeMs = 0, playedMs = 500))
        val library = listOf(ep(3, 1, played = true, at = 100), ep(3, 3), ep(3, 4))
        assertEquals(NextUp(3, 3, continuing = false), NextEpisode.of(library, streamed))
    }

    @Test fun a_part_watched_stream_is_not_continued_because_it_cannot_resume() {
        val streamed = listOf(EpisodeWatch(3, 2, played = false, resumeMs = 400_000, playedMs = 500))
        val library = listOf(ep(3, 1, played = true, at = 100), ep(3, 2), ep(3, 3))
        assertEquals(NextUp(3, 2, continuing = false), NextEpisode.of(library, streamed))
    }

    @Test fun a_finished_series_wraps_to_the_first_episode() {
        val up = NextEpisode.of(listOf(ep(1, 1), ep(1, 2, played = true, at = 300)))!!
        assertFalse(up.continuing)
        assertEquals(NextUp(1, 1, continuing = false), up)
        assertTrue(NextEpisode.of(listOf(ep(1, 1, played = true, at = 1)))!!.season == 1)
    }
}

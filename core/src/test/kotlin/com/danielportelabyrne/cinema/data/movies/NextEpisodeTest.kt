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

    @Test fun following_is_the_next_in_order_whatever_the_listing_order_and_null_at_the_end() {
        val listing = listOf(3 to 1, 1 to 2, 1 to 1, 2 to 1)
        assertEquals(1 to 2, NextEpisode.following(listing, 1, 1))
        assertEquals(2 to 1, NextEpisode.following(listing, 1, 2))
        assertEquals(3 to 1, NextEpisode.following(listing, 2, 1))
        assertNull(NextEpisode.following(listing, 3, 1))
    }

    private fun streamed(s: Int, e: Int, positionMs: Long, at: Long, durationMs: Long = 2_700_000L) =
        StreamedEpisode(s, e, positionMs = positionMs, durationMs = durationMs, atMs = at)

    @Test fun stream_nothing_played_gives_the_first_episode_in_order() {
        assertEquals(NextUp(1, 1, continuing = false), NextEpisode.forStream(listOf(2 to 1, 1 to 2, 1 to 1), emptyList()))
    }

    @Test fun stream_no_episodes_gives_no_button() {
        assertNull(NextEpisode.forStream(emptyList(), emptyList()))
    }

    @Test fun stream_part_watched_past_a_minute_is_continued() {
        val up = NextEpisode.forStream(listOf(1 to 1, 1 to 2, 1 to 3), listOf(streamed(1, 3, positionMs = 600_000, at = 50)))
        assertEquals(NextUp(1, 3, continuing = true), up)
    }

    @Test fun stream_opened_for_under_a_minute_is_not_continued() {
        val up = NextEpisode.forStream(listOf(1 to 1, 1 to 2, 1 to 3), listOf(streamed(1, 3, positionMs = 20_000, at = 50)))
        assertEquals(NextUp(1, 3, continuing = false), up)
    }

    @Test fun stream_finished_gives_the_next_episode() {
        val up = NextEpisode.forStream(listOf(1 to 3, 1 to 4, 1 to 5), listOf(streamed(1, 3, positionMs = 2_600_000, at = 50)))
        assertEquals(NextUp(1, 4, continuing = false), up)
    }

    @Test fun stream_finished_at_a_season_end_gives_the_next_season() {
        val eps = listOf(1 to 9, 1 to 10, 2 to 1)
        val up = NextEpisode.forStream(eps, listOf(streamed(1, 10, positionMs = 2_600_000, at = 50)))
        assertEquals(NextUp(2, 1, continuing = false), up)
    }

    @Test fun stream_most_recent_by_time_decides_not_episode_order() {
        val eps = listOf(1 to 2, 1 to 3, 1 to 5, 1 to 6)
        val up = NextEpisode.forStream(eps, listOf(
            streamed(1, 5, positionMs = 2_600_000, at = 10),   // older, finished
            streamed(1, 2, positionMs = 2_600_000, at = 90)))  // newer, finished
        assertEquals(NextUp(1, 3, continuing = false), up)
    }

    @Test fun stream_last_episode_finished_gives_nothing_next() {
        assertNull(NextEpisode.forStream(listOf(1 to 1, 1 to 2), listOf(streamed(1, 2, positionMs = 2_600_000, at = 50))))
    }
}

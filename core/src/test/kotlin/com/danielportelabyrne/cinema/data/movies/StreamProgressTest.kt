package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamProgressTest {
    @Test fun an_episode_is_watched_from_ninety_percent() {
        assertTrue(StreamedEpisode(1, 5, positionMs = 2_700_000, durationMs = 3_000_000, atMs = 1).played)
        assertFalse(StreamedEpisode(1, 5, positionMs = 2_000_000, durationMs = 3_000_000, atMs = 1).played)
        assertFalse(StreamedEpisode(1, 5, positionMs = 10, durationMs = 0, atMs = 1).played)
    }

    @Test fun progress_keeps_the_furthest_point_and_stays_per_series() {
        var json = StreamProgress.record(null, "tt2649356", 1, 5, positionMs = 500_000, durationMs = 2_700_000, atMs = 10)
        json = StreamProgress.record(json, "tt2649356", 1, 5, positionMs = 100_000, durationMs = 0, atMs = 20)
        json = StreamProgress.record(json, "tt9999999", 1, 5, positionMs = 2_700_000, durationMs = 2_700_000, atMs = 30)
        val saved = StreamProgress.forSeries(json, "tt2649356")
        assertEquals(listOf(StreamedEpisode(1, 5, positionMs = 500_000, durationMs = 2_700_000, atMs = 20)), saved)
    }

    @Test fun unreadable_saved_progress_starts_afresh() {
        assertEquals(emptyList<StreamedEpisode>(), StreamProgress.forSeries("not json", "tt2649356"))
        assertEquals(1, StreamProgress.forSeries(StreamProgress.record("not json", "tt2649356", 2, 3, 1, 1, 1), "tt2649356").size)
    }
}

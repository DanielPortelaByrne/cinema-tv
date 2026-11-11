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
        assertEquals(listOf(StreamedEpisode(1, 5, positionMs = 500_000, durationMs = 2_700_000, atMs = 20, resumeMs = 100_000)), saved)
    }

    @Test fun unreadable_saved_progress_starts_afresh() {
        assertEquals(emptyList<StreamedEpisode>(), StreamProgress.forSeries("not json", "tt2649356"))
        assertEquals(1, StreamProgress.forSeries(StreamProgress.record("not json", "tt2649356", 2, 3, 1, 1, 1), "tt2649356").size)
    }

    private val min = 60_000L
    private fun last(json: String) = StreamProgress.forSeries(json, "tt1").single()

    @Test fun seeking_back_moves_resume_but_keeps_furthest() {
        var json = StreamProgress.record(null, "tt1", 1, 1, 20 * min, 45 * min, 1)
        json = StreamProgress.record(json, "tt1", 1, 1, 11 * min, 45 * min, 2)
        assertEquals(20 * min, last(json).positionMs)
        assertEquals(11 * min, last(json).resumeMs)
    }

    @Test fun forward_playback_advances_both() {
        val json = StreamProgress.record(StreamProgress.record(null, "tt1", 1, 1, 5 * min, 45 * min, 1), "tt1", 1, 1, 6 * min, 45 * min, 2)
        assertEquals(6 * min, last(json).positionMs)
        assertEquals(6 * min, last(json).resumeMs)
    }

    @Test fun records_without_resume_field_stay_readable() {
        val old = """{"tt1|1|1":{"p":1200000,"d":2700000,"t":5}}"""
        assertEquals(1_200_000L, last(old).resumeMs)
        assertEquals(1_200_000L, last(old).positionMs)
        assertFalse(last(old).hasResume)
        assertTrue(last(StreamProgress.record(old, "tt1", 1, 1, 5_000, 2_700_000, 6)).hasResume)
    }

    @Test fun a_cleared_resume_point_is_told_apart_from_an_old_record_without_one() {
        val old = """{"tt1|1|1":{"p":1200000,"d":2700000,"t":5}}"""
        val cleared = """{"tt1|1|1":{"p":1200000,"r":0,"d":2700000,"t":5}}"""
        assertFalse(last(old).hasResume)
        assertEquals(1_200_000L, last(old).resumeMs)
        assertTrue(last(cleared).hasResume)
        assertEquals(0L, last(cleared).resumeMs)
        // Continue watching: the old record resumes from its furthest point; the cleared one starts over.
        val inputs = { json: String -> ContinueInputs("", "2026-11-11", streamProgress = json) }
        assertEquals(ContinueAction.RESUME, ContinueEngine.select(inputs(old), { null }).single().action)
        assertEquals(1_200_000L, ContinueEngine.select(inputs(old), { null }).single().resumeMs)
        assertEquals(ContinueAction.START_OVER, ContinueEngine.select(inputs(cleared), { null }).single().action)
    }

    @Test fun a_finished_episode_watched_again_and_stopped_is_not_finished_by_its_furthest_point() {
        var json = StreamProgress.record(null, "tt1", 1, 3, 44 * min, 45 * min, 1)   // finished
        json = StreamProgress.record(json, "tt1", 1, 3, 6 * min, 45 * min, 2)        // watched again, stopped at 6 min
        val ep = last(json)
        assertTrue(ep.played)              // the card still shows it was watched
        assertFalse(ep.finishedLast)       // but the latest viewing did not finish
        // So nothing moves on to E4: the series page carries on with E3, history is not told it finished.
        assertEquals(NextUp(1, 3, continuing = true), NextEpisode.forStream(listOf(1 to 3, 1 to 4), StreamProgress.forSeries(json, "tt1")))
        assertFalse(PlayHistory.fromStreamProgress(json).single().completed)
        // An old record with no resume point can only go by its furthest point.
        assertTrue(last("""{"tt1|1|3":{"p":2600000,"d":2700000,"t":5}}""").finishedLast)
    }

    @Test fun completion_follows_furthest_and_unknown_duration_never_completes() {
        var json = StreamProgress.record(null, "tt1", 1, 1, 41 * min, 45 * min, 1)   // past 90 %
        json = StreamProgress.record(json, "tt1", 1, 1, 10 * min, 45 * min, 2)
        assertTrue(last(json).played)
        assertFalse(last(StreamProgress.record(null, "tt1", 1, 1, 41 * min, 0, 1)).played)
    }
}

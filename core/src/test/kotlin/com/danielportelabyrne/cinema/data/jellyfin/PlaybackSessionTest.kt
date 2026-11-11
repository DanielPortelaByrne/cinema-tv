package com.danielportelabyrne.cinema.data.jellyfin

import org.junit.Assert.*
import org.junit.Test

class PlaybackSessionTest {
    @Test fun reports_only_between_start_and_stop() {
        val session = JellyfinPlaybackSession { "first" }
        assertFalse(session.accept("Progress"))
        assertFalse(session.accept("Stopped"))
        assertTrue(session.accept("Playing"))
        assertFalse(session.accept("Playing"))
        assertTrue(session.accept("Progress"))
        assertTrue(session.accept("Stopped"))
        assertFalse(session.accept("Stopped"))
        assertFalse(session.accept("Progress"))
    }

    @Test fun returning_to_a_stopped_player_opens_a_new_session() {
        var count = 0
        val session = JellyfinPlaybackSession { (++count).toString() }
        session.accept("Playing")
        val original = session.id
        session.accept("Stopped")
        assertTrue(session.stopped)
        assertTrue(session.accept("Playing"))
        assertFalse(session.stopped)
        assertNotEquals(original, session.id)
        assertTrue(session.accept("Progress"))
    }

    @Test fun server_positions_override_old_local_positions_including_short_positions_and_restarts() {
        assertEquals(20_000L, JellyfinApi.resumePosition(20_000L, 150_000L))
        assertEquals(0L, JellyfinApi.resumePosition(0L, 150_000L))
        assertEquals(0L, JellyfinApi.resumePosition(-1L, 150_000L))
        assertEquals(150_000L, JellyfinApi.resumePosition(null, 150_000L))
        assertEquals(0L, JellyfinApi.resumePosition(null, 20_000L))
    }

    @Test fun a_fresher_local_resume_point_is_not_overridden_when_the_full_item_arrives() {
        val settled = JellyfinReport("http://192.168.1.20:8096", "i1", "s1", startMs = 1_800_000L, settledAtMs = 5_000L)
        // The server last saw it earlier, at 2 000: this TV's later position stands, even if the server has it further on.
        assertEquals(1_800_000L, settled.freshStart(played = false, resumeMs = 2_400_000L, playedAtMs = 2_000L))
        // Watched again here after the server marked it played: the rewatch's place stands too.
        assertEquals(1_800_000L, settled.freshStart(played = true, resumeMs = 0L, playedAtMs = 2_000L))
        // No date from the server: the settled point, which was chosen knowing the server's record, stands.
        assertEquals(1_800_000L, settled.freshStart(played = false, resumeMs = 600_000L, playedAtMs = null))
    }

    @Test fun a_fresher_server_record_wins_and_its_completion_stays_authoritative() {
        val settled = JellyfinReport("http://192.168.1.20:8096", "i1", "s1", startMs = 1_800_000L, settledAtMs = 5_000L)
        assertEquals(2_400_000L, settled.freshStart(played = false, resumeMs = 2_400_000L, playedAtMs = 9_000L))
        assertEquals(0L, settled.freshStart(played = true, resumeMs = 2_400_000L, playedAtMs = 9_000L))
        assertEquals(700_000L, settled.freshStart(played = false, resumeMs = 700_000L, playedAtMs = 5_000L))   // a tie: the server
    }

    @Test fun without_a_settled_point_the_full_item_decides_as_before() {
        val plain = JellyfinReport("http://192.168.1.20:8096", "i1", "s1", startMs = 300_000L)
        assertEquals(900_000L, plain.freshStart(played = false, resumeMs = 900_000L, playedAtMs = 1L))
        assertEquals(0L, plain.freshStart(played = true, resumeMs = 900_000L, playedAtMs = null))
        val back = JellyfinReport.fromJson(JellyfinReport("b", "i", "s", 10L, 77L).toJson())!!
        assertEquals(77L, back.settledAtMs)
        assertEquals(0L, JellyfinReport.fromJson(plain.toJson())!!.settledAtMs)
    }

    @Test fun paused_reports_preserve_pause_and_position() {
        val body = org.json.JSONObject(JellyfinApi.playbackBody("item", "source", 20_000L, true, "session"))
        assertTrue(body.getBoolean("IsPaused"))
        assertEquals(200_000_000L, body.getLong("PositionTicks"))
        assertEquals("session", body.getString("PlaySessionId"))
    }
}

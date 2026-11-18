package com.danielportelabyrne.cinema.data.jellyfin

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class EpisodeAutoplayTest {
    private fun episode(number: Int, season: Int = 1, id: String = "$season:$number") =
        JellyfinItem(id, "Episode $number", null, "Episode", id, season = season, episode = number, seriesId = "series")

    @Test fun countdown_begins_at_the_end_and_advances_once_after_three_seconds() {
        val state = EpisodeAutoplay()
        state.ready("next", 0)
        assertNull(state.remainingMs(10_000))
        assertNull(state.takeNext(10_000))
        state.end(10_000)
        assertEquals(3_000L, state.remainingMs(10_000))
        assertEquals(.5f, state.fraction(11_500), .001f)
        assertNull(state.takeNext(12_999))
        assertEquals("next", state.takeNext(13_000))
        assertNull(state.takeNext(15_000))
        assertNull(state.takeNext(15_000, playNow = true))
    }

    @Test fun estimated_credits_offer_next_but_never_advance_until_the_real_end() {
        val state = EpisodeAutoplay()
        state.ready("next", 0); state.end(1_000, auto = false)
        assertNull(state.remainingMs(1_000))
        assertNull(state.takeNext(500_000))
        state.ready("next", 600_000) // a late lookup must not start the countdown either
        assertNull(state.remainingMs(600_000))
        state.end(700_000) // the player really ended
        assertEquals(3_000L, state.remainingMs(700_000))
        assertEquals("next", state.takeNext(703_000))
    }

    @Test fun explicit_next_and_dismiss_work_during_estimated_credits() {
        val explicit = EpisodeAutoplay()
        explicit.ready("next", 0); explicit.end(1_000, auto = false)
        assertEquals("next", explicit.takeNext(2_000, playNow = true))
        val dismissed = EpisodeAutoplay()
        dismissed.ready("next", 0); dismissed.end(1_000, auto = false); dismissed.cancel()
        dismissed.end(900_000)
        assertNull(dismissed.takeNext(1_000_000))
    }

    @Test fun duplicate_end_and_lookup_callbacks_do_not_extend_the_countdown() {
        val state = EpisodeAutoplay()
        state.ready("next", 0); state.end(100)
        state.end(2_000); state.ready("next", 2_500)
        assertEquals("next", state.takeNext(3_100))
    }

    @Test fun late_metadata_still_gets_a_full_countdown() {
        val state = EpisodeAutoplay()
        state.end(0)
        assertNull(state.takeNext(10_000))
        state.ready("next", 10_000)
        assertNull(state.takeNext(12_999))
        assertEquals("next", state.takeNext(13_000))
    }

    @Test fun cancelling_before_a_late_lookup_cannot_start_hidden_playback() {
        val state = EpisodeAutoplay()
        state.end(0); state.cancel(); state.ready("next", 2_000)
        assertNull(state.remainingMs(10_000))
        assertNull(state.takeNext(10_000))
    }

    @Test fun cancelling_on_the_last_frame_prevents_autoplay_but_explicit_play_still_works() {
        val state = EpisodeAutoplay()
        state.ready("next", 0); state.end(0); state.cancel()
        assertNull(state.takeNext(3_000))
        assertEquals("next", state.takeNext(3_000, playNow = true))
    }

    @Test fun play_now_is_immediate_but_cannot_skip_a_still_playing_episode() {
        val state = EpisodeAutoplay()
        state.ready("next", 0)
        assertNull(state.takeNext(0, playNow = true))
        state.end(0)
        assertEquals("next", state.takeNext(50, playNow = true))
    }

    @Test fun sequencing_uses_the_current_episode_even_when_rewatching() {
        val current = episode(2)
        val next = episode(3).copy(played = true)
        assertEquals(next, JellyfinApi.followingEpisode(current, listOf(episode(4), next, current, episode(1))))
    }

    @Test fun sequencing_does_not_skip_a_missing_episode_or_switch_to_another_series() {
        val current = episode(2)
        assertNull(JellyfinApi.followingEpisode(current, listOf(current, episode(4))))
        assertNull(JellyfinApi.followingEpisode(current, listOf(current, episode(3).copy(missing = true), episode(4))))
        assertNull(JellyfinApi.followingEpisode(current, listOf(episode(3).copy(seriesId = "other"))))
    }

    @Test fun sequencing_crosses_into_the_next_season_but_never_skips_a_season() {
        val current = episode(10)
        assertEquals("2:1", JellyfinApi.followingEpisode(current, listOf(current, episode(1, 2), episode(1, 0)))?.id)
        assertNull(JellyfinApi.followingEpisode(current, listOf(current, episode(2, 2))))
        assertNull(JellyfinApi.followingEpisode(current, listOf(current, episode(1, 3))))
    }

    @Test fun multi_episode_files_advance_from_the_last_episode_in_the_file() {
        val current = episode(2).copy(episodeEnd = 3)
        assertEquals("1:4", JellyfinApi.followingEpisode(current, listOf(current, episode(3), episode(4)))?.id)
    }

    @Test fun specials_do_not_lead_into_regular_seasons_and_movies_never_autoplay() {
        assertNull(JellyfinApi.followingEpisode(episode(1, 0), listOf(episode(1, 1))))
        assertNull(JellyfinApi.followingEpisode(episode(1).copy(type = "Movie"), listOf(episode(2))))
        assertNull(JellyfinApi.followingEpisode(episode(1), listOf(episode(1))))
    }

    @Test fun parser_preserves_episode_sequence_and_virtual_availability() {
        val item = JellyfinApi.parseItem(JSONObject("""{"Id":"one","Name":"Two parts","Type":"Episode","SeriesId":"series","ParentIndexNumber":1,"IndexNumber":2,"IndexNumberEnd":3,"LocationType":"Virtual"}"""))!!
        assertEquals("series", item.seriesId); assertEquals(3, item.episodeEnd); assertTrue(item.missing)
    }

    @Test fun every_autoplay_episode_has_a_new_reporting_session_after_the_previous_stop() {
        var count = 0
        val session = JellyfinPlaybackSession { (++count).toString() }
        assertTrue(session.accept("Playing"))
        val first = session.id
        assertTrue(session.accept("Stopped"))
        assertFalse(session.accept("Stopped"))
        assertTrue(session.accept("Playing"))
        assertNotEquals(first, session.id)
        assertTrue(session.accept("Progress"))
    }
}

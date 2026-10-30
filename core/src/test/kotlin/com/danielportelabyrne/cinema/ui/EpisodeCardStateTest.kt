package com.danielportelabyrne.cinema.ui

import com.danielportelabyrne.cinema.data.movies.Availability
import com.danielportelabyrne.cinema.data.movies.EpisodeState
import com.danielportelabyrne.cinema.data.movies.QueueItem
import com.danielportelabyrne.cinema.data.movies.QueueSource
import org.junit.Assert.*
import org.junit.Test

class EpisodeCardStateTest {
    private val today = "2026-10-22"

    private fun state(inLibrary: Boolean = false, sonarr: EpisodeState? = null, season: Availability = Availability.NONE,
                      airDate: String? = "2025-03-30", canRequest: Boolean = true, canStream: Boolean = true, download: QueueItem? = null) =
        episodeCardState(inLibrary, sonarr, season, airDate, today, canRequest, canStream, download)

    private fun item(detail: String?, size: Long = 100, left: Long = 58, tmdb: Long = 7) =
        QueueItem(QueueSource.SONARR, 1, "MobLand", detail, tmdb, "downloading", size, left, null, null)

    @Test fun an_episode_in_the_library_has_a_saffron_badge_and_an_unfocusable_check() {
        val look = state(inLibrary = true)
        assertEquals(CardState.LIBRARY, look.state); assertEquals("IN LIBRARY", look.badge); assertTrue(look.badgeAccent)
        assertTrue(look.playShown); assertTrue(look.downloadShown); assertFalse(look.downloadFocusable)
    }

    @Test fun nothing_asked_for_offers_a_download() {
        val look = state()
        assertEquals(CardState.REQUEST, look.state); assertNull(look.badge); assertTrue(look.downloadFocusable)
    }

    @Test fun without_a_stream_service_an_episode_not_in_the_library_has_no_play() {
        val asked = state(canStream = false)
        assertEquals(CardState.REQUEST, asked.state); assertFalse(asked.playShown); assertTrue(asked.downloadFocusable)
        assertFalse(state(canStream = false, sonarr = EpisodeState.REQUESTED).playShown)
        assertFalse(state(canStream = false, download = item("S2 E2 · Song 2")).playShown)
        // Play on an episode in the library plays from Jellyfin, so it stays.
        assertTrue(state(canStream = false, inLibrary = true).playShown)
    }

    @Test fun no_way_to_request_leaves_play_alone() {
        val look = state(canRequest = false)
        assertEquals(CardState.STREAM_ONLY, look.state); assertTrue(look.playShown); assertFalse(look.downloadShown)
    }

    @Test fun the_episode_itself_wins_over_its_season() {
        assertEquals(CardState.REQUESTED, state(sonarr = EpisodeState.REQUESTED).state)
        assertEquals(CardState.LIBRARY, state(sonarr = EpisodeState.DOWNLOADED, season = Availability.REQUESTED).state)
        val requested = state(season = Availability.PROCESSING)
        assertEquals(CardState.REQUESTED, requested.state); assertEquals("REQUESTED", requested.badge); assertFalse(requested.badgeAccent)
        assertTrue(requested.downloadFocusable)
    }

    @Test fun a_queued_download_shows_its_percentage() {
        val look = state(sonarr = EpisodeState.REQUESTED, download = item("S2 E2 · Song 2"))
        assertEquals(CardState.DOWNLOADING, look.state); assertEquals("DOWNLOADING 42%", look.badge); assertEquals(0.42f, look.progress!!, 0.001f)
        val unknown = state(season = Availability.DOWNLOADING)
        assertEquals(CardState.DOWNLOADING, unknown.state); assertEquals("DOWNLOADING", unknown.badge); assertNull(unknown.progress)
    }

    @Test fun a_future_episode_not_asked_for_can_be_requested_ahead_but_not_played() {
        val look = state(airDate = "2026-12-21")
        assertEquals(CardState.UNAIRED, look.state); assertTrue(look.unaired); assertNull(look.badge)
        assertFalse(look.playShown); assertTrue(look.downloadShown); assertTrue(look.downloadFocusable)
        assertEquals(CardState.REQUEST, state(airDate = today).state)   // airs today: it is out
        assertEquals(CardState.LIBRARY, state(inLibrary = true, airDate = "2027-01-01").state)
        // Without a way to request it, nothing on it can be pressed (the card itself takes focus).
        val stuck = state(airDate = "2026-12-21", canRequest = false)
        assertFalse(stuck.downloadShown); assertFalse(stuck.playShown)
    }

    @Test fun a_future_episode_already_asked_for_says_so_and_keeps_its_air_date() {
        val look = state(airDate = "2026-12-21", sonarr = EpisodeState.REQUESTED)
        assertEquals(CardState.UNAIRED_REQUESTED, look.state); assertTrue(look.unaired)
        assertEquals("REQUESTED", look.badge); assertFalse(look.playShown); assertTrue(look.downloadFocusable)   // pressing offers to cancel
        assertEquals("Airs 21 Dec 2026", EpisodeCards.airsLabel("2026-12-21"))
    }

    @Test fun an_aired_episode_asked_for_is_plainly_requested() {
        val look = state(airDate = "2025-03-30", sonarr = EpisodeState.REQUESTED)
        assertEquals(CardState.REQUESTED, look.state); assertFalse(look.unaired); assertTrue(look.playShown)
    }

    @Test fun no_air_date_is_airs_tba_and_can_be_asked_for() {
        val look = state(airDate = null)
        assertEquals(CardState.UNAIRED, look.state); assertTrue(look.downloadFocusable)
        assertEquals("Airs TBA", EpisodeCards.airsLabel(null))
        assertEquals("Get S2 E5 when it airs", EpisodeCards.preRequestTip(2, 5))
        assertEquals("S2 E5 will download when it airs on 21 Dec", EpisodeCards.preRequestDone(2, 5, "2026-12-21"))
        assertEquals("S2 E5 will download when it airs", EpisodeCards.preRequestDone(2, 5, null))
    }

    @Test fun dates_read_as_day_month_year() {
        assertEquals("21 Dec 2026", EpisodeCards.formatDate("2026-12-21"))
        assertEquals("6 Apr 2025", EpisodeCards.formatDate("2025-04-06"))
        assertNull(EpisodeCards.formatDate("soon")); assertNull(EpisodeCards.formatDate(null)); assertNull(EpisodeCards.formatDate("2025-13-01"))
    }

    @Test fun resume_point_gives_the_bar_and_the_minutes_left() {
        val (fraction, left) = EpisodeCards.resume(20 * 60_000L, 52)!!
        assertEquals(20f / 52f, fraction, 0.001f); assertEquals(32, left)
        assertNull(EpisodeCards.resume(0, 52)); assertNull(EpisodeCards.resume(60_000, null)); assertNull(EpisodeCards.resume(60 * 60_000L, 52))
    }

    @Test fun meta_line_leaves_out_what_is_unknown() {
        assertEquals(listOf("52 min · 32 min left", "6 Apr 2025"), EpisodeCards.metaParts(52, 32, "2025-04-06"))
        assertEquals(listOf("21 Dec 2026"), EpisodeCards.metaParts(null, null, "2026-12-21"))
        assertTrue(EpisodeCards.metaParts(null, null, null).isEmpty())
    }

    @Test fun downloads_match_on_season_and_episode_only() {
        val items = listOf(item("Season 2 · 6 episodes"), item("S2 E12 · Later"), item("S2 E1 · First"), item("S2 E1", tmdb = 9))
        assertEquals("S2 E1 · First", EpisodeCards.download(items, 7, 2, 1)?.detail)
        assertEquals("S2 E12 · Later", EpisodeCards.download(items, 7, 2, 12)?.detail)
        assertNull(EpisodeCards.download(items, 7, 2, 2)); assertNull(EpisodeCards.download(items, null, 2, 1))
    }

    @Test fun season_bar_counts_and_action() {
        assertEquals(listOf("8 episodes" to true, "All in your library" to false), EpisodeCards.seasonInfo(8, 8, 0))
        assertEquals(listOf("10 episodes" to true, "1 in library" to false, "1 downloading" to false), EpisodeCards.seasonInfo(10, 1, 1))
        assertEquals(listOf("1 episode" to true), EpisodeCards.seasonInfo(1, 0, 0))
        assertEquals("Download Season 2", EpisodeCards.seasonAction(Availability.NONE, "Season 2", false))
        assertEquals("Requested ✓", EpisodeCards.seasonAction(Availability.PROCESSING, "Season 2", false))
        assertEquals("In your library", EpisodeCards.seasonAction(Availability.AVAILABLE, "Season 2", false))
        assertNull(EpisodeCards.seasonAction(Availability.AVAILABLE, "Season 1", true))
    }
}

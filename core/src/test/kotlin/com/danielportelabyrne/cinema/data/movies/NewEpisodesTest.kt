package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewEpisodesTest {
    private val today = "2026-11-18"
    private fun cat(vararg e: CatalogueEpisode) = EpisodeCatalogue(e.toList(), complete = true)
    private fun ep(s: Int, n: Int, date: String?, last: Int = n) = CatalogueEpisode(s, n, airDate = date, lastEpisode = last)

    @Test fun an_episode_from_this_week_is_new() {
        assertTrue(NewEpisodes.has(cat(ep(1, 1, "2026-11-15")), today, emptySet()))
    }

    @Test fun the_day_it_airs_counts_but_tomorrow_does_not() {
        assertTrue(NewEpisodes.has(cat(ep(1, 1, "2026-11-18")), today, emptySet()))
        assertFalse(NewEpisodes.has(cat(ep(1, 1, "2026-11-19")), today, emptySet()))
    }

    @Test fun the_window_is_seven_days() {
        assertTrue(NewEpisodes.has(cat(ep(1, 1, "2026-11-12")), today, emptySet()))
        assertFalse(NewEpisodes.has(cat(ep(1, 1, "2026-11-11")), today, emptySet()))
    }

    @Test fun unknown_or_unreadable_dates_and_no_catalogue_are_quietly_not_new() {
        assertFalse(NewEpisodes.has(cat(ep(1, 1, null), ep(1, 2, "soon")), today, emptySet()))
        assertFalse(NewEpisodes.has(null, today, emptySet()))
        assertFalse(NewEpisodes.has(cat(ep(1, 1, "2026-11-15")), "garbage", emptySet()))
    }

    @Test fun a_watched_episode_clears_it_but_one_unwatched_keeps_one_badge() {
        val c = cat(ep(2, 1, "2026-11-16"), ep(2, 2, "2026-11-17"))
        assertTrue(NewEpisodes.has(c, today, setOf(2 to 1)))
        assertFalse(NewEpisodes.has(c, today, setOf(2 to 1, 2 to 2)))
    }

    @Test fun a_double_episode_file_counts_as_watched_when_any_part_is() {
        assertFalse(NewEpisodes.has(cat(ep(2, 1, "2026-11-16", last = 2)), today, setOf(2 to 2)))
    }

    @Test fun an_old_episode_backfilled_into_the_library_is_not_new() {
        assertFalse(NewEpisodes.has(cat(ep(1, 1, "2019-05-01")), today, emptySet()))
    }

    @Test fun specials_are_ignored() {
        assertFalse(NewEpisodes.has(cat(ep(0, 1, "2026-11-16")), today, emptySet()))
    }

    @Test fun history_is_matched_by_series_id_and_only_finished_episodes() {
        val h = listOf(
            PlayedEntry(Kind.SHOW, seriesTmdbId = "10", season = 1, episode = 1, completed = true),
            PlayedEntry(Kind.SHOW, seriesTmdbId = "10", season = 1, episode = 2, completed = false),
            PlayedEntry(Kind.SHOW, seriesTmdbId = "99", season = 1, episode = 3, completed = true),
            PlayedEntry(Kind.MOVIE, tmdbId = "10", completed = true)
        )
        assertEquals(setOf(1 to 1), NewEpisodes.watchedOf(h, "10", null, null))
    }
}

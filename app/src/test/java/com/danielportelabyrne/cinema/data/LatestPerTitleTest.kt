package com.danielportelabyrne.cinema.data

import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.PlayedEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class LatestPerTitleTest {
    private data class Entry(val title: String, val subtitle: String, val lastMs: Long)
    private fun latest(vararg e: Entry) = latestPerTitle(e.toList(), { it.title }, { it.lastMs }).map { it.subtitle }

    @Test fun a_series_shows_only_its_most_recent_episode() {
        assertEquals(listOf("S1 E3"), latest(Entry("The Show", "S1 E2", 100), Entry("The Show", "S1 E3", 300), Entry("The Show", "S1 E1", 200)))
    }

    @Test fun different_titles_stay_and_come_newest_first() {
        assertEquals(listOf("film", "S2 E1", "S1 E4"),
            latest(Entry("Other", "S1 E4", 100), Entry("The Show", "S2 E1", 200), Entry("A Film", "film", 300), Entry("The Show", "S1 E9", 50)))
    }

    @Test fun a_stream_and_the_library_copy_of_a_series_count_once() {
        assertEquals(listOf("stream"), latest(Entry("The Show ", "library", 100), Entry("the show", "stream", 200)))
    }

    @Test fun a_stream_titled_with_its_episode_counts_as_its_series() {
        // As seen on the TV: the library entry is "Ruby Gloom", the stream's saved title is "Ruby Gloom · S1 E4".
        assertEquals(listOf("stream"), latest(Entry("Ruby Gloom", "library", 100), Entry("Ruby Gloom · S1 E4", "stream", 200)))
        assertEquals(listOf("newer"), latest(Entry("Ruby Gloom · S1 E3", "older", 100), Entry("Ruby Gloom S01E04 - Missing Buns", "newer", 200)))
    }

    @Test fun only_a_trailing_episode_label_is_removed() {
        assertEquals(listOf("a", "b"), latest(Entry("Se7en", "a", 200), Entry("Season Two", "b", 100)))
        assertEquals("the show", seriesKey("The Show · S2 E10"))
        assertEquals("se7en", seriesKey("Se7en"))
    }

    @Test fun nothing_in_nothing_out() {
        assertEquals(emptyList<String>(), latest())
    }

    // --- By identity (what the TV's Continue watching provider uses)

    private data class Row(val title: String, val subtitle: String, val lastMs: Long, val id: PlayedEntry?)
    private fun byId(vararg r: Row) = latestPerIdentity(r.toList(), { it.id }, { it.title }, { it.lastMs }).map { it.subtitle }
    private fun ep(seriesImdb: String? = null, seriesJf: String? = null, s: Int = 1, e: Int = 1) =
        PlayedEntry(Kind.SHOW, seriesImdbId = seriesImdb, seriesJellyfinId = seriesJf, season = s, episode = e)

    @Test fun two_series_with_the_same_name_stay_apart() {
        assertEquals(listOf("new", "old"), byId(Row("The Office", "old", 100, ep("tt1")), Row("The Office", "new", 200, ep("tt2"))))
    }

    @Test fun a_stream_and_the_library_copy_join_by_the_series_ids_not_the_name() {
        val library = ep(seriesImdb = "tt5", seriesJf = "abc", s = 1, e = 3)
        val stream = ep(seriesImdb = "tt5", s = 1, e = 4)
        assertEquals(listOf("stream"), byId(Row("Ruby Gloom", "library", 100, library), Row("Ruby Gloom (2006) · S1 E4", "stream", 200, stream)))
    }

    @Test fun a_film_joins_its_other_copy_by_any_shared_id() {
        val a = PlayedEntry(Kind.MOVIE, tmdbId = "9", jellyfinId = "j9")
        val b = PlayedEntry(Kind.MOVIE, tmdbId = "9", imdbId = "tt9")
        assertEquals(listOf("b"), byId(Row("Heat", "a", 100, a), Row("Heat (1995)", "b", 200, b)))
        assertEquals(listOf("show", "film"), byId(Row("X", "film", 100, a), Row("X", "show", 200, ep("tt9"))))   // a film is never a series
    }

    @Test fun rows_without_ids_fall_back_to_their_names_among_themselves_only() {
        assertEquals(listOf("b"), byId(Row("Clip", "a", 100, null), Row("Clip · S1 E2", "b", 200, null)))
        // A row with ids is never joined to one without by name.
        assertEquals(listOf("named", "id"), byId(Row("Clip", "id", 100, ep("tt1")), Row("Clip", "named", 200, null)))
    }
}

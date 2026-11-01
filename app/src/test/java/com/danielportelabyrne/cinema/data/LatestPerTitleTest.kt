package com.danielportelabyrne.cinema.data

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
}

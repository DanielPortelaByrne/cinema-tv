package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.platform.MemoryPlatform
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelGuideTest {
    private fun title(name: String, kind: Kind) =
        Title(name, kind, name, null, null, null, emptyList(), null, null, null, null, null, null)

    private fun slot(name: String, kind: Kind = Kind.MOVIE, pending: PlaylistEpisode? = null) =
        PlaylistSlot(title(name, kind), pending = pending)

    @Test fun channels_are_numbered_in_list_order_from_one() {
        val list = listOf(BroadcastPlaylist("A"), BroadcastPlaylist("B"), BroadcastPlaylist("C"))
        assertEquals(listOf(1, 2, 3), ChannelGuide.summaries(list).map { it.number })
        assertEquals(listOf("A", "B", "C"), ChannelGuide.summaries(list).map { it.name })
    }

    @Test fun each_channel_has_its_own_colour_and_only_saffron_takes_dark_text() {
        val looks = (1..6).map { ChannelGuide.look(it) }
        assertEquals(6, looks.map { it.colour }.toSet().size)
        assertEquals(listOf(false, false, true, false, false, false), looks.map { it.darkText })
        // Past the sixth the colours come round again, but the shape cut into them has moved on.
        assertEquals(looks[0].colour, ChannelGuide.look(7).colour)
        assertTrue(ChannelGuide.look(1).motif != ChannelGuide.look(7).motif)
        assertEquals(ChannelGuide.look(1), ChannelGuide.look(0))
    }

    @Test fun the_words_about_a_channel_count_films_and_series() {
        val channel = ChannelSummary(1, BroadcastPlaylist("Evening", slots = listOf(
            slot("F1"), slot("F2"), slot("F3"), slot("F4"), slot("S1", Kind.SHOW), slot("S2", Kind.SHOW))))
        assertEquals("6 programmes", channel.programmes)
        assertEquals("4 films, 2 series", channel.mix)
        assertEquals("6 programmes · 4 films, 2 series", channel.detail)

        val one = ChannelSummary(2, BroadcastPlaylist("Solo", slots = listOf(slot("S", Kind.SHOW))))
        assertEquals("1 programme · 1 series", one.detail)
        assertEquals("Nothing on yet", ChannelSummary(3, BroadcastPlaylist("Empty")).detail)
    }

    @Test fun a_part_watched_series_is_what_the_channel_resumes() {
        val channel = ChannelSummary(2, BroadcastPlaylist("Sitcoms", slots = listOf(
            slot("Film"), slot("Corner Shop", Kind.SHOW, pending = PlaylistEpisode(3, 4)), slot("Flatmates", Kind.SHOW, pending = PlaylistEpisode(2, 2)))))
        assertEquals("Corner Shop · S3 E4", channel.resume)
        assertNull(ChannelSummary(1, BroadcastPlaylist("Plain", slots = listOf(slot("Film")))).resume)
    }

    @Test fun the_preview_is_the_order_that_then_plays() {
        val slots = (1..6).map { slot("T$it") }
        val queue = BroadcastQueue(Random(7))
        val preview = queue.upcoming(slots)
        assertEquals(6, preview.size)
        // Asking again changes nothing.
        assertEquals(preview.map { it.id }, queue.upcoming(slots).map { it.id })
        assertEquals(preview.first().id, queue.peek(slots)?.id)
        // Tuning in plays exactly that order, and the preview shrinks as programmes are taken.
        val played = (1..6).map { queue.next(slots)!!.id }
        assertEquals(preview.map { it.id }, played)
        assertTrue(queue.upcoming(slots).isEmpty())
    }

    @Test fun an_unfinished_round_is_not_reshuffled_by_looking_at_it() {
        val slots = (1..3).map { slot("T$it") }
        val queue = BroadcastQueue(Random(1))
        queue.next(slots)
        val rest = queue.upcoming(slots).map { it.id }
        assertEquals(2, rest.size)
        queue.next(slots); queue.next(slots)
        // The round is used up but its last programme was never completed: no new round yet.
        assertTrue(queue.upcoming(slots).isEmpty())
    }

    @Test fun finished_films_are_not_previewed() {
        val done = slot("Done").copy(filmFinished = true)
        val slots = listOf(done, slot("A"), slot("B"))
        val queue = BroadcastQueue(Random(3))
        assertFalse(queue.upcoming(slots).any { it.id == done.id })
    }

    @Test fun the_channel_last_tuned_is_remembered_and_forgotten_when_deleted() {
        val platform = MemoryPlatform()
        val store = PlaylistStore(platform)
        val a = store.create("A"); val b = store.create("B")
        assertNull(store.lastTuned)
        store.lastTuned = b.id
        assertEquals(b.id, PlaylistStore(platform).lastTuned)
        store.delete(a.id)
        assertEquals(b.id, store.lastTuned)
        store.delete(b.id)
        assertNull(store.lastTuned)
        store.lastTuned = a.id
        store.lastTuned = null
        assertNull(PlaylistStore(platform).lastTuned)
    }

    @Test fun the_channel_number_and_tune_in_survive_the_trip_to_the_player() {
        val playback = PlaylistPlayback("p", "s", PlaylistEpisode(1, 2), "Evening Cinema", "Next", number = 3, tuneIn = true)
        assertEquals(playback, PlaylistPlayback.decode(playback.encode()))
        val plain = PlaylistPlayback.decode(PlaylistPlayback("p", "s", null).encode())!!
        assertNull(plain.number)
        assertFalse(plain.tuneIn)
    }

    @Test fun the_next_programme_is_got_ready_in_the_last_few_minutes_only() {
        val hour = 60 * 60_000L
        assertFalse(ChannelPreload.due(positionMs = 0, durationMs = hour))
        assertFalse(ChannelPreload.due(positionMs = hour - 4 * 60_000L, durationMs = hour))
        assertTrue(ChannelPreload.due(positionMs = hour - 3 * 60_000L, durationMs = hour))
        assertTrue(ChannelPreload.due(positionMs = hour - 10_000L, durationMs = hour))
        // The length is not known yet, or the position is not: never due.
        assertFalse(ChannelPreload.due(positionMs = 0, durationMs = 0))
        assertFalse(ChannelPreload.due(positionMs = -1, durationMs = hour))
        // A short programme is due partway in, never before its last three minutes: a found stream is held for four.
        assertFalse(ChannelPreload.due(positionMs = 30_000L, durationMs = 5 * 60_000L))
        assertTrue(ChannelPreload.due(positionMs = 2 * 60_000L, durationMs = 5 * 60_000L))
        assertTrue(ChannelPreload.due(positionMs = 0, durationMs = 2 * 60_000L))
        assertTrue(ChannelPreload.WINDOW_MS < 4 * 60_000L)
    }
}

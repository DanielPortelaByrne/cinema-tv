package com.danielportelabyrne.cinema.data.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CompletableFuture

class AheadStreamsTest {
    private var clock = 0L
    private fun streams(max: Int = 2, ttl: Long = 1_000L) = AheadStreams<String>(max, ttl) { clock }
    private fun done(v: String) = CompletableFuture.completedFuture(v)

    @Test fun a_held_stream_is_taken_once() {
        val s = streams()
        assertTrue(s.begin("film", { done("link") }))
        assertEquals("link", s.take("film")?.get())
        assertNull(s.take("film"))
    }

    @Test fun nothing_held_gives_nothing() {
        assertNull(streams().take("film"))
    }

    @Test fun a_second_begin_for_the_same_key_does_not_search_again() {
        val s = streams()
        var searches = 0
        assertTrue(s.begin("film", { searches++; done("a") }))
        assertFalse(s.begin("film", { searches++; done("b") }))
        assertEquals(1, searches)
        assertEquals("a", s.take("film")?.get())
    }

    @Test fun a_stream_goes_stale_after_its_time() {
        val s = streams(ttl = 1_000L)
        s.begin("film", { done("link") })
        clock = 999
        assertTrue(s.holds("film"))
        clock = 1_000
        assertFalse(s.holds("film"))
        assertNull(s.take("film"))
    }

    @Test fun a_stale_key_can_be_found_again() {
        val s = streams(ttl = 1_000L)
        s.begin("film", { done("old") })
        clock = 5_000
        assertTrue(s.begin("film", { done("new") }))
        assertEquals("new", s.take("film")?.get())
    }

    @Test fun a_new_one_pushes_the_oldest_out() {
        val s = streams(max = 2)
        s.begin("a", { done("1") }); s.begin("b", { done("2") }); s.begin("c", { done("3") })
        assertFalse(s.holds("a"))
        assertTrue(s.holds("b"))
        assertTrue(s.holds("c"))
    }

    @Test fun a_search_in_flight_is_handed_over_when_taken() {
        val s = streams()
        val pending = CompletableFuture<String>()
        s.begin("film", { pending })
        val taken = s.take("film")!!
        assertFalse(taken.isDone)
        pending.complete("link")
        assertEquals("link", taken.get())
    }

    @Test fun clear_drops_everything() {
        val s = streams()
        s.begin("a", { done("1") }); s.begin("b", { done("2") })
        s.clear()
        assertFalse(s.holds("a")); assertFalse(s.holds("b"))
    }
}

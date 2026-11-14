package com.danielportelabyrne.cinema.design.cinema

import com.danielportelabyrne.cinema.design.cinema.SplashJingle.Timeline
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class SplashJingleTest {
    private val pcm = SplashJingleSynth.samples()
    private val rate = SplashJingleSynth.RATE

    /** The loudest sample in either channel between two moments of the score. */
    private fun peak(fromMs: Int, toMs: Int) = (fromMs * rate / 1000 until toMs * rate / 1000).maxOf { maxOf(abs(pcm[2 * it].toInt()), abs(pcm[2 * it + 1].toInt())) }

    @Test fun lasts_as_long_as_the_score() {
        assertEquals(rate * Timeline.END / 1000 * 2, pcm.size)
    }

    @Test fun is_silent_until_the_bass_plucks() {
        assertEquals(0, peak(0, Timeline.BUP))
    }

    @Test fun every_note_sounds_on_its_beat() {
        // Each of the four gestures is a clear rise at exactly its moment in the score, the picture's cue.
        for (beat in listOf(Timeline.BUP, Timeline.DI, Timeline.DIT, Timeline.DUM)) {
            val before = peak(beat - 25, beat)
            val after = peak(beat, beat + 30)
            assertTrue("beat at $beat: $before then $after", after > before * 1.6 + 600)
        }
    }

    @Test fun the_held_breath_is_nearly_silent() {
        // From a quarter second after the second piano note until the landing, only the room's last whisper remains.
        assertTrue(peak(Timeline.DIT + 280, Timeline.DUM) < 32767 * SplashJingleSynth.CEILING / 30)
    }

    @Test fun stays_under_its_ceiling_and_ends_in_silence() {
        val ceiling = (32767 * SplashJingleSynth.CEILING).toInt() + 1
        assertTrue(pcm.all { abs(it.toInt()) <= ceiling })
        assertTrue(peak(Timeline.END - 5, Timeline.END) < 40)
    }

    @Test fun the_shipped_sound_is_this_score() {
        // Change the score or the Timeline without re-rendering and this fails: the picture would no longer meet its notes.
        val shipped = File(SplashJingleRender.SHIPPED).readBytes()
        val expected = SplashJingleRender.wav(pcm)
        assertEquals(expected.size, shipped.size)
        assertArrayEquals(expected.copyOf(44), shipped.copyOf(44))
        var worst = 0
        for (i in 44 until shipped.size step 2) {
            val a = (shipped[i].toInt() and 0xff) or (shipped[i + 1].toInt() shl 8)
            val b = (expected[i].toInt() and 0xff) or (expected[i + 1].toInt() shl 8)
            worst = maxOf(worst, abs(a - b))
        }
        assertTrue("differs by $worst", worst <= 2)
    }

    @Test fun the_beats_come_in_order() {
        assertTrue(Timeline.DROP < Timeline.BUP)
        assertTrue(Timeline.BUP < Timeline.DI - Timeline.PIANO_DROP && Timeline.DI < Timeline.DIT)
        assertTrue(Timeline.DIT + 400 < Timeline.DUM - Timeline.SNAP)   // a real silence, longer than everything before it
        assertTrue(Timeline.DUM < Timeline.TRIANGLE && Timeline.TRIANGLE < Timeline.LOCKUP)
        assertTrue(Timeline.LOCKUP + Timeline.LOCKUP_LENGTH < Timeline.FADE_START && Timeline.END < Timeline.FADE_START)
    }
}

class IdentClockTest {
    private val ms = 1_000_000L

    @Test fun holds_at_zero_until_the_sound_is_heard_then_follows_it() {
        val clock = IdentClock()
        assertEquals(0.0, clock.tick(0, null), 0.0)
        assertEquals(0.0, clock.tick(80 * ms, null), 0.0)        // output latency: nothing heard yet, picture waits
        assertEquals(12.0, clock.tick(96 * ms, 12.0), 0.0)
        assertEquals(29.0, clock.tick(113 * ms, 29.0), 0.0)
        assertFalse(clock.freeRunning)
    }

    @Test fun never_steps_back_and_jumps_ahead_after_a_stall() {
        val clock = IdentClock()
        clock.tick(0, 100.0)
        assertEquals(100.0, clock.tick(16 * ms, 99.0), 0.0)      // jitter in the timestamp never runs the picture backwards
        assertEquals(350.0, clock.tick(266 * ms, 350.0), 0.0)    // a busy main thread: the picture catches the sound up
    }

    @Test fun carries_on_in_real_time_after_the_sound_ends() {
        val clock = IdentClock()
        clock.tick(0, 1600.0)
        assertEquals(1616.0, clock.tick(16 * ms, null), 1e-9)
        assertEquals(1632.0, clock.tick(32 * ms, null), 1e-9)
        assertTrue(clock.freeRunning)
    }

    @Test fun runs_alone_when_no_sound_arrives() {
        val clock = IdentClock(waitMs = 450.0)
        clock.tick(0, null)
        assertEquals(0.0, clock.tick(400 * ms, null), 0.0)
        clock.tick(460 * ms, null)
        assertEquals(16.0, clock.tick(476 * ms, null), 1e-9)
        assertTrue(clock.freeRunning)
    }
}

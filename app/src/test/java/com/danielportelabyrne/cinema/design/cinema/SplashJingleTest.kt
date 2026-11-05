package com.danielportelabyrne.cinema.design.cinema

import com.danielportelabyrne.cinema.design.cinema.SplashJingle.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SplashJingleTest {
    private val pcm = SplashJingleSynth.samples()
    private fun peak(fromMs: Int, toMs: Int) = (fromMs * SplashJingleSynth.RATE / 1000 until toMs * SplashJingleSynth.RATE / 1000).maxOf { abs(pcm[it].toInt()) }

    @Test fun lasts_as_long_as_the_picture() {
        assertEquals(SplashJingleSynth.RATE * Timeline.END / 1000, pcm.size)
    }

    @Test fun is_silent_until_the_stripes_move_and_loud_at_the_hit() {
        assertEquals(0, peak(0, Timeline.SLIDE_START))
        assertTrue(peak(Timeline.IMPACT, Timeline.IMPACT + 300) > 15_000)
    }

    @Test fun never_clips_and_ends_quietly() {
        assertTrue(pcm.all { abs(it.toInt()) < Short.MAX_VALUE })
        assertTrue(peak(Timeline.END - 20, Timeline.END) < 500)
    }

    @Test fun the_beats_come_in_order() {
        assertTrue(Timeline.WRAP_START >= Timeline.SLIDE_START + 4 * Timeline.SLIDE_STEP + Timeline.SLIDE_LENGTH - 4 * Timeline.WRAP_STEP)
        assertTrue(Timeline.IMPACT >= Timeline.WRAP_START + 4 * Timeline.WRAP_STEP + Timeline.WRAP_LENGTH - 50)
        assertTrue(Timeline.TRIANGLE < Timeline.IMPACT && Timeline.IMPACT < Timeline.LOCKUP)
        assertTrue(Timeline.LOCKUP + Timeline.LOCKUP_LENGTH < Timeline.FADE_START && Timeline.FADE_START < Timeline.END)
    }
}

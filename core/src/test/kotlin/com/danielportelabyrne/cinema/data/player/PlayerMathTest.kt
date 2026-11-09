package com.danielportelabyrne.cinema.data.player

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveGeometryTest {
    @Test fun sineStartsAtTheBaselineAndPeaksAQuarterWaveIn() {
        assertEquals(900f, PlayerMath.waveY(96f, 96f, 900f, 13f, 150f), 0.001f)
        assertEquals(913f, PlayerMath.waveY(96f + 37.5f, 96f, 900f, 13f, 150f), 0.001f)
        assertEquals(887f, PlayerMath.waveY(96f + 112.5f, 96f, 900f, 13f, 150f), 0.001f)
    }

    @Test fun samplesEveryStepAndEndExactly() {
        val xs = PlayerMath.waveSamples(0f, 10f, 4f)
        assertEquals(listOf(0f, 4f, 8f, 10f), xs)
        assertEquals(listOf(5f), PlayerMath.waveSamples(5f, 5f, 4f))
    }

    @Test fun playheadIsClamped() {
        assertEquals(96f, PlayerMath.playheadX(-1f, 96f, 1824f), 0f)
        assertEquals(1824f, PlayerMath.playheadX(2f, 96f, 1824f), 0f)
        assertEquals(960f, PlayerMath.playheadX(0.5f, 96f, 1824f), 0f)
    }
}

class ChaptersTest {
    private val json = JSONArray("""[
        {"StartPositionTicks": 0, "Name": "Kathmandu", "ImageTag": "a"},
        {"StartPositionTicks": 8500000000, "Name": "The approach"},
        {"StartPositionTicks": 20200000000, "Name": ""}
    ]""")

    @Test fun parsesTicksNamesAndTags() {
        val c = PlayerMath.parseChapters(json)
        assertEquals(3, c.size)
        assertEquals(Chapter(1, "The approach", 850_000, null), c[1])
        assertEquals("Chapter 3", c[2].name)
        assertEquals("a", c[0].imageTag)
    }

    @Test fun findsTheChapterPlayingAndLabelsIt() {
        val c = PlayerMath.parseChapters(json)
        assertEquals("The approach", PlayerMath.chapterAt(c, 1_000_000)!!.name)
        assertEquals("Kathmandu", PlayerMath.chapterAt(c, 0)!!.name)
        assertEquals("Chapter 3 of 3", PlayerMath.chapterLabel(PlayerMath.chapterAt(c, 9_000_000)!!, c.size))
        assertEquals(0.5f, PlayerMath.chapterProgress(c, c[0], 425_000, 7_534_000), 0.001f)
    }

    @Test fun noChaptersMeansNothingToShow() {
        assertTrue(PlayerMath.parseChapters(null).isEmpty())
        assertNull(PlayerMath.chapterAt(emptyList(), 1000))
    }
}

class TrickplayTest {
    private val info = JSONObject("""{"src1": {"320": {"Width": 320, "Height": 180, "TileWidth": 10, "TileHeight": 10, "ThumbnailCount": 450, "Interval": 10000},
                                                "640": {"Width": 640, "Height": 360, "TileWidth": 10, "TileHeight": 10, "ThumbnailCount": 450, "Interval": 10000}}}""")

    @Test fun picksTheWidestUnderTheLimit() {
        assertEquals(320, PlayerMath.parseTrickplay(info, "src1", maxWidth = 480)!!.width)
        assertEquals(640, PlayerMath.parseTrickplay(info, "other", maxWidth = 1000)!!.width)
        assertNull(PlayerMath.parseTrickplay(null, "src1"))
    }

    @Test fun locatesATileInItsSheet() {
        val t = PlayerMath.parseTrickplay(info, "src1")!!
        assertEquals(Triple(0, 0, 0), t.tile(0))
        assertEquals(Triple(0, 320 * 3, 180 * 1), t.tile(130_000))        // thumbnail 13: row 1, column 3
        assertEquals(Triple(1, 320 * 5, 0), t.tile(1_050_000))            // thumbnail 105: second sheet
        assertEquals(t.tile(449 * 10_000L), t.tile(99_999_999))          // clamped to the last one
    }
}

class EndsAtTest {
    @Test fun nowPlusWhatRemains() {
        assertEquals(1_000_000L + 84 * 60_000L, PlayerMath.endsAt(1_000_000L, 41 * 60_000L, 125 * 60_000L))
        assertEquals(1_000_000L + 42 * 60_000L, PlayerMath.endsAt(1_000_000L, 41 * 60_000L, 125 * 60_000L, speed = 2f))
        assertNull(PlayerMath.endsAt(0, 0, 0))
    }

    @Test fun clockFormatting() {
        assertEquals("41:17", PlayerMath.clock(2_477_000))
        assertEquals("1:24:17", PlayerMath.clock(5_057_000))
        assertEquals("0:00", PlayerMath.clock(-5))
    }
}

class StreamStatsTest {
    @Test fun formatsWhatIsKnownAndHidesTheRest() {
        assertEquals("2160p · 4K", PlayerMath.resolution(3840, 2160))
        assertEquals("1080p · HD", PlayerMath.resolution(1920, 804))   // a scope film is still 1080p
        assertEquals("2160p · 4K", PlayerMath.resolution(3840, 1600))
        assertNull(PlayerMath.resolution(0, 0))
        assertEquals("4K HDR", PlayerMath.qualityChip(3840, 2160, true))
        assertEquals("1080p", PlayerMath.qualityChip(1920, 1080, false))
        assertEquals("720p", PlayerMath.qualityChip(1280, 536, false))
        assertEquals("HEVC", PlayerMath.videoCodec("video/hevc"))
        assertEquals("E-AC-3", PlayerMath.audioCodec("audio/eac3-joc"))
        assertEquals("Atmos 7.1", PlayerMath.channels(8, "audio/eac3-joc"))
        assertEquals("5.1", PlayerMath.channels(6, "audio/ac3"))
        assertEquals("18.4 Mbps", PlayerMath.bitrate(18_400_000))
        assertEquals("768 kbps", PlayerMath.bitrate(768_000))
        assertNull(PlayerMath.bitrate(-1))
        assertEquals("14.8 GB", PlayerMath.size(14_800_000_000))
    }

    @Test fun bitrateHistoryKeepsSixtyAndThePeak() {
        val h = BitrateHistory()
        (1..70).forEach { h.add(it * 1_000_000L) }
        assertEquals(60, h.values().size)
        assertEquals(70_000_000L, h.peak)
        h.add(0)
        assertEquals(60, h.values().size)
    }
}

class PlayerUiStateTest {
    @Test fun okShowsControlsAndBackHidesThenLeaves() {
        assertEquals(PlayerUi.Controls, PlayerUi.Hidden.onKey(PlayerUi.Key.OK, 0, 0, 1000, true, false).state)
        assertEquals(PlayerUi.Hidden, PlayerUi.Controls.onKey(PlayerUi.Key.BACK, 0, 0, 1000, true, false).state)
        assertEquals(false, PlayerUi.Hidden.onKey(PlayerUi.Key.BACK, 0, 0, 1000, true, false).consumed)
    }

    @Test fun leftRightScrubAndAccelerateWhenHeld() {
        val s = PlayerUi.Hidden.onKey(PlayerUi.Key.RIGHT, 0, 60_000, 7_534_000, true, false).state as PlayerUi.Scrubbing
        assertEquals(70_000, s.targetMs)
        val held = s.onKey(PlayerUi.Key.RIGHT, 20, 60_000, 7_534_000, true, false).state as PlayerUi.Scrubbing
        assertEquals(130_000, held.targetMs)
        val clamped = PlayerUi.Hidden.onKey(PlayerUi.Key.LEFT, 0, 3_000, 7_534_000, true, false).state as PlayerUi.Scrubbing
        assertEquals(0, clamped.targetMs)
    }

    @Test fun okCommitsTheSeekAndBackCancelsWithout() {
        val s = PlayerUi.Scrubbing(4_780_000, PlayerUi.Controls)
        assertEquals(PlayerUi.Step(PlayerUi.Controls, PlayerUi.Action.Seek(4_780_000)), s.onKey(PlayerUi.Key.OK, 0, 2_477_000, 7_534_000, true, true))
        assertEquals(PlayerUi.Step(PlayerUi.Controls, PlayerUi.Action.None), s.onKey(PlayerUi.Key.BACK, 0, 2_477_000, 7_534_000, true, true))
    }

    @Test fun inControlsLeftRightOnlyScrubFromTheScrubber() {
        assertEquals(false, PlayerUi.Controls.onKey(PlayerUi.Key.LEFT, 0, 0, 1000, true, onScrubber = false).consumed)
        assertTrue(PlayerUi.Controls.onKey(PlayerUi.Key.LEFT, 0, 500, 1000, true, onScrubber = true).state is PlayerUi.Scrubbing)
    }

    @Test fun playPausePausesIntoTheInfoScreenAndResumesOut() {
        assertEquals(PlayerUi.Step(PlayerUi.Paused, PlayerUi.Action.Pause), PlayerUi.Hidden.onKey(PlayerUi.Key.PLAY_PAUSE, 0, 0, 1000, true, false))
        assertEquals(PlayerUi.Step(PlayerUi.Hidden, PlayerUi.Action.Play), PlayerUi.Paused.onKey(PlayerUi.Key.PLAY_PAUSE, 0, 0, 1000, false, false))
        assertEquals(PlayerUi.Controls, PlayerUi.Paused.onKey(PlayerUi.Key.BACK, 0, 0, 1000, false, false).state)
    }

    @Test fun liveStreamsWithNoLengthNeverScrub() {
        assertEquals(PlayerUi.Controls, PlayerUi.Hidden.onKey(PlayerUi.Key.LEFT, 0, 0, 0, true, false).state)
    }

    @Test fun a_stream_can_seek_forward_only_into_what_has_downloaded() {
        val hour = 3_600_000L
        // A quarter downloaded: up to 15 min less the larger of 15 s and 3 % (108 s).
        assertEquals(hour / 4 - 108_000, PlayerMath.streamSeekLimit(hour, 0.25f, 30_000))
        // The player's own buffer counts when it is further.
        assertEquals(20 * 60_000L, PlayerMath.streamSeekLimit(hour, 0.25f, 20 * 60_000L))
        // Fully downloaded: anywhere.
        assertEquals(hour, PlayerMath.streamSeekLimit(hour, 1f, 0))
        // Nothing known from the server, or no duration: only the buffer.
        assertEquals(40_000L, PlayerMath.streamSeekLimit(hour, null, 40_000))
        assertEquals(40_000L, PlayerMath.streamSeekLimit(0, 0.5f, 40_000))
    }

    @Test fun clamping_never_blocks_going_back_and_never_moves_backwards() {
        assertEquals(10_000L, PlayerMath.clampStreamSeek(10_000, 60_000, 30_000))   // back: always
        assertEquals(50_000L, PlayerMath.clampStreamSeek(50_000, 40_000, 90_000))   // forward, inside: as asked
        assertEquals(90_000L, PlayerMath.clampStreamSeek(600_000, 40_000, 90_000))  // too far: held at the limit
        assertEquals(40_000L, PlayerMath.clampStreamSeek(600_000, 40_000, 20_000))  // limit behind us: stay put
    }
}

class CreditsStartTest {
    private val hour = 60 * 60_000L
    private fun ch(i: Int, name: String, min: Int) = Chapter(i, name, min * 60_000L, null)

    @Test fun a_credits_chapter_near_the_end_is_where_the_card_comes_up() {
        val chapters = listOf(ch(0, "Scene 1", 0), ch(1, "Intro", 2), ch(2, "Scene 2", 5), ch(3, "Credits", 52))
        assertEquals(52 * 60_000L, PlayerMath.creditsStartMs(chapters, 55 * 60_000L))
        assertEquals(50 * 60_000L, PlayerMath.creditsStartMs(listOf(ch(0, "Start", 0), ch(1, "End Credits", 50)), hour))
    }

    @Test fun without_one_the_credits_are_guessed_from_the_length() {
        // An hour's credits are guessed at about 3.6 minutes (6 % of the length), not 30 seconds.
        assertEquals(hour - 216_000L, PlayerMath.creditsStartMs(listOf(ch(0, "Chapter 1", 0), ch(1, "Chapter 2", 30)), hour))
        // "Credits" right at the start (an opening titles chapter) is not the end credits.
        assertEquals(hour - 216_000L, PlayerMath.creditsStartMs(listOf(ch(0, "Opening credits", 1)), hour))
    }

    @Test fun the_guessed_tail_scales_with_the_length_within_limits() {
        assertEquals(60_000L, PlayerMath.guessedTailMs(10 * 60_000L))      // 6 % of 10 min is 36 s: a minute at least
        assertEquals(72_000L, PlayerMath.guessedTailMs(20 * 60_000L))      // 6 % of 20 min
        assertEquals(90_000L, PlayerMath.guessedTailMs(25 * 60_000L))      // 6 % of 25 min
        assertEquals(4 * 60_000L, PlayerMath.guessedTailMs(90 * 60_000L))  // never more than four minutes
        assertEquals(20 * 60_000L - 72_000L, PlayerMath.creditsStartMs(emptyList(), 20 * 60_000L))
    }

    @Test fun a_short_unnamed_last_chapter_near_the_end_is_the_credits() {
        // Fallout S2 E1: nine chapters named "Chapter N", the last at 56:17 of 60:09.
        val fallout = (0..7).map { ch(it, "Chapter ${it + 1}", it * 6) } + Chapter(8, "Chapter 9", 3_377_165L, null)
        assertEquals(3_377_165L, PlayerMath.creditsStartMs(fallout, 3_609_731L))
        // A long last act is not the credits, nor is a named scene.
        assertEquals(hour - 216_000L, PlayerMath.creditsStartMs(listOf(ch(0, "Chapter 1", 0), ch(1, "Chapter 2", 50)), hour))
        assertEquals(hour - 216_000L, PlayerMath.creditsStartMs(listOf(ch(0, "Chapter 1", 0), ch(1, "The showdown", 57)), hour))
    }

    @Test fun short_or_unknown_lengths_give_nothing() {
        assertNull(PlayerMath.creditsStartMs(emptyList(), 5 * 60_000L))
        assertNull(PlayerMath.creditsStartMs(emptyList(), 0L))
    }

    @Test fun a_known_start_beats_the_learned_tail_and_the_guess() {
        val hour = 60 * 60_000L
        assertEquals(2_400_000L, PlayerMath.creditsStartMs(emptyList(), hour, knownStartMs = 2_400_000L, learnedTailMs = 200_000L))
        // Without one, the learned tail: a viewer habitually leaves 200 s before the end.
        assertEquals(hour - 200_000L, PlayerMath.creditsStartMs(emptyList(), hour, learnedTailMs = 200_000L))
        // An implausible known start is ignored, and so is an implausible learned tail: the guess is used.
        assertEquals(hour - 216_000L, PlayerMath.creditsStartMs(emptyList(), hour, knownStartMs = 100_000L, learnedTailMs = 50 * 60_000L))
    }

    @Test fun a_named_credits_chapter_still_beats_everything() {
        val hour = 60 * 60_000L
        assertEquals(50 * 60_000L, PlayerMath.creditsStartMs(listOf(ch(0, "Start", 0), ch(1, "End Credits", 50)), hour, knownStartMs = 40 * 60_000L, learnedTailMs = 200_000L))
    }
}

class SubtitleNameTest {
    @Test fun plain_track_is_just_the_language() {
        assertEquals("English", PlayerMath.subtitleName("eng", null, forced = false, captions = false))
        assertEquals("English", PlayerMath.subtitleName("eng", "English", forced = false, captions = false))
    }

    @Test fun flags_are_named() {
        assertEquals("English · Forced", PlayerMath.subtitleName("eng", null, forced = true, captions = false))
        assertEquals("English · SDH", PlayerMath.subtitleName("eng", null, forced = false, captions = true))
    }

    @Test fun label_is_kept_without_repeats() {
        assertEquals("English SDH", PlayerMath.subtitleName("eng", "English SDH", forced = false, captions = true))
        assertEquals("English · Signs & Songs · Forced", PlayerMath.subtitleName("eng", "Signs & Songs", forced = true, captions = false))
        assertEquals("Unknown", PlayerMath.subtitleName("und", null, forced = false, captions = false))
    }
}

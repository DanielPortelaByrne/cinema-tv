package com.danielportelabyrne.cinema.data.music

import com.danielportelabyrne.cinema.data.music.SeekKeys.Action
import org.junit.Assert.*
import org.junit.Test

class SeekKeysTest {
    @Test fun a_tap_jumps_once_no_second_tap_can_follow() {
        val k = SeekKeys()
        assertNull(k.down(1, 0, 0))
        assertNull(k.up(80))
        assertNull(k.due(250))
        assertEquals(Action.Jump(1), k.due(301))
        assertNull(k.due(400))
    }

    @Test fun a_double_tap_changes_track_and_does_not_jump() {
        val k = SeekKeys()
        k.down(-1, 0, 0); k.up(70)
        assertEquals(Action.Skip(-1), k.down(-1, 0, 200))
        k.up(260)
        assertNull(k.due(1_000))
    }

    @Test fun taps_the_other_way_are_two_jumps() {
        val k = SeekKeys()
        k.down(1, 0, 0); k.up(50)
        assertNull(k.down(-1, 0, 150)); k.up(200)
        assertEquals(Action.Jump(-1), k.due(500))
    }

    @Test fun holding_scans_until_release_and_never_jumps() {
        val k = SeekKeys()
        k.down(1, 0, 0)
        assertNull(k.due(350))                       // still held: not a tap yet
        assertNull(k.down(1, 1, 350))                // a repeat before the hold time
        assertEquals(Action.ScanStart(1), k.down(1, 2, 420))
        assertNull(k.down(1, 3, 470))
        assertTrue(k.scanning)
        assertEquals(Action.ScanEnd, k.up(3_000))
        assertNull(k.due(4_000))
    }

    @Test fun scanning_speeds_up() {
        assertEquals(8_000L, SeekKeys.scanOffset(1_000))
        assertEquals(16_000L + 24_000L, SeekKeys.scanOffset(3_000))
    }
}

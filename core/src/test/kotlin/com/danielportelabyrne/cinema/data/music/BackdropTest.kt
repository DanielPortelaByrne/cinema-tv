package com.danielportelabyrne.cinema.data.music

import org.junit.Assert.*
import org.junit.Test

class BackdropTest {
    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun alpha(c: Int) = c ushr 24

    @Test fun blur_spreads_a_bright_dot() {
        val w = 21
        val px = IntArray(w * w) { 0xFF000000.toInt() }.also { it[10 * w + 10] = 0xFFFF0000.toInt() }
        Backdrop.blur(px, w, w, 2)
        assertTrue(red(px[10 * w + 10]) in 1..254)
        assertTrue(red(px[10 * w + 12]) > 0)
        assertEquals(0, red(px[0]))
        assertTrue(px.all { alpha(it) == 255 })
    }

    @Test fun tint_darkens_and_keeps_grey_grey() {
        val px = intArrayOf(0xFF808080.toInt())
        Backdrop.tint(px)
        val c = px[0]
        assertEquals(red(c), (c shr 8) and 0xFF); assertEquals(red(c), c and 0xFF)
        assertTrue(red(c) < 0x80)
    }

    @Test fun round_off_hides_the_corners_and_keeps_the_middle() {
        val px = IntArray(32 * 32) { -1 }
        Backdrop.roundOff(px, 32, 32)
        assertEquals(0, alpha(px[0]))
        assertEquals(255, alpha(px[16 * 32 + 16]))
    }
}

package com.danielportelabyrne.cinema.data.pairing

import org.junit.Assert.*
import org.junit.Test

class QrSvgTest {
    @Test fun a_short_known_string_fits_version_one() {
        // "HELLO WORLD" at medium error correction is the reference example in Nayuki's own tests: version 1, 21 modules.
        assertEquals(1, QrSvg.versionOf("HELLO WORLD"))
    }

    @Test fun the_svg_is_sized_to_the_modules_plus_the_quiet_zone() {
        val svg = QrSvg.toSvg("HELLO WORLD", border = 4)
        assertTrue(svg.startsWith("<svg "))
        assertTrue(svg.contains("viewBox=\"0 0 29 29\""))   // 21 modules + 4 on each side
    }

    @Test fun a_pairing_address_still_fits_a_small_code() {
        val url = "http://192.168.1.217:45123/setup/428193"
        assertTrue(QrSvg.versionOf(url) <= 3)
    }
}

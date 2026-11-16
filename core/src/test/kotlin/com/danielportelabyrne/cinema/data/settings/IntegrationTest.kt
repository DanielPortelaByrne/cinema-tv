package com.danielportelabyrne.cinema.data.settings

import com.danielportelabyrne.cinema.platform.MemoryPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationTest {
    @Test fun letterboxd_is_on_until_switched_off() {
        val settings = CinemaSettings(MemoryPlatform())
        assertTrue(settings.isOn(Integration.LETTERBOXD))
        settings.setOn(Integration.LETTERBOXD, false)
        assertFalse(settings.isOn(Integration.LETTERBOXD))
        settings.setOn(Integration.LETTERBOXD, true)
        assertTrue(settings.isOn(Integration.LETTERBOXD))
    }

    @Test fun reset_brings_back_the_defaults() {
        val settings = CinemaSettings(MemoryPlatform())
        settings.setOn(Integration.LETTERBOXD, false)
        settings.reset()
        assertTrue(settings.isOn(Integration.LETTERBOXD))
    }

    @Test fun keys_are_stable() {
        assertEquals("integration_letterboxd", Integration.LETTERBOXD.key)
    }

    @Test fun suggestashow_is_off_until_a_server_is_set_up() {
        val settings = CinemaSettings(MemoryPlatform())
        assertFalse(settings.isOn(Integration.SUGGESTASHOW))
        settings.suggestashowUrl = "192.168.1.20:8099/"
        assertEquals("http://192.168.1.20:8099", settings.suggestashowUrl)
    }
}

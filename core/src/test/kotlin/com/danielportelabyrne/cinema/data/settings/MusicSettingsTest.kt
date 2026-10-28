package com.danielportelabyrne.cinema.data.settings

import com.danielportelabyrne.cinema.data.music.SubsonicConfig
import com.danielportelabyrne.cinema.platform.MemoryPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSettingsTest {
    private val platform = MemoryPlatform()

    @Test fun saves_the_server_and_keeps_the_password_secret() {
        val settings = CinemaSettings(platform)
        settings.subsonic = SubsonicConfig("music.example:4533/", "listener", "pw-example")
        assertEquals(SubsonicConfig("http://music.example:4533", "listener", "pw-example"), settings.subsonic)
        assertFalse(platform.prefs.values.any { p -> p.values.values.contains("pw-example") })
    }

    @Test fun forgetting_the_server_forgets_the_secret() {
        val settings = CinemaSettings(platform)
        settings.subsonic = SubsonicConfig("http://music.example", "", apiKey = "key-example")
        settings.subsonic = null
        assertNull(settings.subsonic)
        assertNull(platform.secrets.get("subsonic_key"))
    }

    @Test fun music_is_on_but_hidden_until_a_server_is_set_up() {
        val settings = CinemaSettings(platform)
        assertTrue(settings.isOn(Integration.MUSIC))
        assertNull(settings.subsonic)
    }
}

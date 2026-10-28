package com.danielportelabyrne.cinema.data.settings

import com.danielportelabyrne.cinema.data.movies.ClientKind
import com.danielportelabyrne.cinema.data.music.SubsonicConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetTest {
    @Test fun readsEveryService() {
        val p = Preset.parse("""
            {
              "jellyfin": { "url": "192.168.1.5:8096/", "username": "conor", "password": "pw" },
              "seerr": { "url": "https://seerr.home", "signIn": "local", "email": "a@b.c", "password": "spw" },
              "sonarr": { "url": "http://nas:8989", "apiKey": "s-key" },
              "radarr": { "url": "http://nas:7878", "apiKey": "r-key" },
              "qbittorrent": { "username": "admin", "password": "qpw" },
              "region": "IE",
              "streamServiceUrl": "http://nas:9000"
            }
        """)
        assertEquals(PresetServer("http://192.168.1.5:8096", PresetLogin("conor", "pw")), p.jellyfin)
        assertEquals(PresetServer("https://seerr.home", PresetLogin("a@b.c", "spw")), p.seerr)
        assertTrue(p.seerrLocal)
        assertEquals(ManualArr("http://nas:8989", "s-key"), p.sonarr)
        assertEquals(ManualArr("http://nas:7878", "r-key"), p.radarr)
        assertEquals(mapOf(ClientKind.QBITTORRENT to PresetLogin("admin", "qpw")), p.clients)
        assertEquals("IE", p.region)
        assertEquals("http://nas:9000", p.streamServiceUrl)
        assertNull(p.requestHelperUrl)
    }

    @Test fun skipsIncompleteEntries() {
        val p = Preset.parse("""{ "sonarr": { "url": "http://nas:8989" }, "radarr": { "apiKey": "k" }, "deluge": {}, "region": "Ireland" }""")
        assertNull(p.sonarr)
        assertNull(p.radarr)
        assertTrue(p.clients.isEmpty())
        assertNull(p.region)
        assertFalse(p.needsSignIn)
    }

    @Test fun seerrAloneStillSignsIn() {
        val p = Preset.parse("""{ "jellyfin": { "url": "jf" }, "seerr": { "url": "seerr" } }""")
        assertNull(p.jellyfin?.login)
        assertFalse(p.seerrLocal)
        assertTrue(p.needsSignIn)
    }

    @Test fun readsTheMusicServer() {
        assertEquals(SubsonicConfig("http://192.168.1.20:4533", "someone", "pw"),
            Preset.parse("""{ "music": { "url": "192.168.1.20:4533", "username": "someone", "password": "pw" } }""").music)
        assertEquals(SubsonicConfig("https://music.home", "", "", "k"),
            Preset.parse("""{ "music": { "url": "https://music.home", "apiKey": "k" } }""").music)
        assertNull(Preset.parse("""{ "music": { "url": "https://music.home", "username": "someone" } }""").music)
        assertNull(Preset.parse("""{ "music": { "username": "someone", "password": "pw" } }""").music)
    }

    @Test fun fingerprintFollowsContent() {
        assertNotEquals(Preset.parse("""{"region":"IE"}""").fingerprint, Preset.parse("""{"region":"GB"}""").fingerprint)
    }
}

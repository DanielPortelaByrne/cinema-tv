package com.danielportelabyrne.cinema.data.pairing

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinSession
import com.danielportelabyrne.cinema.data.movies.SeerrClient
import com.danielportelabyrne.cinema.data.movies.SeerrSession
import com.danielportelabyrne.cinema.data.music.SubsonicConfig
import com.danielportelabyrne.cinema.data.settings.Arr
import com.danielportelabyrne.cinema.data.settings.CinemaSettings
import com.danielportelabyrne.cinema.data.settings.Integration
import com.danielportelabyrne.cinema.data.settings.LibraryOrder
import com.danielportelabyrne.cinema.data.settings.ManualArr
import com.danielportelabyrne.cinema.platform.Platform
import com.danielportelabyrne.cinema.platform.MemoryPlatform
import org.junit.Assert.*
import org.junit.Test

class RestoreFormatTest {
    private fun tv(platform: Platform) = Triple(CinemaSettings(platform), JellyfinClient(platform), SeerrClient(platform))

    @Test fun a_restore_file_brings_back_settings_and_sign_ins_on_a_fresh_tv() {
        val old = MemoryPlatform()
        val (settings, jellyfin, seerr) = tv(old)
        settings.region = "GB"
        settings.libraryOrder = LibraryOrder.entries.last()
        settings.setManualArr(Arr.SONARR, ManualArr("http://10.0.0.5:8989", "sonarr-key"))
        settings.setOn(Integration.entries.first(), true)
        settings.requestHelperUrl = "http://10.0.0.6:9000"
        settings.stremioKey = "stremio-key"
        settings.subsonic = SubsonicConfig("http://10.0.0.7:4533", "daniel", "music-pw", "")
        settings.setClientUser("http://10.0.0.11:8090", "admin")
        settings.setClientPassword("http://10.0.0.11:8090", "qbit-pw")
        jellyfin.restoreSession(JellyfinSession("http://10.0.0.8:8096", "jf-token", "user-1"))
        seerr.restoreSession(SeerrSession("http://10.0.0.9:5055", "connect.sid=s%3Aabc"))

        val json = RestoreFormat.build(settings, jellyfin, seerr)

        val fresh = MemoryPlatform()
        val (freshSettings, freshJellyfin, freshSeerr) = tv(fresh)
        RestoreFormat.apply(json, freshSettings, freshJellyfin, freshSeerr)

        assertEquals("GB", freshSettings.region)
        assertEquals(LibraryOrder.entries.last(), freshSettings.libraryOrder)
        assertEquals(ManualArr("http://10.0.0.5:8989", "sonarr-key"), freshSettings.manualArr(Arr.SONARR))
        assertTrue(freshSettings.isOn(Integration.entries.first()))
        assertEquals("http://10.0.0.6:9000", freshSettings.requestHelperUrl)
        assertEquals("stremio-key", freshSettings.stremioKey)
        assertEquals("music-pw", freshSettings.subsonic?.password)
        assertEquals("admin", freshSettings.clientUser("http://10.0.0.11:8090"))
        assertEquals("qbit-pw", freshSettings.clientPassword("http://10.0.0.11:8090"))
        assertEquals("jf-token", freshJellyfin.savedSession()?.token)
        assertEquals("user-1", freshJellyfin.savedSession()?.userId)
        assertEquals("connect.sid=s%3Aabc", freshSeerr.savedSession()?.cookie)
        assertTrue(freshSettings.setupDone)
    }

    @Test fun sign_ins_that_were_not_signed_in_are_left_out_of_the_file() {
        val (settings, jellyfin, seerr) = tv(MemoryPlatform())
        val json = org.json.JSONObject(RestoreFormat.build(settings, jellyfin, seerr))
        assertFalse(json.has("jellyfin"))
        assertFalse(json.has("seerr"))
    }

    @Test fun a_file_from_a_newer_cinema_asks_for_an_update() {
        val (settings, jellyfin, seerr) = tv(MemoryPlatform())
        val error = assertThrows(RestoreException::class.java) {
            RestoreFormat.apply("""{"kind":"cinema-restore","version":99,"settings":{}}""", settings, jellyfin, seerr)
        }
        assertTrue(error.message!!.contains("newer Cinema"))
    }

    @Test fun a_file_of_another_kind_changes_nothing() {
        val (settings, jellyfin, seerr) = tv(MemoryPlatform())
        settings.region = "FR"
        assertThrows(RestoreException::class.java) {
            RestoreFormat.apply("""{"kind":"something-else","version":1,"settings":{"region":"US"}}""", settings, jellyfin, seerr)
        }
        assertThrows(RestoreException::class.java) { RestoreFormat.apply("not json", settings, jellyfin, seerr) }
        assertEquals("FR", settings.region)
    }
}

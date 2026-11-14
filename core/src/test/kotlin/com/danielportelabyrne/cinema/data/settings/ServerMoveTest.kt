package com.danielportelabyrne.cinema.data.settings

import com.danielportelabyrne.cinema.platform.MemoryPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The home server got a new address: everything that lived on it moves with Jellyfin, nothing else does. */
class ServerMoveTest {
    @Test fun moves_every_service_on_the_old_machine_and_nothing_else() {
        val p = MemoryPlatform()
        p.preferences("seerr").edit().putString("server", "http://192.168.1.233:5055").putString("cookie", "c").apply()
        val settings = CinemaSettings(p)
        settings.streamServiceUrl = "http://192.168.1.233:11470"
        settings.requestHelperUrl = "http://nas.local:9000"                        // another machine: left alone
        settings.setManualArr(Arr.SONARR, ManualArr("http://192.168.1.233:8989", "key"))
        settings.setClientUser("http://192.168.1.233:8080", "admin")
        settings.setClientPassword("http://192.168.1.233:8080", "pw")

        val moved = ServerMove.follow(p, "http://192.168.1.233:8096", "http://192.168.1.183:8096")

        assertEquals(4, moved)
        assertEquals("http://192.168.1.183:5055", p.preferences("seerr").getString("server", null))
        assertEquals("c", p.preferences("seerr").getString("cookie", null))       // still signed in to Seerr
        assertEquals("http://192.168.1.183:11470", settings.streamServiceUrl)
        assertEquals("http://nas.local:9000", settings.requestHelperUrl)
        assertEquals(ManualArr("http://192.168.1.183:8989", "key"), settings.manualArr(Arr.SONARR))
        assertEquals("pw", settings.clientPassword("http://192.168.1.183:8080"))
        assertEquals("admin", settings.clientUser("http://192.168.1.183:8080"))
    }

    @Test fun running_again_changes_nothing() {
        val p = MemoryPlatform()
        p.preferences("seerr").edit().putString("server", "http://192.168.1.233:5055").apply()
        ServerMove.follow(p, "http://192.168.1.233:8096", "http://192.168.1.183:8096")
        assertEquals(0, ServerMove.follow(p, "http://192.168.1.233:8096", "http://192.168.1.183:8096"))
    }

    @Test fun rehosts_only_the_matching_host_keeping_port_and_path() {
        assertEquals("https://10.0.0.2:8443/seerr", ServerMove.rehost("https://10.0.0.1:8443/seerr", "10.0.0.1", "10.0.0.2"))
        assertNull(ServerMove.rehost("http://10.0.0.11:5055", "10.0.0.1", "10.0.0.2"))
        assertNull(ServerMove.rehost("not an address", "10.0.0.1", "10.0.0.2"))
    }
}

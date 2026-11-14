package com.danielportelabyrne.cinema.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProtocolTest {
    @Test fun announceRoundTrips() {
        val a = RemoteProtocol.parseAnnounce(RemoteProtocol.announce("Living room", 47800))
        assertEquals("Living room", a?.name)
        assertEquals(47800, a?.port)
    }

    @Test fun ignoresOtherBroadcasts() {
        assertNull(RemoteProtocol.parseAnnounce("""{"name":"x"}"""))
        assertNull(RemoteProtocol.parseAnnounce("not json"))
    }

    @Test fun keysAreCaseInsensitive() {
        assertEquals(RemoteProtocol.Key.PLAY_PAUSE, RemoteProtocol.key("play_pause"))
        assertNull(RemoteProtocol.key("POWER"))
    }

    @Test fun codeAllowsFewGuessesAndExpires() {
        val w = RemoteProtocol.PairingWindow("1234", startedAt = 0)
        assertFalse(w.check("0000", 10))
        assertTrue(w.check("1234", 20))
        val late = RemoteProtocol.PairingWindow("1234", startedAt = 0)
        assertFalse(late.check("1234", RemoteProtocol.CODE_MILLIS + 1))
        val guessed = RemoteProtocol.PairingWindow("1234", startedAt = 0)
        repeat(RemoteProtocol.MAX_TRIES) { guessed.check("9999", 1) }
        assertFalse(guessed.check("1234", 2))
    }

    @Test fun formDecodes() {
        val f = RemoteProtocol.form(RemoteProtocol.encode("text" to "Twin Peaks & more", "key" to "OK"))
        assertEquals("Twin Peaks & more", f["text"])
        assertEquals("OK", f["key"])
    }

    @Test fun codesAreFourDigits() {
        repeat(50) { assertTrue(RemoteProtocol.newCode().matches(Regex("\\d{4}"))) }
    }
}

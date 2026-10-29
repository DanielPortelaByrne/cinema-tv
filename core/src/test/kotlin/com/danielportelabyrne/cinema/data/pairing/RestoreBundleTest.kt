package com.danielportelabyrne.cinema.data.pairing

import org.junit.Assert.*
import org.junit.Test

class RestoreBundleTest {
    private val json = """{"jellyfin":{"url":"http://192.168.1.5:8096","token":"abc123"},"seerr":{"cookie":"connect.sid=s%3Axyz"}}"""
    private val pin = "482913"

    @Test fun round_trips_the_settings_with_the_right_pin() {
        val sealed = RestoreBundle.seal(json.toByteArray(Charsets.UTF_8), pin)
        assertEquals(json, String(RestoreBundle.open(sealed, pin), Charsets.UTF_8))
    }

    @Test fun a_wrong_pin_is_refused() {
        val sealed = RestoreBundle.seal(json.toByteArray(Charsets.UTF_8), pin)
        val error = assertThrows(RestoreException::class.java) { RestoreBundle.open(sealed, "482914") }
        assertTrue(error.message!!.contains("PIN is wrong"))
    }

    @Test fun a_changed_byte_is_refused_whether_in_the_header_or_the_ciphertext() {
        val sealed = RestoreBundle.seal(json.toByteArray(Charsets.UTF_8), pin)
        for (index in listOf(5, sealed.size - 1)) {
            val tampered = sealed.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertThrows(RestoreException::class.java) { RestoreBundle.open(tampered, pin) }
        }
    }

    @Test fun each_seal_uses_a_fresh_salt_and_nonce() {
        val a = RestoreBundle.seal(json.toByteArray(), pin)
        val b = RestoreBundle.seal(json.toByteArray(), pin)
        assertFalse(a.contentEquals(b))
    }

    @Test fun a_pin_shorter_than_the_minimum_is_not_accepted_when_sealing() {
        assertThrows(IllegalArgumentException::class.java) {
            RestoreBundle.seal(json.toByteArray(), "12345")
        }
    }

    @Test fun a_file_of_another_kind_is_refused_as_not_a_restore_file() {
        val error = assertThrows(RestoreException::class.java) { RestoreBundle.open("{\"jellyfin\":{}}".toByteArray(), pin) }
        assertEquals("Not a Cinema restore file", error.message)
    }

    @Test fun an_absurd_iteration_count_is_refused_before_any_work_is_done() {
        val sealed = RestoreBundle.seal(json.toByteArray(), pin)
        sealed[1] = 0x7F; sealed[2] = 0x7F; sealed[3] = 0x7F; sealed[4] = 0x7F
        assertThrows(RestoreException::class.java) { RestoreBundle.open(sealed, pin) }
    }
}

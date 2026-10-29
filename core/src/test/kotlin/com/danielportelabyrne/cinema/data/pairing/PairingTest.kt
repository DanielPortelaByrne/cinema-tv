package com.danielportelabyrne.cinema.data.pairing

import org.junit.Assert.*
import org.junit.Test

class PairingTest {
    /** A clock the test moves by hand, so expiry needs no sleeping. */
    private class Clock(var now: Long = 1_000_000L) : () -> Long {
        override fun invoke(): Long = now
    }

    @Test fun codes_are_six_digits_with_leading_zeros() {
        assertEquals(6, Pairing.newCode().length)
        assertTrue(Pairing.newCode().all { it.isDigit() })
        assertEquals("000042", Pairing.newCode(object : java.security.SecureRandom() {
            override fun nextInt(bound: Int): Int = 42
        }))
    }

    @Test fun accepts_the_right_code_and_refuses_a_wrong_one() {
        val pairing = Pairing(code = "428193", now = Clock())
        assertEquals(Pairing.Check.OK, pairing.check("428193"))
        assertEquals(Pairing.Check.WRONG_CODE, pairing.check("428194"))
        assertEquals(Pairing.Check.WRONG_CODE, pairing.check("42819"))
        assertEquals(Pairing.Check.WRONG_CODE, pairing.check(""))
    }

    @Test fun checking_does_not_spend_the_code() {
        val pairing = Pairing(code = "111111", now = Clock())
        repeat(3) { assertEquals(Pairing.Check.OK, pairing.check("111111")) }
    }

    @Test fun expires_after_ten_minutes() {
        val clock = Clock()
        val pairing = Pairing(code = "555555", now = clock)
        clock.now += Pairing.TEN_MINUTES - 1
        assertEquals(Pairing.Check.OK, pairing.check("555555"))
        clock.now += 1
        assertEquals(Pairing.Check.EXPIRED, pairing.check("555555"))
        assertTrue(pairing.isExpired)
        assertTrue(pairing.isSpent)
    }

    @Test fun a_successful_submission_spends_the_code_once() {
        val pairing = Pairing(code = "777777", now = Clock())
        assertEquals(Pairing.Check.OK, pairing.begin("777777"))
        pairing.finish(success = true)
        assertEquals(Pairing.Check.USED, pairing.check("777777"))
        assertEquals(Pairing.Check.USED, pairing.begin("777777"))
    }

    @Test fun a_failed_submission_leaves_the_code_for_another_try() {
        val pairing = Pairing(code = "777777", now = Clock())
        assertEquals(Pairing.Check.OK, pairing.begin("777777"))
        pairing.finish(success = false)
        assertEquals(Pairing.Check.OK, pairing.begin("777777"))
    }

    @Test fun only_one_submission_runs_at_a_time() {
        val pairing = Pairing(code = "777777", now = Clock())
        assertEquals(Pairing.Check.OK, pairing.begin("777777"))
        assertEquals(Pairing.Check.USED, pairing.begin("777777"))
        pairing.finish(success = false)
        assertEquals(Pairing.Check.OK, pairing.begin("777777"))
    }

    @Test fun a_wrong_code_cannot_start_a_submission() {
        val pairing = Pairing(code = "777777", now = Clock())
        assertEquals(Pairing.Check.WRONG_CODE, pairing.begin("000000"))
        assertEquals(Pairing.Check.OK, pairing.begin("777777"))
    }
}

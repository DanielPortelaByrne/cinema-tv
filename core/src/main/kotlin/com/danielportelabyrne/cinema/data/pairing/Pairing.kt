package com.danielportelabyrne.cinema.data.pairing

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * One pairing session: a random six-digit code that a phone must present to use the TV's setup pages. It expires
 * [ttlMillis] after it is made, and it is spent once a submission succeeds. [now] is injected so tests need no sleeps.
 */
class Pairing(
    val code: String = newCode(),
    private val ttlMillis: Long = TEN_MINUTES,
    private val now: () -> Long = System::currentTimeMillis,
) {
    enum class Check { OK, WRONG_CODE, EXPIRED, USED }

    private val expiresAt = now() + ttlMillis
    private var used = false
    private var inFlight = false

    /** Whether [presented] may be used right now. Does not spend the code, so a page can be loaded more than once. */
    @Synchronized
    fun check(presented: String): Check = when {
        used -> Check.USED
        now() >= expiresAt -> Check.EXPIRED
        !sameCode(presented) -> Check.WRONG_CODE
        else -> Check.OK
    }

    /**
     * Claims the code for one submission. Returns [Check.OK] only if the code may be used and nothing else is being
     * submitted. Pair it with [finish].
     */
    @Synchronized
    fun begin(presented: String): Check {
        val result = check(presented)
        if (result != Check.OK) return result
        if (inFlight) return Check.USED
        inFlight = true
        return Check.OK
    }

    /** Ends a submission started with [begin]. A successful one spends the code; a failed one lets the phone try again. */
    @Synchronized
    fun finish(success: Boolean) {
        inFlight = false
        if (success) used = true
    }

    val isExpired: Boolean @Synchronized get() = now() >= expiresAt

    val isSpent: Boolean @Synchronized get() = used || isExpired

    private fun sameCode(presented: String): Boolean =
        MessageDigest.isEqual(presented.toByteArray(Charsets.UTF_8), code.toByteArray(Charsets.UTF_8))

    companion object {
        const val TEN_MINUTES = 10 * 60 * 1000L
        const val DIGITS = 6

        /** A random six-digit code, zero-padded, from a secure source. */
        fun newCode(random: SecureRandom = SecureRandom()): String =
            random.nextInt(1_000_000).toString().padStart(DIGITS, '0')
    }
}

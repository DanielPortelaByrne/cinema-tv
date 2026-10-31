package com.danielportelabyrne.cinema.data.letterboxd

/**
 * Decides whether a plain request (no browser) is worth trying. Refusals by Letterboxd's browser check in a row close the
 * gate; while it is closed, one plain request is let through every [probeEveryMs], so a clearance that a browser load
 * has since obtained is found again. A plain request that gets through opens the gate at once. Browser loads do not
 * change the gate, so the probes stay one a minute however many pages the browser fetches.
 */
class PlainGate(
    private val limit: Int = PLAIN_REFUSALS,
    private val probeEveryMs: Long = PROBE_EVERY_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var refusals = 0
    private var nextProbeAt = 0L

    /** True when a plain request may be tried now. While closed, only the first call after each probe interval says yes. */
    @Synchronized fun allow(): Boolean {
        if (refusals < limit) return true
        val now = clock()
        if (now < nextProbeAt) return false
        nextProbeAt = now + probeEveryMs
        return true
    }

    @Synchronized fun refusal() {
        refusals++
        nextProbeAt = clock() + probeEveryMs
    }

    @Synchronized fun success() {
        refusals = 0
        nextProbeAt = 0L
    }

    @Synchronized fun refusalsInARow(): Int = refusals

    companion object {
        /** Browser-check refusals in a row after which plain requests wait for a probe. */
        const val PLAIN_REFUSALS = 3

        /** How often a closed gate lets one plain request through. */
        const val PROBE_EVERY_MS = 60_000L
    }
}

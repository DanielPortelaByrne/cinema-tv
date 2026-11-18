package com.danielportelabyrne.cinema.ui.account

/**
 * A press-and-hold on the remote's OK button, kept apart from any view so it can be tested with a fake clock.
 * A short press ends as [End.CLICK] (the profile opens as it always did); a hold that reaches [holdMs] fires
 * once from [tick] and its key-up ends as [End.CONSUMED], so the profile does not open behind the ceremony.
 */
class HoldGesture(private val holdMs: Long = HOLD_MS) {
    enum class End { CLICK, CANCELLED, CONSUMED }

    private var downAt = -1L
    private var fired = false

    val holding: Boolean get() = downAt >= 0

    /** OK went down at [now]. Key repeats while already held are ignored; true when this started the hold. */
    fun down(now: Long): Boolean {
        if (holding) return false
        downAt = now; fired = false
        return true
    }

    /** How far through the hold [now] is, 0..1 (0 when not holding). */
    fun progress(now: Long): Float =
        if (!holding) 0f else ((now - downAt).toFloat() / holdMs).coerceIn(0f, 1f)

    /** True exactly once per hold: the first time [now] is at or past the hold time. */
    fun tick(now: Long): Boolean {
        if (!holding || fired || now - downAt < holdMs) return false
        fired = true
        return true
    }

    /** OK came up at [now]. A key-up that never saw its key-down (the press began before focus) does nothing. */
    fun up(now: Long): End {
        if (!holding) return End.CANCELLED
        val done = fired
        downAt = -1L; fired = false
        return if (done) End.CONSUMED else End.CLICK
    }

    /** Focus moved, the window lost focus or Back was pressed: forget the hold without opening anything. */
    fun cancel() { downAt = -1L; fired = false }

    companion object { const val HOLD_MS = 3000L }
}

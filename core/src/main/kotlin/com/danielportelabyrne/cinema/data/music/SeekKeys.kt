package com.danielportelabyrne.cinema.data.music

/**
 * Left and Right on now playing: a tap jumps a few seconds, a double tap changes track, holding scans. A tap waits
 * [doubleMs] for a second tap before it counts. [direction] is -1 for back, 1 for forward. Pure, so it can be tested.
 */
class SeekKeys(private val doubleMs: Long = 300, private val holdMs: Long = 400) {
    sealed class Action {
        data class Jump(val direction: Int) : Action()
        data class Skip(val direction: Int) : Action()
        data class ScanStart(val direction: Int) : Action()
        object ScanEnd : Action()
    }

    private var pendingDirection = 0
    private var pendingAt = 0L
    private var pressedAt = 0L
    private var held = false
    var scanning = false
        private set

    fun down(direction: Int, repeat: Int, atMs: Long): Action? {
        if (repeat == 0) {
            held = true
            pressedAt = atMs
            if (pendingDirection == direction && atMs - pendingAt <= doubleMs) { pendingDirection = 0; return Action.Skip(direction) }
            pendingDirection = direction; pendingAt = atMs
            return null
        }
        if (!scanning && atMs - pressedAt >= holdMs) {
            scanning = true; pendingDirection = 0
            return Action.ScanStart(direction)
        }
        return null
    }

    fun up(atMs: Long): Action? {
        held = false
        if (scanning) { scanning = false; return Action.ScanEnd }
        return due(atMs)
    }

    /** The waiting tap, once no second tap can follow. Call every frame. */
    fun due(atMs: Long): Action? {
        if (pendingDirection == 0 || held || atMs - pendingAt <= doubleMs) return null
        return Action.Jump(pendingDirection).also { pendingDirection = 0 }
    }

    companion object {
        const val JUMP_MS = 10_000L

        /** How far a scan has gone after [heldMs]: eight times speed for two seconds, then twenty-four. */
        fun scanOffset(heldMs: Long): Long = if (heldMs <= 2_000) heldMs * 8 else 16_000 + (heldMs - 2_000) * 24
    }
}

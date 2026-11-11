package com.danielportelabyrne.cinema.ui

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Where the Cinema page scrolls to keep the focused card in view. Plain numbers, all in the page's content coordinates
 * (layout positions, before any scale or lift on the card), so it is tested without a screen.
 */
object PageScroll {
    /** Every vertical move takes this long, on the curve (0.22, 1.0, 0.36, 1.0): no bounce, no overshoot. */
    const val DURATION_MS = 240L
    val CURVE = floatArrayOf(0.22f, 1.0f, 0.36f, 1.0f)
    /** Room kept clear above and below the focused card, in dp. */
    const val TOP_CLEAR_DP = 24
    const val BOTTOM_CLEAR_DP = 56

    /**
     * Where the page should rest so the card [itemTop]..[itemBottom] shows with the clearances, moving as little as it
     * can from [scroll]. The row's heading ([headingTop]) is kept in view with the card when both fit; when they do not,
     * or the card alone is taller than the room, the card wins and its top is shown. Clamped to 0..[maxScroll]; moving up
     * to within [topClear] of the top goes all the way, so the page never rests a sliver short of its top.
     */
    fun target(scroll: Int, viewport: Int, maxScroll: Int, itemTop: Int, itemBottom: Int, headingTop: Int?, topClear: Int, bottomClear: Int): Int {
        val room = viewport - topClear - bottomClear
        val groupTop = headingTop?.let { minOf(it, itemTop) } ?: itemTop
        val top = if (itemBottom - groupTop <= room) groupTop else itemTop
        val wanted = when {
            itemBottom - itemTop > room -> itemTop - topClear
            top - topClear < scroll -> top - topClear
            itemBottom + bottomClear > scroll + viewport -> itemBottom + bottomClear - viewport
            else -> scroll
        }
        val clamped = wanted.coerceIn(0, maxOf(0, maxScroll))
        return if (clamped < scroll && clamped < topClear) 0 else clamped
    }

    /**
     * Whether a glide towards [target] should start. Never a second glide to where one is already going ([runningTarget]),
     * and none at all when the page is already there.
     */
    fun shouldStart(current: Int, runningTarget: Int?, target: Int): Boolean = if (runningTarget != null) runningTarget != target else current != target

    /**
     * True when focus moved sideways along one row: same row, and the new card's top is where the last one's was (within a
     * pixel). The page then stays still; only the row moves. A grid row's Up and Down change the top, so they still count.
     */
    fun sideways(sameRow: Boolean, lastTop: Int?, newTop: Int): Boolean = sameRow && lastTop != null && abs(lastTop - newTop) <= 1
}

/** What Left does at the page's left edge (see [NavGate.left]). */
enum class LeftAction {
    /** An ordinary move inside the page. */
    MOVE,
    /** Swallowed: the key is held, or the row is not back at its start yet. Nothing moves. */
    HOLD,
    /** Swallowed, and the row glides (back) to its start. */
    SETTLE_ROW,
    /** Into the navigation rail. */
    ENTER_RAIL
}

/**
 * The rule for entering the navigation rail from the page. Only Left ever enters it, and only a fresh press (not a held
 * key's repeats) made while focus is already on the first card of a row whose scroll is back at its start (within a
 * pixel). A row still gliding left is finished first. A control in no row needs only the fresh press.
 */
object NavGate {
    /** The row's scroll position counts as its start within this many physical pixels. */
    const val START_SLOP_PX = 1

    /**
     * [toRail]: Left from here would leave the page for the rail. [rowScrollX]: the focused card's row position, null for a
     * control in no row. [fresh]: this is a new press (repeat count 0), not a held key.
     */
    fun left(toRail: Boolean, rowScrollX: Int?, fresh: Boolean): LeftAction = when {
        !toRail -> LeftAction.MOVE
        rowScrollX != null && rowScrollX > START_SLOP_PX -> LeftAction.SETTLE_ROW
        fresh -> LeftAction.ENTER_RAIL
        else -> LeftAction.HOLD
    }
}

/**
 * The navigation rail opening and closing, as one number [p]: 0 closed, 1 open. Everything that moves (the page's push,
 * the rail's width, the dim and blur over the page) is read from the same [p], so they can never drift apart.
 */
object RailMotion {
    const val OPEN_MS = 240L
    const val CLOSE_MS = 200L
    /** The page's dim at full open. */
    const val DIM = 0.06f

    /** How long a move from [p] to [target] takes: the full time for the whole way, less for what is left of it. */
    fun duration(p: Float, target: Float): Long {
        val full = if (target > p) OPEN_MS else CLOSE_MS
        return (full * abs(target - p).coerceIn(0f, 1f)).roundToLong()
    }

    /** [p] kept to 0..1, with a value a hair from either end taken as that end, so closing always lands on exactly 0. */
    fun settle(p: Float): Float = when {
        p <= 0.001f -> 0f
        p >= 0.999f -> 1f
        else -> p
    }

    fun lerp(closed: Float, open: Float, p: Float) = closed + (open - closed) * settle(p)

    /** How far the page is pushed right: the rail's extra width when open. */
    fun push(p: Float, closedPx: Float, openPx: Float) = lerp(0f, openPx - closedPx, p)

    fun dim(p: Float) = lerp(0f, DIM, p)
}

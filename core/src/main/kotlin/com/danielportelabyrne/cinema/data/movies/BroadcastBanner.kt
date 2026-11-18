package com.danielportelabyrne.cinema.data.movies

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The times on a channel's station label: when the programme on now began and ends, and when the next is expected. They
 * come from the programme's real length and where it is now, never from a schedule, so the next start is an estimate
 * (marked "~") that only holds while nothing is paused or skipped. Pure; [zone] is injected so it can be tested.
 */
object BroadcastBanner {
    private fun clock(ms: Long, zone: TimeZone) = SimpleDateFormat("HH:mm", Locale.ROOT).apply { timeZone = zone }.format(Date(ms))

    /** "1 h 52 min" or "24 min"; null for a length not known. */
    fun length(durationMs: Long): String? {
        if (durationMs <= 0) return null
        val minutes = ((durationMs + 30_000) / 60_000).toInt().coerceAtLeast(1)
        return if (minutes >= 60) "${minutes / 60} h" + (if (minutes % 60 > 0) " ${minutes % 60} min" else "") else "$minutes min"
    }

    /** "NOW 20:15–20:39 · 24 min", from when it began (now less [positionMs]); only the length when [durationMs] is known but nothing else is; null when the length is not known. */
    fun nowLine(nowMs: Long, positionMs: Long, durationMs: Long, zone: TimeZone = TimeZone.getDefault()): String? {
        val len = length(durationMs) ?: return null
        val start = nowMs - positionMs.coerceAtLeast(0)
        return "NOW ${clock(start, zone)}–${clock(start + durationMs, zone)} · $len"
    }

    /** "NEXT ~20:39 · Title", or "NEXT · Title" when the end of this programme is not known; null with nothing next. */
    fun nextLine(nowMs: Long, positionMs: Long, durationMs: Long, nextTitle: String?, zone: TimeZone = TimeZone.getDefault()): String? {
        val title = nextTitle?.takeIf { it.isNotBlank() } ?: return null
        if (durationMs <= 0) return "NEXT · $title"
        val end = nowMs + (durationMs - positionMs).coerceAtLeast(0)
        return "NEXT ~${clock(end, zone)} · $title"
    }
}

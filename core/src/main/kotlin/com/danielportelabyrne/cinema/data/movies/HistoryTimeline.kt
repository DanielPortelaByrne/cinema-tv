package com.danielportelabyrne.cinema.data.movies

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * One stop on the viewing timeline: a film, or an unbroken run of episodes of one season of one series. A run is a stretch
 * of the viewing order, not the season: it holds only the episodes that were played back to back, in the order the history
 * has them (newest first).
 */
sealed class HistoryItem {
    /** The events in this stop, newest first. Never empty. */
    abstract val cards: List<RecentCard>

    /** The newest event: the one the stop is dated by, and the one OK opens. */
    val lead: RecentCard get() = cards.first()
    val at: Long get() = lead.entry.playedAt

    data class Film(val card: RecentCard) : HistoryItem() { override val cards get() = listOf(card) }

    data class Run(override val cards: List<RecentCard>) : HistoryItem() {
        val title: String get() = lead.title
        val season: Int? get() = lead.entry.season
        /** "Season 2 · 3 episodes". */
        val summary: String get() = listOfNotNull(season?.let { "Season $it" }, if (cards.size == 1) "1 episode" else "${cards.size} episodes").joinToString(" · ")
    }
}

/** A stretch of the timeline under one date heading. */
data class HistoryDay(val label: String, val items: List<HistoryItem>)

/** Pure grouping of the viewing history into timeline stops and days. Reads only what it is given; invents nothing. */
object HistoryTimeline {
    /** [cards] (any order) as stops, newest first. Only adjacent events of one series and season share a stop. */
    fun group(cards: List<RecentCard>): List<HistoryItem> {
        val out = ArrayList<HistoryItem>()
        val run = ArrayList<RecentCard>()
        fun flush() {
            if (run.isEmpty()) return
            out += if (run.first().entry.isEpisode) HistoryItem.Run(run.toList()) else HistoryItem.Film(run.single())
            run.clear()
        }
        // A stable sort: events with one timestamp keep the order they came in.
        for (c in cards.sortedByDescending { it.entry.playedAt }) {
            if (run.isNotEmpty() && !joins(run.last().entry, c.entry)) flush()
            run += c
            if (!c.entry.isEpisode) flush()
        }
        flush()
        return out
    }

    /** Whether [next] (older) continues the run that [last] ends: episodes of the same series, in a known and equal season. */
    private fun joins(last: PlayedEntry, next: PlayedEntry) =
        last.isEpisode && next.isEpisode && last.season != null && last.season == next.season && last.sameTitle(next)

    /** [items] under date headings: Today, Yesterday, the weekday for the rest of the week, then the date. */
    fun days(items: List<HistoryItem>, nowMs: Long, zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): List<HistoryDay> {
        val out = ArrayList<Pair<String, ArrayList<HistoryItem>>>()
        for (item in items) {
            val label = dayLabel(item.at, nowMs, zone, locale)
            if (out.lastOrNull()?.first != label) out += label to ArrayList()
            out.last().second += item
        }
        return out.map { HistoryDay(it.first, it.second) }
    }

    fun dayLabel(atMs: Long, nowMs: Long, zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): String {
        if (atMs <= 0) return "Earlier"
        val ago = Math.round((startOfDay(nowMs, zone) - startOfDay(atMs, zone)) / 86_400_000.0).toInt()
        fun fmt(p: String) = SimpleDateFormat(p, locale).apply { timeZone = zone }.format(atMs)
        return when {
            ago <= 0 -> "Today"
            ago == 1 -> "Yesterday"
            ago < 7 -> fmt("EEEE")
            sameYear(atMs, nowMs, zone) -> fmt("EEEE d MMMM")
            else -> fmt("d MMMM yyyy")
        }
    }

    /** The time of day an event was played, "21:40". Blank when unknown. */
    fun clock(atMs: Long, zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): String =
        if (atMs <= 0) "" else SimpleDateFormat("HH:mm", locale).apply { timeZone = zone }.format(atMs)

    private fun startOfDay(ms: Long, zone: TimeZone) = Calendar.getInstance(zone).apply {
        timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun sameYear(a: Long, b: Long, zone: TimeZone) =
        Calendar.getInstance(zone).apply { timeInMillis = a }.get(Calendar.YEAR) == Calendar.getInstance(zone).apply { timeInMillis = b }.get(Calendar.YEAR)
}

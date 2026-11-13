package com.danielportelabyrne.cinema.data.movies

import org.json.JSONObject
import kotlin.random.Random

/** A title that could be featured on Home: [key] names it, [group] where it came from ("today", a service or a channel). */
data class FeaturedCandidate<T>(val key: String, val group: String, val item: T)

/** How often a title has been featured, since when, and whether the viewer ever opened it. */
data class FeaturedSeen(val shown: Int, val firstAt: Long, val lastAt: Long, val opened: Boolean)

/**
 * Which three titles Home features. The candidates are the number one of each followed service and channel, and what
 * is top today; the three chosen come from three different places, the least shown first, so the landing page does not
 * show the same few titles for days. A title shown [SHOWS_BEFORE_TIRED] times, or for [HOURS_BEFORE_TIRED] hours, without
 * being opened is rested for [REST_DAYS] days; one that was opened gets three times the patience. Pure.
 */
object FeaturedRotation {
    const val SHOWS_BEFORE_TIRED = 4
    const val HOURS_BEFORE_TIRED = 36L
    const val REST_DAYS = 5L
    const val KEPT = 400

    private const val HOUR_MS = 60L * 60 * 1000
    private const val DAY_MS = 24L * HOUR_MS

    /** True when [s] has been featured long enough, or often enough, that it should give way. */
    fun tired(s: FeaturedSeen, now: Long): Boolean {
        val patience = if (s.opened) 3 else 1
        return s.shown >= SHOWS_BEFORE_TIRED * patience || now - s.firstAt >= HOURS_BEFORE_TIRED * HOUR_MS * patience
    }

    /** Whether a title may be featured now: never shown, not tired, or tired and rested long enough. */
    fun eligible(s: FeaturedSeen?, now: Long): Boolean = s == null || !tired(s, now) || now - s.lastAt >= REST_DAYS * DAY_MS

    /**
     * Up to [count] candidates from different groups: the eligible ones first (least shown, then at random), and only when
     * there are too few of those, the tired ones, least recently shown first. [seen] is by key.
     */
    fun <T> pick(candidates: List<FeaturedCandidate<T>>, seen: Map<String, FeaturedSeen>, now: Long, count: Int = 3, random: Random = Random.Default): List<FeaturedCandidate<T>> {
        val fresh = candidates.filter { eligible(seen[it.key], now) }.shuffled(random).sortedBy { seen[it.key]?.takeIf { s -> !tired(s, now) }?.shown ?: 0 }
        val tiredOnes = candidates.filterNot { eligible(seen[it.key], now) }.shuffled(random).sortedBy { seen[it.key]?.lastAt ?: 0L }
        val out = ArrayList<FeaturedCandidate<T>>()
        for (list in listOf(fresh, tiredOnes)) {
            // Three different places first; then, if the places run out, more from the same.
            for (c in list) if (out.size < count && out.none { it.group == c.group } && out.none { it.key == c.key }) out += c
            for (c in list) if (out.size < count && out.none { it.key == c.key }) out += c
        }
        return out
    }

    /** [seen] after [keys] were featured at [now]: a title that had rested starts again from one showing. */
    fun markShown(seen: Map<String, FeaturedSeen>, keys: List<String>, now: Long): Map<String, FeaturedSeen> {
        val next = LinkedHashMap(seen)
        keys.distinct().forEach { k ->
            val s = next[k]
            next[k] = if (s == null || !eligible(s, now) || (tired(s, now) && now - s.lastAt >= REST_DAYS * DAY_MS)) FeaturedSeen(1, now, now, false)
                else s.copy(shown = s.shown + 1, lastAt = now)
        }
        return trim(next)
    }

    /** [seen] with [key] marked as opened, which gives it more patience. Unknown titles are left out: only featured ones matter. */
    fun markOpened(seen: Map<String, FeaturedSeen>, key: String): Map<String, FeaturedSeen> =
        seen[key]?.let { seen + (key to it.copy(opened = true)) } ?: seen

    private fun trim(m: Map<String, FeaturedSeen>): Map<String, FeaturedSeen> =
        if (m.size <= KEPT) m else m.entries.sortedByDescending { it.value.lastAt }.take(KEPT).associate { it.key to it.value }

    fun toJson(m: Map<String, FeaturedSeen>): String = JSONObject().also { o ->
        m.forEach { (k, s) -> o.put(k, org.json.JSONArray().put(s.shown).put(s.firstAt).put(s.lastAt).put(if (s.opened) 1 else 0)) }
    }.toString()

    fun parse(json: String?): Map<String, FeaturedSeen> = try {
        val o = JSONObject(json ?: "{}")
        o.keys().asSequence().mapNotNull { k ->
            val a = o.optJSONArray(k) ?: return@mapNotNull null
            if (a.length() < 4) null else k to FeaturedSeen(a.optInt(0), a.optLong(1), a.optLong(2), a.optInt(3) == 1)
        }.toMap()
    } catch (_: Exception) { emptyMap() }
}

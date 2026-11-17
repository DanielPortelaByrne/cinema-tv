package com.danielportelabyrne.cinema.data.movies

import kotlin.math.ln
import kotlin.random.Random

/**
 * How a channel puts its programmes in order, using what classic network television ran together
 * ([ScheduleKnowledge]). Every round is a weighted random walk: the first programme is picked at random, and each next one
 * is drawn from those left, more likely the more often it ran next to the last one (or on the same night as it), and more
 * likely when their years on air overlap. A title the data does not know keeps the base weight, so nothing is ever left
 * out and anything can still come next. The order is drawn again every round, so it is never the same list twice.
 */
object ChannelOrder {
    /** What a pair with nothing known about it weighs. */
    const val BASE = 1.0
    /** Per ln(1 + seasons) the pair ran directly after one another. */
    const val ADJACENT = 4.0
    /** Per ln(1 + seasons) the pair ran on the same night of a network. */
    const val SAME_NIGHT = 1.5
    /** When one title's year is within [ERA_YEARS] of the other's years on air. */
    const val ERA = 0.75
    const val ERA_YEARS = 2

    /** How much more likely [next] is than a stranger to follow [previous]. At least [BASE]. */
    fun weight(data: ScheduleKnowledge, previous: Title, next: Title): Double {
        val together = data.together(previous.name, next.name)
        var w = BASE
        if (together.adjacent > 0) w += ADJACENT * ln(1.0 + together.adjacent)
        if (together.sameNight > 0) w += SAME_NIGHT * ln(1.0 + together.sameNight)
        if (sameEra(data, previous, next)) w += ERA
        return w
    }

    /** The years a title was on air, from the data for a series it knows, or the release year for a film. */
    private fun years(data: ScheduleKnowledge, title: Title): IntRange? {
        data.find(title.name)?.let { return it.firstYear..it.lastYear }
        return title.year?.takeIf { it > 1900 }?.let { it..it }
    }

    private fun sameEra(data: ScheduleKnowledge, a: Title, b: Title): Boolean {
        // Two titles neither of which the data knows say nothing about an era: a film's year alone is not evidence.
        if (data.find(a.name) == null && data.find(b.name) == null) return false
        val x = years(data, a) ?: return false
        val y = years(data, b) ?: return false
        val gap = maxOf(x.first, y.first) - minOf(x.last, y.last)   // negative or zero when they overlap
        return gap <= ERA_YEARS
    }

    /**
     * [slots] in the order to play them. The first is random (but not the title that played last, when there is a choice),
     * and each next is drawn by [weight]. Every slot appears exactly once.
     */
    fun sequence(slots: List<PlaylistSlot>, random: Random, data: ScheduleKnowledge, lastPlayedTitleKey: String? = null): List<PlaylistSlot> {
        if (slots.size < 2) return slots
        val remaining = slots.toMutableList()
        val firstChoices = remaining.filter { it.title.key != lastPlayedTitleKey }.ifEmpty { remaining }
        val first = firstChoices[random.nextInt(firstChoices.size)]
        remaining.remove(first)
        val order = ArrayList<PlaylistSlot>(slots.size).apply { add(first) }
        while (remaining.isNotEmpty()) {
            val last = order.last().title
            val weights = remaining.map { weight(data, last, it.title) }
            var pick = random.nextDouble() * weights.sum()
            var index = remaining.lastIndex
            for (i in remaining.indices) { pick -= weights[i]; if (pick < 0) { index = i; break } }
            order.add(remaining.removeAt(index))
        }
        return order
    }

    /** Whether the data actually shapes this channel's order: some pair of its titles weighs more than a stranger pair. */
    fun shapes(data: ScheduleKnowledge, slots: List<PlaylistSlot>): Boolean {
        for (i in slots.indices) for (j in slots.indices) {
            if (i != j && weight(data, slots[i].title, slots[j].title) > BASE) return true
        }
        return false
    }
}

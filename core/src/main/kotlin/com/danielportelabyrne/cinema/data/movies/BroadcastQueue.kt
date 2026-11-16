package com.danielportelabyrne.cinema.data.movies

import kotlin.random.Random

/** One shuffled visit per slot in a round. Only a completed programme permits another round. */
class BroadcastQueue(private val random: Random = Random.Default) {
    private val pending = ArrayDeque<String>()
    private var started = false
    private var completed = false
    private var previous: String? = null
    private var previousTitle: String? = null

    fun completed(playback: PlaylistPlayback) {
        if (playback.slotId == previous) completed = true
    }

    /** Shuffles a new round when the last one is used up and another is allowed. */
    private fun refill(eligible: List<PlaylistSlot>) {
        if (pending.isNotEmpty()) return
        if (started && !completed) return
        started = true
        completed = false
        val shuffled = eligible.shuffled(random).toMutableList()
        if (shuffled.size > 1 && shuffled.first().title.key == previousTitle) {
            val alternatives = shuffled.indices.filter { shuffled[it].title.key != previousTitle }
            if (alternatives.isNotEmpty()) {
                val index = alternatives.random(random)
                val first = shuffled[0]
                shuffled[0] = shuffled[index]
                shuffled[index] = first
            }
        }
        pending.addAll(shuffled.map { it.id })
    }

    fun next(slots: List<PlaylistSlot>): PlaylistSlot? {
        val eligible = slots.filterNot { it.filmFinished }
        refill(eligible)
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            val slot = eligible.firstOrNull { it.id == id } ?: continue
            previous = slot.id
            previousTitle = slot.title.key
            return slot
        }
        return null
    }

    /** An upcoming slot is a preview until its episode and source have been resolved. */
    fun peek(slots: List<PlaylistSlot>): PlaylistSlot? =
        pending.firstNotNullOfOrNull { id -> slots.firstOrNull { it.id == id && !it.filmFinished } }

    /**
     * What is on next, in the order it will play. Shuffles now if no round has been drawn yet, and keeps that order,
     * so a channel's preview and the tune-in that follows it agree.
     */
    fun upcoming(slots: List<PlaylistSlot>): List<PlaylistSlot> {
        val eligible = slots.filterNot { it.filmFinished }
        refill(eligible)
        return pending.mapNotNull { id -> eligible.firstOrNull { it.id == id } }
    }
}

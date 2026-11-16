package com.danielportelabyrne.cinema.data.movies

import kotlin.random.Random

/** One shuffled visit per slot in a round. Only a completed programme permits another round. */
class BroadcastQueue(private val random: Random = Random.Default) {
    private val pending = ArrayDeque<String>()
    private var started = false
    private var completed = false
    private var previous: String? = null

    fun completed(playback: PlaylistPlayback) {
        if (playback.slotId == previous) completed = true
    }

    fun next(slots: List<PlaylistSlot>): PlaylistSlot? {
        val eligible = slots.filterNot { it.filmFinished }
        if (pending.isEmpty()) {
            if (started && !completed) return null
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

    private var previousTitle: String? = null
}

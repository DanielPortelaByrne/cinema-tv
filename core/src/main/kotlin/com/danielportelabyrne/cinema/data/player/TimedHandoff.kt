package com.danielportelabyrne.cinema.data.player

/**
 * A one-shot note the player leaves for the page that opened it (the episode the viewer picked, a link to find again).
 * It is only good for a short while and only once: a note nobody collected, such as one left by a player that then failed,
 * must never be found later and played as if the viewer had just asked for it.
 */
class TimedHandoff<T : Any>(private val ttlMs: Long, private val clock: () -> Long) {
    private var held: T? = null
    private var at = 0L

    @Synchronized fun put(value: T) { held = value; at = clock() }

    /** The note, if it is still fresh; either way it is gone afterwards. */
    @Synchronized fun take(): T? {
        val value = held; held = null
        return value?.takeIf { clock() - at in 0 until ttlMs }
    }

    @Synchronized fun clear() { held = null }
}

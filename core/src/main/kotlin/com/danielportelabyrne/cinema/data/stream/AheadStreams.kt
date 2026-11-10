package com.danielportelabyrne.cinema.data.stream

import java.util.concurrent.Future

/**
 * Streams found before Play was pressed, so Play can open one at once. Each is kept under a key (the title, and the
 * episode for a series), for [ttlMs], and at most [max] are held: a new one pushes the oldest out. A held stream is taken
 * once, when it is played. [T] is whatever finding a stream gives; this class never looks inside it.
 */
class AheadStreams<T>(private val max: Int = 2, private val ttlMs: Long = 8 * 60_000L, private val now: () -> Long) {
    private class Held<T>(val at: Long, val result: Future<T>)

    private val held = LinkedHashMap<String, Held<T>>()

    /**
     * Starts [find] for [key], unless one is already held and fresh. Returns whether it started one. [find] only hands the
     * work to another thread and returns its [Future]; that work can ask [holds] to skip itself if it was pushed out.
     */
    @Synchronized fun begin(key: String, find: () -> Future<T>): Boolean {
        dropStale()
        if (held.containsKey(key)) return false
        held[key] = Held(now(), find())
        while (held.size > max) held.remove(held.keys.first())
        return true
    }

    /** Whether a stream for [key] is still held, so work queued for it can tell it has been pushed out or has gone stale. */
    @Synchronized fun holds(key: String): Boolean { dropStale(); return held.containsKey(key) }

    /** The stream for [key], taken out so it is used once; null when none is held or it has gone stale. */
    @Synchronized fun take(key: String): Future<T>? { dropStale(); return held.remove(key)?.result }

    @Synchronized fun clear() = held.clear()

    private fun dropStale() { val t = now(); held.values.removeAll { t - it.at >= ttlMs } }
}

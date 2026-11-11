package com.danielportelabyrne.cinema.data

import java.util.concurrent.ConcurrentHashMap

class TimedCache<V : Any>(private val ttlMs: Long, private val clock: () -> Long = System::currentTimeMillis) {
    private class Held<V>(val value: V, val at: Long)
    private val held = ConcurrentHashMap<String, Held<V>>()
    private val locks = ConcurrentHashMap<String, Any>()

    fun get(key: String, fetch: () -> V?): V? {
        fresh(key)?.let { return it }
        return synchronized(locks.getOrPut(key) { Any() }) {
            fresh(key) ?: fetch()?.also { held[key] = Held(it, clock()) }
        }
    }

    /** What is held for [key] now, if it is younger than [maxAgeMs] (this cache's own limit by default). Never fetches. */
    fun peek(key: String, maxAgeMs: Long = ttlMs): V? = held[key]?.takeIf { clock() - it.at < maxAgeMs }?.value

    fun forget(prefix: String = "") { held.keys.removeIf { it.startsWith(prefix) } }

    private fun fresh(key: String): V? = held[key]?.takeIf { clock() - it.at < ttlMs }?.value
}

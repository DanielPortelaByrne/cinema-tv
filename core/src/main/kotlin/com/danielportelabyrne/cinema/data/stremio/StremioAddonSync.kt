package com.danielportelabyrne.cinema.data.stremio

/**
 * Keeps the TV's add-ons in step with the linked Stremio account, so streaming does not depend on the owner having
 * pressed "Port" in Settings, and add-ons installed in Stremio later are picked up.
 *
 * The account is asked when nothing is saved yet, or when the last check is older than [maxAgeMs]. If Stremio cannot be
 * reached, the saved list is used as it is. Call it off the main thread: it can make a network request.
 */
class StremioAddonSync(
    private val fetch: (key: String) -> List<StremioAddon>?,
    private val clock: () -> Long,
    private val maxAgeMs: Long = 6 * 60 * 60 * 1000L
) {
    private var checkedAt: Long? = null

    @Synchronized
    fun current(key: String?, saved: List<StremioAddon>, save: (List<StremioAddon>) -> Unit): List<StremioAddon> {
        if (key.isNullOrBlank()) return saved
        val last = checkedAt
        if (saved.isNotEmpty() && last != null && clock() - last < maxAgeMs) return saved
        val fresh = fetch(key) ?: return saved
        checkedAt = clock()
        if (fresh != saved) save(fresh)
        return fresh
    }
}

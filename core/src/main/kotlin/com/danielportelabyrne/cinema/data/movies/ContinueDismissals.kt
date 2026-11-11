package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.platform.Platform
import org.json.JSONObject

/**
 * Cards the viewer removed from Continue watching. A card stays away until it is played again: what is kept is when it was
 * last played at removal, and only a later viewing brings it back. Each server and user has their own, like favourites.
 */
class ContinueDismissals(platform: Platform) {
    private val prefs = platform.preferences("continue_dismissed")

    private fun key(scope: String) = "gone_" + scope.ifBlank { "local" }

    /** Card key to the time it was last played when removed. */
    fun all(scope: String): Map<String, Long> = try {
        val o = JSONObject(prefs.getString(key(scope), null) ?: "{}")
        o.keys().asSequence().associateWith { o.optLong(it) }
    } catch (_: Exception) { emptyMap() }

    fun dismiss(scope: String, cardKey: String, lastPlayedAt: Long) {
        val o = JSONObject(all(scope).plus(cardKey to lastPlayedAt).entries.sortedByDescending { it.value }.take(CAP).associate { it.key to it.value })
        prefs.edit().putString(key(scope), o.toString()).apply()
    }

    companion object {
        const val CAP = 200

        /** Whether [e] was removed and has not been played since. */
        fun hidden(gone: Map<String, Long>, e: ContinueEntry): Boolean = gone[e.key]?.let { e.lastPlayedAt <= it } == true
    }
}

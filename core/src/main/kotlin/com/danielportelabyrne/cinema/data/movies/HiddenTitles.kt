package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.platform.Platform
import org.json.JSONArray
import org.json.JSONObject

/**
 * Titles the viewer hid from every list (hold OK on a card, Hide). Each server and user has their own, like favourites.
 * A title is recognised by any id it carries (TMDB, IMDb) or, failing those, its name and year, so the same film hides
 * wherever it turns up: a shelf, a genre page, search, a library row.
 */
class HiddenTitles(platform: Platform) {
    private val prefs = platform.preferences("hidden_titles")

    private fun key(scope: String) = "hidden_" + scope.ifBlank { "local" }

    /** What was hidden: its name (to say how many) and every key it can be recognised by. */
    class Hidden(val name: String, val keys: Set<String>)

    @Volatile private var cache: Pair<String, List<Hidden>>? = null
    @Volatile private var cachedKeys: Pair<String, Set<String>>? = null

    fun list(scope: String): List<Hidden> {
        cache?.takeIf { it.first == scope }?.let { return it.second }
        val parsed = try {
            val a = JSONArray(prefs.getString(key(scope), null) ?: "[]")
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val k = o.optJSONArray("k") ?: return@mapNotNull null
                Hidden(o.optString("n"), (0 until k.length()).map { k.getString(it) }.toSet())
            }
        } catch (_: Exception) { emptyList() }
        cache = scope to parsed
        return parsed
    }

    private fun allKeys(scope: String): Set<String> {
        cachedKeys?.takeIf { it.first == scope }?.let { return it.second }
        return list(scope).flatMapTo(HashSet()) { it.keys }.also { cachedKeys = scope to it }
    }

    fun count(scope: String) = list(scope).size

    /** Whether a title with these ids is hidden. */
    fun isHidden(scope: String, kind: Kind, tmdbId: String?, imdbId: String?, name: String, year: Int?): Boolean {
        val hidden = allKeys(scope)
        return hidden.isNotEmpty() && keysOf(kind, tmdbId, imdbId, name, year).any { it in hidden }
    }

    fun hide(scope: String, kind: Kind, tmdbId: String?, imdbId: String?, name: String, year: Int?) {
        val keys = keysOf(kind, tmdbId, imdbId, name, year)
        if (keys.isEmpty()) return
        save(scope, list(scope) + Hidden(name, keys))
    }

    /** Brings every hidden title back. */
    fun clear(scope: String) = save(scope, emptyList())

    private fun save(scope: String, items: List<Hidden>) {
        val a = JSONArray()
        items.takeLast(CAP).forEach { h -> a.put(JSONObject().put("n", h.name).put("k", JSONArray(h.keys.toList()))) }
        prefs.edit().putString(key(scope), a.toString()).apply()
        cache = null; cachedKeys = null
    }

    companion object {
        const val CAP = 2000

        /** Every key a title can be recognised by; a kind keeps a film's TMDB id apart from a series'. */
        fun keysOf(kind: Kind, tmdbId: String?, imdbId: String?, name: String, year: Int?): Set<String> = buildSet {
            val k = if (kind == Kind.SHOW) "s" else "m"
            tmdbId?.takeIf { it.isNotBlank() }?.let { add("$k:tmdb:$it") }
            imdbId?.takeIf { it.isNotBlank() }?.let { add("imdb:$it") }
            if (name.isNotBlank()) add("$k:name:${name.trim().lowercase()}:${year ?: 0}")
        }
    }
}

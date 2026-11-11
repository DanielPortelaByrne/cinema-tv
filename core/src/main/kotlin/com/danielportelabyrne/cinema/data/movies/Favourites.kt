package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.platform.Platform
import org.json.JSONArray
import org.json.JSONObject

/**
 * A film or whole series the owner has favourited. Keeps every id it is known by (TMDB, IMDb, Jellyfin) so a streamed
 * copy and a library copy of the same title are one favourite, plus enough to draw a card without the network.
 */
data class Favourite(
    val kind: Kind,
    val tmdbId: String?,
    val imdbId: String?,
    val jellyfinId: String?,
    val name: String,
    val year: Int?,
    val poster: String?,
    val backdrop: String?,
    val addedAt: Long
) {
    /** True when [other] is the same title: same kind and at least one id in common. Never compared by name. */
    fun sameAs(other: Favourite) = kind == other.kind && (
        shares(tmdbId, other.tmdbId) || shares(imdbId, other.imdbId) || shares(jellyfinId, other.jellyfinId))

    private fun shares(a: String?, b: String?) = !a.isNullOrBlank() && a == b

    val hasId get() = !tmdbId.isNullOrBlank() || !imdbId.isNullOrBlank() || !jellyfinId.isNullOrBlank()

    /** This favourite with the ids and details [o] knows that this one does not. */
    fun learn(o: Favourite) = copy(
        tmdbId = tmdbId ?: o.tmdbId, imdbId = imdbId ?: o.imdbId, jellyfinId = jellyfinId ?: o.jellyfinId,
        poster = poster ?: o.poster, backdrop = backdrop ?: o.backdrop, year = year ?: o.year
    )
}

/** Favourite lists, kept newest first. Plain Kotlin so it is tested without a TV. */
object Favourites {
    fun contains(list: List<Favourite>, f: Favourite) = list.any { it.sameAs(f) }

    /** [f] at the front, absorbing the ids of any entry it matches. A title with no id at all is not kept. */
    fun add(list: List<Favourite>, f: Favourite): List<Favourite> {
        if (!f.hasId) return list
        val merged = list.filter { it.sameAs(f) }.fold(f) { acc, old -> acc.learn(old) }
        return listOf(merged) + list.filterNot { it.sameAs(f) }
    }

    fun remove(list: List<Favourite>, f: Favourite): List<Favourite> = list.filterNot { it.sameAs(f) }

    /** What the Home shelf shows: newest first (the list's own order), one card per title even if the list somehow holds two. */
    fun shelf(list: List<Favourite>): List<Favourite> = list.fold(emptyList()) { acc, f -> if (acc.any { it.sameAs(f) }) acc else acc + f }

    /** Where focus goes when the card at [removedIndex] is removed from a shelf now [sizeAfter] long: the card that took its place, else the one before it, else -1. */
    fun focusAfterRemoval(removedIndex: Int, sizeAfter: Int): Int = if (sizeAfter <= 0) -1 else minOf(removedIndex, sizeAfter - 1)

    /**
     * Where focus goes after the shelf changes from [old] to [now] with [focused] (an index in [old]) focused: the same
     * title wherever it moved to, else the nearest one that stayed (the one that followed, then those before), else -1.
     */
    fun focusAfter(old: List<Favourite>, focused: Int, now: List<Favourite>): Int {
        if (focused !in old.indices) return -1
        fun at(f: Favourite) = now.indexOfFirst { it.sameAs(f) }
        for (i in focused until old.size) at(old[i]).takeIf { it >= 0 }?.let { return it }
        for (i in focused - 1 downTo 0) at(old[i]).takeIf { it >= 0 }?.let { return it }
        return -1
    }

    /** When shelf [from] empties: the next visible shelf, else the previous one, else -1. [visible] says which shelves are showing. */
    fun nearestVisible(visible: List<Boolean>, from: Int): Int =
        (from + 1 until visible.size).firstOrNull { visible[it] } ?: (from - 1 downTo 0).firstOrNull { visible[it] } ?: -1

    fun toJson(list: List<Favourite>): String = JSONArray().also { a ->
        list.forEach { f ->
            a.put(JSONObject().put("kind", f.kind.name).put("tmdb", f.tmdbId ?: "").put("imdb", f.imdbId ?: "").put("jf", f.jellyfinId ?: "")
                .put("name", f.name).put("year", f.year ?: 0).put("poster", f.poster ?: "").put("backdrop", f.backdrop ?: "").put("at", f.addedAt))
        }
    }.toString()

    fun parse(json: String?): List<Favourite> = try {
        val a = JSONArray(json ?: "[]")
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrNull() ?: return@mapNotNull null
            val f = Favourite(kind, o.optString("tmdb").ifBlank { null }, o.optString("imdb").ifBlank { null }, o.optString("jf").ifBlank { null },
                o.optString("name"), o.optInt("year").takeIf { it > 0 }, o.optString("poster").ifBlank { null }, o.optString("backdrop").ifBlank { null }, o.optLong("at"))
            f.takeIf { it.hasId }
        }
    } catch (_: Exception) { emptyList() }
}

/**
 * Favourites kept on this TV, in their own preferences (not the watchlist, not Letterboxd). Each Jellyfin server and
 * signed-in user has its own list, so a Jellyfin id from one server never matches another's. [scope] is blank before
 * any server is set up; a list is then kept under a shared "local" key.
 */
class FavouritesStore(platform: Platform) {
    private val prefs = platform.preferences("favourites")

    private fun key(scope: String) = "favs_" + scope.ifBlank { "local" }

    /**
     * The list for [scope]. A server and user with no list of their own yet start from the one kept before any server was
     * set up, so favourites chosen then are not lost on connecting Jellyfin; it never carries a Jellyfin id across.
     */
    fun list(scope: String): List<Favourite> {
        prefs.getString(key(scope), null)?.let { return Favourites.parse(it) }
        if (scope.isBlank()) return emptyList()
        return Favourites.parse(prefs.getString(key(""), null)).map { it.copy(jellyfinId = null) }.filter { it.hasId }
    }

    fun isFavourite(scope: String, f: Favourite) = Favourites.contains(list(scope), f)

    /** Flips [f]; returns true when it is now a favourite. */
    fun toggle(scope: String, f: Favourite): Boolean {
        val now = list(scope)
        val on = !Favourites.contains(now, f)
        save(scope, if (on) Favourites.add(now, f) else Favourites.remove(now, f))
        return on
    }

    private fun save(scope: String, list: List<Favourite>) { prefs.edit().putString(key(scope), Favourites.toJson(list)).apply() }

    /** Adds [items] (newest first, as kept) to what [scope] holds: a restore never loses a favourite already here. */
    fun merge(scope: String, items: List<Favourite>) {
        if (items.isEmpty()) return
        // Oldest first, each to the front, so the restored ones keep their order; one already here keeps its place in time.
        val merged = items.asReversed().fold(list(scope)) { acc, f -> if (Favourites.contains(acc, f)) acc else Favourites.add(acc, f) }
        save(scope, merged.sortedByDescending { it.addedAt })
    }

    companion object {
        /** The list a signed-in Jellyfin user on [server] owns. */
        fun scope(server: String, userId: String?): String = if (server.isBlank()) "" else server.trimEnd('/').lowercase() + "|" + userId.orEmpty()
    }
}

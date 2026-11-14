package com.danielportelabyrne.cinema.data.account

import com.danielportelabyrne.cinema.platform.Preferences

/**
 * Starts one person as a copy of another: every personal file (watchlist, layouts, followed services, favourites,
 * history, picks, searches, Letterboxd follows, resume points) copied across, keys that carry the first person's
 * Jellyfin id ("favs_<server>|<id>") moved to the second's. The second person's own character is kept.
 */
object ProfileCopy {
    /** The personal settings that, for the person who had the TV before accounts, may still sit in the TV's settings file. */
    val LEGACY_KEYS = listOf("library_order", "stream_progress", "featured_history", "page_layouts", "followed_channels",
        "followed_services", "watchlist_films") + com.danielportelabyrne.cinema.data.search.SearchKeyboard.values().map { it.key }

    /** Copies [fromId]'s files ([from]) over [toId]'s ([to]); [legacy] is the TV's settings file when [fromId] owned it. Returns how many values. */
    fun copy(from: (String) -> Preferences, to: (String) -> Preferences, legacy: Preferences?, fromId: String, toId: String): Int {
        var n = 0
        ProfileScope.PERSONAL.forEach { name ->
            val source = from(name).all
            val target = to(name)
            val ownAvatar = if (name == "cinema_personal") target.getString("avatar", null) else null
            val e = target.edit().clear()
            source.forEach { (k, v) ->
                if (name == "cinema_personal" && k == "avatar") return@forEach
                val key = k.replace(fromId, toId)
                when (v) {
                    is String -> e.putString(key, v)
                    is Boolean -> e.putBoolean(key, v)
                    is Long -> e.putLong(key, v)
                    is Int -> e.putInt(key, v)
                    else -> return@forEach
                }
                n++
            }
            if (name == "cinema_personal" && legacy != null) LEGACY_KEYS.forEach { k ->
                if (!source.containsKey(k)) legacy.getString(k, null)?.let { e.putString(k, it); n++ }
            }
            ownAvatar?.let { e.putString("avatar", it) }
            e.commit()
        }
        return n
    }
}

package com.danielportelabyrne.cinema.ui.account

import com.danielportelabyrne.cinema.data.movies.Favourite
import com.danielportelabyrne.cinema.data.movies.SeerrClient

/**
 * The characters of someone's favourite films and series, to be on Cinema as: the actors' photos for live action, and
 * for an animation its own artwork (its characters are drawn; the cast are only their voices). Blocks: not for the main
 * thread. Each is (picture id for Avatars, what the picker calls it).
 */
object FavouriteCharacters {
    fun find(favourites: List<Favourite>, seerr: SeerrClient, titles: Int = 5, perTitle: Int = 4, limit: Int = 14): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        favourites.filter { it.tmdbId?.toLongOrNull() != null }.take(titles).forEach { f ->
            val (animated, cast) = runCatching { seerr.castOf(f.kind, f.tmdbId!!.toLong()) }.getOrNull() ?: return@forEach
            if (animated) {
                // Each character's own drawing (from their Wikipedia article); the title's poster only when none is found.
                val drawn = cast.take(6).mapNotNull { m -> com.danielportelabyrne.cinema.data.movies.CharacterArt.find(m.character, f.name)?.let { url -> m to url } }
                if (drawn.isNotEmpty()) drawn.forEach { (m, url) -> out += Avatars.artId(url, m.character) to "${m.character} · ${f.name}" }
                else f.poster?.let { url -> out += Avatars.artId(url, f.name) to f.name }
            } else {
                cast.take(perTitle).forEach { m -> out += Avatars.castId(m.profilePath, m.character) to "${m.character} · ${f.name}" }
            }
        }
        return out.distinctBy { it.first }.take(limit)
    }
}

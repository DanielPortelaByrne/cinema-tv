package com.danielportelabyrne.cinema.mobile.request

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.movies.Kind
import org.json.JSONArray
import org.json.JSONObject

/**
 * A request the owner also wants on this phone, once the server has it. A series names the [episodes] still to come;
 * a film has none.
 */
data class Waiting(
    val kind: Kind, val tmdbId: String, val imdbId: String?, val name: String, val poster: String?,
    val episodes: Set<EpisodeNumber> = emptySet(),
) {
    val key: String get() = "${kind.name}:$tmdbId"

    /** What is left once [items] have arrived, or null when nothing is. */
    fun without(items: List<JellyfinItem>): Waiting? {
        if (items.isEmpty()) return this
        if (kind == Kind.MOVIE) return null
        val left = episodes - items.flatMap { it.episodeNumbers() }.toSet()
        return if (left.isEmpty()) null else copy(episodes = left)
    }

    fun toJson(): JSONObject = JSONObject().put("kind", kind.name).put("tmdb", tmdbId).put("imdb", imdbId).put("name", name).put("poster", poster)
        .put("episodes", JSONArray(episodes.map { JSONArray(listOf(it.season, it.episode)) }))

    companion object {
        private fun JSONObject.text(name: String): String? = if (isNull(name)) null else optString(name).ifBlank { null }

        fun of(o: JSONObject): Waiting {
            val e = o.optJSONArray("episodes")
            return Waiting(Kind.valueOf(o.getString("kind")), o.getString("tmdb"), o.text("imdb"), o.getString("name"), o.text("poster"),
                (0 until (e?.length() ?: 0)).map { i -> e!!.getJSONArray(i).let { EpisodeNumber(it.getInt(0), it.getInt(1)) } }.toSet())
        }
    }
}

/** The requests waiting to be saved to the phone. Change it on the main thread only. */
class WaitingList(context: Context) {
    private val prefs = context.getSharedPreferences("waiting", Context.MODE_PRIVATE)
    val items = mutableStateListOf<Waiting>().apply {
        addAll(runCatching { JSONArray(prefs.getString("waiting", "[]")).let { a -> (0 until a.length()).map { Waiting.of(a.getJSONObject(it)) } } }.getOrDefault(emptyList()))
    }

    private fun save() = prefs.edit().putString("waiting", JSONArray(items.map { it.toJson() }).toString()).apply()

    fun find(key: String): Waiting? = items.firstOrNull { it.key == key }

    /** Waits for [want], along with anything already waited for of the same title. */
    fun add(want: Waiting) {
        val i = items.indexOfFirst { it.key == want.key }
        if (i < 0) items += want else items[i] = want.copy(episodes = items[i].episodes + want.episodes)
        save()
    }

    fun arrived(key: String, arrived: List<JellyfinItem>) {
        val i = items.indexOfFirst { it.key == key }
        if (i < 0) return
        val left = items[i].without(arrived)
        if (left == null) items.removeAt(i) else items[i] = left
        save()
    }

    fun remove(key: String) { if (items.removeAll { it.key == key }) save() }

    fun clear() { items.clear(); save() }
}

/** Looks on the Jellyfin server for what is being waited for. */
object ServerWatch {
    /** The episodes of [all] that [want] is still waiting for and that have a file. */
    fun ready(want: Waiting, all: List<JellyfinItem>): List<JellyfinItem> =
        all.filter { e -> !e.missing && e.sizeBytes != null && e.episodeNumbers().any { it in want.episodes } }

    /** Each wanted title with whatever of it the server now has. Blocking. */
    fun look(client: JellyfinClient, wants: List<Waiting>): List<Pair<Waiting, List<JellyfinItem>>> {
        val library = client.library(force = true)
        return wants.mapNotNull { want ->
            val found = JellyfinApi.findByIds(library, want.imdbId, want.tmdbId, want.kind == Kind.SHOW) ?: return@mapNotNull null
            val items = if (want.kind == Kind.SHOW) ready(want, client.episodes(found.id))
            // The full item, for its size: until the server knows that, the file is not ready.
            else listOfNotNull(client.item(found.id)?.takeIf { it.sizeBytes != null })
            if (items.isEmpty()) null else want to items
        }
    }
}

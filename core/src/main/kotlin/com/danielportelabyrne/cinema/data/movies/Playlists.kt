package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.platform.Platform
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PlaylistEpisode(val season: Int, val number: Int) : Comparable<PlaylistEpisode> {
    init {
        require(season >= 0 && number > 0)
    }

    override fun compareTo(other: PlaylistEpisode): Int =
        compareValuesBy(this, other, PlaylistEpisode::season, PlaylistEpisode::number)

    override fun toString() = "S${season}E${number}"

    internal fun json() = JSONArray().put(season).put(number)

    companion object {
        internal fun read(value: JSONArray?) = value?.let { PlaylistEpisode(it.getInt(0), it.getInt(1)) }
    }
}

data class PlaylistSlot(
    val title: Title,
    val libraryId: String? = null,
    val start: PlaylistEpisode? = null,
    val id: String = UUID.randomUUID().toString(),
    val lastFinished: PlaylistEpisode? = null,
    val filmFinished: Boolean = false,
    val pending: PlaylistEpisode? = null
) {
    val queuedEpisode: PlaylistEpisode?
        get() = pending ?: start.takeIf { lastFinished == null }

    fun nextEpisode(available: Iterable<PlaylistEpisode>): PlaylistEpisode? {
        queuedEpisode?.let { return it }
        val minimumSeason = if (start?.season == 0) 0 else 1
        return available.asSequence()
            .filter { it.season >= minimumSeason && (lastFinished == null || it > lastFinished) }
            .minOrNull()
    }
}

data class BroadcastPlaylist(
    val name: String,
    val id: String = UUID.randomUUID().toString(),
    val slots: List<PlaylistSlot> = emptyList()
)

/** Identifies the exact slot and episode handed to the player. */
data class PlaylistPlayback(
    val playlistId: String, val slotId: String, val episode: PlaylistEpisode?,
    val station: String? = null, val nextTitle: String? = null
) {
    fun encode(): String = JSONObject().put("playlist", playlistId).put("slot", slotId).put("episode", episode?.json())
        .put("station", station).put("nextTitle", nextTitle).toString()

    companion object {
        fun decode(raw: String?): PlaylistPlayback? = raw?.let {
            runCatching {
                val value = JSONObject(it)
                PlaylistPlayback(value.getString("playlist"), value.getString("slot"), PlaylistEpisode.read(value.optJSONArray("episode")),
                    value.optString("station").ifBlank { null }, value.optString("nextTitle").ifBlank { null })
            }.getOrNull()
        }
    }
}

class PlaylistStore(platform: Platform) {
    private val prefs = platform.preferences("broadcast_playlists")

    fun all(): List<BroadcastPlaylist> = runCatching {
        JSONArray(prefs.getString("list", "[]") ?: "[]").objects().map { value ->
            BroadcastPlaylist(value.getString("name"), value.getString("id"), value.getJSONArray("slots").objects().map(::readSlot))
        }
    }.getOrDefault(emptyList())

    fun create(name: String): BroadcastPlaylist {
        val playlist = BroadcastPlaylist(name.trim())
        synchronized(lock) { save(all() + playlist) }
        return playlist
    }

    fun delete(id: String) = synchronized(lock) { save(all().filterNot { it.id == id }) }

    fun update(id: String, change: (BroadcastPlaylist) -> BroadcastPlaylist) = synchronized(lock) {
        save(all().map { if (it.id == id) change(it) else it })
    }

    fun resetProgress(id: String) = update(id) { playlist ->
        playlist.copy(slots = playlist.slots.map { it.copy(lastFinished = null, pending = null, filmFinished = false) })
    }

    fun queue(playback: PlaylistPlayback) {
        val episode = playback.episode ?: return
        update(playback.playlistId) { playlist ->
            playlist.copy(slots = playlist.slots.map { slot ->
                if (slot.id == playback.slotId && slot.queuedEpisode == null &&
                    (slot.lastFinished == null || episode > slot.lastFinished)) slot.copy(pending = episode)
                else slot
            })
        }
    }

    fun finish(playback: PlaylistPlayback) = update(playback.playlistId) { playlist ->
        playlist.copy(slots = playlist.slots.map { slot ->
            when {
                slot.id != playback.slotId -> slot
                slot.title.kind == Kind.MOVIE && playback.episode == null -> slot.copy(filmFinished = true)
                slot.title.kind == Kind.SHOW && playback.episode != null &&
                    (slot.queuedEpisode == null || playback.episode == slot.queuedEpisode) &&
                    (slot.lastFinished == null || playback.episode > slot.lastFinished) ->
                    slot.copy(lastFinished = playback.episode, pending = null)
                else -> slot
            }
        })
    }

    private fun save(list: List<BroadcastPlaylist>) {
        val json = JSONArray()
        list.forEach { playlist ->
            val slots = JSONArray()
            playlist.slots.forEach { slots.put(writeSlot(it)) }
            json.put(JSONObject().put("name", playlist.name).put("id", playlist.id).put("slots", slots))
        }
        prefs.edit().putString("list", json.toString()).apply()
    }

    private fun writeSlot(slot: PlaylistSlot): JSONObject {
        val title = slot.title
        return JSONObject().put("id", slot.id).put("key", title.key).put("kind", title.kind.name)
            .put("name", title.name).put("year", title.year).put("runtime", title.runtimeMin)
            .put("imdb", title.imdbId).put("tmdb", title.tmdbId).put("library", slot.libraryId)
            .put("poster", title.posterUrl).put("backdrop", title.backdropUrl)
            .put("start", slot.start?.json()).put("lastFinished", slot.lastFinished?.json())
            .put("filmFinished", slot.filmFinished).put("pending", slot.pending?.json())
    }

    private fun readSlot(value: JSONObject): PlaylistSlot {
        fun string(key: String) = value.optString(key).ifBlank { null }
        val title = Title(value.getString("key"), Kind.valueOf(value.getString("kind")), value.getString("name"),
            value.optInt("year").takeIf { it > 0 }, null, value.optInt("runtime").takeIf { it > 0 }, emptyList(),
            null, null, string("poster"), string("backdrop"), string("imdb"), string("tmdb"))
        return PlaylistSlot(title, string("library"), PlaylistEpisode.read(value.optJSONArray("start")),
            value.getString("id"), PlaylistEpisode.read(value.optJSONArray("lastFinished")), value.optBoolean("filmFinished"),
            PlaylistEpisode.read(value.optJSONArray("pending")))
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private companion object {
        val lock = Any()
    }
}

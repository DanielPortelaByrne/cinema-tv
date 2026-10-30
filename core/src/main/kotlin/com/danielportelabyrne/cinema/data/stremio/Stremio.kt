package com.danielportelabyrne.cinema.data.stremio

import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.Title
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A sign-in the owner approves from a phone: the short [code], the page to type it into and the same page as a QR picture. */
data class StremioLink(val code: String, val link: String, val qr: String)

/**
 * An add-on installed in the owner's Stremio account. [transportUrl] is the add-on's manifest address, which can carry the
 * owner's own settings, so it is kept as a secret like the sign-in key. [streams] is whether it answers for streams.
 */
data class StremioAddon(val name: String, val types: List<String>, val transportUrl: String, val streams: Boolean)

/** One film saved in the owner's Stremio library. [added] is Stremio's own time stamp, which sorts as text. */
data class StremioFilm(val imdbId: String, val name: String, val poster: String?, val year: Int?, val watched: Boolean, val added: String)

/** The plain-Kotlin half of the Stremio integration: how answers from Stremio's public servers are read. */
object StremioApi {
    fun link(json: String): StremioLink? = try {
        val r = JSONObject(json).optJSONObject("result")
        val code = r?.optString("code").orEmpty()
        if (code.isBlank()) null else StremioLink(code, r!!.optString("link"), r.optString("qrcode"))
    } catch (_: Exception) { null }

    /** The sign-in key once the phone has approved the code; null while it is still waiting. */
    fun authKey(json: String): String? = try {
        JSONObject(json).optJSONObject("result")?.optString("authKey")?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    /** The add-ons the owner ported to Cinema, as saved by [savedAddonsJson]. Anything unreadable gives none. */
    fun savedAddons(json: String?): List<StremioAddon> = try {
        val a = JSONArray(json ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            StremioAddon(o.optString("name"), (o.optJSONArray("types") ?: JSONArray()).let { t -> (0 until t.length()).map { t.optString(it) } },
                o.optString("transportUrl"), o.optBoolean("streams"))
        }
    } catch (_: Exception) { emptyList() }

    fun savedAddonsJson(addons: List<StremioAddon>): String = JSONArray(addons.map { a ->
        JSONObject().put("name", a.name).put("types", JSONArray(a.types)).put("transportUrl", a.transportUrl).put("streams", a.streams)
    }).toString()

    /** The add-ons installed in the account, in Stremio's order. Ones without a manifest or address are left out. */
    fun addons(json: String): List<StremioAddon> = try {
        val a = JSONObject(json).optJSONObject("result")?.optJSONArray("addons") ?: JSONArray()
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val m = o.optJSONObject("manifest") ?: return@mapNotNull null
            val url = o.optString("transportUrl").takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val resources = m.optJSONArray("resources") ?: JSONArray()
            val answers = (0 until resources.length()).any { r ->
                val res = resources.opt(r)
                (res as? String) == "stream" || (res as? JSONObject)?.optString("name") == "stream"
            }
            StremioAddon(
                m.optString("name").ifBlank { url }, m.optJSONArray("types")?.let { t -> (0 until t.length()).map { t.optString(it) } } ?: emptyList(),
                url, answers
            )
        }
    } catch (_: Exception) { emptyList() }

    /**
     * Saved films, newest first, left out when removed from the library or only a temporary entry from watching. Series and
     * anything without an IMDb id (anime, channels) are not films Cinema can look up.
     */
    fun films(json: String): List<StremioFilm> = try {
        val a = JSONObject(json).optJSONArray("result") ?: JSONArray()
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("_id")
            if (o.optString("type") != "movie" || !Regex("tt\\d+").matches(id) || o.optBoolean("removed") || o.optBoolean("temp")) return@mapNotNull null
            val state = o.optJSONObject("state")
            StremioFilm(
                id, o.optString("name").ifBlank { id }, o.optString("poster").takeIf { it.startsWith("http") },
                Regex("\\d{4}").find(o.optString("year"))?.value?.toIntOrNull(),
                (state?.optInt("timesWatched") ?: 0) > 0 || (state?.optInt("flaggedWatched") ?: 0) > 0,
                o.optString("_mtime")
            )
        }.sortedByDescending { it.added }.distinctBy { it.imdbId }
    } catch (_: Exception) { emptyList() }

    /** The full film page for [film] from Cinemeta's answer, or null when it is not a film answer. */
    fun title(json: String, film: StremioFilm): Title? = try {
        val m = JSONObject(json).optJSONObject("meta") ?: return null
        Title(
            key = film.imdbId, kind = Kind.MOVIE, name = m.optString("name").ifBlank { film.name },
            year = Regex("\\d{4}").find(m.optString("year"))?.value?.toIntOrNull() ?: film.year,
            overview = m.optString("description").ifBlank { null },
            runtimeMin = Regex("\\d+").find(m.optString("runtime"))?.value?.toIntOrNull(),
            genres = m.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.optString(it) } } ?: emptyList(),
            age = null, imdbScore = m.optString("imdbRating").toDoubleOrNull(),
            posterUrl = m.optString("poster").ifBlank { film.poster }, backdropUrl = m.optString("background").ifBlank { null },
            imdbId = film.imdbId, tmdbId = m.optString("moviedb_id").ifBlank { null }
        )
    } catch (_: Exception) { null }

    /** The page when Cinemeta has nothing: just what the library holds. */
    fun bare(film: StremioFilm) = Title(film.imdbId, Kind.MOVIE, film.name, film.year, null, null, emptyList(), null, null, film.poster, null, film.imdbId, null)
}

/** One torrent an add-on offered: its release [title], the torrent's [infoHash], its [seeders] and its [size] in bytes. */
data class AddonStream(val title: String, val infoHash: String, val seeders: Int, val size: Long)

object AddonStreams {
    /** The torrents in an add-on's answer. Only those with a readable hash, a release name, seeders and a size are kept. */
    fun parse(json: String): List<AddonStream> = try {
        val a = JSONObject(json).optJSONArray("streams") ?: JSONArray()
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val hash = o.optString("infoHash").lowercase()
            val text = o.optString("title")
            val name = text.lineSequence().firstOrNull()?.trim().orEmpty()
            val seeds = Regex("👤\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
            val size = Regex("💾\\s*([\\d.,]+)\\s*(GB|MB)").find(text)
            if (!Regex("[0-9a-f]{40}").matches(hash) || name.isEmpty() || seeds == null || size == null) return@mapNotNull null
            val amount = size.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            val unit = if (size.groupValues[2] == "GB") 1024.0 * 1024 * 1024 else 1024.0 * 1024
            AddonStream(name, hash, seeds, (amount * unit).toLong())
        }
    } catch (_: Exception) { emptyList() }
}

/** Talks to Stremio's public servers. Every call blocks, never throws, and answers null when Stremio can't be reached. */
class StremioClient(private val send: (method: String, url: String, body: String?) -> String? = ::request) {
    fun createLink(): StremioLink? = send("POST", "https://link.stremio.com/api/v2/create?type=Create", null)?.let { StremioApi.link(it) }

    /** The sign-in key once [code] has been approved on the phone, else null. */
    fun readLink(code: String): String? = send("GET", "https://link.stremio.com/api/v2/read?type=Read&code=$code", null)?.let { StremioApi.authKey(it) }

    /** Saved films; null when Stremio can't be reached or the key no longer works. */
    fun films(authKey: String): List<StremioFilm>? {
        val reply = send("POST", "https://api.strem.io/api/datastoreGet", libraryRequest(authKey)) ?: return null
        return try { if (JSONObject(reply).has("result")) StremioApi.films(reply) else null } catch (_: Exception) { null }
    }

    /** The add-ons installed in the account; null when Stremio can't be reached or the key no longer works. */
    fun addons(authKey: String): List<StremioAddon>? {
        val body = JSONObject().put("authKey", authKey).put("update", true).toString()
        val reply = send("POST", "https://api.strem.io/api/addonCollectionGet", body) ?: return null
        return try { if (JSONObject(reply).has("result")) StremioApi.addons(reply) else null } catch (_: Exception) { null }
    }

    /** True when Stremio says the key is no longer valid (signed out everywhere), so the owner has to link again. */
    fun keyRejected(authKey: String): Boolean =
        send("POST", "https://api.strem.io/api/datastoreGet", libraryRequest(authKey))?.let { r -> try { JSONObject(r).has("error") } catch (_: Exception) { false } } ?: false

    /** What one add-on offers for a film ([episode] null) or an episode (season, episode), asked from this device. */
    fun addonStreams(addon: StremioAddon, imdb: String, episode: Pair<Int, Int>?): List<AddonStream> {
        val base = addon.transportUrl.removeSuffix("/manifest.json")
        val kind = if (episode == null) "movie" else "series"
        val id = if (episode == null) imdb else "$imdb:${episode.first}:${episode.second}"
        return send("GET", "$base/stream/$kind/$id.json", null)?.let { AddonStreams.parse(it) }.orEmpty()
    }

    /**
     * What every add-on that streams offers, asked at once. An add-on that has not answered within [seconds] is left out,
     * so one slow add-on cannot hold the others back.
     */
    fun addonStreams(addons: List<StremioAddon>, imdb: String, episode: Pair<Int, Int>?, seconds: Long = 25): List<AddonStream> {
        val streamers = addons.filter { it.streams }
        if (streamers.isEmpty()) return emptyList()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(streamers.size)
        return try {
            pool.invokeAll(streamers.map { a -> java.util.concurrent.Callable { addonStreams(a, imdb, episode) } }, seconds, java.util.concurrent.TimeUnit.SECONDS)
                .flatMap { f -> if (f.isCancelled) emptyList() else try { f.get() } catch (_: Exception) { emptyList() } }
        } finally { pool.shutdownNow() }
    }

    fun filmPage(film: StremioFilm): Title =
        send("GET", "https://v3-cinemeta.strem.io/meta/movie/${film.imdbId}.json", null)?.let { StremioApi.title(it, film) } ?: StremioApi.bare(film)

    private fun libraryRequest(authKey: String) =
        JSONObject().put("authKey", authKey).put("collection", "libraryItem").put("ids", JSONArray()).put("all", true).toString()
}

private fun request(method: String, url: String, body: String?): String? = try {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.requestMethod = method; c.connectTimeout = 10_000; c.readTimeout = 25_000
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toByteArray()) } }
        (if (c.responseCode < 400) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } finally { c.disconnect() }
} catch (_: Exception) { null }

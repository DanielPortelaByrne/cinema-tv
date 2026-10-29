package com.danielportelabyrne.cinema.data.stremio

import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.Title
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A sign-in the owner approves from a phone: the short [code], the page to type it into and the same page as a QR picture. */
data class StremioLink(val code: String, val link: String, val qr: String)

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

    /** True when Stremio says the key is no longer valid (signed out everywhere), so the owner has to link again. */
    fun keyRejected(authKey: String): Boolean =
        send("POST", "https://api.strem.io/api/datastoreGet", libraryRequest(authKey))?.let { r -> try { JSONObject(r).has("error") } catch (_: Exception) { false } } ?: false

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

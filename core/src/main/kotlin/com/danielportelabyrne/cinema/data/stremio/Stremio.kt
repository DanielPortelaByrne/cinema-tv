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

/** One episode of a series as Cinemeta lists it. [still] is its picture, when there is one. */
data class SeriesEpisode(val season: Int, val episode: Int, val name: String, val overview: String?, val still: String?)

/** A series as Cinemeta lists it: its name and every episode, specials (season 0) left out, in order. */
data class SeriesListing(val name: String, val episodes: List<SeriesEpisode>)

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
    fun films(json: String): List<StremioFilm> = saved(json, "movie")

    /** Saved series, newest first, on the same terms as [films]; "watched" means at least one episode was. */
    fun savedSeries(json: String): List<StremioFilm> = saved(json, "series")

    private fun saved(json: String, type: String): List<StremioFilm> = try {
        val a = JSONObject(json).optJSONArray("result") ?: JSONArray()
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("_id")
            if (o.optString("type") != type || !Regex("tt\\d+").matches(id) || o.optBoolean("removed") || o.optBoolean("temp")) return@mapNotNull null
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
    fun title(json: String, film: StremioFilm, kind: Kind = Kind.MOVIE): Title? = try {
        val m = JSONObject(json).optJSONObject("meta") ?: return null
        // Asked as a series, only a series answers: Cinemeta says what the title is.
        if (kind == Kind.SHOW && m.optString("type") != "series") null else Title(
            key = film.imdbId, kind = kind, name = m.optString("name").ifBlank { film.name },
            year = Regex("\\d{4}").find(m.optString("year"))?.value?.toIntOrNull() ?: film.year,
            overview = m.optString("description").ifBlank { null },
            runtimeMin = Regex("\\d+").find(m.optString("runtime"))?.value?.toIntOrNull(),
            genres = m.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.optString(it) } } ?: emptyList(),
            age = null, imdbScore = m.optString("imdbRating").toDoubleOrNull(),
            posterUrl = m.optString("poster").ifBlank { film.poster }, backdropUrl = m.optString("background").ifBlank { null },
            imdbId = film.imdbId, tmdbId = m.optString("moviedb_id").ifBlank { null }
        )
    } catch (_: Exception) { null }

    /** A series' episodes from Cinemeta's answer, or null when it is not a series answer. */
    fun series(json: String): SeriesListing? = try {
        val m = JSONObject(json).optJSONObject("meta")
        val videos = m?.optJSONArray("videos")
        if (m == null || videos == null) null else SeriesListing(m.optString("name"), (0 until videos.length()).mapNotNull { i ->
            val v = videos.optJSONObject(i) ?: return@mapNotNull null
            val season = v.optInt("season", -1); val number = v.optInt("episode", v.optInt("number", -1))
            if (season <= 0 || number <= 0) return@mapNotNull null
            SeriesEpisode(season, number, v.optString("name").ifBlank { v.optString("title") }.ifBlank { "Episode $number" },
                v.optString("overview").ifBlank { v.optString("description") }.ifBlank { null }, v.optString("thumbnail").ifBlank { null })
        }.distinctBy { it.season to it.episode }.sortedWith(compareBy({ it.season }, { it.episode })))
    } catch (_: Exception) { null }

    /** The page when Cinemeta has nothing: just what the library holds. */
    fun bare(film: StremioFilm) = Title(film.imdbId, Kind.MOVIE, film.name, film.year, null, null, emptyList(), null, null, film.poster, null, film.imdbId, null)
}

/** One add-on torrent. Seeder count and size are optional display metadata, not protocol requirements. */
data class AddonStream(val title: String, val infoHash: String, val seeders: Int?, val size: Long?,
                       val fileIdx: Int? = null, val sources: List<String> = emptyList()) {
    fun candidateJson(): JSONObject = JSONObject().put("title", title).put("infoHash", infoHash)
        .put("seeders", seeders).put("size", size).apply {
            fileIdx?.let { put("fileIdx", it) }
            if (sources.isNotEmpty()) put("sources", JSONArray(sources))
        }
}

/**
 * One direct http(s) stream an add-on offered (a debrid service, a hosted file). [text] is the add-on's name and
 * description lines, which carry the quality. [headers] are ones the add-on says the request needs.
 */
data class AddonLink(val addon: String, val title: String, val url: String, val headers: Map<String, String>,
                     val text: String, val size: Long?, val uncached: Boolean)

/** Everything the add-ons offered for one title: torrents for the stream service, and links the TV can play itself. */
data class AddonAnswers(val torrents: List<AddonStream>, val links: List<AddonLink>) {
    companion object { val NONE = AddonAnswers(emptyList(), emptyList()) }
}

object AddonStreams {
    /**
     * The direct links in an add-on's answer. Links a debrid add-on marks as not yet downloaded ("[RD download]",
     * "⏳", "uncached") are kept but flagged, since they play a placeholder or nothing at all.
     */
    fun links(json: String, addon: String): List<AddonLink> = try {
        val a = JSONObject(json).optJSONArray("streams") ?: JSONArray()
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url").takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return@mapNotNull null
            val name = o.optString("name")
            val description = o.optString("description").ifBlank { o.optString("title") }
            val hints = o.optJSONObject("behaviorHints")
            val request = hints?.optJSONObject("proxyHeaders")?.optJSONObject("request")
            val headers = request?.keys()?.asSequence()?.mapNotNull { k -> (request.opt(k) as? String)?.let { k to it } }?.toMap().orEmpty()
            val title = hints?.optString("filename").orEmpty().ifBlank { description.lineSequence().firstOrNull()?.trim().orEmpty() }.ifBlank { name }
            val size = (hints?.opt("videoSize") as? Number)?.toLong()?.takeIf { it > 0 } ?: displayedSize(description)
            val uncached = Regex("download\\]|⏳|uncached|not cached", RegexOption.IGNORE_CASE).containsMatchIn("$name $description")
            AddonLink(addon, title, url, headers, "$name\n$description", size, uncached)
        }
    } catch (_: Exception) { emptyList() }

    private fun displayedSize(text: String): Long? = Regex("💾\\s*([\\d.,]+)\\s*(GiB|MiB|GB|MB)", RegexOption.IGNORE_CASE).find(text)?.let { m ->
        val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        (amount * if (m.groupValues[2].startsWith("G", true)) 1024.0 * 1024 * 1024 else 1024.0 * 1024).toLong()
    }

    /** Preserve the exact torrent file and peer discovery hints; missing display metadata remains unknown. */
    fun parse(json: String): List<AddonStream> = try {
        val a = JSONObject(json).optJSONArray("streams") ?: JSONArray()
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val hash = o.optString("infoHash").lowercase()
            val text = o.optString("description").ifBlank { o.optString("title") }
            val hints = o.optJSONObject("behaviorHints")
            val name = hints?.optString("filename").orEmpty().ifBlank { text.lineSequence().firstOrNull()?.trim().orEmpty() }
            val seeds = (o.opt("seeders") as? Number)?.toInt()?.takeIf { it >= 0 }
                ?: Regex("(?:👤|👥|[Ss]eeders?\\s*:)\\s*([\\d,]+)").find(text)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
            val size = Regex("💾\\s*([\\d.,]+)\\s*(GiB|MiB|GB|MB)", RegexOption.IGNORE_CASE).find(text)
            val bytes = (hints?.opt("videoSize") as? Number)?.toLong()?.takeIf { it > 0 }
                ?: size?.let { match ->
                    val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@let null
                    (amount * if (match.groupValues[2].startsWith("G", true)) 1024.0 * 1024 * 1024 else 1024.0 * 1024).toLong()
                }
            if (!Regex("[0-9a-f]{40}").matches(hash) || name.isEmpty()) return@mapNotNull null
            val fileIdx = if (o.has("fileIdx") && !o.isNull("fileIdx")) {
                val value = o.opt("fileIdx") as? Number ?: return@mapNotNull null
                value.toInt().takeIf { it in 0..65535 && it.toDouble() == value.toDouble() } ?: return@mapNotNull null
            } else null
            val sources = o.optJSONArray("sources")?.let { s -> (0 until s.length()).mapNotNull { s.opt(it) as? String }.take(32) }.orEmpty()
            AddonStream(name, hash, seeds, bytes, fileIdx, sources)
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

    /** The series saved in the account's library; null when Stremio can't be reached or the key no longer works. */
    fun savedSeries(authKey: String): List<StremioFilm>? {
        val reply = send("POST", "https://api.strem.io/api/datastoreGet", libraryRequest(authKey)) ?: return null
        return try { if (JSONObject(reply).has("result")) StremioApi.savedSeries(reply) else null } catch (_: Exception) { null }
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
    fun addonStreams(addon: StremioAddon, imdb: String, episode: Pair<Int, Int>?): List<AddonStream> = addonAnswer(addon, imdb, episode).torrents

    /** One add-on's whole answer for a film ([episode] null) or an episode: its torrents and its direct links. */
    fun addonAnswer(addon: StremioAddon, imdb: String, episode: Pair<Int, Int>?): AddonAnswers {
        val base = addon.transportUrl.removeSuffix("/manifest.json")
        val kind = if (episode == null) "movie" else "series"
        val id = if (episode == null) imdb else "$imdb:${episode.first}:${episode.second}"
        val reply = send("GET", "$base/stream/$kind/$id.json", null) ?: return AddonAnswers.NONE
        return AddonAnswers(AddonStreams.parse(reply), AddonStreams.links(reply, addon.name))
    }

    fun addonStreams(addons: List<StremioAddon>, imdb: String, episode: Pair<Int, Int>?, seconds: Long = 25): List<AddonStream> =
        addonAnswers(addons, imdb, episode, seconds * 1000).torrents

    /**
     * What every add-on that streams offers, asked at once. Waiting stops when all have answered, [maxMs] has passed, or
     * [graceMs] after the first useful answer: a slow add-on cannot hold Play back once others have offered something.
     */
    fun addonAnswers(addons: List<StremioAddon>, imdb: String, episode: Pair<Int, Int>?, maxMs: Long = 12_000, graceMs: Long = 4_000,
                     clock: () -> Long = System::currentTimeMillis): AddonAnswers {
        val streamers = addons.filter { it.streams }
        if (streamers.isEmpty()) return AddonAnswers.NONE
        val pool = java.util.concurrent.Executors.newFixedThreadPool(streamers.size.coerceAtMost(16))
        val done = java.util.concurrent.ExecutorCompletionService<AddonAnswers>(pool)
        streamers.forEach { a -> done.submit { addonAnswer(a, imdb, episode) } }
        val torrents = mutableListOf<AddonStream>(); val links = mutableListOf<AddonLink>()
        val start = clock(); var deadline = start + maxMs
        try {
            repeat(streamers.size) {
                val wait = deadline - clock()
                if (wait <= 0) return@repeat
                val answer = try { done.poll(wait, java.util.concurrent.TimeUnit.MILLISECONDS)?.get() } catch (_: Exception) { AddonAnswers.NONE }
                    ?: return@repeat   // timed out
                torrents += answer.torrents; links += answer.links
                if (answer.torrents.isNotEmpty() || answer.links.isNotEmpty()) deadline = minOf(deadline, clock() + graceMs)
            }
        } finally { pool.shutdownNow() }
        return AddonAnswers(torrents, links)
    }

    /** Every episode of the series with IMDb id [imdbId], from Cinemeta; null when it can't be read. */
    fun series(imdbId: String): SeriesListing? = send("GET", "https://v3-cinemeta.strem.io/meta/series/$imdbId.json", null)?.let { StremioApi.series(it) }

    /** The page for a saved title that turns out to be a series (Cinemeta's film lookup finds nothing for it); null when it is not one either. */
    fun seriesPage(film: StremioFilm): Title? =
        send("GET", "https://v3-cinemeta.strem.io/meta/series/${film.imdbId}.json", null)?.let { StremioApi.title(it, film, Kind.SHOW) }

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

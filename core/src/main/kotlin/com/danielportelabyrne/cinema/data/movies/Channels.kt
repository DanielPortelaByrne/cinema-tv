package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.exchange
import com.danielportelabyrne.cinema.platform.Log
import com.danielportelabyrne.cinema.platform.Platform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A television channel (Nickelodeon, CITV, History): somewhere shows first aired, as Wikidata records it. Streaming
 * services come from JustWatch, which does not list channels; the shows a channel made come from Wikidata, and what
 * they look like from Cinemeta. [about] tells two channels of one name apart ("American pay television channel").
 */
data class Channel(val id: String, val name: String, val about: String? = null, val logo: String? = null, val shows: Int = 0)

/** Which of a channel's shows a shelf holds: those that began in [from]..[to] (any year when both are null), best known first. */
data class ChannelQuery(val id: String, val from: Int? = null, val to: Int? = null)

/** One show a channel first aired, with how well known it is (the number of Wikipedia languages that have it). */
data class ChannelShow(val imdbId: String, val name: String, val year: Int?, val renown: Int)

/** The channels followed, kept like [FollowedServices]: nothing until the owner follows one. Pure. */
object FollowedChannels {
    fun toJson(list: List<Channel>): String = JSONArray().also { a ->
        list.forEach { c -> a.put(JSONObject().put("id", c.id).put("name", c.name).put("about", c.about ?: "").put("logo", c.logo ?: "")) }
    }.toString()

    fun parse(json: String?): List<Channel> = try {
        val a = JSONArray(json ?: "[]")
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            Channel(id, o.optString("name").ifBlank { id }, o.optString("about").ifBlank { null }, o.optString("logo").ifBlank { null })
        }.distinctBy { it.id }
    } catch (_: Exception) { emptyList() }

    fun follow(list: List<Channel>, c: Channel): List<Channel> = if (list.any { it.id == c.id }) list else list + c
    fun unfollow(list: List<Channel>, id: String): List<Channel> = list.filterNot { it.id == id }
    fun isFollowing(list: List<Channel>, id: String) = list.any { it.id == id }
}

/** Reading Wikidata's and Cinemeta's answers, and the questions to ask. Pure, so it can be tested. */
object ChannelData {
    private val ID = Regex("^Q[0-9]+$")

    /** One page of name matches, [PAGE] at a time from [offset]. A short name matches thousands of things, so the channel can be deep. */
    fun searchUrl(term: String, offset: Int = 0) = "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=" + URLEncoder.encode(term, "UTF-8") +
        "&language=en&uselang=en&type=item&limit=$PAGE&continue=$offset&format=json"

    const val PAGE = 50

    /** The entities a name search found, as (id, name, description). */
    fun parseSearch(json: String): List<Triple<String, String, String?>> = try {
        val a = JSONObject(json).getJSONArray("search")
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val id = o.optString("id").takeIf { ID.matches(it) } ?: return@mapNotNull null
            val label = o.optString("label").ifBlank { return@mapNotNull null }
            Triple(id, label, o.optString("description").ifBlank { null })
        }
    } catch (_: Exception) { emptyList() }

    /** Which of [ids] are channels that first aired shows (with IMDb ids), and how many, and their logo. Ids are checked: they go into a query. */
    fun countQuery(ids: List<String>): String =
        "SELECT ?ch (COUNT(DISTINCT ?s) AS ?n) (SAMPLE(?logo) AS ?lg) WHERE { VALUES ?ch { ${ids.filter { ID.matches(it) }.joinToString(" ") { "wd:$it" }} } " +
        "?s wdt:P449 ?ch . ?s wdt:P345 ?imdb . OPTIONAL { ?ch wdt:P154 ?logo } } GROUP BY ?ch"

    fun sparqlUrl(query: String) = "https://query.wikidata.org/sparql?format=json&query=" + URLEncoder.encode(query, "UTF-8")

    /** Wikimedia's picture of a logo file, as a 240 px image the TV can draw (it renders the vector ones). */
    fun logoUrl(raw: String): String = raw.replace("http://", "https://").substringBefore("?") + "?width=240"

    /** The channels among the search's [candidates] that have shows, with the show count, in the order found. */
    fun channels(candidates: List<Triple<String, String, String?>>, countsJson: String): List<Channel> {
        val counts = HashMap<String, Pair<Int, String?>>()
        try {
            val rows = JSONObject(countsJson).getJSONObject("results").getJSONArray("bindings")
            for (i in 0 until rows.length()) {
                val r = rows.getJSONObject(i)
                val id = r.getJSONObject("ch").getString("value").substringAfterLast('/')
                counts[id] = (r.getJSONObject("n").getString("value").toIntOrNull() ?: 0) to r.optJSONObject("lg")?.optString("value")?.takeIf { it.isNotBlank() }?.let(::logoUrl)
            }
        } catch (_: Exception) { return emptyList() }
        return candidates.mapNotNull { (id, name, about) -> counts[id]?.takeIf { it.first > 0 }?.let { (n, logo) -> Channel(id, name, about, logo, n) } }
    }

    /** The question for a channel's shows: most widely known first, at most [limit]. */
    fun showsQuery(channelId: String, limit: Int = 400): String {
        require(ID.matches(channelId)) { "not an entity id" }
        return "SELECT ?imdb ?sLabel ?year ?links WHERE { ?s wdt:P449 wd:$channelId . ?s wdt:P345 ?imdb . ?s wikibase:sitelinks ?links . " +
            "OPTIONAL { ?s wdt:P580 ?st } OPTIONAL { ?s wdt:P571 ?inc } BIND(YEAR(COALESCE(?st, ?inc)) AS ?year) " +
            "SERVICE wikibase:label { bd:serviceParam wikibase:language \"en\". } } ORDER BY DESC(?links) LIMIT $limit"
    }

    /** A channel's shows, best known first; one entry per IMDb id and per name and year (a show is often entered twice). */
    fun parseShows(json: String): List<ChannelShow> = try {
        val rows = JSONObject(json).getJSONObject("results").getJSONArray("bindings")
        val all = (0 until rows.length()).mapNotNull { i ->
            val r = rows.getJSONObject(i)
            val imdb = r.getJSONObject("imdb").getString("value").takeIf { it.startsWith("tt") } ?: return@mapNotNull null
            val name = r.optJSONObject("sLabel")?.optString("value")?.takeIf { it.isNotBlank() && !ID.matches(it) } ?: return@mapNotNull null
            ChannelShow(imdb, name, r.optJSONObject("year")?.optString("value")?.toIntOrNull(), r.optJSONObject("links")?.optString("value")?.toIntOrNull() ?: 0)
        }
        all.sortedByDescending { it.renown }.distinctBy { it.imdbId }.distinctBy { it.name.lowercase() to it.year }
    } catch (_: Exception) { emptyList() }

    /** The shows that began in [q]'s years, best known first, at most [limit]. A show with no known year is only in an all-years shelf. */
    fun select(shows: List<ChannelShow>, q: ChannelQuery, limit: Int): List<ChannelShow> =
        shows.filter { s -> (q.from == null && q.to == null) || (s.year != null && (q.from == null || s.year >= q.from) && (q.to == null || s.year <= q.to)) }
            .sortedByDescending { it.renown }.take(limit)

    fun metaUrl(imdbId: String) = "https://v3-cinemeta.strem.io/meta/series/$imdbId.json"

    /** One show as a title for a row, or null without a picture (a poster-less show would only be a grey box). */
    fun parseMeta(json: String, imdbId: String): Title? = try {
        val m = JSONObject(json).getJSONObject("meta")
        val poster = m.optString("poster").ifBlank { null }?.replace("/poster/small/", "/poster/medium/")
        val name = m.optString("name").ifBlank { null }
        if (poster == null || name == null) null else Title(
            key = "ch-$imdbId", kind = Kind.SHOW, name = name,
            year = Regex("^[0-9]{4}").find(m.optString("year").ifBlank { m.optString("releaseInfo") })?.value?.toIntOrNull(),
            overview = m.optString("description").ifBlank { null },
            runtimeMin = Regex("[0-9]+").find(m.optString("runtime"))?.value?.toIntOrNull(),
            genres = m.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
            age = null, imdbScore = m.optString("imdbRating").toDoubleOrNull(),
            posterUrl = poster, backdropUrl = m.optString("background").ifBlank { null },
            imdbId = imdbId, tmdbId = m.optString("moviedb_id").ifBlank { null }
        )
    } catch (_: Exception) { null }
}

/**
 * Looks channels up and lists what they made. Wikidata is asked about a channel once a fortnight and Cinemeta about a
 * show once a month; both are kept on disk. Blocking; call it off the main thread.
 */
class ChannelRepository(platform: Platform, private val clock: () -> Long = System::currentTimeMillis) {
    private val dir = File(platform.cacheDir, "channels")
    private val searches = ConcurrentHashMap<String, List<Channel>>()

    /** The channels named like [term] that have shows to browse. Empty when nothing was found or the network failed. */
    fun search(term: String): List<Channel> {
        val key = term.trim().lowercase()
        if (key.length < 2) return emptyList()
        searches[key]?.let { return it }
        // Up to three pages of matches, stopping at the first that is not full: Tiny Pop is the 60th thing called "tiny".
        val candidates = ArrayList<Triple<String, String, String?>>()
        for (page in 0 until SEARCH_PAGES) {
            val found = get(ChannelData.searchUrl(key, page * ChannelData.PAGE), "application/json")?.let(ChannelData::parseSearch) ?: break
            candidates += found
            if (found.size < ChannelData.PAGE) break
        }
        if (candidates.isEmpty()) return emptyList()
        val counts = get(ChannelData.sparqlUrl(ChannelData.countQuery(candidates.map { it.first })), "application/sparql-results+json") ?: return emptyList()
        return ChannelData.channels(candidates, counts).also { searches[key] = it }
    }

    /** A channel's shows, best known first: kept two weeks, and a stale copy is used when the network fails. */
    fun shows(channelId: String): List<ChannelShow> {
        val file = File(dir, "$channelId.json")
        val fresh = file.takeIf { it.exists() && clock() - it.lastModified() < FORTNIGHT_MS }
        val json = fresh?.let { runCatching { it.readText() }.getOrNull() }
            ?: get(ChannelData.sparqlUrl(ChannelData.showsQuery(channelId)), "application/sparql-results+json")?.also { text ->
                if (ChannelData.parseShows(text).isNotEmpty()) runCatching { dir.mkdirs(); file.writeText(text); file.setLastModified(clock()) }
            }
            ?: runCatching { file.readText() }.getOrNull()
        return json?.let(ChannelData::parseShows).orEmpty()
    }

    /** The titles of a shelf: the channel's shows in [q]'s years, best known first, with their pictures. */
    fun titles(q: ChannelQuery, limit: Int): List<Title> {
        // A few more than asked for: some shows have no picture and are dropped.
        val picked = ChannelData.select(shows(q.id), q, limit + 8)
        if (picked.isEmpty()) return emptyList()
        val metas = picked.map { s -> s to POOL.submit(Callable { meta(s.imdbId) }) }
        return metas.mapNotNull { (_, f) -> runCatching { f.get(30, TimeUnit.SECONDS) }.getOrNull() }.take(limit)
    }

    private fun meta(imdbId: String): Title? {
        val file = File(File(dir, "meta"), "$imdbId.json")
        val fresh = file.takeIf { it.exists() && clock() - it.lastModified() < MONTH_MS }
        val json = fresh?.let { runCatching { it.readText() }.getOrNull() }
            ?: get(ChannelData.metaUrl(imdbId), "application/json")?.also { text ->
                runCatching { file.parentFile?.mkdirs(); file.writeText(text); file.setLastModified(clock()) }
            }
        return json?.let { ChannelData.parseMeta(it, imdbId) }
    }

    private fun get(url: String, accept: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 45_000; c.requestMethod = "GET"
        c.setRequestProperty("Accept", accept); c.setRequestProperty("User-Agent", "CinemaTV/1.0 (Android TV)")
        c.exchange { if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else { Log.w(TAG, "HTTP ${c.responseCode}"); null } }
    } catch (e: Exception) { Log.w(TAG, "fetch failed: ${e.javaClass.simpleName}"); null }

    companion object {
        private const val TAG = "Channels"
        private const val SEARCH_PAGES = 3
        private const val FORTNIGHT_MS = 14L * 24 * 60 * 60 * 1000
        private const val MONTH_MS = 30L * 24 * 60 * 60 * 1000
        private val POOL = Executors.newFixedThreadPool(8) { r -> Thread(r, "channel-meta").apply { isDaemon = true } }
    }
}

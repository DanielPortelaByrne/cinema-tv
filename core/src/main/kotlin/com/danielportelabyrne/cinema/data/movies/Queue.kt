package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.exchange
import com.danielportelabyrne.cinema.data.jellyfin.Reachability
import com.danielportelabyrne.cinema.platform.Log
import com.danielportelabyrne.cinema.platform.Platform
import com.danielportelabyrne.cinema.ui.Perf
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "Queue"

enum class QueueSource { SONARR, RADARR }

/**
 * One thing Sonarr or Radarr has handed to the download client. [position] is the place in line among those still
 * waiting, taken from the torrent client's actual queue. It is 0 when the position is unknown or it is not queued.
 */
data class QueueItem(
    val source: QueueSource, val id: Int, val title: String, val detail: String?, val tmdbId: Long,
    val status: String, val sizeBytes: Long, val sizeLeftBytes: Long, val etaSeconds: Long?, val addedMs: Long?, val position: Int = 0,
    val downloadId: String? = null, val downloadClient: String? = null, val canPrioritize: Boolean = false, val forced: Boolean = false, val season: Int? = null,
    val episodeIds: List<Int> = emptyList(), val movieId: Int? = null, val clientVerified: Boolean = false, val speedBytes: Long? = null, val seeds: Int? = null
) {
    val isDownloading: Boolean get() = status == "downloading"
    val isActive: Boolean get() = status in setOf("downloading", "metadata", "stalled", "checking")
    val isWaiting: Boolean get() = status == "queued" || status == "delayed" || status == "paused"

    /** Completed copies awaiting import are kept even if an old queue snapshot still says downloading. */
    val canCancel: Boolean get() = status != "completed" && (sizeBytes <= 0 || sizeLeftBytes > 0)

    /** Fully downloaded: the torrent is finished and only sits in the client (and queue) waiting to be imported or cleaned up. */
    val isComplete: Boolean get() = status == "completed" || (sizeBytes > 0 && sizeLeftBytes <= 0)

    /**
     * A finished download whose torrent and files can be removed in one explicit step. Needs the torrent's own hash, and
     * a single movie or episode: a season pack (several episodes sharing one torrent) is ambiguous, so it is refused.
     */
    val canRemoveCompleted: Boolean get() = isComplete && downloadId != null &&
        if (source == QueueSource.SONARR) episodeIds.size == 1 else movieId != null

    /** 0..1 once bytes are known; null for something that has not started (its size is not known yet). */
    fun progress(): Float? = if (sizeBytes > 0) ((sizeBytes - sizeLeftBytes).toFloat() / sizeBytes).coerceIn(0f, 1f) else null
}

object QueueApi {
    /** Reads a queue page fetched with the series or movie and episode included. */
    fun parse(json: String, source: QueueSource): List<QueueItem> = try {
        val records = JSONObject(json).getJSONArray("records")
        (0 until records.length()).map { records.getJSONObject(it) }.map { r ->
            val series = r.optJSONObject("series"); val movie = r.optJSONObject("movie"); val ep = r.optJSONObject("episode")
            val title = (series?.optString("title") ?: movie?.optString("title")).orEmpty().ifBlank { r.optString("title") }
            QueueItem(
                source, r.optInt("id"), title,
                if (ep != null) "S${ep.optInt("seasonNumber")} E${ep.optInt("episodeNumber")}" + ep.optString("title").takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty() else movie?.optInt("year")?.takeIf { it > 0 }?.toString(),
                (series?.optLong("tmdbId") ?: movie?.optLong("tmdbId") ?: 0L),
                r.optString("status").lowercase(), r.optLong("size", 0L), r.optLong("sizeleft", 0L),
                timeLeftSeconds(r.optString("timeleft")), r.optString("added").takeIf { it.isNotBlank() }?.let { com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi.utcMillis(it) },
                downloadId = r.optString("downloadId").takeIf { QbitApi.validHash(it) },
                downloadClient = r.optString("downloadClient").takeIf { it.isNotBlank() },
                season = ep?.optInt("seasonNumber") ?: r.optInt("seasonNumber", -1).takeIf { it >= 0 },
                episodeIds = listOfNotNull(r.optInt("episodeId", ep?.optInt("id") ?: 0).takeIf { it > 0 }),
                movieId = r.optInt("movieId", movie?.optInt("id") ?: 0).takeIf { it > 0 }
            )
        }.groupBy { it.downloadId?.lowercase() ?: "record:${it.id}" }.values.map { group ->
            val first = group.first()
            val ids = group.flatMap { it.episodeIds }.distinct()
            if (group.size > 1 && source == QueueSource.SONARR) first.copy(
                detail = "Season ${first.season ?: "?"} · ${group.size} episodes", episodeIds = ids
            ) else first
        }
    } catch (_: Exception) { emptyList() }

    /** "00:12:34" or "1.02:03:04" (days.hours:minutes:seconds) as seconds; null when there is none. */
    fun timeLeftSeconds(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val days = if ('.' in text.substringBefore(':')) text.substringBefore('.').toLongOrNull() ?: 0L else 0L
        val parts = (if (days > 0) text.substringAfter('.') else text).split(':').mapNotNull { it.substringBefore('.').toLongOrNull() }
        if (parts.size != 3) return null
        return days * 86_400 + parts[0] * 3600 + parts[1] * 60 + parts[2]
    }

    /** Downloading first, then waiting in line, then the rest. */
    fun ordered(items: List<QueueItem>): List<QueueItem> =
        items.sortedWith(compareBy<QueueItem> { if (it.isActive) 0 else if (it.isWaiting) 1 else 2 }.thenBy { if (it.position > 0) it.position else Int.MAX_VALUE }.thenBy { it.addedMs ?: Long.MAX_VALUE })

    /** Cancelling removes it from the download client too (and its partial files) without blocking the release. */
    fun cancelPath(id: Int) = "/api/v3/queue/$id?removeFromClient=true&blocklist=false"
}

/**
 * The live download queue, read from Sonarr and Radarr using the address and key Seerr already holds for them, and
 * the way to cancel an item. Every call blocks and none throw.
 */
class QueueClient(private val seerr: SeerrClient, platform: Platform) {
    private val sessions = platform.preferences("qbit_sessions")
    private val settings = com.danielportelabyrne.cinema.data.settings.CinemaSettings(platform)
    private val preset = com.danielportelabyrne.cinema.data.settings.Preset.load(platform)
    private var sonarr: SonarrConfig? = null
    private var radarr: SonarrConfig? = null
    private val torrentClients = HashMap<Pair<QueueSource, String?>, DownloadClient>()

    /** The torrent client a queue row is in, when it is one this app can control. */
    @Synchronized
    fun clientFor(item: QueueItem): DownloadClient? {
        if (!QbitApi.validHash(item.downloadId)) return null
        val key = item.source to item.downloadClient
        return torrentClients[key] ?: run {
            val config = (if (item.source == QueueSource.SONARR) sonarrConfig() else radarrConfig()) ?: return null
            val json = arrCall(config, "GET", "/api/v3/downloadclient") ?: return null
            val client = DownloadClients.config(json, item.downloadClient, java.net.URL(config.baseUrl).host) ?: return null
            create(client).also { torrentClients[key] = it }
        }
    }

    private fun create(config: ClientConfig): DownloadClient =
        DownloadClients.create(config, savedUser(config), savedPassword(config), sessions.getString(config.baseUrl, null)) { cookie ->
            sessions.edit().putString(config.baseUrl, cookie).apply()
        }

    /** A torrent client Sonarr or Radarr uses, with its connection when it is one this app can control. */
    data class FoundClient(val listed: ListedClient, val config: ClientConfig?)

    /** Every torrent client Sonarr and Radarr use (once each), for setup. Empty when neither can be reached. */
    fun detectClients(): List<FoundClient> {
        val out = LinkedHashMap<String, FoundClient>()
        for (c in listOfNotNull(sonarrConfig(), radarrConfig())) {
            val json = arrCall(c, "GET", "/api/v3/downloadclient") ?: continue
            val host = java.net.URL(c.baseUrl).host
            DownloadClients.list(json, host).forEach { listed ->
                val config = if (listed.kind != null) DownloadClients.config(json, listed.name, host) else null
                out.getOrPut(config?.baseUrl ?: (listed.implementation + "@" + listed.host)) { FoundClient(listed, config) }
            }
        }
        return out.values.toList()
    }

    /** True when the client needs a login typed in on the TV (Sonarr/Radarr hide its password) and none is saved yet. */
    fun needsLogin(config: ClientConfig): Boolean = config.passwordHidden && savedPassword(config) == null

    /** A login typed in on this TV, else the preset's. */
    private fun savedPassword(config: ClientConfig) = settings.clientPassword(config.baseUrl) ?: preset?.clients?.get(config.kind)?.password
    private fun savedUser(config: ClientConfig) = settings.clientUser(config.baseUrl) ?: preset?.clients?.get(config.kind)?.username

    /** Tries a login for a client found during setup and keeps it (encrypted) when it works. */
    fun signIn(config: ClientConfig, username: String, password: String): Boolean {
        if (!create(config).signIn(username, password)) return false
        remember(config.baseUrl, username, password)
        return true
    }

    /** Signs in to the client a queue row is in, and keeps the login (encrypted) when it works. */
    fun signIn(item: QueueItem, username: String, password: String): Boolean {
        val client = clientFor(item) ?: return false
        if (!client.signIn(username, password)) return false
        remember(client.config.baseUrl, username, password)
        return true
    }

    private fun remember(baseUrl: String, username: String, password: String) {
        settings.setClientUser(baseUrl, username.ifBlank { null })
        settings.setClientPassword(baseUrl, password)
        synchronized(this) { torrentClients.clear() }
    }

    private fun sonarrConfig() = sonarr ?: seerr.sonarrConfig()?.also { sonarr = it }
    private fun radarrConfig() = radarr ?: seerr.radarrConfig()?.also { radarr = it }

    /** Everything in both queues, numbered and in the order they will be dealt with. Null when neither could be reached. */
    fun items(): List<QueueItem>? {
        val s = sonarrConfig()?.let { c -> arrCall(c, "GET", "/api/v3/queue?pageSize=200&includeSeries=true&includeEpisode=true")?.let { QueueApi.parse(it, QueueSource.SONARR) } }
        val r = radarrConfig()?.let { c -> arrCall(c, "GET", "/api/v3/queue?pageSize=200&includeMovie=true")?.let { QueueApi.parse(it, QueueSource.RADARR) } }
        if (s == null && r == null) return null
        val serverStates = seerr.serverTorrents()
        val states = HashMap<DownloadClient, Map<String, TorrentState>?>()
        return QueueApi.ordered((s.orEmpty() + r.orEmpty()).map { item ->
            serverStates?.get(item.downloadId?.lowercase())?.let { return@map QbitApi.apply(item, it) }
            val client = clientFor(item)
            val torrents = if (client == null) null else {
                if (!states.containsKey(client)) states[client] = client.states()
                states[client]
            }
            val torrent = torrents?.get(item.downloadId?.lowercase())
            if (torrent == null) item.copy(canPrioritize = client != null && item.status !in setOf("completed", "failed", "warning"))
            else QbitApi.apply(item, torrent)
        })
    }

    /**
     * Asks Sonarr or Radarr to look for a release for [request] right now: the film, or each requested season. Used
     * when there is no request helper service to do it. True when the search was accepted.
     */
    fun searchAgain(request: SeerrRequest): Boolean {
        val series = request.kind == Kind.SHOW
        val config = (if (series) sonarrConfig() else radarrConfig()) ?: return false
        val list = arrCall(config, "GET", if (series) "/api/v3/series" else "/api/v3/movie") ?: return false
        val id = try {
            val records = JSONArray(list)
            (0 until records.length()).map { records.getJSONObject(it) }.firstOrNull { it.optLong("tmdbId") == request.tmdbId }?.optInt("id")
        } catch (_: Exception) { null }?.takeIf { it > 0 } ?: return false
        val commands = if (!series) listOf(JSONObject().put("name", "MoviesSearch").put("movieIds", JSONArray().put(id)))
            else if (request.seasons.isEmpty()) listOf(JSONObject().put("name", "SeriesSearch").put("seriesId", id))
            else request.seasons.map { JSONObject().put("name", "SeasonSearch").put("seriesId", id).put("seasonNumber", it) }
        return commands.all { arrCall(config, "POST", "/api/v3/command", it.toString()) != null }
    }

    fun prioritize(item: QueueItem, action: DownloadAction): DownloadActionResult {
        val hash = item.downloadId ?: return DownloadActionResult.NOT_FOUND
        seerr.serverPrioritize(hash, action)?.let { return it }
        val client = clientFor(item) ?: return DownloadActionResult.NOT_CONFIGURED
        return client.perform(item.downloadId ?: return DownloadActionResult.NOT_FOUND, action)
    }

    /** Withdraw a pending request and stop its monitoring, keeping downloaded files and other seasons. */
    fun cancelRequest(request: SeerrRequest): Boolean {
        if (!request.canCancel) return false
        if (request.stage != Stage.WAITING_APPROVAL) {
            val series = request.kind == Kind.SHOW
            val config = (if (series) sonarrConfig() else radarrConfig()) ?: return false
            val list = arrCall(config, "GET", if (series) "/api/v3/series" else "/api/v3/movie") ?: return false
            val records = try { JSONArray(list) } catch (_: Exception) { return false }
            val match = (0 until records.length()).map { records.getJSONObject(it) }.firstOrNull { it.optLong("tmdbId") == request.tmdbId }
            if (match != null) {
                val id = match.optInt("id").takeIf { it > 0 } ?: return false
                if (series) {
                    if (request.seasons.isEmpty()) return false // never unmonitor someone else's seasons
                    val detail = arrCall(config, "GET", "/api/v3/series/$id") ?: return false
                    val update = RequestCancellationApi.stopSeasons(detail, request.seasons) ?: return false
                    if (arrCall(config, "PUT", "/api/v3/series/$id", update) == null) return false
                    val episodes = arrCall(config, "GET", "/api/v3/episode?seriesId=$id") ?: return false
                    val ids = RequestCancellationApi.episodeIds(episodes, request.seasons) ?: return false
                    if (ids.isNotEmpty() && arrCall(config, "PUT", "/api/v3/episode/monitor", SonarrApi.monitorBody(ids, false)) == null) return false
                } else {
                    val update = JSONObject().put("movieIds", JSONArray().put(id)).put("monitored", false).toString()
                    if (arrCall(config, "PUT", "/api/v3/movie/editor", update) == null) return false
                }
            }
            // A release may have been grabbed since the page was drawn. Stop that download too.
            val current = arrCall(config, "GET", if (series)
                "/api/v3/queue?pageSize=200&includeSeries=true&includeEpisode=true" else "/api/v3/queue?pageSize=200&includeMovie=true") ?: return false
            val source = if (series) QueueSource.SONARR else QueueSource.RADARR
            val matching = QueueApi.parse(current, source).filter { it.tmdbId == request.tmdbId && (!series || it.season in request.seasons) }
            if (matching.filter { it.canCancel }.any { arrCall(config, "DELETE", QueueApi.cancelPath(it.id)) == null }) return false
        }
        return seerr.cancelRequest(request.id)
    }

    /**
     * Remove a finished download: Sonarr/Radarr drop the queue entry and ask the download client to delete the torrent
     * with its data. Nothing is unmonitored and the library is not touched, so an imported copy stays.
     */
    fun removeCompleted(item: QueueItem): Boolean {
        if (!item.canRemoveCompleted) return false
        val c = (if (item.source == QueueSource.SONARR) sonarrConfig() else radarrConfig()) ?: return false
        return arrCall(c, "DELETE", QueueApi.cancelPath(item.id)) != null
    }

    fun cancel(item: QueueItem): Boolean {
        if (!item.canCancel) return false
        val c = (if (item.source == QueueSource.SONARR) sonarrConfig() else radarrConfig()) ?: return false
        // Cancellation must survive both RSS and the server retry worker. Stop only this torrent's episodes/movie.
        if (item.source == QueueSource.SONARR) {
            if (item.episodeIds.isEmpty()) return false
            if (arrCall(c, "PUT", "/api/v3/episode/monitor", SonarrApi.monitorBody(item.episodeIds, false)) == null) return false
        } else {
            val movieId = item.movieId ?: return false
            val update = JSONObject().put("movieIds", JSONArray().put(movieId)).put("monitored", false).toString()
            if (arrCall(c, "PUT", "/api/v3/movie/editor", update) == null) return false
        }
        return arrCall(c, "DELETE", QueueApi.cancelPath(item.id)) != null
    }
}

/**
 * Per Sonarr or Radarr address. An address read from Seerr's settings can be one only Seerr reaches (its own
 * localhost, a container name), so the TV skips it for a while rather than every call waiting out the timeout.
 */
private val arrReachability = ConcurrentHashMap<String, Reachability>()
private const val ARR_BACKOFF_MS = 60_000L

/** One call to Sonarr or Radarr with their key. Returns the body (or "{}" for an empty one), or null on any failure. */
internal fun arrCall(c: SonarrConfig, method: String, path: String, body: String? = null): String? {
    val gate = arrReachability.getOrPut(c.baseUrl) { Reachability(ARR_BACKOFF_MS) }
    if (gate.backingOff) return null
    var connected = false
    val at = Perf.now()
    return try {
        val conn = URL(c.baseUrl + path).openConnection() as HttpURLConnection
        conn.requestMethod = method; conn.connectTimeout = 6000; conn.readTimeout = 20000
        conn.setRequestProperty("X-Api-Key", c.apiKey); conn.setRequestProperty("Accept", "application/json")
        if (body != null) { conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json") }
        conn.exchange {
            conn.connect(); connected = true; gate.reached()
            if (body != null) conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            Perf.took("arr $method ${path.substringBefore('?')} -> $code", Perf.now() - at)
            if (code in 200..299) (conn.inputStream.bufferedReader().use { it.readText() }.ifBlank { "{}" }) else { Log.w(TAG, "$method ${path.substringBefore('?')} -> HTTP $code"); null }
        }
    } catch (e: Exception) {
        val unreachable = gate.failed(e, duringConnect = !connected)
        Log.w(TAG, "$method ${c.baseUrl}${path.substringBefore('?')} failed: ${e.javaClass.simpleName} after ${Perf.now() - at} ms" +
            if (unreachable) " (unreachable, skipped for ${ARR_BACKOFF_MS / 1000} s)" else "")
        null
    }
}

package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.TimedCache
import com.danielportelabyrne.cinema.data.exchange
import com.danielportelabyrne.cinema.platform.Platform
import com.danielportelabyrne.cinema.platform.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "Seerr"
private const val TITLE_TTL_MS = 2 * 60_000L

private val titles = TimedCache<String>(TITLE_TTL_MS)
private val listings = TimedCache<String>(6 * 60 * 60_000L)

/**
 * Sent on every call to the request helper, which refuses actions without it (a browser page cannot add it, so a
 * malicious page cannot trigger them). The name comes from the helper this app was first built against; keep it so
 * existing helpers keep working.
 */
const val HELPER_ACTION_HEADER = "X-MCM-Action"

/** [PROCESSING] is Seerr's own word: the request is with Radarr or Sonarr, which may not have found anything yet. Only [DOWNLOADING] means bytes are arriving. */
enum class Availability { NONE, REQUESTED, PROCESSING, DOWNLOADING, PARTIAL, AVAILABLE }

enum class RequestOutcome { QUEUED, ALREADY, NEEDS_SIGN_IN, FAILED }

data class TitleRequestState(val availability: Availability, val staleCandidate: Boolean)

/** The plain-Kotlin half of the Seerr client, so request bodies and answers can be tested. */
object SeerrApi {
    fun signInBody(username: String, password: String): String =
        JSONObject().put("username", username).put("password", password).toString()

    /** Films are asked for outright. A series asks for the given [seasons], or for every season when none are named. Seerr cannot ask for single episodes. */
    fun requestBody(kind: Kind, tmdbId: Long, seasons: List<Int>? = null): String {
        val o = JSONObject().put("mediaType", if (kind == Kind.MOVIE) "movie" else "tv").put("mediaId", tmdbId)
        if (kind == Kind.SHOW) o.put("seasons", if (seasons.isNullOrEmpty()) "all" else org.json.JSONArray(seasons))
        return o.toString()
    }

    fun path(kind: Kind, tmdbId: Long) = (if (kind == Kind.MOVIE) "/api/v1/movie/" else "/api/v1/tv/") + tmdbId

    /** The IMDb id in a title's details (`externalIds.imdbId`), or null when Seerr has none for it. */
    fun imdbId(json: String): String? = try {
        JSONObject(json).optJSONObject("externalIds")?.optString("imdbId")?.trim()?.takeIf { it.startsWith("tt") && it.length > 2 }
    } catch (_: Exception) { null }

    /** Seerr's media status numbers: 1 unknown, 2 pending, 3 processing, 4 partly available, 5 available. */
    fun parseAvailability(json: String): Availability = try {
        val info = JSONObject(json).optJSONObject("mediaInfo")
        fun of(status: Int?, downloading: Boolean) = when (status) {
            2 -> Availability.REQUESTED
            3 -> if (downloading) Availability.DOWNLOADING else Availability.PROCESSING
            4 -> Availability.PARTIAL
            5 -> Availability.AVAILABLE
            else -> Availability.NONE
        }
        of(info?.optInt("status", 1), (info?.optJSONArray("downloadStatus")?.length() ?: 0) > 0)
    } catch (_: Exception) { Availability.NONE }

    /** Where one season stands, from the media record Seerr keeps once something has been asked for. */
    fun seasonAvailability(status: Int): Availability = when (status) { 2 -> Availability.REQUESTED; 3 -> Availability.PROCESSING; 4 -> Availability.PARTIAL; 5 -> Availability.AVAILABLE; else -> Availability.NONE }

    /** Imported Arr monitoring is not a request. The server validates repairs against live downloads/files. */
    fun titleState(json: String): TitleRequestState {
        val stale = try {
            val info = JSONObject(json).getJSONObject("mediaInfo")
            info.optInt("id") > 0 && info.optInt("status") in 2..3 && info.optInt("status4k", 1) == 1 &&
                info.getJSONArray("requests").length() == 0 && info.getJSONArray("downloadStatus").length() == 0 &&
                (info.optJSONArray("downloadStatus4k")?.length() ?: 0) == 0 &&
                info.optJSONArray("seasons").let { seasons -> (0 until (seasons?.length() ?: 0)).all { i ->
                    val season = seasons!!.getJSONObject(i)
                    season.optInt("status", 1) in 1..3 && season.optInt("status4k", 1) == 1
                } }
        } catch (_: Exception) { false }
        return TitleRequestState(parseAvailability(json), stale)
    }

    fun outcomeFor(httpCode: Int): RequestOutcome = when (httpCode) {
        200, 201, 202 -> RequestOutcome.QUEUED
        409 -> RequestOutcome.ALREADY
        401, 403 -> RequestOutcome.NEEDS_SIGN_IN
        else -> RequestOutcome.FAILED
    }

    /** Complete sign-in: a saved session is success (null), not a reason to enter the missing-cookie fallback. */
    fun finishSignIn(httpCode: Int, headers: Map<String?, List<String>>?, saveSession: (String) -> Unit): String? = when (httpCode) {
        200 -> {
            val session = sessionCookie(setCookies(headers))
            if (session == null) "Signed in, but no session came back" else {
                saveSession(session)
                null
            }
        }
        401, 403 -> "Wrong username or password"
        else -> "Couldn't sign in (HTTP $httpCode)"
    }

    /** The session cookie out of a sign-in answer's Set-Cookie headers, as it should be sent back. */
    fun sessionCookie(setCookie: List<String>?): String? =
        setCookie?.firstOrNull { it.trim().startsWith("connect.sid=", ignoreCase = true) }?.trim()?.substringBefore(';')

    /** Every Set-Cookie value, however the connection spelled the header name (some stacks lower-case them). */
    fun setCookies(headers: Map<String?, List<String>>?): List<String> =
        headers?.entries?.filter { it.key.equals("Set-Cookie", ignoreCase = true) }?.flatMap { it.value }.orEmpty()
}

/**
 * Asks the owner's Seerr to download a title. The TV signs in once with the owner's Jellyfin account, typed on the
 * TV; only the session cookie is kept (private storage), never the password. Every call blocks and none throw.
 */
enum class StreamStatus { READY, NOT_SIGNED_IN, NOT_FOUND, CONNECTING, UNAVAILABLE }

/** [url] is set only when [status] is READY: the film's bytes, which the player reads as they download. */
data class StreamStart(val status: StreamStatus, val url: String?, val label: String?)

/** A Seerr sign-in as a restore file keeps it: where Seerr is, and its session cookie. */
data class SeerrSession(val server: String, val cookie: String)

class SeerrClient(platform: Platform) {
    private val prefs = platform.preferences("seerr")
    private val settings = com.danielportelabyrne.cinema.data.settings.CinemaSettings(platform)
    /** Seerr's address, or "" before setup has given one. */
    private val base: String get() = prefs.getString("server", null).orEmpty()
    val server: String get() = base
    val hasServer: Boolean get() = base.isNotBlank()
    val isSignedIn: Boolean get() = hasServer && !prefs.getString("cookie", null).isNullOrBlank()

    fun signOut() { prefs.edit().remove("cookie").apply() }

    /** The address and session cookie a restore file carries, or null when not signed in. */
    fun savedSession(): SeerrSession? {
        val cookie = prefs.getString("cookie", null)
        return if (isSignedIn && !cookie.isNullOrBlank()) SeerrSession(base, cookie) else null
    }

    /** Puts back a sign-in from a restore file. */
    fun restoreSession(session: SeerrSession) {
        prefs.edit().putString("server", session.server).putString("cookie", session.cookie).apply()
    }

    /** Points this TV at a (possibly different) Seerr; the old sign-in is dropped. Blank forgets Seerr altogether. */
    fun setServer(address: String?) {
        val clean = com.danielportelabyrne.cinema.data.settings.CinemaSettings.cleanUrl(address)
        if (clean != prefs.getString("server", null)) prefs.edit().putString("server", clean).remove("cookie").apply()
    }

    /** True when something answering as Seerr (or Overseerr/Jellyseerr) is at [address]. Blocking. */
    fun probe(address: String): Boolean = try {
        val url = com.danielportelabyrne.cinema.data.settings.CinemaSettings.cleanUrl(address) ?: return false
        val c = open("GET", "$url/api/v1/status", withCookie = false)
        c.exchange { c.responseCode == 200 && JSONObject(c.inputStream.bufferedReader().use { it.readText() }).has("version") }
    } catch (_: Exception) { false }

    /** Signs in with a Seerr (local) account's email and password. Null on success, otherwise a short reason. */
    fun signInLocal(email: String, password: String): String? = signInAt("/api/v1/auth/local", JSONObject().put("email", email).put("password", password).toString())

    /** Null on success, otherwise a short reason to show the owner. */
    fun signIn(username: String, password: String): String? = signInAt("/api/v1/auth/jellyfin", SeerrApi.signInBody(username, password))

    private fun signInAt(path: String, body: String): String? = try {
        if (!hasServer) throw IllegalStateException("no address")
        val c = open("POST", "$base$path", withCookie = false)
        c.doOutput = true
        c.exchange {
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val error = SeerrApi.finishSignIn(code, c.headerFields) { session ->
                prefs.edit().putString("cookie", session).apply()
            }
            if (code == 200 && error != null) Log.w(TAG, "Sign-in response omitted a usable session cookie")
            error
        }
    } catch (e: Exception) { Log.w(TAG, "signIn failed: ${e.javaClass.simpleName}"); "Couldn't reach Seerr" }

    /** Asks for a film, or for [seasons] of a series (every season when none are named). */
    fun request(kind: Kind, tmdbId: Long, seasons: List<Int>? = null): RequestOutcome = try {
        val c = open("POST", "$base/api/v1/request")
        c.doOutput = true
        c.exchange {
            c.outputStream.use { it.write(SeerrApi.requestBody(kind, tmdbId, seasons).toByteArray()) }
            val code = c.responseCode
            if (code == 401) signOut()
            SeerrApi.outcomeFor(code)
        }.also { titles.forget(base + SeerrApi.path(kind, tmdbId)) }
    } catch (e: Exception) { Log.w(TAG, "request failed: ${e.javaClass.simpleName}"); RequestOutcome.FAILED }

    /** Withdraws a request from Seerr, including one no release has been found for yet. True when Seerr accepted it. */
    fun cancelRequest(id: Int): Boolean = try {
        val c = open("DELETE", "$base/api/v1/request/$id")
        c.exchange { val code = c.responseCode; if (code == 401) signOut(); code in 200..299 }.also { titles.forget() }
    } catch (e: Exception) { Log.w(TAG, "cancelRequest failed: ${e.javaClass.simpleName}"); false }

    /** Drops the titles kept for two minutes, so the next look at a title asks Seerr again (after a delete, say). */
    fun forgetTitles() = titles.forget()

    fun availability(kind: Kind, tmdbId: Long): Availability = titleState(kind, tmdbId)?.availability ?: Availability.NONE

    fun titleState(kind: Kind, tmdbId: Long): TitleRequestState? = title(kind, tmdbId)?.let { SeerrApi.titleState(it) }

    /** The IMDb id for a title known only by its TMDB id, so it can go on the watchlist. Blocks; null when not found or not signed in. */
    fun imdbId(kind: Kind, tmdbId: Long): String? = title(kind, tmdbId)?.let { SeerrApi.imdbId(it) }

    private fun title(kind: Kind, tmdbId: Long): String? = SeerrApi.path(kind, tmdbId).let { p -> titles.get(base + p) { get(p) } }

    fun warm(kind: Kind, tmdbId: Long) {
        val page = title(kind, tmdbId) ?: return
        if (kind == Kind.SHOW) SeerrSeasons.parseSeasons(page).firstOrNull()?.let { episodes(tmdbId, it.number) }
    }

    /** Admin-only repair. A conflict or unavailable service keeps the existing state intact. */
    fun clearStaleStatus(kind: Kind, tmdbId: Long): Boolean = (tmdbId > 0 &&
        pipelineCall("POST", "/media/${if (kind == Kind.SHOW) "tv" else "movie"}/$tmdbId/clear-stale", readTimeoutMs = 30_000)?.let {
            try { JSONObject(it).getBoolean("cleared") } catch (_: Exception) { false }
        } == true).also { titles.forget(base + SeerrApi.path(kind, tmdbId)) }

    /** Everything the owner has asked for, newest first. Empty when not signed in or Seerr cannot be reached. */
    fun requests(): List<SeerrRequest> = requestsOrNull().orEmpty()

    /** Null on connectivity/authentication failure, so the UI cannot confuse failure with an empty list. */
    fun requestsOrNull(): List<SeerrRequest>? {
        val rows = ArrayList<SeerrRequest>()
        for (skip in 0 until 10_000 step 100) {
            val json = get("/api/v1/request?take=100&skip=$skip&sort=added") ?: return null
            rows += SeerrRequests.parse(json)
            val count = try { JSONObject(json).getJSONArray("results").length() } catch (_: Exception) { return null }
            if (count < 100) break
        }
        return rows
    }

    /** True when the optional request helper service has been set up (Settings, Advanced). */
    val hasRequestHelper: Boolean get() = settings.requestHelperUrl != null
    /** True when the optional stream service has been set up (Settings, Advanced). Without it there is no Stream now. */
    val hasStreamService: Boolean get() = settings.streamServiceUrl != null

    /** The request helper's own view of the download client, when there is a helper: no client password reaches the TV. */
    fun serverTorrents(): Map<String, TorrentState>? = pipelineCall("GET", "/torrents")?.takeIf { it.trimStart().startsWith("[") }?.let { QbitApi.torrents(it) }

    fun serverPrioritize(hash: String, action: DownloadAction): DownloadActionResult? {
        if (!QbitApi.validHash(hash)) return DownloadActionResult.NOT_FOUND
        val json = pipelineCall("POST", "/torrents/${hash.lowercase()}/${if (action == DownloadAction.NEXT) "next" else "now"}") ?: return null
        return try { DownloadActionResult.valueOf(JSONObject(json).getString("result")) } catch (_: Exception) { DownloadActionResult.FAILED }
    }

    fun pipelineStatus(): PipelineSnapshot? = pipelineCall("GET", "/requests")?.let { RequestPipelineApi.parse(it) }

    /** Explicit retry of a completed unsuccessful search; the server checks ownership and rate limits. */
    fun retrySearch(id: Int): Boolean = id > 0 && pipelineCall("POST", "/requests/$id/search") != null

    private fun pipelineCall(method: String, path: String, readTimeoutMs: Int = 6000): String? = try {
        val endpoint = settings.requestHelperUrl ?: return null
        val c = open(method, endpoint.trimEnd('/') + path)
        c.connectTimeout = 3000; c.readTimeout = readTimeoutMs
        c.setRequestProperty(HELPER_ACTION_HEADER, "1")
        c.exchange { if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null }
    } catch (_: Exception) { null }

    /**
     * Asks the home stream service for a seeded copy of a film and returns where the player can read it. It searches the
     * same indexers the library uses, so nothing goes through Stremio. The service waits for torrent metadata, so this call is slow.
     */
    /** [season] and [episode] are set for a TV episode; the server then looks for that one episode. */
    fun startStream(title: String, year: Int?, season: Int? = null, episode: Int? = null, runtimeMin: Int? = null,
                    candidates: List<com.danielportelabyrne.cinema.data.stremio.AddonStream> = emptyList()): StreamStart = try {
        if (!hasStreamService) throw IllegalStateException("no stream service")
        val c = open("POST", "${streamEndpoint()}/start")
        c.readTimeout = 120_000
        c.doOutput = true
        val endpoint = streamEndpoint()
        c.exchange {
            val body = streamBody(title, year, season, episode, runtimeMin, candidates)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            when (c.responseCode) {
                200 -> {
                    val body = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    // The original file, never the service's on-the-fly audio conversion: the player decodes any soundtrack
                    // itself now, and only the original can be seeked.
                    StreamStart(StreamStatus.READY, endpoint + body.getString("path") + "?audio=original", body.optString("label").ifBlank { null })
                }
                401 -> { signOut(); StreamStart(StreamStatus.NOT_SIGNED_IN, null, null) }
                404, 422 -> StreamStart(StreamStatus.NOT_FOUND, null, null)
                504 -> StreamStart(StreamStatus.CONNECTING, null, null)
                else -> StreamStart(StreamStatus.UNAVAILABLE, null, null)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "startStream failed: ${e.javaClass.simpleName}")
        StreamStart(StreamStatus.UNAVAILABLE, null, null)
    }

    private fun streamEndpoint() = settings.streamServiceUrl.orEmpty().trimEnd('/')

    private fun streamBody(title: String, year: Int?, season: Int?, episode: Int?, runtimeMin: Int?,
                           candidates: List<com.danielportelabyrne.cinema.data.stremio.AddonStream> = emptyList()) = JSONObject().put("title", title).put("year", year?.toString().orEmpty()).apply {
        if (season != null && episode != null) put("season", season).put("episode", episode)
        runtimeMin?.takeIf { it > 0 }?.let { put("runtime", it) }   // lets the service judge which release can keep up
        put("audio", "any")   // this player decodes Dolby Digital Plus and DTS, so those releases need not be passed over
        // Torrents the owner's Stremio add-ons offered, found from this TV: the service picks among them with its own results.
        if (candidates.isNotEmpty()) put("candidates", org.json.JSONArray(candidates.map { c ->
            JSONObject().put("title", c.title).put("infoHash", c.infoHash).put("seeders", c.seeders).put("size", c.size)
        }))
    }

    private val prepared = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Asks the stream service to search for a title now, so Stream now (if pressed) skips the indexer search. Fire and
     * forget: at most once per title every ten minutes, short timeouts, and a service without /prepare (404) is fine.
     */
    fun prepareStream(title: String, year: Int?, season: Int? = null, episode: Int? = null, runtimeMin: Int? = null) {
        if (!isSignedIn || !hasStreamService) return
        val key = listOf(title, year, season, episode).joinToString("|")
        val now = System.currentTimeMillis()
        if ((prepared[key] ?: 0L) > now - 10 * 60_000L) return
        prepared[key] = now
        try {
            val c = open("POST", "${streamEndpoint()}/prepare")
            c.connectTimeout = 3000; c.readTimeout = 5000; c.doOutput = true
            c.exchange {
                c.outputStream.use { it.write(streamBody(title, year, season, episode, runtimeMin).toString().toByteArray()) }
                val code = c.responseCode
                (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes() }
                if (code == 401) prepared.remove(key)
            }
        } catch (e: Exception) { prepared.remove(key); Log.w(TAG, "prepareStream failed: ${e.javaClass.simpleName}") }
    }

    fun seasons(tmdbId: Long): List<SeasonInfo> = title(Kind.SHOW, tmdbId)?.let { SeerrSeasons.parseSeasons(it) }.orEmpty()

    fun episodes(tmdbId: Long, season: Int): List<EpisodeInfo> = (SeerrApi.path(Kind.SHOW, tmdbId) + "/season/$season").let { p ->
        listings.get(base + p) { get(p) }?.let { SeerrSeasons.parseEpisodes(it) }.orEmpty()
    }

    fun titleInfo(kind: Kind, tmdbId: Long): Pair<String, String?>? = title(kind, tmdbId)?.let { SeerrRequests.parseTitle(it) }

    /** TMDB's recommendations for a title, through Seerr (which also says what is already on the server). Null when unreachable. */
    fun recommendations(kind: Kind, tmdbId: Long): List<Suggestion>? = get(SeerrApi.path(kind, tmdbId) + "/recommendations?page=1")?.let { Picks.parseSuggestions(it) }

    /** Free space where films are stored, in bytes, or null. */
    fun freeSpaceBytes(): Long? = get("/api/v1/service/radarr/0")?.let { SeerrRequests.parseFreeSpace(it) }

    /** Seerr's Sonarr settings (address, key, profile, folder). Needs an admin sign-in. */
    fun sonarrSettings(): String? = get("/api/v1/settings/sonarr")
    fun radarrSettings(): String? = get("/api/v1/settings/radarr")

    /**
     * Where Sonarr is and how Seerr adds series to it: typed in by hand during setup, or else read from Seerr's own
     * settings (which needs a Seerr admin sign-in). Null when neither is available.
     */
    fun sonarrConfig(): SonarrConfig? = arrConfig(com.danielportelabyrne.cinema.data.settings.Arr.SONARR)
    fun radarrConfig(): SonarrConfig? = arrConfig(com.danielportelabyrne.cinema.data.settings.Arr.RADARR)

    private fun arrConfig(arr: com.danielportelabyrne.cinema.data.settings.Arr): SonarrConfig? {
        settings.manualArr(arr)?.let { return ArrSetup.resolve(it) }
        val json = if (arr == com.danielportelabyrne.cinema.data.settings.Arr.SONARR) sonarrSettings() else radarrSettings()
        return json?.let { SonarrApi.parseConfig(it, host) }
    }

    /** The machine Seerr runs on, as the TV reaches it ("" before setup). */
    val host: String get() = try { URL(base).host.orEmpty() } catch (_: Exception) { "" }

    private fun get(path: String): String? = try {
        val c = open("GET", base + path)
        c.exchange { if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else { if (c.responseCode == 401) signOut(); null } }
    } catch (_: Exception) { null }

    private fun open(method: String, url: String, withCookie: Boolean = true): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 6000; c.readTimeout = 12000
        c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json")
        if (withCookie) prefs.getString("cookie", null)?.let { c.setRequestProperty("Cookie", it) }
        return c
    }
}

// --- Requests and where they have got to --------------------------------------------------------

/** One download Radarr or Sonarr is working on, as Seerr reports it. A series can have several (one per episode). */
data class Download(val sizeBytes: Long, val sizeLeftBytes: Long, val etaMs: Long?)

enum class Stage { WAITING_APPROVAL, SEARCHING, DOWNLOADING, READY, DECLINED, FAILED }

/** A request the owner has made, with how far along it is. */
data class SeerrRequest(val id: Int, val kind: Kind, val tmdbId: Long, val stage: Stage, val downloads: List<Download>, val requestedAtMs: Long?, val seasons: List<Int> = emptyList()) {
    val canCancel: Boolean get() = stage in setOf(Stage.WAITING_APPROVAL, Stage.SEARCHING, Stage.DOWNLOADING)
    val totalBytes: Long get() = downloads.sumOf { it.sizeBytes }
    val leftBytes: Long get() = downloads.sumOf { it.sizeLeftBytes }

    /** 0..1 across everything being fetched for this title, or null when nothing has started. */
    fun progress(): Float? = if (stage == Stage.READY) 1f else if (totalBytes > 0) ((totalBytes - leftBytes).toFloat() / totalBytes).coerceIn(0f, 1f) else null

    /** The soonest-finishing estimate is no use for a series; the latest one is when it is all done. */
    fun etaMs(): Long? = downloads.mapNotNull { it.etaMs }.maxOrNull()
}

object SeerrRequests {
    /** Reads Seerr's request list. Request status: 1 awaiting approval, 2 approved, 3 declined, 4 failed, 5 completed. Media status 5 is available. */
    fun parse(json: String): List<SeerrRequest> = try {
        val results = JSONObject(json).getJSONArray("results")
        (0 until results.length()).mapNotNull { i ->
            val r = results.getJSONObject(i)
            val media = r.optJSONObject("media") ?: return@mapNotNull null
            val tmdb = media.optLong("tmdbId", 0L).takeIf { it > 0 } ?: return@mapNotNull null
            val queue = media.optJSONArray("downloadStatus")
            val downloads = (0 until (queue?.length() ?: 0)).map { k ->
                val d = queue!!.getJSONObject(k)
                Download(d.optLong("size", 0L), d.optLong("sizeLeft", 0L), d.optString("estimatedCompletionTime").takeIf { it.isNotBlank() }?.let { com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi.utcMillis(it) })
            }
            val stage = when {
                r.optInt("status") == 3 -> Stage.DECLINED
                r.optInt("status") == 4 -> Stage.FAILED
                media.optInt("status") == 5 || r.optInt("status") == 5 -> Stage.READY
                r.optInt("status") == 1 -> Stage.WAITING_APPROVAL
                downloads.isNotEmpty() -> Stage.DOWNLOADING
                else -> Stage.SEARCHING
            }
            SeerrRequest(
                r.optInt("id"), if (r.optString("type") == "tv") Kind.SHOW else Kind.MOVIE, tmdb, stage, downloads,
                r.optString("createdAt").takeIf { it.isNotBlank() }?.let { com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi.utcMillis(it) },
                seasons = r.optJSONArray("seasons")?.let { ss -> (0 until ss.length()).mapNotNull { i ->
                    ss.optJSONObject(i)?.optInt("seasonNumber", -1)?.takeIf { it >= 0 }
                }.distinct() }.orEmpty()
            )
        }
    } catch (_: Exception) { emptyList() }

    /** Free space on the first root folder Radarr reports, in bytes. */
    fun parseFreeSpace(json: String): Long? = try {
        val folders = JSONObject(json).optJSONArray("rootFolders")
        folders?.optJSONObject(0)?.optLong("freeSpace", -1L)?.takeIf { it >= 0 }
    } catch (_: Exception) { null }

    /** The title and poster Seerr knows for a TMDB id. */
    fun parseTitle(json: String): Pair<String, String?>? = try {
        val o = JSONObject(json)
        val name = o.optString("title").ifBlank { o.optString("name") }.ifBlank { null }
        name?.let { it to o.optString("posterPath").takeIf { p -> p.isNotBlank() }?.let { p -> "https://image.tmdb.org/t/p/w185$p" } }
    } catch (_: Exception) { null }
}

/** "10.8 GB", "601 GB", "1.2 TB": three digits at most, so it stays small on screen. */
object ByteSize {
    fun format(bytes: Long): String {
        val gb = bytes / 1e9
        return when {
            gb >= 1000 -> String.format(java.util.Locale.UK, "%.1f TB", gb / 1000)
            gb >= 100 -> String.format(java.util.Locale.UK, "%.0f GB", gb)
            gb >= 1 -> String.format(java.util.Locale.UK, "%.1f GB", gb)
            else -> String.format(java.util.Locale.UK, "%.0f MB", bytes / 1e6)
        }
    }
}

// --- Seasons and episodes ----------------------------------------------------------------------

/** A season of a series with where it stands. Season 0 (specials) is left out by [SeerrSeasons.parseSeasons]. */
data class SeasonInfo(val number: Int, val name: String, val episodeCount: Int, val availability: Availability)

/** [stillPath] is TMDB's path for the episode still ("/abc.jpg"); [runtime] is in minutes, when TMDB has it. */
data class EpisodeInfo(val season: Int, val number: Int, val name: String, val overview: String?, val airDate: String?, val stillPath: String? = null, val runtime: Int? = null) {
    val stillUrl: String? get() = stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
}

/** Seerr versions return either a TMDB path or a complete TMDB image URL. */
object EpisodeImages {
    fun path(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
        if (raw.startsWith("/") && !raw.startsWith("//")) return raw
        return try {
            val uri = java.net.URI(raw)
            if (uri.scheme != "https" || uri.host != "image.tmdb.org") null
            else uri.path.substringAfterLast('/').takeIf { it.isNotBlank() }?.let { "/$it" }
        } catch (_: Exception) { null }
    }
}

object SeerrSeasons {
    fun parseSeasons(json: String): List<SeasonInfo> = try {
        val root = JSONObject(json)
        val tracked = root.optJSONObject("mediaInfo")?.optJSONArray("seasons")
        val status = HashMap<Int, Int>()
        for (i in 0 until (tracked?.length() ?: 0)) tracked!!.getJSONObject(i).let { status[it.optInt("seasonNumber")] = it.optInt("status", 1) }
        val seasons = root.getJSONArray("seasons")
        (0 until seasons.length()).map { seasons.getJSONObject(it) }
            .filter { it.optInt("seasonNumber") > 0 }
            .map { SeasonInfo(it.optInt("seasonNumber"), it.optString("name").ifBlank { "Season ${it.optInt("seasonNumber")}" }, it.optInt("episodeCount"), SeerrApi.seasonAvailability(status[it.optInt("seasonNumber")] ?: 1)) }
    } catch (_: Exception) { emptyList() }

    fun parseEpisodes(json: String): List<EpisodeInfo> = try {
        val eps = JSONObject(json).getJSONArray("episodes")
        (0 until eps.length()).map { eps.getJSONObject(it) }.map {
            EpisodeInfo(it.optInt("seasonNumber"), it.optInt("episodeNumber"), it.optString("name").ifBlank { "Episode ${it.optInt("episodeNumber")}" },
                it.optString("overview").ifBlank { null }, it.optString("airDate").ifBlank { null },
                // TMDB sends null for a missing still, which optString turns into "null".
                EpisodeImages.path(it.optString("stillPath")),
                it.optInt("runtime", 0).takeIf { r -> r > 0 })
        }
    } catch (_: Exception) { emptyList() }
}

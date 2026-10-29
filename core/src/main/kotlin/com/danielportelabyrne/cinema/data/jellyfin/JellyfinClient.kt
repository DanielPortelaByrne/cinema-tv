package com.danielportelabyrne.cinema.data.jellyfin

import com.danielportelabyrne.cinema.data.exchange
import com.danielportelabyrne.cinema.platform.Log
import com.danielportelabyrne.cinema.platform.Platform
import com.danielportelabyrne.cinema.ui.Perf
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "Jellyfin"

/** A Quick Connect attempt in progress: [code] is what to type into an already signed-in Jellyfin. */
data class QuickConnectTicket(val secret: String, val code: String)

/** A Jellyfin sign-in as a restore file keeps it: where the server is, and the access token for this TV's user. */
data class JellyfinSession(val server: String, val token: String, val userId: String)

/**
 * Talks to the owner's Jellyfin server, at the address they gave during setup. Signed in once, either with Quick Connect
 * (a short code approved from a phone or browser) or with a username and password; either way only the access token is
 * kept, in private storage, never the password.
 * Every call here blocks, so callers run it off the main thread, and none of them throw.
 */
class JellyfinClient(platform: Platform) {
    private val prefs = platform.preferences("jellyfin")

    private val deviceId: String = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() }
    private val deviceName: String = platform.deviceName
    private val libraryFiles = File(platform.cacheDir, "jellyfin")

    /** The server's address, or "" before setup has given one. */
    val server: String get() = debugServer ?: prefs.getString("server", null).orEmpty()
    val hasServer: Boolean get() = server.isNotBlank()

    /** Points this TV at a (possibly different) server; any sign-in for the old one is dropped. */
    fun setServer(address: String) {
        val base = JellyfinApi.cleanBase(address)
        if (base != prefs.getString("server", null)) prefs.edit().putString("server", base).remove("token").remove("userId").apply()
        invalidateLibrary()
    }

    /** True when something answering as Jellyfin is at [address]. Blocking. */
    fun probe(address: String): Boolean = request("GET", JellyfinApi.cleanBase(address) + "/System/Info/Public", authed = false)
        ?.let { try { JSONObject(it).has("Id") } catch (_: Exception) { false } } == true

    /** Signs in with a username and password. Null on success, otherwise a short reason to show. Only the token is kept. */
    fun signInByName(username: String, password: String): String? {
        if (!hasServer) return "Enter the server address first"
        val body = request("POST", JellyfinApi.cleanBase(server) + "/Users/AuthenticateByName", authed = false,
            json = JSONObject().put("Username", username).put("Pw", password).toString())
            ?: return "Couldn't sign in. Check the username and password."
        return try {
            val o = JSONObject(body)
            prefs.edit().putString("token", o.getString("AccessToken")).putString("userId", o.getJSONObject("User").getString("Id")).apply()
            invalidateLibrary()
            null
        } catch (_: Exception) { "Jellyfin gave an unexpected answer" }
    }
    val isConnected: Boolean get() = !prefs.getString("token", null).isNullOrBlank() && !prefs.getString("userId", null).isNullOrBlank()
    private val token: String? get() = prefs.getString("token", null)
    private val userId: String? get() = prefs.getString("userId", null)

    /**
     * What a player needs to fetch a stream: the token travels in a header, never in the address. Jellyfin 12
     * answers 401 to the older X-Emby-Token header and to ?api_key=, so it has to be the Authorization header.
     */
    fun playbackHeaders(): Map<String, String> = token?.let { mapOf("Authorization" to JellyfinApi.authHeader(deviceId, deviceName, it)) }.orEmpty()

    fun streamUrl(item: JellyfinItem) = JellyfinApi.directStreamUrl(JellyfinApi.cleanBase(server), item)
    fun transcodeUrl(item: JellyfinItem) = JellyfinApi.transcodeUrl(JellyfinApi.cleanBase(server), item, deviceId)

    fun disconnect() { prefs.edit().remove("token").remove("userId").apply() }

    /** Forgets the server as well as the sign-in (Settings, Reset everything). */
    fun forget() { prefs.edit().remove("server").remove("token").remove("userId").apply(); invalidateLibrary() }

    /** The server and token a restore file carries, or null when not signed in. The password is never here. */
    fun savedSession(): JellyfinSession? {
        val session = JellyfinSession(server, token.orEmpty(), userId.orEmpty())
        return session.takeIf { it.server.isNotBlank() && it.token.isNotBlank() && it.userId.isNotBlank() }
    }

    /** Puts back a sign-in from a restore file, as a normal sign-in would keep it: the token only. */
    fun restoreSession(session: JellyfinSession) {
        prefs.edit().putString("server", session.server).putString("token", session.token).putString("userId", session.userId).apply()
        invalidateLibrary()
    }

    // --- Sign in ---------------------------------------------------------------------------------

    /** Null means the server could not be reached or has Quick Connect switched off; [error] says which. */
    fun startQuickConnect(address: String, error: (String) -> Unit): QuickConnectTicket? {
        val base = JellyfinApi.cleanBase(address)
        val enabled = request("GET", "$base/QuickConnect/Enabled", authed = false)
        if (enabled == null) { error("Couldn't reach Jellyfin at $base"); return null }
        if (!enabled.trim().equals("true", ignoreCase = true)) { error("Quick Connect is switched off on the server"); return null }
        val body = request("POST", "$base/QuickConnect/Initiate", authed = false, json = "{}") ?: run { error("Quick Connect didn't start"); return null }
        return try {
            val o = JSONObject(body)
            prefs.edit().putString("server", base).apply()
            QuickConnectTicket(o.getString("Secret"), o.getString("Code"))
        } catch (_: Exception) { error("Quick Connect gave an unexpected answer"); null }
    }

    /** True once the code has been approved on another device. */
    fun isApproved(ticket: QuickConnectTicket): Boolean {
        val body = request("GET", "${JellyfinApi.cleanBase(server)}/QuickConnect/Connect?secret=${java.net.URLEncoder.encode(ticket.secret, "UTF-8")}", authed = false) ?: return false
        return try { JSONObject(body).optBoolean("Authenticated", false) } catch (_: Exception) { false }
    }

    /** Trades an approved ticket for an access token. */
    fun finishQuickConnect(ticket: QuickConnectTicket): Boolean {
        val body = request("POST", "${JellyfinApi.cleanBase(server)}/Users/AuthenticateWithQuickConnect", authed = false,
            json = JSONObject().put("Secret", ticket.secret).toString()) ?: return false
        return try {
            val o = JSONObject(body)
            val newToken = o.getString("AccessToken")
            val id = o.getJSONObject("User").getString("Id")
            prefs.edit().putString("token", newToken).putString("userId", id).apply()
            true
        } catch (_: Exception) { false }
    }

    // --- Library ---------------------------------------------------------------------------------

    @Volatile private var library: List<JellyfinItem> = emptyList()
    @Volatile private var libraryAt = 0L

    private val libraryFlight = SingleFlight<List<JellyfinItem>>()
    @Volatile private var restored = false
    private val refreshing = AtomicBoolean(false)

    @Volatile var onLibraryChanged: (() -> Unit)? = null

    /** When the library was last fetched (0 = never). Lets a page tell whether badges need another look. */
    val libraryLoadedAt: Long get() = libraryAt

    /**
     * Every film and series, newest first, with play dates, kept for a few minutes so flicking through picks costs
     * two requests, not many.
     * One fetch at a time: callers arriving while it runs share its answer, and no lock is held across the network.
     */
    fun library(force: Boolean = false): List<JellyfinItem> {
        val uid = userId ?: return emptyList()
        if (!restored) synchronized(libraryFiles) { if (!restored) { restoreLibrary(uid); restored = true } }
        if (!force && libraryAt != 0L && System.currentTimeMillis() - libraryAt < LIBRARY_TTL_MS) return library
        // The server was unreachable a moment ago: answer from what we have rather than making every caller wait again.
        if (!force && reachability.backingOff) return library
        if (!force && libraryAt != 0L && library.isNotEmpty()) { refreshLater(uid); return library }
        return fetchNow(uid)
    }

    private fun fetchNow(uid: String): List<JellyfinItem> = libraryFlight.run {
        fetchLibrary(uid)?.let { library = it; libraryAt = System.currentTimeMillis() }
        library
    }

    private fun refreshLater(uid: String) {
        if (!refreshing.compareAndSet(false, true)) return
        refresher.execute {
            try {
                val before = library
                if (fetchNow(uid) != before) onLibraryChanged?.invoke()
            } finally { refreshing.set(false) }
        }
    }

    /** Null when the server cannot be asked. Without play dates it still sorts by date added. */
    private fun fetchLibrary(uid: String): List<JellyfinItem>? {
        val base = JellyfinApi.cleanBase(server)
        val itemsJson = request("GET", JellyfinApi.libraryUrl(base, uid)) ?: return null
        val items = JellyfinApi.parseItems(itemsJson)
        val playedJson = request("GET", JellyfinApi.recentlyPlayedUrl(base, uid))
        saveLibrary(uid, itemsJson, playedJson)
        return JellyfinApi.withPlays(items, playedJson?.let { JellyfinApi.parseItems(it) }.orEmpty())
    }

    private fun libraryKey(uid: String) = "${JellyfinApi.cleanBase(server)}|$uid"

    private fun saveLibrary(uid: String, itemsJson: String, playedJson: String?) {
        try {
            libraryFiles.mkdirs()
            val tmp = File(libraryFiles, "library.tmp")
            tmp.writeText("${libraryKey(uid)}\n${System.currentTimeMillis()}\n${itemsJson.replace('\n', ' ')}\n${playedJson?.replace('\n', ' ').orEmpty()}")
            val target = File(libraryFiles, "library.txt")
            if (!tmp.renameTo(target) && !(target.delete() && tmp.renameTo(target))) tmp.delete()
        } catch (e: Exception) { Log.w(TAG, "library not saved: ${e.javaClass.simpleName}") }
    }

    private fun restoreLibrary(uid: String) {
        try {
            val lines = File(libraryFiles, "library.txt").takeIf { it.exists() }?.readLines() ?: return
            if (lines.size < 3 || lines[0] != libraryKey(uid)) return
            val items = JellyfinApi.parseItems(lines[2]).takeIf { it.isNotEmpty() } ?: return
            val played = lines.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { JellyfinApi.parseItems(it) }.orEmpty()
            library = JellyfinApi.withPlays(items, played)
            libraryAt = lines[1].toLong()
        } catch (e: Exception) { Log.w(TAG, "saved library unreadable: ${e.javaClass.simpleName}") }
    }

    /** Films and episodes part-watched, most recently watched first. Blocking; empty when signed out or unreachable. */
    fun continueWatching(): List<JellyfinItem> {
        val uid = userId ?: return emptyList()
        val items = request("GET", JellyfinApi.resumeUrl(JellyfinApi.cleanBase(server), uid))?.let { JellyfinApi.parseItems(it) }
        return items.orEmpty().sortedByDescending { it.playedMs ?: 0L }
    }

    /** The library as last fetched, without asking the server. */
    fun loadedLibrary(): List<JellyfinItem> = library

    /** Forgets when the library was fetched, so the next [library] call asks the server (after playback or a delete). */
    fun invalidateLibrary() { libraryAt = 0L }

    fun movies(force: Boolean = false): List<JellyfinItem> = library(force).filter { it.isMovie }

    /**
     * Fetches the library from the server now, replaces the cached copy with it, and says whether it still holds [itemId].
     * Null when the server cannot be asked. Used after a delete, so the cache does not keep showing what was removed.
     */
    fun stillInLibrary(itemId: String): Boolean? {
        val uid = userId ?: return null
        library = fetchLibrary(uid) ?: return null; libraryAt = System.currentTimeMillis()
        return library.any { it.id == itemId }
    }

    /** The library copy of a title from elsewhere, matched on IMDb or TMDB id. May fetch the library, so not for the main thread. */
    fun find(imdbId: String?, tmdbId: String?, series: Boolean): JellyfinItem? = JellyfinApi.findByIds(library(), imdbId, tmdbId, series)

    /** Like [find] but only ever looks at what is already loaded, so it is safe to call while drawing. */
    fun findLoaded(imdbId: String?, tmdbId: String?, series: Boolean): JellyfinItem? = JellyfinApi.findByIds(library, imdbId, tmdbId, series)

    /** Current server resume state for a single item; call off the main thread. */
    /** Debug aid: which of the player's extra fields the server's item answer carries at all (no values, no secrets). */
    fun itemFieldReport(itemId: String): String {
        val uid = userId ?: return "not signed in"
        val body = request("GET", JellyfinApi.itemUrl(JellyfinApi.cleanBase(server), uid, itemId)) ?: return "no answer"
        return try {
            val o = JSONObject(body)
            "Chapters=${if (o.has("Chapters")) o.optJSONArray("Chapters")?.length() ?: "null" else "absent"} Trickplay=${if (o.has("Trickplay")) (o.optJSONObject("Trickplay")?.length() ?: "null") else "absent"}"
        } catch (_: Exception) { "unreadable" }
    }

    fun item(itemId: String): JellyfinItem? {
        val uid = userId ?: return null
        val body = request("GET", JellyfinApi.itemUrl(JellyfinApi.cleanBase(server), uid, itemId)) ?: return null
        return try { JellyfinApi.parseItem(JSONObject(body)) } catch (_: Exception) { null }
    }

    /** The episode to play for a series: the next unwatched one, else the first. Null when it has none or Jellyfin is unreachable. */
    fun episodeToPlay(seriesId: String): JellyfinItem? {
        val uid = userId ?: return null
        val base = JellyfinApi.cleanBase(server)
        val next = request("GET", JellyfinApi.nextUpUrl(base, uid, seriesId))?.let { JellyfinApi.parseItems(it).firstOrNull() }
        return next ?: request("GET", JellyfinApi.firstEpisodeUrl(base, uid, seriesId))?.let { JellyfinApi.parseItems(it).firstOrNull() }
    }

    /** Every episode of a series, with season and episode numbers, or empty when there are none or Jellyfin cannot be reached. */
    fun episodes(seriesId: String): List<JellyfinItem> {
        val uid = userId ?: return emptyList()
        return request("GET", JellyfinApi.episodesUrl(JellyfinApi.cleanBase(server), uid, seriesId))?.let { JellyfinApi.parseItems(it) }.orEmpty()
    }

    /** Resolve ahead of the end screen; no dependency on a watched-state report reaching the server first. */
    fun followingEpisode(current: JellyfinItem): JellyfinItem? = current.seriesId?.let {
        JellyfinApi.followingEpisode(current, episodes(it))
    }

    /** The signed-in user's name, to save typing it into the request service. */
    fun userName(): String? = request("GET", JellyfinApi.cleanBase(server) + "/Users/Me")?.let { try { JSONObject(it).optString("Name").ifBlank { null } } catch (_: Exception) { null } }

    fun backdropUrl(id: String) = JellyfinApi.backdropUrl(JellyfinApi.cleanBase(server), id)

    /** Asks Jellyfin to rescan its libraries. Used when something has been downloaded that it has not yet picked up. Needs an admin sign-in. */
    fun refreshLibrary(): Boolean = request("POST", JellyfinApi.cleanBase(server) + "/Library/Refresh", json = "{}") != null

    /**
     * Deletes an item from Jellyfin and its files from disk. For what the download manager does not hold (added some other
     * way). Needs a user allowed to delete media. True when Jellyfin accepted it.
     */
    fun deleteItem(itemId: String): Boolean = request("DELETE", JellyfinApi.cleanBase(server) + "/Items/" + java.net.URLEncoder.encode(itemId, "UTF-8")) != null

    /** How much space the library takes, in bytes; null when Jellyfin cannot be reached. */
    fun libraryBytes(): Long? {
        val uid = userId ?: return null
        return request("GET", JellyfinApi.sizesUrl(JellyfinApi.cleanBase(server), uid))?.let { JellyfinApi.parseTotalBytes(it) }
    }

    /** The film in the library with this IMDb id, or null when there is none (or Jellyfin cannot be reached). */
    fun findMovieByImdb(imdbId: String): JellyfinItem? = JellyfinApi.findByImdb(movies(), imdbId)

    // --- Live TV ---------------------------------------------------------------------------------

    /** The owner's live channels with what is on now. Null when Jellyfin could not be reached, so "none yet" and "offline" are told apart. */
    fun liveChannels(): List<LiveChannel>? {
        val uid = userId ?: return null
        val body = request("GET", JellyfinApi.channelsUrl(JellyfinApi.cleanBase(server), uid)) ?: return null
        return JellyfinApi.parseChannels(body)
    }

    /** Opens the channel's stream on the server and returns the HLS address to play, or null. */
    fun liveStreamUrl(channelId: String): String? {
        val uid = userId ?: return null
        val base = JellyfinApi.cleanBase(server)
        val body = request("POST", JellyfinApi.playbackInfoUrl(base, uid, channelId), json = JellyfinApi.PLAYBACK_INFO_BODY) ?: return null
        return JellyfinApi.parseLiveStreamUrl(base, body)
    }

    /** The schedule for every channel between [fromMs] and [toMs]. Empty when there is none or Jellyfin cannot be reached. */
    fun guide(fromMs: Long, toMs: Long): List<Programme> {
        val uid = userId ?: return emptyList()
        val body = request("GET", JellyfinApi.programsUrl(JellyfinApi.cleanBase(server), uid, fromMs, toMs)) ?: return emptyList()
        return JellyfinApi.parsePrograms(body)
    }

    fun imageUrl(id: String, maxHeight: Int = 300) = JellyfinApi.posterUrl(JellyfinApi.cleanBase(server), id, maxHeight)

    /** An episode's still from the library. Load it with [playbackHeaders], like any other image from this server. */
    fun episodeImageUrl(id: String) = JellyfinApi.episodeImageUrl(JellyfinApi.cleanBase(server), id)

    /** When a request last found the server unreachable. Callers then stop queuing up behind a server that is not there. */
    private val reachability = Reachability(UNREACHABLE_BACKOFF_MS)

    /** True for a short while after the server could not be reached. */
    val backingOff: Boolean get() = reachability.backingOff

    companion object {
        /** Debug builds only: overrides the address for this process, to time the app with the server unreachable. */
        @Volatile var debugServer: String? = null
        private const val LIBRARY_TTL_MS = 5 * 60_000L
        private const val UNREACHABLE_BACKOFF_MS = 30_000L
        private val refresher = Executors.newSingleThreadExecutor { r -> Thread(r, "library-refresh").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    }

    // --- Plumbing --------------------------------------------------------------------------------

    /**
     * One quiet retry for reads, but only after a timeout: the TV's Wi-Fi drops a request now and then. A server that
     * refuses the connection or cannot be routed to will not answer a second try any sooner.
     */
    private fun request(method: String, url: String, authed: Boolean = true, json: String? = null): String? {
        // Signed-in reads give up at once while the server is known to be unreachable (asleep, off the network).
        if (method == "GET" && authed && reachability.backingOff) return null
        val first = requestOnce(method, url, authed, json)
        if (first.body != null || first.unreachable) return first.body
        return if (method == "GET" && (!authed || isConnected)) requestOnce(method, url, authed, json).body else null
    }

    /** [unreachable] when the request never reached the server, rather than failing on the server's answer. */
    private class Outcome(val body: String?, val unreachable: Boolean = false)

    private fun requestOnce(method: String, url: String, authed: Boolean, json: String?): Outcome {
        var connected = false
        val at = Perf.now()
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 6000
            connection.readTimeout = 10000
            connection.setRequestProperty("Authorization", JellyfinApi.authHeader(deviceId, deviceName, if (authed) token else null))
            connection.setRequestProperty("Accept", "application/json")
            if (json != null) { connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json") }
            connection.exchange {
                // Connecting separately tells a server that never answers (asleep) from one that is only slow to reply.
                connection.connect(); connected = true; reachability.reached()
                if (json != null) connection.outputStream.use { it.write(json.toByteArray()) }
                val code = connection.responseCode
                Outcome(when {
                    code == 401 && authed -> { Log.w(TAG, "Token rejected; signing out"); disconnect(); null }
                    code !in 200..299 -> { Log.w(TAG, "$method ${url.substringBefore('?')} -> HTTP $code"); null }
                    else -> connection.inputStream.bufferedReader().use { it.readText() }
                }).also { Perf.took("jellyfin $method ${url.substringBefore('?').removePrefix(server)} -> $code", Perf.now() - at) }
            }
        } catch (e: Exception) {
            val unreachable = reachability.failed(e, duringConnect = !connected)
            Log.w(TAG, "$method ${url.substringBefore('?')} failed: ${e.javaClass.simpleName} after ${Perf.now() - at} ms" + if (unreachable) " (unreachable)" else "")
            Outcome(null, unreachable)
        }
    }
}

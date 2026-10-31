package com.danielportelabyrne.cinema.data.pairing

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinSession
import com.danielportelabyrne.cinema.data.movies.SeerrClient
import com.danielportelabyrne.cinema.data.movies.Watchlist
import com.danielportelabyrne.cinema.data.movies.SeerrSession
import com.danielportelabyrne.cinema.data.music.SubsonicConfig
import com.danielportelabyrne.cinema.data.settings.Arr
import com.danielportelabyrne.cinema.data.settings.CinemaSettings
import com.danielportelabyrne.cinema.data.settings.ClientLogin
import com.danielportelabyrne.cinema.data.settings.Integration
import com.danielportelabyrne.cinema.data.settings.LibraryOrder
import com.danielportelabyrne.cinema.data.settings.ManualArr
import org.json.JSONObject

/**
 * The JSON inside a restore file: the settings this TV was given, plus the Jellyfin and Seerr sign-ins as tokens and
 * cookies. Passwords are never written, because the phone types them once and the TV keeps only what a sign-in leaves.
 *
 * Download-client logins are included with their addresses, so a restored TV controls its downloads without a second
 * sign-in. Stremio and Subsonic secrets are included too, and so are the Letterboxd members followed and the whole
 * watchlist. The whole file is sealed with the PIN ([RestoreBundle]).
 */
object RestoreFormat {
    const val KIND = "cinema-restore"
    const val VERSION = 1

    /** The JSON for this TV's settings and sign-ins. Sign-ins that are not present are left out. */
    fun build(settings: CinemaSettings, jellyfin: JellyfinClient, seerr: SeerrClient): String {
        val root = JSONObject().put("kind", KIND).put("version", VERSION)
        jellyfin.savedSession()?.let {
            root.put("jellyfin", JSONObject().put("server", it.server).put("token", it.token).put("userId", it.userId))
        }
        seerr.savedSession()?.let {
            root.put("seerr", JSONObject().put("server", it.server).put("cookie", it.cookie))
        }
        val s = JSONObject()
            .put("region", settings.region)
            .put("libraryOrder", settings.libraryOrder.name)
            .put("integrations", JSONObject().apply { Integration.entries.forEach { put(it.key, settings.isOn(it)) } })
        settings.requestHelperUrl?.let { s.put("requestHelperUrl", it) }
        settings.streamServiceUrl?.let { s.put("streamServiceUrl", it) }
        Arr.entries.forEach { arr ->
            settings.manualArr(arr)?.let { s.put(arr.name.lowercase(), JSONObject().put("url", it.baseUrl).put("apiKey", it.apiKey)) }
        }
        settings.subsonic?.let {
            s.put("subsonic", JSONObject().put("url", it.baseUrl).put("username", it.username)
                .put("password", it.password).put("apiKey", it.apiKey))
        }
        settings.stremioKey?.let { s.put("stremioKey", it) }
        val clients = org.json.JSONArray()
        settings.clientLogins().forEach {
            clients.put(JSONObject().put("url", it.baseUrl).put("username", it.username).put("password", it.password))
        }
        s.put("clients", clients)
        // What the owner has built up on the TV: who they follow on Letterboxd, and the whole watchlist (the one imported from Stremio too).
        s.put("letterboxdFollowing", org.json.JSONArray(settings.letterboxdFollowing))
        s.put("watchlist", org.json.JSONArray(Watchlist.toJson(settings.watchlist)))
        root.put("settings", s)
        return root.toString()
    }

    /**
     * Writes a restore file's contents back. Everything is read and checked first, so a malformed file changes nothing.
     * Throws [RestoreException] when the file is not a Cinema restore file or was made by a newer Cinema.
     */
    fun apply(json: String, settings: CinemaSettings, jellyfin: JellyfinClient, seerr: SeerrClient) {
        val root = try { JSONObject(json) } catch (_: Exception) { throw RestoreException("Not a Cinema restore file") }
        if (root.optString("kind") != KIND) throw RestoreException("Not a Cinema restore file")
        val version = root.optInt("version", 0)
        if (version < 1) throw RestoreException("Not a Cinema restore file")
        if (version > VERSION) throw RestoreException("This file was made by a newer Cinema. Update Cinema, then try again.")
        val s = root.optJSONObject("settings") ?: throw RestoreException("Not a Cinema restore file")

        val jellyfinSession = root.optJSONObject("jellyfin")?.let {
            JellyfinSession(text(it, "server"), text(it, "token"), text(it, "userId")).takeIf { s -> s.server.isNotBlank() && s.token.isNotBlank() && s.userId.isNotBlank() }
        }
        val seerrSession = root.optJSONObject("seerr")?.let {
            SeerrSession(text(it, "server"), text(it, "cookie")).takeIf { s -> s.server.isNotBlank() && s.cookie.isNotBlank() }
        }
        val region = text(s, "region").takeIf { it.length == 2 }
        val order = LibraryOrder.of(text(s, "libraryOrder").ifBlank { null })
        val manual = Arr.entries.associateWith { arr ->
            s.optJSONObject(arr.name.lowercase())?.let { ManualArr(text(it, "url"), text(it, "apiKey")) }
                ?.takeIf { it.baseUrl.isNotBlank() && it.apiKey.isNotBlank() }
        }
        val subsonic = s.optJSONObject("subsonic")?.let {
            SubsonicConfig(text(it, "url"), text(it, "username"), text(it, "password"), text(it, "apiKey"))
                .takeIf { c -> c.baseUrl.isNotBlank() && (c.apiKey.isNotBlank() || c.password.isNotBlank()) }
        }
        val integrations = s.optJSONObject("integrations")
        val clients = s.optJSONArray("clients")?.let { list ->
            (0 until list.length()).mapNotNull { i ->
                list.optJSONObject(i)?.let { ClientLogin(text(it, "url"), text(it, "username"), text(it, "password")) }
            }.filter { it.baseUrl.isNotBlank() && it.password.isNotBlank() }
        }.orEmpty()
        // Absent in a file from an older build: then what is on this TV is left alone, not wiped.
        val following = s.optJSONArray("letterboxdFollowing")?.let { list ->
            (0 until list.length()).mapNotNull { i -> list.optString(i).trim().takeIf { it.isNotEmpty() } }.distinctBy { it.lowercase() }
        }
        val watchlist = s.optJSONArray("watchlist")?.let { Watchlist.parse(it.toString()) }
        val stremio = text(s, "stremioKey").ifBlank { null }
        val helper = text(s, "requestHelperUrl").ifBlank { null }
        val stream = text(s, "streamServiceUrl").ifBlank { null }

        region?.let { settings.region = it }
        settings.libraryOrder = order
        settings.requestHelperUrl = helper
        settings.streamServiceUrl = stream
        Arr.entries.forEach { settings.setManualArr(it, manual[it]) }
        settings.subsonic = subsonic
        settings.stremioKey = stremio
        integrations?.let { o -> Integration.entries.forEach { if (o.has(it.key)) settings.setOn(it, o.optBoolean(it.key)) } }
        following?.let { settings.letterboxdFollowing = it }
        watchlist?.let { settings.watchlist = it }
        clients.forEach {
            settings.setClientUser(it.baseUrl, it.username.ifBlank { null })
            settings.setClientPassword(it.baseUrl, it.password)
        }
        jellyfinSession?.let { jellyfin.restoreSession(it) }
        seerrSession?.let { seerr.restoreSession(it) }
        settings.setupDone = true
    }

    private fun text(o: JSONObject, name: String): String = if (o.isNull(name)) "" else o.optString(name, "").trim()
}

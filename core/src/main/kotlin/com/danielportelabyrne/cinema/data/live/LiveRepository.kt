package com.danielportelabyrne.cinema.data.live

import com.danielportelabyrne.cinema.platform.Platform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class LivePreferences(platform: Platform) {
    private val prefs = platform.preferences("live_tv")
    private fun list(key: String) = runCatching { JSONArray(prefs.getString(key, "[]")).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
    private fun save(key: String, ids: List<String>) { prefs.edit().putString(key, JSONArray(ids.distinct()).toString()).apply() }
    var favourites: List<String>
        get() = list("favourites")
        set(value) = save("favourites", value)
    var hidden: List<String>
        get() = list("hidden")
        set(value) = save("hidden", value)
    val recent: List<String> get() = list("recent")
    fun watched(id: String) = save("recent", (listOf(id) + recent).distinct().take(30))
    fun favourite(id: String) { favourites = if (id in favourites) favourites - id else favourites + id }
    fun moveFirst(id: String) { favourites = listOf(id) + (favourites - id) }
    fun hide(id: String) { hidden = hidden + id }
    fun health(url: String): SourceHealth = runCatching {
        val o = JSONObject(prefs.getString("source_${key(url)}", "{}")!!)
        SourceHealth(o.optInt("ok"), o.optInt("fail"), o.optLong("last"), o.optLong("failed"), o.optString("reason"))
    }.getOrDefault(SourceHealth())
    @Synchronized fun result(url: String, success: Boolean, reason: String = "", now: Long = System.currentTimeMillis()) {
        val h = health(url)
        prefs.edit().putString("source_${key(url)}", JSONObject().put("ok", h.successes + if (success) 1 else 0)
            .put("fail", h.failures + if (success) 0 else 1).put("last", if (success) now else h.lastSuccess)
            .put("failed", if (success) h.failedAt else now).put("reason", if (success) "" else reason.take(120)).toString()).apply()
    }
    private fun key(raw: String) = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
}

data class CatalogResult(val channels: List<LiveChannel>, val updatedAt: Long, val warning: String? = null)

/** One atomic snapshot: a partial refresh never overwrites a working catalogue. No dependency on media servers. */
class LiveRepository(platform: Platform, private val fetch: (String) -> String = { PublicTvUrl.read(it, 40_000_000) }) {
    private val cache = File(platform.cacheDir, "live-catalog-v1.json")
    private val playlist = File(platform.filesDir, "live-playlist-v1.m3u")
    val preferences = LivePreferences(platform)
    private var memory: CatalogResult? = null
    @Synchronized fun load(force: Boolean = false): CatalogResult {
        val old = memory ?: readCache()
        if (!force && old != null && System.currentTimeMillis() - old.updatedAt < 24 * 60 * 60_000L) return old
        return try {
            // Discard unrelated records one at a time, before fetching the next endpoint. In particular the
            // worldwide guides endpoint is >25 MB; keeping its entire object graph would exhaust some TVs.
            val data = LiveCatalog.endpoints.associateWith { key -> LiveCatalog.compact(key, fetch("https://iptv-org.github.io/api/$key.json")) }
            val channels = LiveCatalog.parse(data)
            require(JSONArray(data.getValue("channels")).length() > 0) { "Empty catalogue response" }
            val now = System.currentTimeMillis()
            val snapshot = JSONObject().put("time", now).put("data", JSONObject(data)).toString()
            cache.parentFile?.mkdirs()
            val temp = File(cache.parentFile, "${cache.name}.tmp"); temp.writeText(snapshot)
            // renameTo replaces atomically on Android/Linux and is available on the app's API 24 minimum.
            // Windows requires moving the previous snapshot aside first; keep it recoverable until promotion succeeds.
            if (!temp.renameTo(cache)) {
                val backup = File(cache.parentFile, "${cache.name}.bak")
                require(!cache.exists() || cache.renameTo(backup)) { "Cannot preserve cache" }
                if (!temp.renameTo(cache)) { backup.renameTo(cache); error("Cannot save cache") }
                backup.delete()
            }
            CatalogResult(channels, now).also { memory = it }
        } catch (_: Exception) {
            (old?.copy(warning = "Refresh failed · showing the last saved catalogue")
                ?: CatalogResult(LiveProviders.seeds(), 0, "Catalogue unavailable · official provider routes are available")).also { memory = it }
        }
    }
    private fun readCache(): CatalogResult? = runCatching {
        val snapshot = JSONObject((cache.takeIf { it.exists() } ?: File(cache.parentFile, "${cache.name}.bak")).readText()); val data = snapshot.getJSONObject("data")
        CatalogResult(LiveCatalog.parse(LiveCatalog.endpoints.associateWith { data.getString(it) }), snapshot.getLong("time"))
    }.getOrNull()
    fun imported(): List<LiveChannel> = runCatching { PlaylistParser.parse(playlist.readText()) }.getOrDefault(emptyList())
    fun importPlaylist(url: String): Int {
        require(PublicTvUrl.syntaxSafe(url) && java.net.URI(url).rawQuery == null) { "Playlist URLs must not contain credentials or query parameters" }
        val text = fetch(url)
        val channels = PlaylistParser.parse(text)
        require(channels.isNotEmpty()) { "No public HTTPS channels found" }
        // Persist only accepted public metadata, never the original playlist's rejected credentials or player options.
        fun clean(value: String) = value.replace(Regex("[\"\\r\\n]"), " ")
        val safe = buildString {
            append("#EXTM3U\n")
            channels.forEach { c -> c.sources.forEach { s ->
                append("#EXTINF:-1 tvg-id=\"${clean(c.id.removePrefix("playlist:"))}\",${clean(c.name)}\n${s.url}\n")
            } }
        }
        playlist.parentFile?.mkdirs(); val temp = File(playlist.parentFile, "${playlist.name}.tmp"); temp.writeText(safe)
        if (!temp.renameTo(playlist)) { playlist.writeText(safe); temp.delete() }
        return channels.size
    }
    fun clearPlaylist() { playlist.delete() }
}

object PlaylistParser {
    /** Ignore executable/player options, cookies, URL headers, DRM and credential-bearing URLs. */
    fun parse(text: String): List<LiveChannel> {
        require(text.length <= 5_000_000 && text.trimStart('\uFEFF', ' ', '\r', '\n').startsWith("#EXTM3U")) { "Not an M3U playlist" }
        val channels = linkedMapOf<String, LiveChannel>()
        var meta: String? = null
        text.lineSequence().take(30_000).forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF:") -> meta = line
                line.startsWith('#') || line.isEmpty() -> Unit
                else -> {
                    val m = meta; meta = null
                    if (m != null && PublicTvUrl.syntaxSafe(line) && java.net.URI(line).rawQuery == null) {
                        fun attr(name: String) = Regex("(?:^|\\s)${Regex.escape(name)}=\"([^\"]*)\"").find(m)?.groupValues?.get(1).orEmpty()
                        var quoted = false
                        val comma = m.indexOfFirst { ch -> if (ch == '"') quoted = !quoted; ch == ',' && !quoted }
                        val name = (if (comma >= 0) m.substring(comma + 1) else attr("tvg-name")).trim().take(160)
                        if (name.isNotBlank()) {
                            // Without an explicit identity do not assume equally named programmes share a broadcaster.
                            val identity = attr("tvg-id").ifBlank { java.security.MessageDigest.getInstance("SHA-256").digest(line.toByteArray()).joinToString("") { "%02x".format(it) } }
                            val id = "playlist:$identity"
                            val source = LiveSource(id, null, line, authorised = true)
                            val existing = channels[id]
                            channels[id] = existing?.copy(sources = (existing.sources + source).distinctBy { it.url }) ?: LiveChannel(id, name, "USER", listOf("other"),
                                logo = attr("tvg-logo").takeIf { PublicTvUrl.syntaxSafe(it) }, sources = listOf(source))
                        }
                    }
                }
            }
        }
        return channels.values.toList()
    }
}

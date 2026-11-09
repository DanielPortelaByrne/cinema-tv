package com.danielportelabyrne.cinema.data.live

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal fun JSONObject.str(key: String): String? = optString(key).takeUnless { it.isBlank() || it == "null" }
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
internal fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()

object LiveCatalog {
    val endpoints = listOf("channels", "feeds", "streams", "logos", "guides", "blocklist")
    /** Streaming object traversal keeps only the launch catalogue's reviewed identities. */
    fun compact(endpoint: String, json: String): String {
        val ids = LiveProviders.seeds().map { it.id }.toSet() + OfficialLiveSources.channelIds
        val input = JSONTokener(json)
        require(input.nextClean() == '[') { "Expected television JSON array" }
        val output = JSONArray()
        if (input.nextClean() == ']') return output.toString()
        input.back()
        while (true) {
            val item = input.nextValue() as? JSONObject ?: error("Expected television record")
            val id = item.str(if (endpoint == "channels") "id" else "channel")
            if (id in ids) output.put(item)
            when (input.nextClean()) { ']' -> return output.toString(); ',' -> Unit; else -> error("Invalid catalogue array") }
        }
    }
    /** No arbitrary IPTV-org URL is promoted into a playback route. Reviewed broadcaster endpoints only. */
    fun parse(data: Map<String, String>): List<LiveChannel> {
        fun rows(key: String) = JSONArray(data[key] ?: "[]").objects()
        val blocked = rows("blocklist").mapNotNull { it.str("channel") }.toSet()
        val feeds = rows("feeds").mapNotNull { o ->
            val channel = o.str("channel") ?: return@mapNotNull null
            LiveFeed(channel, o.getString("id"), o.optString("name"), o.optBoolean("is_main"), o.strings("broadcast_area"))
        }.distinctBy { it.channel to it.id }.groupBy { it.channel }
        val streams = rows("streams").mapNotNull { o ->
            val id = o.str("channel") ?: return@mapNotNull null
            val url = o.str("url") ?: return@mapNotNull null
            if (!PublicTvUrl.syntaxSafe(url)) return@mapNotNull null
            val feed = o.str("feed")
            if (feed != null && feeds[id].orEmpty().none { it.id == feed }) return@mapNotNull null
            LiveSource(id, feed, url, o.strings("labels"), o.optString("quality"), OfficialLiveSources.permits(id, url))
        }.distinctBy { Triple(it.channel, it.feed, it.url) }.groupBy { it.channel }
        val logos = rows("logos").filter { o -> o.optBoolean("in_use") && PublicTvUrl.syntaxSafe(o.optString("url")) &&
            (o.str("feed") == null || feeds[o.optString("channel")].orEmpty().any { it.id == o.str("feed") }) }
            .sortedWith(compareBy<JSONObject> { o -> if (o.str("feed") == null) 0 else if (feeds[o.optString("channel")].orEmpty().any { it.id == o.str("feed") && it.main }) 1 else 2 }
                .thenBy { if (it.optString("format") == "PNG") 0 else 1 }).groupBy { it.optString("channel") }
        val guides = rows("guides").flatMap { o -> o.optJSONArray("sources")?.objects().orEmpty().mapNotNull { s ->
            val id = o.str("channel") ?: return@mapNotNull null
            val url = s.str("url") ?: return@mapNotNull null
            if (!PublicTvUrl.syntaxSafe(url) || s.optString("format") != "XML" ||
                (o.str("feed") != null && feeds[id].orEmpty().none { it.id == o.str("feed") })) null
            else GuideSource(id, o.str("feed"), o.optString("site_id"), url)
        } }.groupBy { it.channel }
        val channels = rows("channels").filter { !it.optBoolean("is_nsfw") && it.str("closed") == null && it.optString("id") !in blocked }
            .map { o -> val id = o.getString("id")
                val seed = LiveProviders.seeds().firstOrNull { it.id == id }
                LiveChannel(id, seed?.name ?: o.getString("name"), o.optString("country"), (o.strings("categories") + seed?.categories.orEmpty()).distinct(), (o.strings("alt_names") + seed?.aliases.orEmpty()).distinct(),
                    logos[id]?.firstOrNull()?.str("url"), feeds[id].orEmpty(), streams[id].orEmpty(), guides[id].orEmpty(), LiveProviders.forChannel(id))
            }.distinctBy { it.id }
        return (channels + LiveProviders.seeds().filter { seed -> channels.none { it.id == seed.id } }).sortedBy { it.name }
    }
}

/** Deliberately small review list; CDN presence alone is not evidence of permission. */
object OfficialLiveSources {
    private val origins = mapOf(
        "TVBrasil.br" to setOf("tvbrasil-stream.ebc.com.br"),
        "DW.de" to setOf("dwamdstream102.akamaized.net"),
        "France24.fr" to setOf("live.france24.com"),
        "NHKWorldJapan.jp" to setOf("masterpl.hls.nhkworld.jp", "media-osa.hls.nhkworld.jp", "media-tyo.hls.nhkworld.jp")
    )
    val channelIds: Set<String> get() = origins.keys
    fun permits(id: String, url: String): Boolean = runCatching {
        val u = java.net.URI(url)
        u.scheme == "https" && u.host in origins[id].orEmpty() && u.rawQuery == null && PublicTvUrl.syntaxSafe(url) &&
            // Keep language and international identities exact; no premium NHK World Premium feeds.
            (id != "France24.fr" || u.path.contains("/F24_EN_HI_HLS/")) &&
            (id != "NHKWorldJapan.jp" || u.path.startsWith("/hls/w/live/")) &&
            (id != "TVBrasil.br" || u.path == "/index.m3u8") &&
            (id != "DW.de" || u.path.startsWith("/hls/live/2015525/dwstream102/"))
    }.getOrDefault(false)
}

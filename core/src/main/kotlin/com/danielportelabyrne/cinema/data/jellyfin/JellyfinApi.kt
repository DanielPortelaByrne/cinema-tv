package com.danielportelabyrne.cinema.data.jellyfin

import org.json.JSONObject
import java.net.URLEncoder

/** One film or show in the Jellyfin library. [resumeMs] is where the signed-in user left off (0 when unwatched). */
data class JellyfinItem(
    val id: String,
    val name: String,
    val year: Int?,
    val type: String,
    val mediaSourceId: String?,
    val resumeMs: Long = 0L,
    val played: Boolean = false,
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val overview: String? = null,
    val genres: List<String> = emptyList(),
    val officialRating: String? = null,
    val runtimeMin: Int? = null,
    val hasBackdrop: Boolean = false,
    /** For an episode: season and episode numbers. */
    val season: Int? = null,
    val episode: Int? = null,
    val seriesName: String? = null,
    val seriesId: String? = null,
    val episodeEnd: Int? = null,
    val missing: Boolean = false,
    /** Whether the server holds a Primary image for it (an episode's still). */
    val hasPrimaryImage: Boolean = false,
    /** Filled only by the single-item fetch the player makes: chapters, trickplay tiles, file size and rating. */
    val chapters: List<com.danielportelabyrne.cinema.data.player.Chapter> = emptyList(),
    val trickplay: com.danielportelabyrne.cinema.data.player.Trickplay? = null,
    val sizeBytes: Long? = null,
    val communityRating: Double? = null,
    /** The default soundtrack's codec ("eac3", "aac"…), when the server listed the file's streams. */
    val audioCodec: String? = null,
    /** When it, or a series' newest episode, reached the server. */
    val addedMs: Long? = null,
    /** When the user last played it, or any episode of a series. */
    val playedMs: Long? = null
) {
    /** Whichever is newer: added or last watched. */
    val recentMs: Long get() = maxOf(addedMs ?: 0L, playedMs ?: 0L)

    val isSeries: Boolean get() = type == "Series"
    val isEpisode: Boolean get() = type == "Episode"

    /** "S1 E3" for an episode, else null. */
    fun episodeLabel(): String? = if (season != null && episode != null) "S$season E$episode" else null

    /** Jellyfin names a single-file film's source after the film itself, so that is the safe default. */
    val sourceId: String get() = mediaSourceId ?: id

    val isMovie: Boolean get() = type == "Movie"

    /** The key the player keeps this title's place under: an episode by its id, a film by its IMDb id when it has one. */
    val resumeKey: String get() = if (isEpisode) "jf:$id" else imdbId ?: "jf:$id"

    /** What a card calls it: an episode's series, else the film's own name. */
    val displayTitle: String get() = if (isEpisode) seriesName ?: name else name

    /** "S1 E3 · Pilot" for an episode, the year for a film. */
    val displaySubtitle: String? get() = if (isEpisode) listOfNotNull(episodeLabel(), name.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { null } else year?.toString()
}

/** One scheduled programme on a live channel. */
data class Programme(val id: String, val channelId: String, val title: String, val overview: String?, val startMs: Long, val endMs: Long, val year: Int?)

/** A live channel from Jellyfin's Live TV (the owner's own Tunarr channels). [nowPlaying] comes from the guide. */
data class LiveChannel(
    val id: String,
    val name: String,
    val number: String?,
    val nowPlaying: String?,
    val nowEndsAtMs: Long?,
    val hasImage: Boolean
)

/**
 * The plain-Kotlin half of the Jellyfin client: how requests are written and responses read. Everything that
 * touches the network or storage lives in [JellyfinClient], so this part can be unit tested.
 */
object JellyfinApi {
    const val CLIENT = "Cinema for Android TV"
    const val VERSION = "1.0"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** The Authorization header Jellyfin expects. [token] is omitted before sign-in. */
    fun authHeader(deviceId: String, deviceName: String, token: String? = null): String =
        "MediaBrowser Client=\"$CLIENT\", Device=\"${deviceName.replace('"', '\'')}\", DeviceId=\"$deviceId\", Version=\"$VERSION\"" +
            (token?.let { ", Token=\"$it\"" } ?: "")

    fun cleanBase(server: String): String {
        val t = server.trim().trimEnd('/')
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
    }

    /**
     * Every film that has an IMDb id, with its provider ids. Matching happens on the TV ([findByImdb]) because the
     * server's own provider-id filter (AnyProviderIdEquals) is ignored by Jellyfin 12.2: asking for one film
     * returns the whole library, so trusting its first answer would "find" the wrong film every time.
     */
    fun libraryUrl(base: String, userId: String): String =
        "$base/Items?userId=${enc(userId)}&Recursive=true&IncludeItemTypes=Movie,Series&SortBy=DateCreated&SortOrder=Descending" +
            "&Fields=ProviderIds,Overview,Genres,OfficialRating,RunTimeTicks,DateCreated,DateLastMediaAdded&Limit=5000"

    /** Films and episodes, last played first. Never-played items sort last. */
    fun recentlyPlayedUrl(base: String, userId: String): String =
        "$base/Items?userId=${enc(userId)}&Recursive=true&IncludeItemTypes=Movie,Episode&SortBy=DatePlayed&SortOrder=Descending&Limit=300"

    /**
     * Films and episodes part-watched, most recently watched first. The server leaves out anything already finished. Asks for
     * more than the row shows because several episodes of one series each count, and the row keeps only the latest of each.
     */
    fun resumeUrl(base: String, userId: String): String =
        "$base/Users/${enc(userId)}/Items/Resume?Limit=40&Recursive=true&MediaTypes=Video" +
            "&Fields=RunTimeTicks,ProviderIds,Overview,Genres,DateCreated,DateLastMediaAdded"

    /** Gives each film and series its latest play, an episode counting for its series. */
    fun withPlays(library: List<JellyfinItem>, played: List<JellyfinItem>): List<JellyfinItem> {
        val latest = HashMap<String, Long>()
        played.forEach { p ->
            val at = p.playedMs ?: return@forEach
            val key = if (p.isEpisode) p.seriesId ?: return@forEach else p.id
            if (at > (latest[key] ?: 0L)) latest[key] = at
        }
        return library.map { item ->
            val at = latest[item.id]
            if (at != null && at > (item.playedMs ?: 0L)) item.copy(playedMs = at) else item
        }
    }

    /** Where to pick up a series: the next unwatched episode, or the first one when nothing has been watched. */
    fun nextUpUrl(base: String, userId: String, seriesId: String): String =
        "$base/Shows/NextUp?userId=${enc(userId)}&seriesId=${enc(seriesId)}&Limit=1"

    fun firstEpisodeUrl(base: String, userId: String, seriesId: String): String =
        "$base/Shows/${enc(seriesId)}/Episodes?userId=${enc(userId)}&Limit=1"

    /** Every film and episode with its files, for adding up how much space the library takes. */
    fun sizesUrl(base: String, userId: String): String =
        "$base/Items?userId=${enc(userId)}&Recursive=true&IncludeItemTypes=Movie,Episode&Fields=MediaSources&Limit=20000"

    /** The total size of the first file of each item, in bytes. */
    fun parseTotalBytes(json: String): Long {
        val sources = "\"MediaSources\":["
        var total = 0L
        var at = json.indexOf(sources)
        while (at >= 0) {
            val next = json.indexOf(sources, at + 1)
            val size = json.indexOf("\"Size\":", at)
            if (size >= 0 && (next < 0 || size < next)) {
                var i = size + 7
                val start = i
                while (i < json.length && json[i].isDigit()) i++
                if (i > start) total += json.substring(start, i).toLongOrNull() ?: 0L
            }
            at = next
        }
        return total
    }

    /** Every episode of a series the signed-in user can see, with their watch state. */
    fun episodesUrl(base: String, userId: String, seriesId: String): String =
        "$base/Shows/${enc(seriesId)}/Episodes?userId=${enc(userId)}&Fields=Overview,MediaSources,RunTimeTicks"

    /** Sequence follows the episode being watched, including rewatches; never jumps over a gap in a season. */
    fun followingEpisode(current: JellyfinItem, episodes: List<JellyfinItem>): JellyfinItem? {
        val season = current.season ?: return null
        val number = current.episodeEnd ?: current.episode ?: return null
        if (!current.isEpisode || current.seriesId == null) return null
        val ordered = episodes.filter { it.isEpisode && it.id != current.id &&
            it.seriesId == current.seriesId && it.season != null && it.episode != null &&
            (it.season == season && it.episode > number || season > 0 && it.season > season) }
            .sortedWith(compareBy<JellyfinItem> { it.season }.thenBy { it.episode })
        val next = ordered.firstOrNull() ?: return null
        val sequential = next.season == season && next.episode == number + 1 ||
            season > 0 && next.season == season + 1 && next.episode == 1
        return next.takeIf { sequential && !it.missing }
    }

    /** Fetch fresh user data before playing, including from an old Continue watching card. */
    fun itemUrl(base: String, userId: String, itemId: String): String =
        "$base/Users/${enc(userId)}/Items/${enc(itemId)}?Fields=MediaSources,Chapters,Trickplay,Overview,Genres,OfficialRating,CommunityRating"

    /** A chapter's still (`Images/Chapter/{index}`), when the server has one. */
    fun chapterImageUrl(base: String, itemId: String, index: Int, tag: String, maxWidth: Int = 480): String =
        "$base/Items/${enc(itemId)}/Images/Chapter/$index?tag=${enc(tag)}&maxWidth=$maxWidth&quality=85"

    /** One sheet of trickplay thumbnails at [width]. */
    fun trickplayUrl(base: String, itemId: String, width: Int, sheet: Int, sourceId: String): String =
        "$base/Videos/${enc(itemId)}/Trickplay/$width/$sheet.jpg?MediaSourceId=${enc(sourceId)}"

    /** A server resume position, including zero or a short position, takes precedence over local history. */
    fun resumePosition(serverMs: Long?, localMs: Long): Long =
        serverMs?.coerceAtLeast(0L) ?: localMs.takeIf { it > 60_000L } ?: 0L

    /** Where to tell Jellyfin that playback started, moved on, or stopped: kind is "Playing", "Progress" or "Stopped". */
    fun reportUrl(base: String, kind: String): String = if (kind == "Playing") "$base/Sessions/Playing" else "$base/Sessions/Playing/$kind"

    /** One playback report. Jellyfin counts position in 100-nanosecond ticks. */
    fun playbackBody(itemId: String, sourceId: String, positionMs: Long, paused: Boolean, sessionId: String): String = JSONObject()
        .put("ItemId", itemId).put("MediaSourceId", sourceId).put("PlaySessionId", sessionId)
        .put("PositionTicks", positionMs.coerceAtLeast(0L) * 10_000L).put("IsPaused", paused).put("CanSeek", true)
        .put("PlayMethod", "DirectStream").toString()

    fun backdropUrl(base: String, id: String, maxWidth: Int = 1920): String = "$base/Items/${enc(id)}/Images/Backdrop?maxWidth=$maxWidth&quality=85"

    /** The film or series with these ids (a TMDB id is used when the IMDb one is missing), or null. Never a title match. */
    fun findByIds(items: List<JellyfinItem>, imdbId: String?, tmdbId: String?, series: Boolean): JellyfinItem? = items.firstOrNull {
        (if (series) it.isSeries else it.isMovie) && ((imdbId != null && it.imdbId.equals(imdbId, ignoreCase = true)) || (tmdbId != null && it.tmdbId == tmdbId))
    }

    /** The film with this IMDb id, or null. Never falls back to a title match or "the first result". */
    fun findByImdb(items: List<JellyfinItem>, imdbId: String): JellyfinItem? =
        items.firstOrNull { it.isMovie && it.imdbId.equals(imdbId, ignoreCase = true) }

    /** The raw bytes of the film as stored. The token rides in a header, never in the address. */
    fun directStreamUrl(base: String, item: JellyfinItem): String =
        "$base/Videos/${item.id}/stream?static=true&mediaSourceId=${enc(item.sourceId)}"

    /** A copy Jellyfin re-encodes for this TV, used when the original file will not play as stored. */
    fun transcodeUrl(base: String, item: JellyfinItem, deviceId: String): String =
        "$base/Videos/${item.id}/master.m3u8?DeviceId=${enc(deviceId)}&MediaSourceId=${enc(item.sourceId)}" +
            "&VideoCodec=h264&AudioCodec=aac&MaxStreamingBitrate=20000000&TranscodingMaxAudioChannels=2&MaxAudioChannels=2&SegmentContainer=ts"

    /** Preserve the picture and convert unsupported surround audio for a stereo TV/analogue output. */
    fun stereoStreamUrl(base: String, itemId: String, sourceId: String, deviceId: String): String =
        "$base/Videos/${enc(itemId)}/master.m3u8?DeviceId=${enc(deviceId)}&MediaSourceId=${enc(sourceId)}" +
            "&VideoCodec=copy&AudioCodec=aac&AllowAudioStreamCopy=false&TranscodingMaxAudioChannels=2" +
            "&MaxAudioChannels=2&AudioBitRate=192000&SegmentContainer=ts"

    fun posterUrl(base: String, id: String, maxHeight: Int = 450): String = "$base/Items/${enc(id)}/Images/Primary?maxHeight=$maxHeight&quality=90"

    /** An episode's still (its Primary image), sized for a title card. */
    fun episodeImageUrl(base: String, id: String, maxWidth: Int = 480): String = "$base/Items/${enc(id)}/Images/Primary?maxWidth=$maxWidth&quality=85"

    // --- Programme guide -------------------------------------------------------------------------

    private fun isoUtc(ms: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(ms))

    /** Everything on any channel that ends after [fromMs] and starts before [toMs], in start order. */
    fun programsUrl(base: String, userId: String, fromMs: Long, toMs: Long): String =
        "$base/LiveTv/Programs?userId=${enc(userId)}&minEndDate=${enc(isoUtc(fromMs))}&maxStartDate=${enc(isoUtc(toMs))}" +
            "&sortBy=StartDate&Fields=Overview&limit=3000"

    fun parsePrograms(json: String): List<Programme> = try {
        val array = JSONObject(json).optJSONArray("Items")
        (0 until (array?.length() ?: 0)).mapNotNull { i ->
            val o = array!!.getJSONObject(i)
            val channel = o.optString("ChannelId").ifBlank { return@mapNotNull null }
            val start = utcMillis(o.optString("StartDate")) ?: return@mapNotNull null
            val end = utcMillis(o.optString("EndDate")) ?: return@mapNotNull null
            Programme(
                id = o.optString("Id"), channelId = channel, title = o.optString("Name"),
                overview = o.optString("Overview").ifBlank { null }, startMs = start, endMs = end,
                year = o.optInt("ProductionYear", 0).takeIf { it > 0 }
            )
        }.sortedBy { it.startMs }
    } catch (_: Exception) { emptyList() }

    // --- Live TV ---------------------------------------------------------------------------------

    fun channelsUrl(base: String, userId: String): String =
        "$base/LiveTv/Channels?userId=${enc(userId)}&AddCurrentProgram=true&SortBy=SortName"

    fun playbackInfoUrl(base: String, userId: String, channelId: String): String =
        "$base/Items/${enc(channelId)}/PlaybackInfo?UserId=${enc(userId)}&AutoOpenLiveStream=true"

    /** Asks for an HLS copy this TV can decode, which is all a live channel is offered as. */
    val PLAYBACK_INFO_BODY: String = JSONObject()
        .put("AutoOpenLiveStream", true)
        .put("DeviceProfile", JSONObject()
            .put("MaxStreamingBitrate", 20_000_000)
            .put("DirectPlayProfiles", org.json.JSONArray())
            .put("TranscodingProfiles", org.json.JSONArray().put(JSONObject()
                .put("Container", "ts").put("Type", "Video").put("AudioCodec", "aac").put("VideoCodec", "h264")
                .put("Protocol", "hls").put("Context", "Streaming"))))
        .toString()

    /** The playable address Jellyfin hands back for a live channel, made absolute. Null when it offered none. */
    fun parseLiveStreamUrl(base: String, json: String): String? = try {
        val sources = JSONObject(json).optJSONArray("MediaSources")
        val path = (0 until (sources?.length() ?: 0)).firstNotNullOfOrNull { sources!!.getJSONObject(it).optString("TranscodingUrl").ifBlank { null } }
        path?.let { if (it.startsWith("http")) it else base + (if (it.startsWith("/")) it else "/$it") }
    } catch (_: Exception) { null }

    fun parseChannels(json: String): List<LiveChannel> = try {
        val array = JSONObject(json).optJSONArray("Items")
        (0 until (array?.length() ?: 0)).mapNotNull { i ->
            val o = array!!.getJSONObject(i)
            if (o.optString("Type") != "TvChannel") return@mapNotNull null
            val id = o.optString("Id").ifBlank { return@mapNotNull null }
            val now = o.optJSONObject("CurrentProgram")
            LiveChannel(
                id = id,
                name = o.optString("Name"),
                number = o.optString("Number").ifBlank { null },
                nowPlaying = now?.optString("Name")?.ifBlank { null },
                nowEndsAtMs = now?.optString("EndDate")?.let { utcMillis(it) },
                hasImage = o.optJSONObject("ImageTags")?.has("Primary") == true
            )
        }
    } catch (_: Exception) { emptyList() }

    /** Jellyfin's timestamps look like 2026-10-20T12:51:00.0000000Z; seconds are all the guide needs. */
    fun utcMillis(stamp: String): Long? = try {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        f.parse(stamp.take(19))?.time
    } catch (_: Exception) { null }

    fun parseItems(json: String): List<JellyfinItem> = try {
        val array = JSONObject(json).optJSONArray("Items")
        (0 until (array?.length() ?: 0)).mapNotNull { parseItem(array!!.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }

    fun parseItem(o: JSONObject): JellyfinItem? {
        val id = o.optString("Id").ifBlank { return null }
        val sources = o.optJSONArray("MediaSources")
        val source = if (sources != null && sources.length() > 0) sources.optJSONObject(0)?.optString("Id")?.ifBlank { null } else null
        val data = o.optJSONObject("UserData")
        return JellyfinItem(
            id = id,
            name = o.optString("Name"),
            year = o.optInt("ProductionYear", 0).takeIf { it > 0 },
            type = o.optString("Type"),
            mediaSourceId = source,
            // Jellyfin counts in 100-nanosecond ticks.
            resumeMs = (data?.optLong("PlaybackPositionTicks", 0L) ?: 0L) / 10_000L,
            played = data?.optBoolean("Played", false) ?: false,
            imdbId = o.optJSONObject("ProviderIds")?.optString("Imdb")?.ifBlank { null },
            tmdbId = o.optJSONObject("ProviderIds")?.optString("Tmdb")?.ifBlank { null },
            overview = o.optString("Overview").ifBlank { null },
            genres = o.optJSONArray("Genres")?.let { g -> (0 until g.length()).map { g.optString(it) } }.orEmpty(),
            officialRating = o.optString("OfficialRating").ifBlank { null },
            runtimeMin = (o.optLong("RunTimeTicks", 0L) / 600_000_000L).toInt().takeIf { it > 0 },
            hasBackdrop = (o.optJSONArray("BackdropImageTags")?.length() ?: 0) > 0,
            season = if (o.has("ParentIndexNumber")) o.optInt("ParentIndexNumber") else null,
            episode = if (o.has("IndexNumber")) o.optInt("IndexNumber") else null,
            seriesName = o.optString("SeriesName").ifBlank { null },
            seriesId = o.optString("SeriesId").ifBlank { null },
            episodeEnd = if (o.has("IndexNumberEnd") && !o.isNull("IndexNumberEnd")) o.optInt("IndexNumberEnd") else null,
            missing = o.optBoolean("IsMissing") || o.optBoolean("IsVirtualItem") || o.optString("LocationType") == "Virtual",
            hasPrimaryImage = o.optJSONObject("ImageTags")?.has("Primary") == true,
            chapters = com.danielportelabyrne.cinema.data.player.PlayerMath.parseChapters(o.optJSONArray("Chapters")),
            trickplay = com.danielportelabyrne.cinema.data.player.PlayerMath.parseTrickplay(o.optJSONObject("Trickplay"), source),
            sizeBytes = sources?.optJSONObject(0)?.optLong("Size", 0L)?.takeIf { it > 0 },
            communityRating = o.optDouble("CommunityRating", 0.0).takeIf { it > 0 },
            audioCodec = defaultAudioCodec(sources?.optJSONObject(0)),
            addedMs = dateOf(o, "DateLastMediaAdded") ?: dateOf(o, "DateCreated"),
            playedMs = data?.let { dateOf(it, "LastPlayedDate") }
        )
    }

    /** Missing, null and unreadable dates are all null. */
    private fun dateOf(o: JSONObject, name: String): Long? = if (o.isNull(name)) null else utcMillis(o.optString(name))

    /** The codec of the source's default audio stream (or its first one), lower case; null when streams are not listed. */
    fun defaultAudioCodec(source: JSONObject?): String? {
        val streams = source?.optJSONArray("MediaStreams") ?: return null
        val audio = (0 until streams.length()).mapNotNull { streams.optJSONObject(it) }.filter { it.optString("Type") == "Audio" }
        val wanted = source.optInt("DefaultAudioStreamIndex", -1)
        val chosen = audio.firstOrNull { it.optInt("Index", -2) == wanted } ?: audio.firstOrNull { it.optBoolean("IsDefault") } ?: audio.firstOrNull()
        return chosen?.optString("Codec")?.lowercase()?.ifBlank { null }
    }
}

/** What the player needs to tell Jellyfin how far playback has got. [startMs] is where the server says the viewer left off. */
data class JellyfinReport(val base: String, val itemId: String, val sourceId: String, val startMs: Long) {
    fun toJson(): String = JSONObject().put("base", base).put("itemId", itemId).put("sourceId", sourceId).put("startMs", startMs).toString()

    companion object {
        fun fromJson(json: String?): JellyfinReport? = try {
            val o = JSONObject(json ?: return null)
            JellyfinReport(o.getString("base"), o.getString("itemId"), o.getString("sourceId"), o.optLong("startMs", 0L))
        } catch (_: Exception) { null }
    }
}

package com.danielportelabyrne.cinema.data.music

import com.danielportelabyrne.cinema.data.TimedCache
import com.danielportelabyrne.cinema.data.movies.HttpTransport
import com.danielportelabyrne.cinema.data.movies.httpTransport
import com.danielportelabyrne.cinema.platform.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

private const val TAG = "Subsonic"
private const val CLIENT = "Cinema"
private const val VERSION = "1.16.1"

/**
 * A Subsonic or OpenSubsonic server (Navidrome, Gonic, Airsonic...). Signs in with [apiKey] when set (the OpenSubsonic
 * extension), otherwise with [username] and [password].
 */
data class SubsonicConfig(val baseUrl: String, val username: String, val password: String = "", val apiKey: String = "")

data class MusicArtist(val id: String, val name: String, val albumCount: Int?, val coverArt: String?)

data class MusicAlbum(
    val id: String, val name: String, val artist: String?, val artistId: String?, val year: Int?,
    val songCount: Int?, val durationSec: Int?, val coverArt: String?
)

data class MusicTrack(
    val id: String, val title: String, val artist: String?, val album: String?, val albumId: String?,
    val number: Int?, val disc: Int?, val durationSec: Int?, val coverArt: String?, val contentType: String?
)

data class MusicSearch(val artists: List<MusicArtist>, val albums: List<MusicAlbum>, val tracks: List<MusicTrack>)

/** One line of lyrics; [startMs] is null when the lyrics are not timed. Blank text is an instrumental gap. */
data class LyricLine(val startMs: Long?, val text: String)

data class Lyrics(val synced: Boolean, val lines: List<LyricLine>) {
    /** The line playing at [positionMs]: the last that has started, or -1 before the first. */
    fun lineAt(positionMs: Long): Int {
        if (!synced) return -1
        var at = -1
        for (i in lines.indices) { if ((lines[i].startMs ?: 0L) <= positionMs) at = i else break }
        return at
    }
}

/** Album lists `getAlbumList2` offers. [key] is the server's name for it. */
enum class AlbumList(val key: String) {
    NEWEST("newest"), RECENT("recent"), FREQUENT("frequent"), RANDOM("random"), STARRED("starred"), BY_NAME("alphabeticalByName")
}

/** [NEEDS_API_KEY]: the server has no token sign-in (LDAP), and a plain password would have to go in the URL. */
enum class SignIn { OK, WRONG_CREDENTIALS, NEEDS_API_KEY, NOT_SUBSONIC, UNREACHABLE }

/** A server answer: the payload inside `subsonic-response`, or why there is none. */
sealed class SubsonicReply {
    data class Ok(val body: JSONObject) : SubsonicReply()
    data class Failed(val code: Int, val message: String) : SubsonicReply()
    object Junk : SubsonicReply()
}

/** The plain-Kotlin half of the client: building requests and reading answers. */
object SubsonicApi {
    /** Subsonic error codes that mean the sign-in itself is wrong. */
    private val authErrors = setOf(40, 42, 43, 44)

    /** Token auth unsupported (an LDAP-backed server). */
    const val TOKEN_UNSUPPORTED = 41

    /** "music.lan:4533/" -> "http://music.lan:4533". */
    fun normalize(address: String): String {
        val a = address.trim().trimEnd('/')
        return if (a.startsWith("http://", true) || a.startsWith("https://", true)) a else "http://$a"
    }

    fun md5(text: String): String = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** `?id=1&c=Cinema&v=1.16.1&f=json` for [endpoint], with no credentials. */
    fun path(endpoint: String, params: List<Pair<String, String>> = emptyList()): String =
        "/rest/$endpoint?" + (params + listOf("c" to CLIENT, "v" to VERSION, "f" to "json"))
            .joinToString("&") { (k, v) -> "$k=${encode(v)}" }

    /** The query that signs a request: the API key, or a salted token so the password never travels. */
    fun auth(config: SubsonicConfig, salt: String): String =
        if (config.apiKey.isNotBlank()) "apiKey=${encode(config.apiKey)}"
        else "u=${encode(config.username)}&t=${md5(config.password + salt)}&s=$salt"

    fun reply(text: String): SubsonicReply = try {
        val r = JSONObject(text).getJSONObject("subsonic-response")
        if (r.optString("status") == "ok") SubsonicReply.Ok(r)
        else r.optJSONObject("error").let { SubsonicReply.Failed(it?.optInt("code", 0) ?: 0, it?.optString("message").orEmpty()) }
    } catch (_: Exception) { SubsonicReply.Junk }

    fun isAuthError(code: Int) = code in authErrors

    private fun JSONObject.str(name: String): String? = optString(name, "").ifBlank { null }
    private fun JSONObject.int(name: String): Int? = if (has(name)) optInt(name, -1).takeIf { it >= 0 } else null
    private fun objects(a: JSONArray?): List<JSONObject> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }

    private fun artistOf(o: JSONObject): MusicArtist? {
        return MusicArtist(o.str("id") ?: return null, o.str("name") ?: return null, o.int("albumCount"), o.str("coverArt"))
    }

    private fun albumOf(o: JSONObject): MusicAlbum? {
        return MusicAlbum(
            o.str("id") ?: return null, o.str("name") ?: o.str("title") ?: return null, o.str("artist"), o.str("artistId"),
            o.int("year")?.takeIf { it > 0 }, o.int("songCount"), o.int("duration"), o.str("coverArt")
        )
    }

    private fun trackOf(o: JSONObject): MusicTrack? {
        return MusicTrack(
            o.str("id") ?: return null, o.str("title") ?: return null, o.str("artist"), o.str("album"), o.str("albumId"),
            o.int("track"), o.int("discNumber"), o.int("duration"), o.str("coverArt"), o.str("contentType")
        )
    }

    /** `getArtists`: every artist, across the alphabet's index groups. */
    fun artists(body: JSONObject): List<MusicArtist> =
        objects(body.optJSONObject("artists")?.optJSONArray("index")).flatMap { objects(it.optJSONArray("artist")) }.mapNotNull { artistOf(it) }

    /** `getAlbumList2`. */
    fun albumList(body: JSONObject): List<MusicAlbum> = objects(body.optJSONObject("albumList2")?.optJSONArray("album")).mapNotNull { albumOf(it) }

    /** `getArtist`: the artist and their albums. */
    fun artistAlbums(body: JSONObject): List<MusicAlbum> = objects(body.optJSONObject("artist")?.optJSONArray("album")).mapNotNull { albumOf(it) }

    /** `getAlbum`: the album and its songs, in disc and track order. */
    fun album(body: JSONObject): Pair<MusicAlbum, List<MusicTrack>>? {
        val o = body.optJSONObject("album") ?: return null
        val tracks = objects(o.optJSONArray("song")).mapNotNull { trackOf(it) }
            .sortedWith(compareBy({ it.disc ?: 0 }, { it.number ?: Int.MAX_VALUE }))
        return (albumOf(o) ?: return null) to tracks
    }

    /** `getRandomSongs`. */
    fun songs(body: JSONObject): List<MusicTrack> = objects(body.optJSONObject("randomSongs")?.optJSONArray("song")).mapNotNull { trackOf(it) }

    /**
     * OpenSubsonic `getLyricsBySongId`: the timed set if there is one, else the first. An LRC offset moves lines
     * earlier when positive, so it is taken off each start.
     */
    fun lyrics(body: JSONObject): Lyrics? {
        val sets = objects(body.optJSONObject("lyricsList")?.optJSONArray("structuredLyrics"))
        val set = sets.firstOrNull { it.optBoolean("synced") } ?: sets.firstOrNull() ?: return null
        val synced = set.optBoolean("synced")
        val offset = set.optLong("offset", 0L)
        val lines = objects(set.optJSONArray("line")).map { l ->
            LyricLine(if (synced && l.has("start")) (l.optLong("start") - offset).coerceAtLeast(0L) else null, l.optString("value").trim())
        }
        return tidy(Lyrics(synced, lines))
    }

    /** Classic `getLyrics`: one block of plain text. */
    fun plainLyrics(body: JSONObject): Lyrics? {
        val text = body.optJSONObject("lyrics")?.optString("value").orEmpty()
        return tidy(Lyrics(false, text.replace("\r", "").split('\n').map { LyricLine(null, it.trim()) }))
    }

    /** Drops blank lines at either end, and repeated blanks; null when nothing is left. */
    private fun tidy(l: Lyrics): Lyrics? {
        val trimmed = l.lines.dropWhile { it.text.isEmpty() }.dropLastWhile { it.text.isEmpty() }
        val lines = trimmed.filterIndexed { i, line -> line.text.isNotEmpty() || trimmed[i - 1].text.isNotEmpty() }
        return if (lines.none { it.text.isNotEmpty() }) null else Lyrics(l.synced, lines)
    }

    /** `search3`. */
    fun search(body: JSONObject): MusicSearch {
        val r = body.optJSONObject("searchResult3")
        return MusicSearch(
            objects(r?.optJSONArray("artist")).mapNotNull { artistOf(it) },
            objects(r?.optJSONArray("album")).mapNotNull { albumOf(it) },
            objects(r?.optJSONArray("song")).mapNotNull { trackOf(it) }
        )
    }
}

/**
 * Talks to one Subsonic server. Every call blocks and none throw. Credentials only ever go into the request being made:
 * [streamPath] and [coverPath] are kept and passed around bare, and [signed] adds the sign-in at the last moment.
 */
class SubsonicClient internal constructor(
    val config: SubsonicConfig,
    private val transport: HttpTransport,
    private val salt: () -> String
) {
    constructor(config: SubsonicConfig) : this(config, httpTransport(config.baseUrl), ::randomSalt)

    private val lists = TimedCache<List<MusicAlbum>>(10 * 60_000L)
    private val artistList = TimedCache<List<MusicArtist>>(30 * 60_000L)

    fun signIn(): SignIn {
        val reply = get("ping") ?: return SignIn.UNREACHABLE
        return when (reply) {
            is SubsonicReply.Ok -> SignIn.OK
            is SubsonicReply.Failed -> when {
                reply.code == SubsonicApi.TOKEN_UNSUPPORTED -> SignIn.NEEDS_API_KEY
                SubsonicApi.isAuthError(reply.code) -> SignIn.WRONG_CREDENTIALS
                else -> SignIn.NOT_SUBSONIC
            }
            SubsonicReply.Junk -> SignIn.NOT_SUBSONIC
        }
    }

    fun artists(): List<MusicArtist>? = artistList.get("all") { ok("getArtists")?.let { SubsonicApi.artists(it) } }

    fun albums(list: AlbumList, size: Int = 40, offset: Int = 0): List<MusicAlbum>? {
        val fetch = { ok("getAlbumList2", listOf("type" to list.key, "size" to "$size", "offset" to "$offset"))?.let { SubsonicApi.albumList(it) } }
        return if (list == AlbumList.RANDOM) fetch() else lists.get("${list.key}#$size#$offset", fetch)
    }

    fun artistAlbums(artistId: String): List<MusicAlbum>? = ok("getArtist", listOf("id" to artistId))?.let { SubsonicApi.artistAlbums(it) }

    fun album(albumId: String): Pair<MusicAlbum, List<MusicTrack>>? = ok("getAlbum", listOf("id" to albumId))?.let { SubsonicApi.album(it) }

    fun randomSongs(size: Int = 50): List<MusicTrack>? = ok("getRandomSongs", listOf("size" to "$size"))?.let { SubsonicApi.songs(it) }

    fun search(query: String): MusicSearch? = ok("search3", listOf("query" to query, "artistCount" to "10", "albumCount" to "20", "songCount" to "30"))
        ?.let { SubsonicApi.search(it) }

    /** The track's lyrics: timed from OpenSubsonic servers when they have them, else plain. Null when there are none. */
    fun lyrics(track: MusicTrack): Lyrics? {
        ok("getLyricsBySongId", listOf("id" to track.id))?.let { SubsonicApi.lyrics(it) }?.let { return it }
        val artist = track.artist ?: return null
        return ok("getLyrics", listOf("artist" to artist, "title" to track.title))?.let { SubsonicApi.plainLyrics(it) }
    }

    /** Tells the server a track is playing ([submission] false) or was played. */
    fun scrobble(trackId: String, submission: Boolean): Boolean =
        ok("scrobble", listOf("id" to trackId, "submission" to "$submission")) != null

    fun streamPath(trackId: String): String = SubsonicApi.path("stream", listOf("id" to trackId))

    fun coverPath(coverArt: String, size: Int): String = SubsonicApi.path("getCoverArt", listOf("id" to coverArt, "size" to "$size"))

    /** [path] as a full URL that signs in. Use it for the request and drop it; never log or keep it. */
    fun signed(path: String): String = config.baseUrl + path + "&" + SubsonicApi.auth(config, salt())

    private fun ok(endpoint: String, params: List<Pair<String, String>> = emptyList()): JSONObject? =
        (get(endpoint, params) as? SubsonicReply.Ok)?.body

    private fun get(endpoint: String, params: List<Pair<String, String>> = emptyList()): SubsonicReply? {
        val path = SubsonicApi.path(endpoint, params)
        val reply = transport.call("GET", path + "&" + SubsonicApi.auth(config, salt()), null, mapOf("Accept" to "application/json"))
        if (reply.code == 0) { Log.w(TAG, "$endpoint unreachable"); return null }
        if (reply.code !in 200..299) { Log.w(TAG, "$endpoint -> HTTP ${reply.code}"); return SubsonicReply.Junk }
        return SubsonicApi.reply(reply.body).also { if (it is SubsonicReply.Failed) Log.w(TAG, "$endpoint -> error ${it.code}") }
    }

    companion object {
        private val random = SecureRandom()
        fun randomSalt(): String = ByteArray(8).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
    }
}

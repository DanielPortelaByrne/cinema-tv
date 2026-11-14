package com.danielportelabyrne.cinema.mobile.data

import com.danielportelabyrne.cinema.data.exchange
import com.danielportelabyrne.cinema.platform.Platform
import com.danielportelabyrne.cinema.platform.Log
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The home screen's rows that the shared client does not fetch: Continue watching and Next up. */
data class HomeShelves(
    val resume: List<JellyfinItem> = emptyList(),
    val nextUp: List<JellyfinItem> = emptyList(),
    val films: List<JellyfinItem> = emptyList(),
    val series: List<JellyfinItem> = emptyList(),
) {
    val isEmpty: Boolean get() = resume.isEmpty() && nextUp.isEmpty() && films.isEmpty() && series.isEmpty()
}

/**
 * Asks Jellyfin for the home rows. The TV keeps its own Continue watching row on the device; on a desktop the
 * server's resume list is the one to trust, so it follows the viewer between the Deck and the TV. Uses the same
 * sign-in as [JellyfinClient] (its token, through [JellyfinClient.playbackHeaders]). Blocking; never throws.
 */
class Shelves(platform: Platform, private val client: JellyfinClient) {
    private val prefs = platform.preferences("jellyfin")
    private val userId: String? get() = prefs.getString("userId", null)

    fun load(force: Boolean = false): HomeShelves {
        val uid = userId ?: return HomeShelves()
        val base = JellyfinApi.cleanBase(client.server)
        val library = client.library(force)
        // One card per series: its latest half-watched episode.
        val resume = (get(ShelfUrls.resume(base, uid))?.let { JellyfinApi.parseItems(it) }
            ?: get(ShelfUrls.legacyResume(base, uid))?.let { JellyfinApi.parseItems(it) }
            ?: emptyList()).distinctBy { it.seriesId ?: it.id }
        // Next up repeats what is already half-watched; the resume row has those.
        val resumeIds = resume.map { it.id }.toSet()
        val nextUp = get(ShelfUrls.nextUp(base, uid))?.let { JellyfinApi.parseItems(it) }.orEmpty().filter { it.id !in resumeIds }
        return HomeShelves(
            resume = resume,
            nextUp = nextUp,
            films = library.filter { it.isMovie }.take(SHELF),
            series = library.filter { it.isSeries }.take(SHELF),
        )
    }

    private fun get(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 10000
        client.playbackHeaders().forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.setRequestProperty("Accept", "application/json")
        c.exchange {
            if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() }
            else { Log.w(TAG, "GET ${url.substringBefore('?')} -> HTTP ${c.responseCode}"); null }
        }
    } catch (e: Exception) {
        Log.w(TAG, "GET ${url.substringBefore('?')} failed: ${e.javaClass.simpleName}")
        null
    }

    private companion object {
        const val TAG = "Shelves"
        const val SHELF = 30
    }
}

object ShelfUrls {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private const val FIELDS = "Fields=Overview,RunTimeTicks,ProviderIds,Genres,OfficialRating&EnableUserData=true"

    /** Jellyfin 10.9 and later. */
    fun resume(base: String, userId: String) = "$base/UserItems/Resume?userId=${enc(userId)}&Limit=24&MediaTypes=Video&$FIELDS"

    /** Older servers. */
    fun legacyResume(base: String, userId: String) = "$base/Users/${enc(userId)}/Items/Resume?Limit=24&MediaTypes=Video&$FIELDS"

    fun nextUp(base: String, userId: String) = "$base/Shows/NextUp?userId=${enc(userId)}&Limit=24&$FIELDS"
}

/** How far through [item] the viewer is, 0..1, from its resume point and running time. 0 when either is unknown. */
fun watchedFraction(item: JellyfinItem): Float {
    val total = (item.runtimeMin ?: return 0f) * 60_000L
    if (total <= 0 || item.resumeMs <= 0) return 0f
    return (item.resumeMs.toFloat() / total).coerceIn(0f, 1f)
}

/** "1h 52m" or "48m". */
fun runtimeLabel(minutes: Int?): String? = minutes?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" }

/** "1:23:45" or "23:45". */
fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

package com.danielportelabyrne.cinema.data.letterboxd

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.danielportelabyrne.cinema.device.AndroidPlatform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Letterboxd for the Cinema page: who is followed, and every page it shows, kept on disk so a page opens at once from
 * the last copy and is then refreshed in the background (a page behind Letterboxd's browser check takes several
 * seconds to fetch). Pages are stored without their scripts and drawings, which are most of their size.
 */
/** [allowBrowser] false (the player) never starts the hidden browser: plain requests and the saved copies only. */
class LetterboxdRepository(activity: Activity, allowBrowser: Boolean = true) {
    /** Two browsers for the pages: a fetch takes whichever is free, so one slow page does not hold up the next. */
    private val pageLanes = java.util.concurrent.LinkedBlockingQueue<LetterboxdWeb>().apply { repeat(2) { add(LetterboxdWeb(activity, allowBrowser)) } }
    /** Poster lookups have a browser of their own, so the pages are not queued behind a rail of posters. */
    private val posterWeb = LetterboxdWeb(activity, allowBrowser)
    private val dir = File(activity.cacheDir, "letterboxd").apply { mkdirs() }
    private val prefs = activity.getSharedPreferences(LetterboxdPages.PREFS, Activity.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(2)
    /** The last [load] of each path that said when it was no longer wanted: a page no one wants is not fetched. */
    private val wanted = ConcurrentHashMap<String, () -> Boolean>()
    private val pages = PageCache(dir, { path ->
        val cancelled = { wanted[path]?.let { !it() } == true }
        if (cancelled()) null else {
            val waitStart = android.os.SystemClock.elapsedRealtime()
            val lane = pageLanes.take()
            com.danielportelabyrne.cinema.ui.Perf.took("lb lane wait $path", android.os.SystemClock.elapsedRealtime() - waitStart)
            try { lane.page(path, cancelled)?.let { slim(it) } } finally { pageLanes.put(lane) }
        }
    })
    private data class Parsed(val html: String, val value: Any)
    private data class Snapshot(val digest: String, val value: Any) : java.io.Serializable
    private val parsed = LinkedHashMap<String, Parsed>(32, 0.75f, true)

    // --- Who is followed ----------------------------------------------------------------------------

    /** Usernames followed here, in the order they were added. Empty until the owner follows someone. */
    var following: List<String>
        get() = prefs.getString("users", null)?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map { a.getString(it) } } } ?: DEFAULT
        private set(value) { prefs.edit().putString("users", JSONArray(value).toString()).apply() }

    fun follow(user: String) { if (following.none { it.equals(user, true) }) following = following + user }
    fun unfollow(user: String) { following = following.filterNot { it.equals(user, true) } }

    // --- Pages ----------------------------------------------------------------------------------------

    /**
     * Calls [done] on the main thread with what [parse] makes of [path]: first from the saved copy, if there is one,
     * then again from a fresh copy when the saved one is older than [maxAgeMs] (only if it changed). Null means the
     * page could not be had at all. [stillWanted] (a search typed past, a list left) lets a fetch that nobody wants any more
     * be dropped; [done] then hears null, which its caller can ignore. [background] is for lookups made in bulk as a page
     * is built (a long list asks about every film): they wait behind any page the owner has opened themselves.
     */
    fun <T> load(path: String, maxAgeMs: Long, parse: (String) -> T?, stillWanted: (() -> Boolean)? = null, background: Boolean = false, done: (T?) -> Unit) {
        if (stillWanted != null) wanted[path] = stillWanted else wanted.remove(path)
        val started = android.os.SystemClock.elapsedRealtime()
        // Each public URL has one model shape. Lambda class names contain runtime identities and cannot be disk keys.
        val key = path
        pages.load(path, maxAgeMs, { html ->
            val snapshot = File(dir, com.danielportelabyrne.cinema.ui.ImageCache.sha1("parsed-v2:" + key) + ".bin")
            val digest by lazy { java.security.MessageDigest.getInstance("SHA-256").digest(html.toByteArray())
                .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') } }
            @Suppress("UNCHECKED_CAST")
            val snapshotStart = android.os.SystemClock.elapsedRealtime()
            val hit = synchronized(parsed) { parsed[key]?.takeIf { it.html == html }?.value as? T } ?: runCatching {
                java.io.ObjectInputStream(snapshot.inputStream().buffered()).use { it.readObject() as Snapshot }
                    .takeIf { it.digest == digest }?.value as? T
            }.getOrNull()
            com.danielportelabyrne.cinema.ui.Perf.took("lb snapshot $path ${if (hit == null) "miss" else "hit"}", android.os.SystemClock.elapsedRealtime() - snapshotStart)
            val parseStart = android.os.SystemClock.elapsedRealtime()
            val value = hit ?: runCatching { parse(html) }.getOrNull()
            com.danielportelabyrne.cinema.ui.Perf.took("letterboxd parse $path", android.os.SystemClock.elapsedRealtime() - parseStart)
            if (value != null) synchronized(parsed) {
                parsed[key] = Parsed(html, value as Any)
                while (parsed.size > 32) parsed.remove(parsed.keys.first())
            }
            main.post {
                com.danielportelabyrne.cinema.ui.Perf.took("letterboxd data $path", android.os.SystemClock.elapsedRealtime() - started)
                done(value)
            }
            // Retain parsed public data across process restarts. A digest and schema version invalidate old snapshots.
            // Only app-private, self-written files are read; corrupt or incompatible copies fall back to HTML parsing.
            if (hit == null && value is java.io.Serializable) runCatching {
                val tmp = File.createTempFile(snapshot.name, ".tmp", dir)
                try {
                    java.io.ObjectOutputStream(tmp.outputStream().buffered()).use { it.writeObject(Snapshot(digest, value)) }
                    if (!tmp.renameTo(snapshot)) tmp.delete()
                } finally { tmp.delete() }
            }
        }, { main.post { done(null) } }, background)
    }

    /** Blocking: the page now, saved copy first when it is recent enough. Null when it cannot be had. */
    fun pageNow(path: String, maxAgeMs: Long): String? {
        return pages.pageNow(path, maxAgeMs)
    }

    /** True when [path] is known not to exist (a member's film page they never logged, a username with no account). */
    fun missing(html: String?) = html != null && html.contains("<title>Letterboxd - Not Found</title>")

    // --- Posters --------------------------------------------------------------------------------------

    private val posters = ConcurrentHashMap<String, String>()
    private val posterFile = File(dir, "posters.json")
    @Volatile private var postersLoaded = false

    /** Blocking: a poster address for [slug] (460 x 690), from Letterboxd's small poster lookup. Kept for good. */
    fun posterUrl(slug: String): String? {
        if (!postersLoaded) loadPosters()
        posters[slug]?.let { return it }
        val lookupStart = android.os.SystemClock.elapsedRealtime()
        val json = posterWeb.json("/film/$slug/poster/std/230/") ?: run {
            com.danielportelabyrne.cinema.ui.Perf.took("lb poster $slug lookup failed", android.os.SystemClock.elapsedRealtime() - lookupStart)
            return null
        }
        // Plain JSON, or (through the browser) the same JSON shown as text inside a page.
        val url = runCatching { JSONObject(json).optString("url2x").ifBlank { null } }.getOrNull()
            ?: Regex("\"url2x\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)?.replace("\\/", "/")
            ?: return null
        posters[slug] = url
        savePostersSoon()
        com.danielportelabyrne.cinema.ui.Perf.took("lb poster $slug lookup", android.os.SystemClock.elapsedRealtime() - lookupStart)
        return url
    }

    @Synchronized private fun loadPosters() {
        if (postersLoaded) return
        runCatching { val o = JSONObject(posterFile.readText()); o.keys().forEach { posters[it] = o.getString(it) } }
        postersLoaded = true
    }

    private val saveTask = Runnable { pool.execute { runCatching { File(dir, "posters.tmp").also { it.writeText(JSONObject(posters as Map<*, *>).toString()) }.renameTo(posterFile) } } }
    private fun savePostersSoon() { main.removeCallbacks(saveTask); main.postDelayed(saveTask, 2000) }

    fun close() { main.removeCallbacks(saveTask); pages.close(); pool.shutdownNow(); pageLanes.forEach { it.close() }; posterWeb.close() }

    companion object {
        val DEFAULT get() = LetterboxdPages.DEFAULT
        const val MINUTE = LetterboxdPages.MINUTE
        const val HOUR = LetterboxdPages.HOUR
        fun followingCount(context: android.content.Context): Int = LetterboxdPages.followingCount(AndroidPlatform(context))
        fun forgetFollowing(context: android.content.Context) = LetterboxdPages.forgetFollowing(AndroidPlatform(context))
        fun slim(html: String): String = LetterboxdPages.slim(html)
    }
}

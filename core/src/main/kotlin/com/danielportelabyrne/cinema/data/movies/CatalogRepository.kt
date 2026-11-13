package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.exchange
import com.danielportelabyrne.cinema.platform.Platform
import com.danielportelabyrne.cinema.platform.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private const val TAG = "Catalog"

/**
 * Fetches the Movies page's shelves from JustWatch and keeps each on disk, so the page opens at once. Blocking.
 * One process-wide [ShelfCache] holds them: a file per shelf, parsed shelves in memory for the session, and a stale
 * shelf shown straight away while a fresh copy is fetched in the background for next time.
 */
class CatalogRepository(platform: Platform) {
    private val cache = shared(platform)
    private val channels = ChannelRepository(platform)

    /** One shelf: from cache when there is one (refreshed behind the scenes when old), otherwise the network. */
    fun shelf(spec: ShelfSpec): Shelf {
        // A channel's shows come from Wikidata and Cinemeta, which keep their own copies.
        spec.channel?.let { return Shelf(spec.heading, channels.titles(it, if (spec.ranked) 10 else 20)) }
        // Keyed by what is asked, not the heading: "Films" means something different on every service's page.
        val body = Catalog.requestBody(spec)
        return Shelf(spec.heading, cache.titles(body, keep = spec.query == null))   // what someone typed is not worth keeping
    }

    companion object {
        private const val TTL_MS = 6L * 60 * 60 * 1000
        @Volatile private var instance: ShelfCache? = null

        private fun shared(platform: Platform): ShelfCache = instance ?: synchronized(this) {
            instance ?: run {
                // The old cache kept every shelf in one SharedPreferences XML (1.7 MB), rewritten whole on every save and
                // waited on when an activity pauses. It is replaced by files; drop it once.
                if (platform.hasPreferences("catalog")) platform.deletePreferences("catalog")
                ShelfCache(File(platform.cacheDir, "catalog"), TTL_MS, System::currentTimeMillis, ::fetch,
                    Executors.newSingleThreadExecutor { r -> Thread(r, "catalog-refresh").apply { priority = Thread.MIN_PRIORITY } })
            }.also { instance = it }
        }

        private fun fetch(body: String): String? = try {
            val c = URL("https://apis.justwatch.com/graphql").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 20_000; c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("User-Agent", "CinemaTV/1.0 (Android TV)")
            c.exchange {
                c.outputStream.use { it.write(body.toByteArray()) }
                if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else { Log.w(TAG, "HTTP ${c.responseCode}"); null }
            }
        } catch (e: Exception) { Log.w(TAG, "fetch failed: ${e.javaClass.simpleName}"); null }
    }
}

/**
 * Stale-while-revalidate cache of catalogue shelves, keyed by the request body. Each shelf is one file in [dir]
 * (written to a temporary file and renamed, so a crash never leaves half a shelf), parsed once and then held in
 * memory. A shelf older than [ttlMs] is still returned at once; a fresh copy is fetched on [refresher] for next time.
 */
class ShelfCache(
    private val dir: File,
    private val ttlMs: Long,
    private val clock: () -> Long,
    private val fetch: (String) -> String?,
    private val refresher: Executor
) {
    private class Held(val titles: List<Title>, val savedAt: Long)
    private val memory = ConcurrentHashMap<String, Held>()
    private val refreshing: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val reading = ConcurrentHashMap<String, Any>()

    fun titles(body: String, keep: Boolean): List<Title> {
        if (!keep) return fetch(body)?.let(::parse).orEmpty()
        val key = key(body)
        // One reader per shelf: a row asking while the start-up pre-read is parsing it waits for that parse.
        val held = memory[key] ?: synchronized(reading.getOrPut(key) { Any() }) { memory[key] ?: read(key)?.also { memory[key] = it } }
        if (held != null) {
            if (clock() - held.savedAt >= ttlMs) refreshLater(key, body)
            return held.titles
        }
        return fetchAndStore(key, body)?.titles.orEmpty()
    }

    private fun refreshLater(key: String, body: String) {
        if (!refreshing.add(key)) return
        try {
            refresher.execute { try { fetchAndStore(key, body) } finally { refreshing.remove(key) } }
        } catch (_: java.util.concurrent.RejectedExecutionException) { refreshing.remove(key) }
    }

    /** Fetches a shelf, and keeps it only when it holds titles: an empty answer never replaces a good copy. */
    private fun fetchAndStore(key: String, body: String): Held? {
        val raw = fetch(body) ?: return null
        val titles = parse(raw)
        if (titles.isEmpty()) return null
        val held = Held(titles, clock())
        memory[key] = held
        write(key, raw, held.savedAt)
        return held
    }

    private fun read(key: String): Held? = try {
        val file = File(dir, "$key.json")
        if (!file.exists()) null else {
            val text = file.readText()
            val cut = text.indexOf('\n')
            val savedAt = text.substring(0, cut).toLong()
            parse(text.substring(cut + 1)).takeIf { it.isNotEmpty() }?.let { Held(it, savedAt) }
        }
    } catch (_: Exception) { null }

    private fun write(key: String, raw: String, savedAt: Long) {
        try {
            dir.mkdirs()
            val tmp = File(dir, "$key.tmp")
            tmp.writeText("$savedAt\n$raw")
            val target = File(dir, "$key.json")
            // Atomic replace on Android; a filesystem that will not rename over a file (the unit tests' host) gets a delete first.
            if (!tmp.renameTo(target) && !(target.delete() && tmp.renameTo(target))) tmp.delete()
        } catch (e: Exception) { Log.w(TAG, "cache write failed: ${e.javaClass.simpleName}") }
    }

    private fun parse(raw: String): List<Title> = Catalog.dedupe(Catalog.parse(raw))

    private fun key(body: String): String =
        MessageDigest.getInstance("SHA-1").digest(body.toByteArray()).joinToString("") { "%02x".format(it) }
}

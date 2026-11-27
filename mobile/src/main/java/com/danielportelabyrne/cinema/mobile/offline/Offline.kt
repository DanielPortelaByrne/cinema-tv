package com.danielportelabyrne.cinema.mobile.offline

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.mutableStateListOf
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.movies.ByteSize
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * A film or episode kept on the phone to watch without the server. [token] tells one request to download it from a
 * later one. [status] says why an unfinished one is not moving. [legacyId] is the Android DownloadManager entry that an
 * older build of Cinema made for it, or -1.
 */
data class OfflineCopy(
    val id: String, val name: String, val subtitle: String?, val poster: String?, val file: String, val token: Long,
    val sizeBytes: Long?, val resumeMs: Long = 0L, val complete: Boolean = false, val failed: Boolean = false,
    val source: String? = null, val legacyId: Long = -1L,
    val doneBytes: Long = 0L, val speed: Long = 0L, val status: String? = null,
) {
    val progress: Float get() = if (complete) 1f else sizeBytes?.takeIf { it > 0 }?.let { (doneBytes.toFloat() / it).coerceIn(0f, 1f) } ?: 0f

    /** The file while it is still arriving, and which version of it that is. */
    val partFile: File get() = File("$file.part")
    val tagFile: File get() = File("$file.tag")

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("subtitle", subtitle).put("poster", poster)
        .put("file", file).put("token", token).put("size", sizeBytes ?: -1).put("resume", resumeMs).put("complete", complete)
        .put("failed", failed).put("error", status.takeIf { failed }).put("source", source).put("downloadId", legacyId)

    companion object {
        private fun JSONObject.text(name: String): String? = if (isNull(name)) null else optString(name).ifBlank { null }.takeIf { it != "null" }

        fun of(o: JSONObject): OfflineCopy {
            val legacy = o.optLong("downloadId", -1)
            return OfflineCopy(o.getString("id"), o.getString("name"), o.text("subtitle"), o.text("poster"), o.getString("file"),
                o.optLong("token", legacy), o.optLong("size", -1).takeIf { it > 0 }, o.optLong("resume"), o.optBoolean("complete"),
                o.optBoolean("failed"), o.text("source"), legacy, status = o.text("error"))
        }
    }
}

/**
 * Offline copies: Jellyfin's original files, and what is known about each. [DownloadService] fetches the unfinished ones,
 * one at a time in list order. Files live in the app's own folder, so uninstalling Cinema removes them. Each copy's
 * poster is kept beside it, so the list looks right with no network. Change the list on the main thread only.
 */
class Offline(private val context: Context, private val client: JellyfinClient) {
    private val prefs = context.getSharedPreferences("offline", Context.MODE_PRIVATE)
    val copies = mutableStateListOf<OfflineCopy>().apply {
        addAll(read().map { if (it.complete) it else it.copy(doneBytes = it.partFile.length()) })
    }

    init { adoptOldDownloads() }

    private fun read(): List<OfflineCopy> = try {
        JSONArray(prefs.getString("copies", "[]")).let { a -> (0 until a.length()).map { OfflineCopy.of(a.getJSONObject(it)) } }
    } catch (_: Exception) { emptyList() }

    private fun save() = prefs.edit().putString("copies", JSONArray(copies.map { it.toJson() }).toString()).apply()

    /**
     * Older builds gave downloads to Android's DownloadManager, which Android 17 keeps off the home network. One it
     * finished is kept; one it had not is taken back, to be fetched by Cinema.
     */
    private fun adoptOldDownloads() {
        val unfinished = copies.filter { !it.complete && it.legacyId >= 0 }
        if (unfinished.isEmpty()) return
        val dm = context.getSystemService(DownloadManager::class.java) ?: return
        unfinished.forEach { c ->
            val finished = runCatching {
                dm.query(DownloadManager.Query().setFilterById(c.legacyId))?.use {
                    it.moveToFirst() && it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                }
            }.getOrNull() == true
            if (!finished) { runCatching { dm.remove(c.legacyId) }; File(c.file).delete() }
            val i = copies.indexOf(c)
            copies[i] = if (finished) c.copy(complete = true, failed = false, status = null)
            else c.copy(legacyId = -1, failed = false, status = null, doneBytes = 0)
        }
        save()
    }

    fun find(id: String): OfflineCopy? = copies.firstOrNull { it.id == id }

    /** The one to fetch now, if any. */
    fun next(): OfflineCopy? = copies.firstOrNull { !it.complete && !it.failed }

    /** Whether this request to download is still wanted: not deleted, nor replaced by a newer one. */
    fun wants(id: String, token: Long): Boolean = find(id)?.token == token

    fun dir(): File = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "offline").apply { mkdirs() }

    private fun posterFile(id: String) = File(dir(), "$id.jpg")

    /** The poster kept on the phone, else the server's. */
    fun posterOf(copy: OfflineCopy): String? = posterFile(copy.id).takeIf { it.exists() }?.let { Uri.fromFile(it).toString() } ?: copy.poster

    /** Adds [item] to the downloads, and says what happened. A download that failed is tried again. */
    fun download(item: JellyfinItem): String {
        find(item.id)?.let {
            if (it.failed) { retry(it.id); return "Trying ${it.name} again" }
            return if (it.complete) "${item.name} is already on this phone" else "${item.name} is already downloading"
        }
        val base = JellyfinApi.cleanBase(client.server)
        val file = File(dir(), item.id + ".media")
        val name = if (item.isEpisode) (item.seriesName ?: item.name) else item.name
        val subtitle = if (item.isEpisode) "S${item.season ?: 0} E${item.episode ?: 0} · ${item.name}" else item.year?.toString()
        val size = item.sizeBytes
        val free = dir().usableSpace
        if (size != null && size > free) return "Not enough space: $name is ${ByteSize.format(size)}, ${ByteSize.format(free)} free"
        val poster = JellyfinApi.posterUrl(base, item.seriesId?.takeIf { item.isEpisode } ?: item.id)
        val copy = OfflineCopy(item.id, name, subtitle, poster, file.absolutePath, System.currentTimeMillis(), size, item.resumeMs,
            source = item.mediaSourceId)
        // Left over from an earlier attempt.
        file.delete(); copy.partFile.delete(); copy.tagFile.delete()
        copies += copy
        save()
        return "Downloading $name over Wi-Fi" + (size?.let { " (${ByteSize.format(it)})" } ?: "")
    }

    /** Puts a failed download back in the queue. What had arrived is kept. */
    fun retry(id: String) {
        update(id, find(id)?.token ?: return) { it.copy(failed = false, status = null, speed = 0) }
        save()
    }

    private fun update(id: String, token: Long, change: (OfflineCopy) -> OfflineCopy): Boolean {
        val i = copies.indexOfFirst { it.id == id && it.token == token }
        if (i < 0) return false
        val next = change(copies[i])
        if (next != copies[i]) copies[i] = next
        return true
    }

    fun progress(id: String, token: Long, done: Long, total: Long, speed: Long) {
        val sizeBefore = find(id)?.sizeBytes
        update(id, token) { it.copy(doneBytes = done, sizeBytes = total.takeIf { t -> t > 0 } ?: it.sizeBytes, speed = speed, status = null) }
        if (find(id)?.sizeBytes != sizeBefore) save()
    }

    /** Why it is not moving just now. */
    fun waiting(id: String, token: Long, why: String) { update(id, token) { it.copy(status = why, speed = 0) } }

    fun finished(id: String, token: Long, size: Long) {
        if (update(id, token) { it.copy(complete = true, failed = false, sizeBytes = size, doneBytes = size, speed = 0, status = null) }) save()
    }

    fun failed(id: String, token: Long, why: String) {
        if (update(id, token) { it.copy(failed = true, status = why, speed = 0) }) save()
    }

    /** Fetches any poster not yet kept on the phone. Blocking; one that can't be fetched is tried again next time. */
    fun savePosters() {
        val base = JellyfinApi.cleanBase(client.server)
        copies.toList().forEach { c ->
            val url = c.poster ?: return@forEach
            val target = posterFile(c.id)
            if (target.exists()) return@forEach
            val part = File(dir(), "${c.id}.jpg.part")
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000; connection.readTimeout = 10_000
                // The sign-in only ever goes to the Jellyfin server.
                if (url.startsWith(base)) client.playbackHeaders().forEach { (k, v) -> connection.setRequestProperty(k, v) }
                try {
                    if (connection.responseCode == 200) {
                        connection.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
                        part.renameTo(target)
                    }
                } finally { connection.disconnect() }
            }
            part.delete()
        }
    }

    fun remove(id: String) {
        val c = find(id) ?: return
        if (c.legacyId >= 0) runCatching { context.getSystemService(DownloadManager::class.java)?.remove(c.legacyId) }
        File(c.file).delete(); c.partFile.delete(); c.tagFile.delete()
        posterFile(id).delete()
        copies.removeAll { it.id == id }
        save()
    }

    fun saveResume(id: String, ms: Long) {
        val i = copies.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        copies[i] = copies[i].copy(resumeMs = ms)
        save()
    }
}

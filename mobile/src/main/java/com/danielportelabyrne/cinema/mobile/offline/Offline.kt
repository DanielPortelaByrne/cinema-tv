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

/** A film or episode kept on the phone to watch without the server. [status] says why an unfinished one is not moving. */
data class OfflineCopy(
    val id: String, val name: String, val subtitle: String?, val poster: String?, val file: String, val downloadId: Long,
    val sizeBytes: Long?, val resumeMs: Long = 0L, val complete: Boolean = false, val failed: Boolean = false, val progress: Float = 0f,
    val status: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("subtitle", subtitle).put("poster", poster)
        .put("file", file).put("downloadId", downloadId).put("size", sizeBytes ?: -1).put("resume", resumeMs).put("complete", complete)

    companion object {
        fun of(o: JSONObject) = OfflineCopy(o.getString("id"), o.getString("name"), o.optString("subtitle").ifBlank { null }.takeIf { it != "null" },
            o.optString("poster").ifBlank { null }.takeIf { it != "null" }, o.getString("file"), o.getLong("downloadId"),
            o.optLong("size", -1).takeIf { it > 0 }, o.optLong("resume"), o.optBoolean("complete"))
    }
}

/**
 * Offline copies: Jellyfin's original file, fetched by Android's DownloadManager (it survives the app closing, shows
 * its own notification and resumes after a dropped connection). The sign-in rides in a header, never in the address.
 * Files live in the app's own folder, so uninstalling Cinema removes them. Each copy's poster is kept beside it, so
 * the list looks right with no network.
 */
class Offline(private val context: Context, private val client: JellyfinClient) {
    private val prefs = context.getSharedPreferences("offline", Context.MODE_PRIVATE)
    private val dm = context.getSystemService(DownloadManager::class.java)
    val copies = mutableStateListOf<OfflineCopy>().apply { addAll(read()) }

    private fun read(): List<OfflineCopy> = try {
        JSONArray(prefs.getString("copies", "[]")).let { a -> (0 until a.length()).map { OfflineCopy.of(a.getJSONObject(it)) } }
    } catch (_: Exception) { emptyList() }

    private fun save() = prefs.edit().putString("copies", JSONArray(copies.map { it.toJson() }).toString()).apply()

    fun find(id: String): OfflineCopy? = copies.firstOrNull { it.id == id }

    fun dir(): File = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "offline").apply { mkdirs() }

    private fun posterFile(id: String) = File(dir(), "$id.jpg")

    /** The poster kept on the phone, else the server's. */
    fun posterOf(copy: OfflineCopy): String? = posterFile(copy.id).takeIf { it.exists() }?.let { Uri.fromFile(it).toString() } ?: copy.poster

    /** Starts keeping [item] on the phone. A download that failed is started again. */
    fun download(item: JellyfinItem): String {
        find(item.id)?.let {
            if (it.failed) remove(it.id)
            else return if (it.complete) "${item.name} is already on this phone" else "${item.name} is already downloading"
        }
        val base = JellyfinApi.cleanBase(client.server)
        val file = File(dir(), item.id + ".media")
        val name = if (item.isEpisode) (item.seriesName ?: item.name) else item.name
        val subtitle = if (item.isEpisode) "S${item.season ?: 0} E${item.episode ?: 0} · ${item.name}" else item.year?.toString()
        val size = item.sizeBytes
        val free = dir().usableSpace
        if (size != null && size > free) return "Not enough space: $name is ${ByteSize.format(size)}, ${ByteSize.format(free)} free"
        // Left over from an earlier attempt, it would make DownloadManager save under another name.
        file.delete()
        val req = DownloadManager.Request(Uri.parse(client.streamUrl(item)))
            .setTitle(name).setDescription(subtitle ?: "Cinema")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
            .setAllowedOverMetered(false)
        client.playbackHeaders().forEach { (k, v) -> req.addRequestHeader(k, v) }
        val id = dm.enqueue(req)
        val poster = JellyfinApi.posterUrl(base, item.seriesId?.takeIf { item.isEpisode } ?: item.id)
        copies += OfflineCopy(item.id, name, subtitle, poster, file.absolutePath, id, size, item.resumeMs)
        save()
        return "Downloading $name over Wi-Fi" + (size?.let { " (${ByteSize.format(it)})" } ?: "")
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

    /** Reads each unfinished download's progress from DownloadManager. Works with no network. */
    fun poll() {
        var changed = false
        copies.toList().forEachIndexed { i, c ->
            if (c.complete) return@forEachIndexed
            dm.query(DownloadManager.Query().setFilterById(c.downloadId))?.use { cur ->
                val next = if (!cur.moveToFirst()) c.copy(failed = true, status = null) else {
                    val status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val reason = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    val done = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        changed = true
                        c.copy(complete = true, failed = false, progress = 1f, status = null, sizeBytes = total.takeIf { it > 0 } ?: c.sizeBytes)
                    } else c.copy(
                        failed = status == DownloadManager.STATUS_FAILED,
                        progress = if (total > 0) done.toFloat() / total else 0f,
                        sizeBytes = total.takeIf { it > 0 } ?: c.sizeBytes,
                        status = DownloadText.of(status, reason),
                    )
                }
                if (next != c) copies[i] = next
            }
        }
        if (changed) save()
    }

    fun remove(id: String) {
        val c = find(id) ?: return
        runCatching { dm.remove(c.downloadId) }
        File(c.file).delete()
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

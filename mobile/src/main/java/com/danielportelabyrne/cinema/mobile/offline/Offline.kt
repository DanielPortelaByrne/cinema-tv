package com.danielportelabyrne.cinema.mobile.offline

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.mutableStateListOf
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A film or episode kept on the phone to watch without the server. */
data class OfflineCopy(
    val id: String, val name: String, val subtitle: String?, val poster: String?, val file: String, val downloadId: Long,
    val sizeBytes: Long?, val resumeMs: Long = 0L, val complete: Boolean = false, val failed: Boolean = false, val progress: Float = 0f,
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
 * Files live in the app's own folder, so uninstalling Cinema removes them.
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

    /** Starts keeping [item] on the phone. */
    fun download(item: JellyfinItem): String {
        find(item.id)?.let { return if (it.complete) "${item.name} is already on this phone" else "${item.name} is already downloading" }
        val base = JellyfinApi.cleanBase(client.server)
        val file = File(dir(), item.id + ".media")
        val name = if (item.isEpisode) (item.seriesName ?: item.name) else item.name
        val subtitle = if (item.isEpisode) "S${item.season ?: 0} E${item.episode ?: 0} · ${item.name}" else item.year?.toString()
        val req = DownloadManager.Request(Uri.parse(client.streamUrl(item)))
            .setTitle(name).setDescription(subtitle ?: "Cinema")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
            .setAllowedOverMetered(false)
        client.playbackHeaders().forEach { (k, v) -> req.addRequestHeader(k, v) }
        val id = dm.enqueue(req)
        val poster = JellyfinApi.posterUrl(base, item.seriesId?.takeIf { item.isEpisode } ?: item.id)
        copies += OfflineCopy(item.id, name, subtitle, poster, file.absolutePath, id, item.sizeBytes, item.resumeMs)
        save()
        return "Downloading $name over Wi-Fi"
    }

    /** Reads each unfinished download's progress from DownloadManager. */
    fun poll() {
        var changed = false
        copies.toList().forEachIndexed { i, c ->
            if (c.complete) return@forEachIndexed
            dm.query(DownloadManager.Query().setFilterById(c.downloadId))?.use { cur ->
                if (!cur.moveToFirst()) { copies[i] = c.copy(failed = true); return@use }
                val status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val done = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                copies[i] = when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> { changed = true; c.copy(complete = true, progress = 1f, sizeBytes = total.takeIf { it > 0 } ?: c.sizeBytes) }
                    DownloadManager.STATUS_FAILED -> c.copy(failed = true)
                    else -> c.copy(progress = if (total > 0) done.toFloat() / total else 0f)
                }
            }
        }
        if (changed) save()
    }

    fun remove(id: String) {
        val c = find(id) ?: return
        runCatching { dm.remove(c.downloadId) }
        File(c.file).delete()
        copies.removeAll { it.id == id }
        save()
    }

    fun saveResume(id: String, ms: Long) {
        val i = copies.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        copies[i] = copies[i].copy(resumeMs = ms)
        save()
    }
}

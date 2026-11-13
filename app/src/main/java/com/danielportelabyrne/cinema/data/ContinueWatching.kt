package com.danielportelabyrne.cinema.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** One part-watched title, as last seen playing in some app. */
data class ResumeItem(
    val title: String,
    val subtitle: String?,
    val packageName: String,
    /** Local file holding the artwork the app supplied (a poster or still, not a video frame). */
    val imageUri: String?,
    val positionMs: Long,
    val durationMs: Long,
    val intentUri: String?,
    val lastEngagedMs: Long,
    /** For streams the app plays itself: everything needed to resume that exact stream. */
    val payload: String? = null,
    /** Whatever identifier the app's media session offers (a YouTube video id or link, for instance). Used to find a thumbnail. */
    val mediaId: String? = null
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    fun remainingLabel(): String? {
        if (positionMs <= 0) return null
        if (durationMs <= 0) return "Stopped at ${clock(positionMs)}"
        val mins = ((durationMs - positionMs) / 60000).toInt().coerceAtLeast(1)
        return if (mins >= 60) "${mins / 60} h ${mins % 60} min left" else "$mins min left"
    }

    private fun clock(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    fun launchIntent(): Intent? = try {
        intentUri?.let { Intent.parseUri(it, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    } catch (_: Exception) { null }
}

/**
 * Where the player got to in streams it played itself, with a frame for artwork, so a stream can be resumed at the same
 * point. Jellyfin keeps its own resume points; this is only for streams that are not in the library.
 */
class ContinueWatching(private val context: Context) {
    private val prefs = com.danielportelabyrne.cinema.device.AndroidPlatform.personalPrefs(context, "continue_watching")
    private val artDir = File(context.filesDir, "resume_art").apply { mkdirs() }

    @Synchronized
    fun load(limit: Int = 12): List<ResumeItem> = try {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).map { array.getJSONObject(it) }.map { o ->
            ResumeItem(
                o.getString("title"), o.optString("subtitle").ifBlank { null }, o.getString("pkg"),
                o.optString("art").ifBlank { null }, o.optLong("pos"), o.optLong("dur"), null, o.optLong("t"), o.optString("payload").ifBlank { null }, o.optString("mid").ifBlank { null }
            )
        }.sortedByDescending { it.lastEngagedMs }.take(limit)
    } catch (_: Exception) { emptyList() }

    /** Records or updates [item]. A finished title (past 95%) is removed instead. */
    @Synchronized
    fun save(item: ResumeItem, art: Bitmap?) {
        val all = load(50).filterNot { it.packageName == item.packageName && it.title == item.title }.toMutableList()
        if (item.durationMs > 0 && item.positionMs >= item.durationMs * 0.95) {
            persist(all); return
        }
        val existing = load(50).firstOrNull { it.packageName == item.packageName && it.title == item.title }
        var artPath = item.imageUri ?: existing?.imageUri
        if (art != null) artPath = writeArt(item, art) ?: artPath
        all += item.copy(imageUri = artPath, mediaId = ResumeLinks.mergeIds(item.mediaId, existing?.mediaId))
        persist(all.sortedByDescending { it.lastEngagedMs }.take(20))
    }

    private fun writeArt(item: ResumeItem, bitmap: Bitmap): String? = try {
        val scaled = if (bitmap.width > 640) Bitmap.createScaledBitmap(bitmap, 640, (bitmap.height * 640f / bitmap.width).toInt().coerceAtLeast(1), true) else bitmap
        val file = File(artDir, "${(item.packageName + item.title).hashCode().toUInt()}.jpg")
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        file.absolutePath
    } catch (_: Exception) { null }

    private fun persist(items: List<ResumeItem>) {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject().put("title", it.title).put("subtitle", it.subtitle ?: "").put("pkg", it.packageName)
                .put("art", it.imageUri ?: "").put("payload", it.payload ?: "").put("mid", it.mediaId ?: "").put("pos", it.positionMs).put("dur", it.durationMs).put("t", it.lastEngagedMs))
        }
        prefs.edit().putString("items", array.toString()).apply()
        // Drop artwork no longer referenced.
        val keep = items.mapNotNull { it.imageUri }.toSet()
        artDir.listFiles()?.filter { it.absolutePath !in keep }?.forEach { it.delete() }
    }

    fun remove(item: ResumeItem) {
        persist(load(50).filterNot { it.packageName == item.packageName && it.title == item.title })
    }

    /** Blocking: call off the main thread. */
    fun image(path: String): Bitmap? = try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
}

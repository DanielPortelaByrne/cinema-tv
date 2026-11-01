package com.danielportelabyrne.cinema.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.danielportelabyrne.cinema.device.AndroidPlatform
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * What other apps on the TV use to read Cinema's Continue watching, and to open a row. Names follow the installed
 * package, so the CI build (".ci" suffix) and the owner's copy can be installed side by side.
 */
object ContinueWatchingContract {
    const val EXTRA_KEY = "key"

    /** Columns of the items URI: key, title, subtitle, progress_ms, duration_ms, last_ms, art_uri. */
    val COLUMNS = arrayOf("key", "title", "subtitle", "progress_ms", "duration_ms", "last_ms", "art_uri")

    fun authority(packageName: String): String = "$packageName.continuewatching"
    /** Declared on the provider. Any app can ask for it; the data is the owner's own titles and artwork. */
    fun permission(packageName: String): String = "$packageName.permission.CONTINUE_WATCHING"
    /** Start the app's CinemaActivity with this action and [EXTRA_KEY] set to a row's key. */
    fun actionResume(packageName: String): String = "$packageName.action.RESUME"

    fun itemsUri(packageName: String): Uri = Uri.parse("content://${authority(packageName)}/items")

    /** Artwork for a row, as a content address the reader opens with openInputStream. */
    fun artUri(packageName: String, key: String): Uri =
        Uri.Builder().scheme("content").authority(authority(packageName)).appendPath("art").appendPath(key).build()

    private const val STREAM_PREFIX = "stream:"
    private const val JELLYFIN_PREFIX = "jf:"

    /** A key for a stream this app played, by its title. */
    fun streamKey(title: String): String = STREAM_PREFIX + URLEncoder.encode(title, "UTF-8")
    fun streamTitle(key: String): String? = key.takeIf { it.startsWith(STREAM_PREFIX) }?.let { URLDecoder.decode(it.removePrefix(STREAM_PREFIX), "UTF-8") }
    /** A key for one of the owner's Jellyfin films or episodes, by its item id. */
    fun jellyfinKey(itemId: String): String = JELLYFIN_PREFIX + itemId
    fun jellyfinItemId(key: String): String? = key.takeIf { it.startsWith(JELLYFIN_PREFIX) }?.removePrefix(JELLYFIN_PREFIX)
}

/**
 * Read-only list of what is part-way through in Cinema: the streams this app played itself, and the owner's Jellyfin
 * films and episodes in progress. Artwork is served by [openFile], so the reader never needs Jellyfin's sign-in, and
 * a stream's saved payload stays inside this app: a reader only gets the key it sends back to resume.
 */
class ContinueWatchingProvider : ContentProvider() {
    private data class Row(val key: String, val title: String, val subtitle: String?, val progressMs: Long, val durationMs: Long, val lastMs: Long)

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
        val context = context ?: return MatrixCursor(ContinueWatchingContract.COLUMNS)
        val cursor = MatrixCursor(ContinueWatchingContract.COLUMNS)
        rows(context).forEach { r ->
            cursor.addRow(arrayOf(r.key, r.title, r.subtitle, r.progressMs, r.durationMs, r.lastMs, ContinueWatchingContract.artUri(context.packageName, r.key).toString()))
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = context ?: throw FileNotFoundException("no context")
        val key = uri.lastPathSegment ?: throw FileNotFoundException("no key")
        val file = art(context, key) ?: throw FileNotFoundException("no artwork for $key")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    private fun rows(context: Context): List<Row> {
        // Only streams with a saved payload can be reopened, so the rest are left out.
        val streams = ContinueWatching(context).load(12).filter { it.payload != null }.map {
            Row(ContinueWatchingContract.streamKey(it.title), it.title, it.subtitle, it.positionMs, it.durationMs, it.lastEngagedMs)
        }
        val client = JellyfinClient(AndroidPlatform(context))
        val library = if (client.isConnected) client.continueWatching() else emptyList()
        val jellyfin = library.map { item ->
            Row(ContinueWatchingContract.jellyfinKey(item.id), item.displayTitle, item.displaySubtitle, item.resumeMs, (item.runtimeMin ?: 0) * 60_000L, item.playedMs ?: 0L)
        }
        // One row a title: a series shows only the episode watched most recently.
        return latestPerTitle(streams + jellyfin, { it.title }, { it.lastMs }).take(12)
    }

    private fun art(context: Context, key: String): File? {
        ContinueWatchingContract.streamTitle(key)?.let { title ->
            return ContinueWatching(context).load(12).firstOrNull { it.title == title }?.imageUri?.let(::File)?.takeIf { it.exists() }
        }
        ContinueWatchingContract.jellyfinItemId(key)?.let { id ->
            val client = JellyfinClient(AndroidPlatform(context)).takeIf { it.isConnected } ?: return null
            val file = File(context.cacheDir, "continue_${id.hashCode()}.jpg")
            if (file.exists() && file.length() > 0) return file
            val base = JellyfinApi.cleanBase(client.server)
            // A film's backdrop when it has one, else its picture (an episode's still).
            val headers = client.playbackHeaders()
            val fetched = download(JellyfinApi.backdropUrl(base, id, 640), headers, file) || download(JellyfinApi.episodeImageUrl(base, id, 640), headers, file)
            return file.takeIf { fetched }
        }
        return null
    }

    private fun download(url: String, headers: Map<String, String>, to: File): Boolean {
        val connection = try { URL(url).openConnection() as HttpURLConnection } catch (_: Exception) { return false }
        return try {
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            if (connection.responseCode != 200) false
            else {
                connection.inputStream.use { input -> to.outputStream().use { input.copyTo(it) } }
                to.length() > 0
            }
        } catch (_: Exception) { false } finally { connection.disconnect() }
    }

}

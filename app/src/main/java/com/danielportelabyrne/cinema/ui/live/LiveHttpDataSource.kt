package com.danielportelabyrne.cinema.ui.live

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.danielportelabyrne.cinema.data.live.PublicTvUrl
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** Validate every manifest, key, segment and redirect, including those supplied by user playlists. */
@UnstableApi
class LiveHttpDataSource : BaseDataSource(true) {
    private var connection: HttpURLConnection? = null
    private var input: InputStream? = null
    private var opened = false
    private var remaining = C.LENGTH_UNSET.toLong()
    private var uri: Uri? = null
    override fun getUri(): Uri? = uri
    override fun getResponseHeaders(): Map<String, List<String>> = connection?.headerFields?.filterKeys { it != null }.orEmpty()
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        var url = dataSpec.uri.toString()
        try {
            repeat(5) {
                PublicTvUrl.validate(url)
                val c = URI(url).toURL().openConnection() as HttpURLConnection
                connection = c
                c.instanceFollowRedirects = false; c.connectTimeout = 10_000; c.readTimeout = 12_000
                c.setRequestProperty("User-Agent", "CinemaTV-Live/1.0")
                if (dataSpec.position > 0 || dataSpec.length != C.LENGTH_UNSET.toLong()) {
                    val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) "" else (dataSpec.position + dataSpec.length - 1).toString()
                    c.setRequestProperty("Range", "bytes=${dataSpec.position}-$end")
                }
                val status = c.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    url = URI(url).resolve(c.getHeaderField("Location") ?: throw IOException("Missing redirect")).toString()
                    c.disconnect(); connection = null
                } else {
                    if (status !in 200..299) throw IOException(if (status in listOf(401, 403, 451)) "Access restricted (HTTP $status); location or account eligibility may be required" else "HTTP $status")
                    input = c.inputStream
                    if (status == 200 && dataSpec.position > 0) {
                        var skip = dataSpec.position
                        val buffer = ByteArray(8192)
                        while (skip > 0) { val n = input!!.read(buffer, 0, minOf(skip, buffer.size.toLong()).toInt()); if (n < 0) throw IOException("Unexpected end of stream"); skip -= n }
                    }
                    remaining = dataSpec.length
                    uri = Uri.parse(url); opened = true; transferStarted(dataSpec)
                    return remaining
                }
            }
            throw IOException("Too many redirects")
        } catch (e: Exception) { close(); throw if (e is IOException) e else IOException("Unsafe or unreachable television URL", e) }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val n = input?.read(buffer, offset, if (remaining < 0) length else minOf(length.toLong(), remaining).toInt()) ?: C.RESULT_END_OF_INPUT
        if (n > 0) { if (remaining > 0) remaining -= n; bytesTransferred(n) }
        return n
    }
    override fun close() {
        try { input?.close() } finally {
            input = null; connection?.disconnect(); connection = null; uri = null
            if (opened) { opened = false; transferEnded() }
        }
    }
    class Factory : DataSource.Factory { override fun createDataSource(): DataSource = LiveHttpDataSource() }
}

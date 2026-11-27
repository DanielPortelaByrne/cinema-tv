package com.danielportelabyrne.cinema.mobile.offline

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Fetches one file over HTTP, carrying on from wherever an earlier attempt got to. */
object Transfer {
    /** The server answered, and not with the file. */
    class Refused(val code: Int) : IOException("HTTP $code")

    /**
     * Appends what is missing from [part]. [tag] remembers which version of the file [part] holds, so a file that has
     * changed on the server is started again rather than stitched onto the old one. [progress] gets the bytes held and
     * the file's size (-1 when the server does not say). Returns true once the whole file is there, false when
     * [keepGoing] said to stop. Anything else throws, leaving [part] to carry on from. Blocking.
     */
    fun fetch(
        url: String, headers: Map<String, String>, part: File, tag: File,
        keepGoing: () -> Boolean, progress: (done: Long, total: Long) -> Unit,
    ): Boolean {
        val have = part.length()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            // A compressed answer would not give the file's real length.
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (have > 0) {
                connection.setRequestProperty("Range", "bytes=$have-")
                if (tag.exists()) connection.setRequestProperty("If-Range", tag.readText())
            }
            val code = connection.responseCode
            if (code == 416 && have > 0) {
                // Nothing left to send: either it is all here, or what is here is not this file.
                if (rangeTotal(connection.getHeaderField("Content-Range")) == have) return true
                part.delete()
                throw IOException("the saved part does not fit the file")
            }
            if (code != 200 && code != 206) throw Refused(code)
            val from: Long
            val total: Long
            if (code == 206) {
                val range = connection.getHeaderField("Content-Range")
                if (rangeStart(range) != have) { part.delete(); throw IOException("the server sent a different range") }
                from = have
                total = rangeTotal(range) ?: -1L
            } else {
                from = 0L
                total = connection.contentLengthLong
                val version = connection.getHeaderField("ETag")?.takeUnless { it.startsWith("W/") } ?: connection.getHeaderField("Last-Modified")
                if (version == null) tag.delete() else tag.writeText(version)
            }
            var done = from
            progress(done, total)
            FileOutputStream(part, from > 0).use { out ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        if (!keepGoing()) return false
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        progress(done, total)
                    }
                }
            }
            if (total > 0 && done < total) throw IOException("the connection ended early")
            return true
        } finally { connection.disconnect() }
    }

    /** The first byte in "bytes 100-199/200". */
    fun rangeStart(header: String?): Long? = header?.substringAfter("bytes ", "")?.substringBefore('-')?.trim()?.toLongOrNull()

    /** The file's size in "bytes 100-199/200" or "bytes *&#47;200". */
    fun rangeTotal(header: String?): Long? = header?.substringAfter('/', "")?.trim()?.toLongOrNull()
}

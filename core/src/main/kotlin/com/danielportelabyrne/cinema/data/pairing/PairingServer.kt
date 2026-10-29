package com.danielportelabyrne.cinema.data.pairing

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** One request to a pairing page. [route] is the first path segment (`setup`, `save`, `restore`). */
class PairingRequest(
    val method: String,
    val route: String,
    val contentType: String?,
    val body: ByteArray,
    /** The query string of the address (`?address=...`), decoded. Empty when there is none. */
    val query: Map<String, String> = emptyMap(),
) {
    /** The fields of a `application/x-www-form-urlencoded` body, or empty for anything else. */
    val form: Map<String, String> by lazy { if (body.isEmpty()) emptyMap() else parseForm(body) }

    private fun parseForm(bytes: ByteArray): Map<String, String> =
        String(bytes, Charsets.UTF_8).split('&').mapNotNull { pair ->
            val name = pair.substringBefore('=', missingDelimiterValue = "")
            if (name.isEmpty()) return@mapNotNull null
            name.decode() to pair.substringAfter('=', "").decode()
        }.toMap()

    private fun String.decode(): String = URLDecoder.decode(this, Charsets.UTF_8.name())
}

class PairingResponse(
    val status: Int,
    val contentType: String,
    val body: ByteArray,
    val headers: Map<String, String> = emptyMap(),
) {
    companion object {
        fun html(status: Int, html: String) = PairingResponse(status, "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
        fun text(status: Int, text: String) = PairingResponse(status, "text/plain; charset=utf-8", text.toByteArray(Charsets.UTF_8))
        fun file(name: String, bytes: ByteArray) = PairingResponse(
            200, "application/octet-stream", bytes,
            mapOf("Content-Disposition" to "attachment; filename=\"$name\""),
        )
    }
}

/**
 * A small HTTP server for the setup pages a phone uses. It answers only while open: it closes itself when the
 * [pairing] expires or is spent, and when [close] is called. Every request must carry the pairing code as the second
 * path segment (`/setup/123456`), or it is refused before [routes] sees it.
 *
 * GET requests load a page and do not spend the code. POST requests are submissions: the code is spent only when
 * [routes] answers with a 2xx status, so a wrong password can be corrected without scanning again.
 *
 * Plain HTTP on the home network is deliberate: the phone and TV are on the same Wi-Fi, and the code plus the TV's
 * own address are what gate access. Nothing here is sent to a third party.
 */
class PairingServer(
    private val pairing: Pairing,
    private val routes: (PairingRequest) -> PairingResponse,
    port: Int = 0,
) : Closeable {
    private val socket = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(port))
        soTimeout = ACCEPT_POLL_MILLIS
    }

    @Volatile private var open = true

    /** Connections are handled in parallel; daemon threads, so an idle one never keeps the app alive. */
    private val connections = Executors.newCachedThreadPool { task -> Thread(task, "cinema-pairing-connection").apply { isDaemon = true } }

    /** The port the server is listening on. Useful when [port] was 0, as in tests. */
    val localPort: Int get() = socket.localPort

    val isOpen: Boolean get() = open

    private val worker = thread(name = "cinema-pairing", isDaemon = true) { acceptLoop() }

    /** The address a phone should open for [page] (`setup`, `save` or `restore`), given the TV's address on the network. */
    fun urlFor(host: String, page: String): String = "http://$host:$localPort/$page/${pairing.code}"

    private fun acceptLoop() {
        try {
            while (open) {
                if (pairing.isSpent) break
                val client = try { socket.accept() } catch (_: SocketTimeoutException) { continue }
                // One thread per connection: a phone's idle spare connection must not hold up the page it really wants.
                connections.execute { client.use { handle(it) } }
            }
        } catch (_: IOException) {
            // The socket was closed from close(); nothing else to do.
        } finally {
            close()
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = CLIENT_TIMEOUT_MILLIS
        val response = try {
            val request = readRequest(client.getInputStream()) ?: return
            answer(request)
        } catch (_: BadRequest) {
            PairingResponse.text(400, "Bad request")
        } catch (_: IOException) {
            return
        }
        writeResponse(client.getOutputStream(), response)
    }

    private fun answer(request: ParsedRequest): PairingResponse {
        val segments = request.path.trim('/').split('/')
        if (segments.size != 2 || segments[0].isEmpty()) return PairingResponse.text(404, "Not found")
        val (route, presented) = segments
        if (request.method != "GET" && request.method != "POST") return PairingResponse.text(405, "Method not allowed")

        val submitting = request.method == "POST"
        val check = if (submitting) pairing.begin(presented) else pairing.check(presented)
        when (check) {
            Pairing.Check.WRONG_CODE -> return PairingResponse.text(403, "That code is wrong. Scan the code on the TV again.")
            Pairing.Check.EXPIRED -> return PairingResponse.text(410, "This code has expired. Start again on the TV.")
            Pairing.Check.USED -> return PairingResponse.text(410, "This code has already been used. Start again on the TV.")
            Pairing.Check.OK -> Unit
        }
        return try {
            val response = routes(PairingRequest(request.method, route, request.contentType, request.body, request.query))
            if (submitting) pairing.finish(response.status in 200..299)
            response
        } catch (e: RuntimeException) {
            if (submitting) pairing.finish(false)
            PairingResponse.text(500, "Something went wrong on the TV. Try again.")
        }
    }

    override fun close() {
        if (!open) return
        open = false
        try { socket.close() } catch (_: IOException) { }
        // Finish any answer already being written (the one that spent the code), then stop taking new work.
        connections.shutdown()
    }

    private class ParsedRequest(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val contentType: String?,
        val body: ByteArray,
    )

    private class BadRequest : IOException("malformed request")

    private fun readRequest(input: InputStream): ParsedRequest? {
        val head = readHead(input) ?: return null
        val lines = head.split("\r\n")
        val requestLine = lines.firstOrNull()?.split(' ') ?: throw BadRequest()
        if (requestLine.size < 2) throw BadRequest()
        val headers = lines.drop(1).filter { it.contains(':') }.associate {
            it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY_BYTES) throw BadRequest()
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) throw BadRequest()
            read += n
        }
        val path = requestLine[1].substringBefore('?')
        val query = parseQuery(requestLine[1].substringAfter('?', ""))
        return ParsedRequest(requestLine[0], path, query, headers["content-type"], body)
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isEmpty()) return emptyMap()
        return raw.split('&').mapNotNull { pair ->
            val name = pair.substringBefore('=', "")
            if (name.isEmpty()) return@mapNotNull null
            URLDecoder.decode(name, Charsets.UTF_8.name()) to URLDecoder.decode(pair.substringAfter('=', ""), Charsets.UTF_8.name())
        }.toMap()
    }

    /** Reads up to the blank line that ends the headers. Returns null when the client sent nothing at all. */
    private fun readHead(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        var lastFour = 0
        while (out.size() < MAX_HEAD_BYTES) {
            val b = input.read()
            if (b < 0) return if (out.size() == 0) null else throw BadRequest()
            out.write(b)
            lastFour = (lastFour shl 8) or b
            if (lastFour == CRLF_CRLF) return String(out.toByteArray(), Charsets.ISO_8859_1).removeSuffix("\r\n\r\n")
        }
        throw BadRequest()
    }

    private fun writeResponse(output: OutputStream, response: PairingResponse) {
        val reason = when (response.status) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            410 -> "Gone"
            500 -> "Internal Server Error"
            else -> "Status"
        }
        val sb = StringBuilder("HTTP/1.1 ${response.status} $reason\r\n")
        sb.append("Content-Type: ${response.contentType}\r\n")
        sb.append("Content-Length: ${response.body.size}\r\n")
        sb.append("Cache-Control: no-store\r\n")
        sb.append("X-Content-Type-Options: nosniff\r\n")
        sb.append("Connection: close\r\n")
        response.headers.forEach { (k, v) -> sb.append("$k: $v\r\n") }
        sb.append("\r\n")
        output.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        output.write(response.body)
        output.flush()
    }

    companion object {
        private const val ACCEPT_POLL_MILLIS = 500
        private const val CLIENT_TIMEOUT_MILLIS = 10_000
        private const val MAX_HEAD_BYTES = 16 * 1024
        private const val CRLF_CRLF = 0x0D0A0D0A
        /** A restore file is a few kilobytes of settings and tokens, so this leaves plenty of room. */
        const val MAX_BODY_BYTES = 2 * 1024 * 1024
    }
}

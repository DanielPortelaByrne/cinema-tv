package com.danielportelabyrne.cinema.data.live

import java.net.InetAddress
import java.net.URI
import java.net.HttpURLConnection

/** Remote catalogues and playlists may never reach local hosts, carry credentials or choose arbitrary protocols. */
object PublicTvUrl {
    fun syntaxSafe(raw: String): Boolean = runCatching {
        val u = URI(raw)
        val host = u.host?.lowercase() ?: return false
        u.scheme == "https" && u.userInfo == null && u.port in listOf(-1, 443) &&
            host.contains('.') && !host.endsWith(".local") && !host.endsWith(".localhost") &&
            !host.endsWith(".internal") && host != "localhost" && !host.contains(':') &&
            !Regex("[0-9.]+").matches(host) && !raw.contains('\\') && !raw.contains('\n') && !raw.contains('\r') &&
            !Regex("(?i)(?:^|[&;])(?:token|access_token|api_key|apikey|password|passwd|username|auth|authorization)=").containsMatchIn(u.rawQuery.orEmpty())
    }.getOrDefault(false)

    fun validate(raw: String) {
        require(syntaxSafe(raw)) { "Only public HTTPS television URLs without credentials are supported" }
        val addresses = InetAddress.getAllByName(URI(raw).host)
        require(addresses.isNotEmpty() && addresses.all(::publicAddress)) { "Private/reserved network resources are not allowed" }
    }

    internal fun publicAddress(a: InetAddress): Boolean {
        if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress) return false
        val b = a.address.map { it.toInt() and 255 }
        if (b.size == 4) return b[0] != 0 && b[0] < 224 &&
            !(b[0] == 100 && b[1] in 64..127) && !(b[0] == 198 && b[1] in 18..19) &&
            !(b[0] == 192 && b[1] == 0 && b[2] in listOf(0, 2)) &&
            !(b[0] == 192 && b[1] == 88 && b[2] == 99) &&
            !(b[0] == 198 && b[1] == 51 && b[2] == 100) && !(b[0] == 203 && b[1] == 0 && b[2] == 113)
        // Only global unicast IPv6; exclude documentation, benchmarking and transition addresses.
        return b.size == 16 && (b[0] and 0xe0) == 0x20 && !(b[0] == 0x20 && b[1] == 2) &&
            !(b[0] == 0x20 && b[1] == 1 && ((b[2] == 0 && b[3] in listOf(0, 2)) || (b[2] == 0x0d && b[3] == 0xb8)))
    }

    fun read(raw: String, limit: Int = 20_000_000): String = String(readBytes(raw, limit), Charsets.UTF_8)
    fun readBytesForLogo(raw: String): ByteArray = readBytes(raw, 1_000_000)
    private fun readBytes(raw: String, limit: Int): ByteArray {
        var url = raw
        repeat(5) {
            validate(url)
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false; connection.connectTimeout = 10_000; connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", "CinemaTV-Live/1.0")
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    url = URI(url).resolve(connection.getHeaderField("Location") ?: error("Missing redirect")).toString()
                } else {
                    require(status in 200..299) { "Television service returned HTTP $status" }
                    return connection.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= limit) { "Feed is too large" }; out.write(buffer, 0, n) }
                        out.toByteArray()
                    }
                }
            } finally { connection.disconnect() }
        }
        error("Too many redirects")
    }
}

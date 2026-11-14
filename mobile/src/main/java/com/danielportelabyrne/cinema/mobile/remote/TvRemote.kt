package com.danielportelabyrne.cinema.mobile.remote

import com.danielportelabyrne.cinema.data.remote.RemoteProtocol
import com.danielportelabyrne.cinema.platform.Platform
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** A TV running Cinema, as the phone knows it. */
data class Tv(val name: String, val host: String, val port: Int = RemoteProtocol.PORT)

/**
 * The phone's half of the TV remote (see [RemoteProtocol]). Every call blocks; none throw. Paired TVs and their tokens
 * are kept with the phone's other secrets.
 */
class TvRemote(platform: Platform) {
    private val prefs = platform.preferences("remote")
    private val secrets = platform.secretStore()

    var current: Tv?
        get() = prefs.getString("host", null)?.let { Tv(prefs.getString("name", null) ?: it, it, prefs.getInt("port", RemoteProtocol.PORT)) }
        set(v) { prefs.edit().putString("host", v?.host).putString("name", v?.name).putInt("port", v?.port ?: RemoteProtocol.PORT).apply() }

    fun token(tv: Tv): String? = secrets.get("remote_${tv.host}")
    fun isPaired(tv: Tv): Boolean = token(tv) != null

    /** TVs that answer the broadcast; when none do (some routers drop broadcasts), the /24 is scanned for the port. */
    fun discover(timeoutMs: Int = 1500): List<Tv> {
        val found = LinkedHashMap<String, Tv>()
        try {
            DatagramSocket().use { s ->
                s.broadcast = true
                s.soTimeout = 300
                val hello = RemoteProtocol.HELLO.toByteArray()
                broadcastAddresses().forEach { addr -> runCatching { s.send(DatagramPacket(hello, hello.size, addr, RemoteProtocol.DISCOVERY_PORT)) } }
                val buf = ByteArray(1024)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val p = DatagramPacket(buf, buf.size)
                    try { s.receive(p) } catch (_: java.net.SocketTimeoutException) { continue }
                    val a = RemoteProtocol.parseAnnounce(String(p.data, 0, p.length)) ?: continue
                    val host = p.address.hostAddress ?: continue
                    found[host] = Tv(a.name, host, a.port)
                }
            }
        } catch (_: Exception) { }
        if (found.isEmpty()) scan().forEach { found[it.host] = it }
        return found.values.toList()
    }

    private fun broadcastAddresses(): List<InetAddress> = buildList {
        add(InetAddress.getByName("255.255.255.255"))
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }.mapNotNull { it.broadcast }.forEach { add(it) }
        }
    }

    private fun scan(): List<Tv> {
        val own = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }
        }.getOrNull() ?: return emptyList()
        val prefix = own.hostAddress!!.substringBeforeLast('.')
        val pool = Executors.newFixedThreadPool(32)
        val hits = java.util.concurrent.ConcurrentLinkedQueue<Tv>()
        (1..254).forEach { n ->
            pool.execute {
                val host = "$prefix.$n"
                try { Socket().use { it.connect(InetSocketAddress(host, RemoteProtocol.PORT), 250) }; hits += Tv(host, host) } catch (_: Exception) { }
            }
        }
        pool.shutdown(); pool.awaitTermination(6, TimeUnit.SECONDS)
        return hits.toList()
    }

    /** Asks the TV to show a pairing code. */
    fun startPairing(tv: Tv): Boolean = call(tv, "POST", "/pair", "", authed = false)?.first == 200

    /** Sends the code read off the TV; on success the token is kept and the TV becomes the current one. */
    fun confirm(tv: Tv, code: String): Boolean {
        val (status, body) = call(tv, "POST", "/pair/confirm", RemoteProtocol.encode("code" to code), authed = false) ?: return false
        if (status != 200 || body.isBlank()) return false
        secrets.put("remote_${tv.host}", body.trim())
        current = tv
        return true
    }

    fun forget(tv: Tv) { secrets.put("remote_${tv.host}", null); if (current?.host == tv.host) current = null }

    fun ping(tv: Tv): Boolean = call(tv, "GET", "/ping", null)?.first == 200
    fun press(tv: Tv, key: RemoteProtocol.Key): Boolean = call(tv, "POST", "/key", RemoteProtocol.encode("key" to key.name))?.first == 200
    fun type(tv: Tv, text: String): Boolean = call(tv, "POST", "/text", RemoteProtocol.encode("text" to text))?.first == 200

    /** Status and body, or null when the TV did not answer. A 401 means the TV forgot this phone. */
    private fun call(tv: Tv, method: String, path: String, body: String?, authed: Boolean = true): Pair<Int, String>? = try {
        val c = URL("http://${tv.host}:${tv.port}$path").openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 2000; c.readTimeout = 4000
        if (authed) token(tv)?.let { c.setRequestProperty(RemoteProtocol.TOKEN_HEADER, it) }
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        try {
            val code = c.responseCode
            code to (if (code in 200..299) c.inputStream.bufferedReader().readText() else "")
        } finally { c.disconnect() }
    } catch (_: Exception) { null }
}

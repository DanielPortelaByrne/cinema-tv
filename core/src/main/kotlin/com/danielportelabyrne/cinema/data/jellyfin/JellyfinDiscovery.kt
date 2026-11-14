package com.danielportelabyrne.cinema.data.jellyfin

import com.danielportelabyrne.cinema.platform.Log
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** A Jellyfin server that answered on the local network. [id] is the server's own, which stays the same when it moves. */
data class FoundServer(val name: String, val address: String, val id: String = "")

/**
 * Finds Jellyfin servers on the home network the way Jellyfin's own apps do: a UDP broadcast of "who is JellyfinServer?"
 * to port 7359, which every server answers with its name and address. Blocking; nothing is found on networks that drop
 * broadcasts, and that is fine, the address can still be typed.
 */
object JellyfinDiscovery {
    private const val PORT = 7359
    private const val QUESTION = "who is JellyfinServer?"

    fun find(timeoutMs: Int = 1500): List<FoundServer> {
        val found = LinkedHashMap<String, FoundServer>()
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = timeoutMs
                val question = QUESTION.toByteArray()
                socket.send(DatagramPacket(question, question.size, InetAddress.getByName("255.255.255.255"), PORT))
                val buffer = ByteArray(2048)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try { socket.receive(packet) } catch (_: SocketTimeoutException) { break }
                    parse(String(packet.data, 0, packet.length, Charsets.UTF_8))?.let { found[it.address] = it }
                }
            }
        } catch (e: Exception) {
            Log.w("JellyfinDiscovery", "discovery failed: ${e.javaClass.simpleName}")
        }
        return found.values.toList()
    }

    /** One server's answer: `{"Address":"http://192.168.1.20:8096","Id":"…","Name":"Living room"}`. */
    fun parse(json: String): FoundServer? = try {
        val o = JSONObject(json)
        val address = o.optString("Address").trim().trimEnd('/')
        if (address.isEmpty()) null else FoundServer(o.optString("Name").ifBlank { address }, address, o.optString("Id"))
    } catch (_: Exception) { null }
}

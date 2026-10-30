package com.danielportelabyrne.cinema.desktop.player

import com.danielportelabyrne.cinema.platform.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.SocketChannel
import java.nio.file.Path

/** Something mpv said: an event ("file-loaded", "end-file"...) or a changed property it was asked to watch. */
sealed class MpvMessage {
    data class Event(val name: String, val reason: String? = null, val error: String? = null, val args: List<String> = emptyList()) : MpvMessage()
    data class Property(val name: String, val value: Any?) : MpvMessage()
}

/** Reads and writes mpv's JSON IPC lines. Kept apart from the socket so it can be tested on its own. */
object MpvProtocol {
    fun command(vararg args: Any?): String = JSONObject().put("command", JSONArray(args.toList())).toString()

    /** Null for replies to commands and anything else that is not an event or a property change. */
    fun parse(line: String): MpvMessage? = try {
        val o = JSONObject(line)
        when (val event = o.optString("event")) {
            "" -> null
            "property-change" -> MpvMessage.Property(o.optString("name"), if (o.has("data") && !o.isNull("data")) o.get("data") else null)
            else -> MpvMessage.Event(event, o.optString("reason").ifBlank { null }, o.optString("file_error").ifBlank { null }, o.optJSONArray("args")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty())
        }
    } catch (_: Exception) { null }
}

/** One connection to mpv's IPC socket. [onMessage] runs on the reader thread; [onClosed] once, when mpv goes away. */
class MpvIpc private constructor(
    private val channel: SocketChannel,
    private val onMessage: (MpvMessage) -> Unit,
    private val onClosed: () -> Unit,
) {
    // The channel is read and written directly. Channels.newInputStream/newOutputStream would share one lock, so a
    // write would wait for the blocked read to finish: the first command would never reach mpv.
    init {
        Thread({
            val buffer = java.nio.ByteBuffer.allocate(64 * 1024)
            val line = java.io.ByteArrayOutputStream()
            try {
                while (channel.read(buffer) >= 0) {
                    buffer.flip()
                    while (buffer.hasRemaining()) {
                        val b = buffer.get()
                        if (b == '\n'.code.toByte()) { MpvProtocol.parse(line.toString(Charsets.UTF_8))?.let(onMessage); line.reset() }
                        else line.write(b.toInt())
                    }
                    buffer.clear()
                }
            } catch (_: Exception) {
            } finally { close(); onClosed() }
        }, "mpv-ipc").apply { isDaemon = true; start() }
    }

    fun send(vararg args: Any?) = sendRaw(MpvProtocol.command(*args))

    @Synchronized
    private fun sendRaw(json: String) {
        try {
            val data = java.nio.ByteBuffer.wrap((json + "\n").toByteArray(Charsets.UTF_8))
            while (data.hasRemaining()) channel.write(data)
        } catch (e: Exception) { Log.w(TAG, "send failed: ${e.javaClass.simpleName}") }
    }

    fun close() { try { channel.close() } catch (_: Exception) {} }

    companion object {
        private const val TAG = "mpv"

        /** Connects to [socket], waiting up to [timeoutMs] for mpv to create it. Null if it never does. */
        fun connect(socket: Path, timeoutMs: Long, alive: () -> Boolean, onMessage: (MpvMessage) -> Unit, onClosed: () -> Unit): MpvIpc? {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline && alive()) {
                try {
                    val ch = SocketChannel.open(StandardProtocolFamily.UNIX)
                    ch.connect(UnixDomainSocketAddress.of(socket))
                    return MpvIpc(ch, onMessage, onClosed)
                } catch (_: Exception) { Thread.sleep(100) }
            }
            return null
        }
    }
}
